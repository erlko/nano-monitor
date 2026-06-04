package com.nano.monitor.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * SQL 分析结果 - 作为规则引擎与 AI 分析的可变上下文载体
 *
 * <p>设计说明:
 * <ul>
 *   <li>作为责任链模式的可变上下文,在分析流程中被多个组件增量填充</li>
 *   <li>既是输入载体(携带待分析的SQL信息),也是输出载体(承载分析结果)</li>
 *   <li>遵循"规则引擎+AI混合分析架构": 缓存检查 → 规则引擎 → 多维度评分 → AI深度分析</li>
 * </ul>
 *
 * <p>数据流向:
 * <ol>
 *   <li>SqlAnalysisEngine 初始化基础字段(fingerprint, originalSql, actualCost等)</li>
 *   <li>ExplainAnalyzer 填充 explainResult(EXPLAIN执行计划)</li>
 *   <li>RuleEngine 填充 advices(规则引擎检测到的优化建议)</li>
 *   <li>AiAnalysisService 填充 aiSummary(AI深度分析摘要)</li>
 *   <li>最终状态由 status 标识(RULE_ANALYZED / AI_ANALYZED / FAILED)</li>
 * </ol>
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AnalysisResult {

    // ==================== 输入字段 (由调用方提供) ====================

    /**
     * SQL 指纹 - 用于唯一标识一类SQL模板
     * <p>输入: 从 SqlRecord 获取,用于缓存查询和基线对比
     */
    private String fingerprint;

    /**
     * 原始SQL语句 - 已替换参数占位符的完整SQL
     * <p>输入: 通过 SqlParamUtil.buildFullSql() 构建,用于 EXPLAIN 分析
     */
    private String originalSql;

    /**
     * 实际执行耗时(毫秒)
     * <p>输入: 从 SqlRecord 获取,用于性能劣化判断和 AI 触发评分
     */
    private long actualCost;

    /**
     * 历史基线耗时(毫秒)
     * <p>输入: 从缓存中获取的历史最优性能,用于判断是否需要重新分析
     * <p>初始值: 首次分析时为 0,后续从缓存的 AnalysisResult 中读取
     */
    private long baselineCost;

    // ==================== 输出字段 (由分析引擎填充) ====================

    /**
     * EXPLAIN 执行计划分析结果
     * <p>输出: 由 ExplainAnalyzer 填充,包含表名、访问类型、索引使用、扫描行数等
     * <p>用途: 规则引擎和 AI 分析的核心依据
     */
    private ExplainResult explainResult;

    /**
     * 规则引擎生成的优化建议列表
     * <p>输出: 由 RuleEngine 填充,可能包含多个优化建议
     * <p>内容: 全表扫描、文件排序、索引缺失、索引失效等问题检测
     * <p>注意: 空列表不代表无问题,可能是规则盲区,需要 AI 深度分析
     */
    private List<OptimizationAdvice> advices;

    /**
     * AI 深度分析摘要
     * <p>输出: 由 AiAnalysisService 填充,仅在多维度评分达到阈值时生成
     * <p>内容: 对规则引擎结果的评估 + 深层问题分析 + 具体优化方案
     * <p>兜底机制: 即使不调用 AI,也会生成提示词记录到日志供人工分析
     */
    private String aiSummary;

    /**
     * 分析状态 - 标识当前分析流程的执行阶段
     * <p>输出: 由 SqlAnalysisEngine 根据分析进度设置
     * <ul>
     *   <li>RULE_ANALYZED: 规则引擎分析完成(可能未触发 AI)</li>
     *   <li>AI_ANALYZED: AI 深度分析完成(高质量分析结果)</li>
     *   <li>FAILED: 分析失败(如 EXPLAIN 执行失败)</li>
     * </ul>
     */
    private AnalysisStatus status;

    /**
     * 分析状态枚举
     * <p>标识分析流程的最终状态,用于日志记录和调用方判断
     */
    public enum AnalysisStatus {
        /** 规则引擎分析完成 */
        RULE_ANALYZED,
        /** AI 深度分析完成 */
        AI_ANALYZED,
        /** 分析失败 */
        FAILED
    }

    /**
     * 优化建议 - 规则引擎检测到的具体问题及改进方案
     *
     * <p>设计说明:
     * <ul>
     *   <li>每个建议对应一个具体的检测规则(如 IndexInvalidRule, TypeFieldRule等)</li>
     *   <li>severity 用于优先级排序,数值越大问题越严重</li>
     *   <li>exampleSql 提供可执行的优化示例,便于开发人员直接参考</li>
     * </ul>
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class OptimizationAdvice {

        /**
         * 规则名称 - 标识触发此建议的检测规则
         * <p>示例: "IndexInvalidRule"(索引失效), "ExtraFieldRule"(文件排序)
         * <p>用途: 便于问题分类统计和规则覆盖率分析
         */
        private String ruleName;

        /**
         * 问题描述 - 清晰说明检测到的性能问题
         * <p>示例: "检测到左模糊查询导致索引失效: LIKE '%keyword'"
         */
        private String problem;

        /**
         * 优化建议 - 具体的改进方案
         * <p>可能包含多行文本,提供详细的操作步骤
         * <p>示例: "建议使用全文索引(FULLTEXT INDEX)替代左模糊查询"
         */
        private String suggestion;

        /**
         * 严重程度 - 数值越大表示问题越严重
         * <p>取值范围: 1-5
         * <ul>
         *   <li>5: 严重(必须立即优化,如全表扫描大表)</li>
         *   <li>4: 高(建议尽快优化)</li>
         *   <li>3: 中(可以优化)</li>
         *   <li>2: 低(可选优化)</li>
         *   <li>1: 提示(轻微问题)</li>
         * </ul>
         */
        private int severity;

        /**
         * 优化示例SQL - 可直接参考执行的改写后SQL
         * <p>可选字段,某些规则可能无法生成示例(如索引设计建议)
         * <p>示例: "SELECT * FROM users WHERE MATCH(name) AGAINST('keyword')"
         */
        private String exampleSql;
    }
}
