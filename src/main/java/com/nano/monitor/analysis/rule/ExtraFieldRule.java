package com.nano.monitor.analysis.rule;

import com.nano.monitor.model.AnalysisResult;
import com.nano.monitor.model.ExplainResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * 基于 EXPLAIN 的 Extra 字段检测排序和临时表问题
 */
public class ExtraFieldRule implements AnalysisRule {

    private static final Pattern ORDER_BY_PATTERN = Pattern.compile(
            "(?i)ORDER\\s+BY\\s+(\\w+)", Pattern.DOTALL);

    @Override
    public String getName() {
        return "排序与临时表检测";
    }

    @Override
    public boolean canHandle(String sql) {
        return true;
    }

    @Override
    public void analyze(AnalysisResult result) {
        ExplainResult explain = result.getExplainResult();
        String sql = result.getOriginalSql();

        if (explain == null || explain.getExtra() == null) {
            return;
        }

        if (explain.getExtra().contains("Using filesort")) {
            Matcher matcher = ORDER_BY_PATTERN.matcher(sql);
            String orderColumn = matcher.find() ? matcher.group(1) : null;

            String addIndexSql = orderColumn != null
                    ? String.format("ALTER TABLE %s ADD INDEX idx_%s (%s)",
                    explain.getTable(), orderColumn, orderColumn)
                    : "-- 为 ORDER BY 字段添加索引";

            result.getAdvices().add(AnalysisResult.OptimizationAdvice.builder()
                    .ruleName(getName())
                    .problem(String.format(
                            "表 '%s' 使用了文件排序 (Using filesort)，扫描 %d 行",
                            explain.getTable(), explain.getRows()))
                    .suggestion("为 ORDER BY 字段添加索引，避免磁盘排序")
                    .severity(2)
                    .exampleSql(addIndexSql)
                    .build());
        }

        if (explain.getExtra().contains("Using temporary")) {
            result.getAdvices().add(AnalysisResult.OptimizationAdvice.builder()
                    .ruleName(getName())
                    .problem(String.format(
                            "表 '%s' 使用了临时表 (Using temporary)，扫描 %d 行",
                            explain.getTable(), explain.getRows()))
                    .suggestion("1. 避免 SELECT DISTINCT + ORDER BY\n2. 使用子查询替代 GROUP BY")
                    .severity(2)
                    .exampleSql("-- 优化：使用子查询或覆盖索引")
                    .build());
        }
    }
}