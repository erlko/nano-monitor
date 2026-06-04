package com.nano.monitor.analysis.rule;

import com.nano.monitor.model.AnalysisResult;
import com.nano.monitor.model.ExplainResult;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.regex.Pattern;

/**
 * 基于 SQL 文本模式检测可能导致索引失效的写法
 * 不依赖 EXPLAIN 结果，通过正则预判潜在问题
 */
public class IndexInvalidRule implements AnalysisRule {

    private static final Pattern LIKE_WILDCARD_PATTERN = Pattern.compile(
            "(?i)LIKE\\s+'%[^']*'", Pattern.DOTALL);
    private static final Pattern FUNCTION_ON_COLUMN_PATTERN = Pattern.compile(
            "(?i)(WHERE|JOIN).*(?:UPPER|LOWER|TRIM|SUBSTRING|CONCAT)\\s*\\(", Pattern.DOTALL);
    private static final Pattern OR_CONDITION_PATTERN = Pattern.compile(
            "(?i)WHERE.*\\bOR\\b", Pattern.DOTALL);

    @Override
    public String getName() {
        return "索引失效检测";
    }

    @Override
    public boolean canHandle(String sql) {
        if (sql == null) {
            return false;
        }
        return LIKE_WILDCARD_PATTERN.matcher(sql).find()
                || FUNCTION_ON_COLUMN_PATTERN.matcher(sql).find()
                || OR_CONDITION_PATTERN.matcher(sql).find();
    }

    @Override
    public void analyze(AnalysisResult result) {
        String sql = result.getOriginalSql();

        if (!canHandle(sql)) {
            return;
        }

        if (LIKE_WILDCARD_PATTERN.matcher(sql).find()) {
            result.getAdvices().add(AnalysisResult.OptimizationAdvice.builder()
                    .ruleName(getName())
                    .problem("LIKE 查询使用左模糊（%开头），导致索引失效")
                    .suggestion("避免左模糊查询，改用右模糊或使用全文索引")
                    .severity(2)
                    .exampleSql("-- 优化前：LIKE '%%abc'\n-- 优化后：LIKE 'abc%%'")
                    .build());
        }

        if (FUNCTION_ON_COLUMN_PATTERN.matcher(sql).find()) {
            result.getAdvices().add(AnalysisResult.OptimizationAdvice.builder()
                    .ruleName(getName())
                    .problem("在索引列上使用函数，导致索引失效")
                    .suggestion("避免在 WHERE 条件中对索引列使用函数，在应用层转换")
                    .severity(1)
                    .exampleSql("-- 优化前：WHERE UPPER(name) = 'ABC'\n-- 优化后：WHERE name = 'abc'")
                    .build());
        }

        if (OR_CONDITION_PATTERN.matcher(sql).find()) {
            result.getAdvices().add(AnalysisResult.OptimizationAdvice.builder()
                    .ruleName(getName())
                    .problem("OR 条件可能导致索引失效")
                    .suggestion("将 OR 改写为 UNION 或使用 IN")
                    .severity(2)
                    .exampleSql("-- 优化前：WHERE a=1 OR a=2\n-- 优化后：WHERE a IN (1,2)")
                    .build());
        }
    }
}