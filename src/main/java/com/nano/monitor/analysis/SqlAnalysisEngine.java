package com.nano.monitor.analysis;

import com.nano.monitor.analysis.rule.RuleEngine;
import com.nano.monitor.model.AnalysisResult;
import com.nano.monitor.model.ExplainResult;
import com.nano.monitor.model.SqlRecord;
import com.nano.monitor.utils.BaselineUtil;
import com.nano.monitor.utils.SqlParamUtil;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.diagnostics.AbstractFailureAnalyzer;

import javax.sql.DataSource;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Map;

@Slf4j
public class SqlAnalysisEngine {

    private final SqlAnalysisCache cache;
    private final RuleEngine ruleEngine;
    private final ExplainAnalyzer explainAnalyzer;
    private final AiAnalysisService aiAnalysisService;

    private final long slowQueryThreshold;
    private final int aiTriggerScoreThreshold;

    public SqlAnalysisEngine(DataSource dataSource, RuleEngine ruleEngine) {
        this(dataSource, ruleEngine, null, 500, 3);
    }

    public SqlAnalysisEngine(DataSource dataSource, RuleEngine ruleEngine,
                             AiAnalysisService aiAnalysisService,
                             long slowQueryThreshold,
                             int aiTriggerScoreThreshold) {
        this.cache = new SqlAnalysisCache();
        this.ruleEngine = ruleEngine;
        this.explainAnalyzer = new ExplainAnalyzer(dataSource);
        this.aiAnalysisService = aiAnalysisService;
        this.slowQueryThreshold = slowQueryThreshold;
        this.aiTriggerScoreThreshold = aiTriggerScoreThreshold;
    }

    public AnalysisResult analyze(SqlRecord record) {
        String fingerprint = record.getFingerprint();
        long actualCost = record.getCost();
        Map<Integer, Object> params = record.getParams();

        log.info("开始SQL分析: fingerprint={}, cost={}ms", fingerprint, actualCost);

        AnalysisResult cachedResult = cache.get(fingerprint);
        long baselineCost = (cachedResult != null) ? cachedResult.getBaselineCost() : 0;

        boolean needsReanalysis = (cachedResult == null) ||
                BaselineUtil.isBaselineExceeded(actualCost, baselineCost);

        if (!needsReanalysis) {
            log.info("缓存命中且未超基线: fingerprint={}, actualCost={}ms, baseline={}ms",
                    fingerprint, actualCost, baselineCost);
            return cachedResult;
        }

        if (cachedResult == null) {
            log.info("缓存未命中，执行完整分析: fingerprint={}", fingerprint);
        } else {
            log.warn("性能劣化，击穿缓存: fingerprint={}, actualCost={}ms, baseline={}ms, 劣化倍数={}",
                    fingerprint, actualCost, baselineCost, (double) actualCost / baselineCost);
            cache.invalidate(fingerprint);
        }

        return executeFullAnalysis(fingerprint, actualCost, params, baselineCost);
    }

    private AnalysisResult executeFullAnalysis(String fingerprint, long actualCost,
                                               Map<Integer, Object> params, long oldBaseline) {
        String originalSql = SqlParamUtil.buildFullSql(fingerprint, params);
        long newBaseline = BaselineUtil.calculateNewBaseline(oldBaseline, actualCost);

        AnalysisResult result = AnalysisResult.builder()
                .fingerprint(fingerprint)
                .originalSql(originalSql)
                .actualCost(actualCost)
                .baselineCost(newBaseline)
                .advices(new ArrayList<>())
                .build();

        ExplainResult explainResult = explainAnalyzer.execute(originalSql);
        if (explainResult == null) {
            log.error("EXPLAIN 执行失败，降级处理: fingerprint={}", fingerprint);
            result.setStatus(AnalysisResult.AnalysisStatus.FAILED);
            result.setAiSummary("EXPLAIN 执行失败，无法分析");
            cache.put(fingerprint, result);
            return result;
        }

        result.setExplainResult(explainResult);
        result.setStatus(AnalysisResult.AnalysisStatus.RULE_ANALYZED);

        ruleEngine.analyze(result);

        if (shouldTriggerAiAnalysis(result, actualCost, newBaseline)) {
            String aiPrompt = buildAiPrompt(result, actualCost, !result.getAdvices().isEmpty());
            aiAnalysisService.analyze(result, aiPrompt);

            if (result.getAiSummary() == null || result.getAiSummary().isEmpty()) {
                log.warn("⚠️ AI 分析结果为空: fingerprint={}, status={}",
                        result.getFingerprint(), result.getStatus());
            } else {
                log.info("✅ AI 分析完成: fingerprint={}, summaryLength={}",
                        result.getFingerprint(), result.getAiSummary().length());
            }
        }

        cache.put(fingerprint, result);
        logAnalysisResult(result, actualCost);

        log.info("分析完成: fingerprint={}, status={}, type={}, advices={}, hasAiSummary={}",
                result.getFingerprint(),
                result.getStatus(),
                explainResult.getType(),
                result.getAdvices().size(),
                result.getAiSummary() != null);

        return result;
    }

    private boolean shouldTriggerAiAnalysis(AnalysisResult result, long actualCost, long baselineCost) {
        if (aiAnalysisService == null) {
            log.debug("AI 分析服务未配置，跳过 AI 分析: fingerprint={}", result.getFingerprint());
            return false;
        }

        int aiScore = calculateAiTriggerScore(result, actualCost, baselineCost);

        boolean shouldTrigger = aiScore >= aiTriggerScoreThreshold;

        if (shouldTrigger) {
            log.info("🤖 触发 AI 深度分析: fingerprint={}, score={} (阈值={})",
                    result.getFingerprint(), aiScore, aiTriggerScoreThreshold);
        }

        return shouldTrigger;
    }

    private int calculateAiTriggerScore(AnalysisResult result, long actualCost, long baselineCost) {
        int score = 0;

        // 维度 1: 性能劣化程度 (0-2 分)
        if (baselineCost > 0) {
            double ratio = (double) actualCost / baselineCost;
            if (ratio > 10) score += 2;      // 严重劣化
            else if (ratio > 5) score += 1;  // 中度劣化
        }

        // 维度 2: 绝对耗时 (0-1 分)
        if (actualCost > slowQueryThreshold * 2) score += 1;

        // 维度 3: 规则发现问题的复杂度 (0-2 分)
        int adviceCount = result.getAdvices().size();
        if (adviceCount >= 3) score += 2;    // 多问题并发
        else if (adviceCount == 1) score += 1; // 单问题

        // 维度 4: 规则未发现问题但耗时长 (0-2 分)
        if (adviceCount == 0 && actualCost > slowQueryThreshold) {
            score += 2; // 规则盲区,需要 AI 诊断
        }

        // 维度 5: 特定 EXPLAIN 类型 (0-1 分)
        if (result.getExplainResult() != null) {
            String type = result.getExplainResult().getType();
            if ("ALL".equals(type) || "INDEX".equals(type)) {
                score += 1; // 全表扫描或索引范围扫描
            }
        }

        return score;
    }



    private void logAnalysisResult(AnalysisResult result, long actualCost) {
        StringBuilder msg = new StringBuilder();
        msg.append("\n========== SQL分析结果 ==========\n");
        msg.append("指纹: ").append(result.getFingerprint()).append("\n");
        msg.append("SQL: ").append(result.getOriginalSql()).append("\n");
        msg.append("耗时: ").append(actualCost).append("ms");

        if (result.getBaselineCost() > 0) {
            double ratio = (double) actualCost / result.getBaselineCost();
            msg.append(" (基线: ").append(result.getBaselineCost()).append("ms, 劣化: ")
                    .append(String.format("%.2f", ratio)).append("倍)");
        }
        msg.append("\n");

        boolean hasAdvices = result.getAdvices() != null && !result.getAdvices().isEmpty();

        if (hasAdvices) {
            msg.append("\n优化建议:\n");
            for (int i = 0; i < result.getAdvices().size(); i++) {
                AnalysisResult.OptimizationAdvice advice = result.getAdvices().get(i);
                msg.append("  ").append(i + 1).append(". [")
                        .append(advice.getRuleName()).append("]\n");
                msg.append("     问题: ").append(advice.getProblem()).append("\n");
                msg.append("     建议: ").append(advice.getSuggestion().replace("\n", "\n     ")).append("\n");
            }
        } else {
            msg.append("\n状态: 规则引擎未检测到明显问题");
            if (result.getExplainResult() != null) {
                msg.append(" (访问类型: ").append(result.getExplainResult().getType()).append(")");
            }
            msg.append("\n");
        }

        int aiScore = calculateAiTriggerScore(result, actualCost, result.getBaselineCost());
        boolean shouldTriggerAi = aiScore >= aiTriggerScoreThreshold;
        msg.append("\n【AI 触发评估】\n");
        msg.append("  - 多维度评分: ").append(aiScore).append("分");
        msg.append(" (阈值: ").append(aiTriggerScoreThreshold).append("分)\n");
        msg.append("  - 是否触发 AI: ").append(shouldTriggerAi ? "✅ 是" : "❌ 否").append("\n");

        if (result.getAiSummary() != null && !result.getAiSummary().isEmpty()) {
            msg.append("\n【AI 深度分析结果】\n");
            msg.append(result.getAiSummary()).append("\n");
        }

        String aiPrompt = buildAiPrompt(result, actualCost, hasAdvices);
        msg.append("\nAI分析提示词（可复制给AI助手）:\n");
        msg.append("```\n");
        msg.append(aiPrompt);
        msg.append("\n```\n");

        msg.append("=============================");
        log.warn(msg.toString());
    }

    public String buildAiPrompt(AnalysisResult result, long actualCost, boolean hasRuleAdvices) {
        StringBuilder prompt = new StringBuilder();

        prompt.append("【背景说明】\n");
        prompt.append("本系统采用「规则引擎 + AI」双层分析架构：\n");
        prompt.append("- 规则引擎：基于预定义规则快速检测常见性能问题（全表扫描、文件排序、索引缺失、索引失效等）\n");
        prompt.append("- AI 分析：深度诊断规则引擎未能覆盖的复杂场景，或评估规则建议的合理性\n\n");

        if (hasRuleAdvices) {
            prompt.append("【规则引擎检测结果】✅ 检测到 ").append(result.getAdvices().size()).append(" 个问题\n\n");

            prompt.append("请基于规则引擎的发现，进行深度分析并补充优化建议：\n\n");
        } else {
            prompt.append("【规则引擎检测结果】❌ 未检测到明显问题\n\n");

            prompt.append("此SQL执行较慢（超过阈值），但规则引擎未能检测到问题。请深入分析是否存在潜在性能瓶颈：\n\n");
        }

        prompt.append("【SQL信息】\n");
        prompt.append("原始SQL: ").append(result.getOriginalSql()).append("\n");
        prompt.append("执行耗时: ").append(actualCost).append("ms\n");

        if (result.getBaselineCost() > 0) {
            double ratio = (double) actualCost / result.getBaselineCost();
            prompt.append("基线耗时: ").append(result.getBaselineCost()).append("ms\n");
            prompt.append("性能劣化: ").append(String.format("%.2f", ratio)).append("倍\n");
        }

        if (result.getExplainResult() != null) {
            prompt.append("\n【EXPLAIN执行计划】\n");
            prompt.append("表名: ").append(result.getExplainResult().getTable()).append("\n");
            prompt.append("访问类型: ").append(result.getExplainResult().getType()).append("\n");
            prompt.append("可能索引: ").append(result.getExplainResult().getPossibleKeys() != null ?
                    result.getExplainResult().getPossibleKeys() : "无").append("\n");
            prompt.append("实际索引: ").append(result.getExplainResult().getKey() != null ?
                    result.getExplainResult().getKey() : "无").append("\n");
            prompt.append("扫描行数: ").append(result.getExplainResult().getRows()).append("\n");
            if (result.getExplainResult().getKeyLen() != null) {
                prompt.append("索引长度: ").append(result.getExplainResult().getKeyLen()).append("\n");
            }
            prompt.append("额外信息: ").append(result.getExplainResult().getExtra() != null ?
                    result.getExplainResult().getExtra() : "无").append("\n");
        }

        if (hasRuleAdvices) {
            prompt.append("\n【规则引擎检测到的具体问题】\n");
            for (int i = 0; i < result.getAdvices().size(); i++) {
                AnalysisResult.OptimizationAdvice advice = result.getAdvices().get(i);
                prompt.append((i + 1)).append(". [").append(advice.getRuleName()).append("]\n");
                prompt.append("   问题: ").append(advice.getProblem()).append("\n");
                prompt.append("   建议: ").append(advice.getSuggestion()).append("\n");
            }
        } else {
            prompt.append("\n【规则引擎已执行的检测项】\n");
            prompt.append("规则引擎已检查以下常见问题，但未发现异常：\n");
            prompt.append("1. 全表扫描检测（type=ALL）\n");
            prompt.append("2. 文件排序检测（Using filesort）\n");
            prompt.append("3. 索引缺失检测（possible_keys为空且rows较大）\n");
            prompt.append("4. 索引失效检测（LIKE左模糊、函数调用、类型转换等）\n");
            prompt.append("\n⚠️ 请特别注意：规则引擎可能存在盲区，需要AI进行更全面的分析\n");
        }

        prompt.append("\n【AI分析要求】\n");
        if (hasRuleAdvices) {
            prompt.append("1. 评估规则引擎检测结果的准确性和完整性\n");
            prompt.append("2. 分析是否有规则引擎未覆盖的深层问题（如索引设计不合理、查询逻辑可优化等）\n");
            prompt.append("3. 给出具体的优化方案（索引调整、SQL改写、架构优化等）\n");
            prompt.append("4. 预估优化后的性能提升幅度\n");
            prompt.append("5. 如果规则建议不够完善，请补充更优的解决方案\n");
        } else {
            prompt.append("1. 仔细分析EXPLAIN执行计划，找出可能导致性能问题的因素\n");
            prompt.append("2. 分析为什么规则引擎未能检测到问题：\n");
            prompt.append("   - 是规则不完善（漏判）？\n");
            prompt.append("   - 还是确实无明显问题（可能是数据量小或缓存影响）？\n");
            prompt.append("3. 如果存在潜在问题，给出具体的优化建议\n");
            prompt.append("4. 如果确实无明显问题，说明当前性能是否在可接受范围内\n");
            prompt.append("5. 建议是否需要新增规则来覆盖此类场景\n");
        }

        return prompt.toString();
    }


    @SuppressWarnings("unused")
    public void invalidateCache(String fingerprint) {
        cache.invalidate(fingerprint);
    }

    public SqlAnalysisCache getCache() {
        return cache;
    }
}
