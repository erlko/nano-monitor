package com.nano.monitor.analysis;

import com.nano.monitor.model.ExplainResult;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

@Slf4j
public class ExplainAnalyzer {

    private final DataSource dataSource;

    public ExplainAnalyzer(DataSource dataSource) {
        this.dataSource = dataSource;
    }

    public ExplainResult execute(String sql) {
        String explainSql = "EXPLAIN " + normalizeSql(sql);

        try (Connection conn = dataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(explainSql);
             ResultSet rs = stmt.executeQuery()) {

            if (rs.next()) {
                ExplainResult result = ExplainResult.builder()
                        .table(rs.getString("table"))
                        .type(rs.getString("type"))
                        .key(rs.getString("key"))
                        .rows(rs.getLong("rows"))
                        .extra(rs.getString("Extra"))
                        .possibleKeys(rs.getString("possible_keys"))
                        .keyLen(rs.getLong("key_len"))
                        .build();

                log.debug("EXPLAIN 分析成功: table={}, type={}, key={}, rows={}",
                        result.getTable(), result.getType(), result.getKey(), result.getRows());

                return result;
            }
        } catch (SQLException e) {
            log.error("执行 EXPLAIN 失败: sql={}, error={}", explainSql, e.getMessage());
            return null;
        }

        return null;
    }

    private String normalizeSql(String sql) {
        if (sql == null || sql.trim().isEmpty()) {
            return sql;
        }

        String normalized = sql.trim();

        if (normalized.toUpperCase().startsWith("EXPLAIN")) {
            return normalized;
        }

        return normalized.replaceAll("\\s+", " ");
    }
}
