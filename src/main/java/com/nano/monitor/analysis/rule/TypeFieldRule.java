package com.nano.monitor.analysis.rule;

import com.nano.monitor.model.AnalysisResult;
import com.nano.monitor.model.ExplainResult;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 基于 EXPLAIN 的 type 和 key 字段检测全表扫描问题
 */
public class TypeFieldRule implements AnalysisRule {

    private static final Pattern WHERE_COLUMN_PATTERN = Pattern.compile(
            "(?i)WHERE\\s+(\\w+)\\s*[=<>!]", Pattern.DOTALL);

    @Override
    public String getName() {
        return "全表扫描检测";
    }

    @Override
    public boolean canHandle(String sql) {
        return true;
    }

    @Override
    public void analyze(AnalysisResult result) {
        ExplainResult explain = result.getExplainResult();
        String sql = result.getOriginalSql();

        if (explain == null || !"ALL".equals(explain.getType())) {
            return;
        }

        if (explain.getKey() == null) {
            if (explain.getPossibleKeys() != null && !explain.getPossibleKeys().isEmpty()) {
                String[] possibleKeys = explain.getPossibleKeys().split(",");
                result.getAdvices().add(AnalysisResult.OptimizationAdvice.builder()
                        .ruleName(getName())
                        .problem(String.format(
                                "表 '%s' 全表扫描，虽有 %d 个可用索引 [%s]，但优化器未选择（可能统计信息过期或数据倾斜）",
                                explain.getTable(),
                                possibleKeys.length,
                                explain.getPossibleKeys()
                        ))
                        .suggestion(String.format(
                                """
                                        1. 更新统计信息：ANALYZE TABLE %s
                                        2. 强制使用索引测试：FORCE INDEX (%s)
                                        3. 检查索引选择性（是否区分度太低）""",
                                explain.getTable(),
                                possibleKeys[0].trim()
                        ))
                        .severity(2)
                        .exampleSql(String.format(
                                "ANALYZE TABLE %s;\n-- 或\nSELECT * FROM %s FORCE INDEX (%s) WHERE ...",
                                explain.getTable(),
                                explain.getTable(),
                                possibleKeys[0].trim()
                        ))
                        .build());
            } else {
                String whereColumn = extractWhereColumn(sql);
                String alterSql = whereColumn != null
                        ? String.format("ALTER TABLE %s ADD INDEX idx_%s (%s)",
                        explain.getTable(), whereColumn, whereColumn)
                        : String.format("-- 请为 %s 表的查询条件字段添加索引", explain.getTable());

                result.getAdvices().add(AnalysisResult.OptimizationAdvice.builder()
                        .ruleName(getName())
                        .problem(String.format(
                                "表 '%s' 全表扫描（type=ALL），无可用索引，扫描 %d 行",
                                explain.getTable(),
                                explain.getRows()
                        ))
                        .suggestion("为 WHERE/JOIN/ORDER BY 条件字段添加索引")
                        .severity(3)
                        .exampleSql(alterSql)
                        .build());
            }
        }
    }

    private String extractWhereColumn(String sql) {
        Matcher matcher = WHERE_COLUMN_PATTERN.matcher(sql);
        if (matcher.find()) {
            return matcher.group(1);
        }
        return null;
    }
}