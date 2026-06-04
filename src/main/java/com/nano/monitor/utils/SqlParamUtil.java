package com.nano.monitor.utils;

import lombok.extern.slf4j.Slf4j;

import java.text.SimpleDateFormat;
import java.util.Map;

@Slf4j
public class SqlParamUtil {

    private static final SimpleDateFormat TIMESTAMP_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS");
    private static final SimpleDateFormat DATE_FORMAT = new SimpleDateFormat("yyyy-MM-dd");
    private static final SimpleDateFormat DATETIME_FORMAT = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss");

    /**
     * 将带占位符的 SQL 和参数合并为完整 SQL
     *
     * @param originalSql 带 ? 占位符的 SQL
     * @param params      参数映射 (索引 -> 值)
     * @return 替换后的完整 SQL
     */
    public static String buildFullSql(String originalSql, Map<Integer, Object> params) {
        if (originalSql == null || originalSql.isEmpty()) {
            return originalSql;
        }

        if (params == null || params.isEmpty()) {
            return originalSql;
        }

        StringBuilder result = new StringBuilder();
        int paramIndex = 1;
        int sqlIndex = 0;

        while (sqlIndex < originalSql.length()) {
            char currentChar = originalSql.charAt(sqlIndex);

            if (currentChar == '?') {
                Object value = params.get(paramIndex);
                result.append(formatValue(value));
                paramIndex++;
            } else {
                result.append(currentChar);
            }

            sqlIndex++;
        }

        return result.toString();
    }

    /**
     * 格式化参数值为 SQL 字面量
     */
    private static String formatValue(Object value) {
        if (value == null) {
            return "NULL";
        } else if (value instanceof String) {
            return "'" + escapeString(value.toString()) + "'";
        } else if (value instanceof java.sql.Timestamp) {
            return "'" + TIMESTAMP_FORMAT.format((java.sql.Timestamp) value) + "'";
        } else if (value instanceof java.sql.Date) {
            return "'" + DATE_FORMAT.format((java.sql.Date) value) + "'";
        } else if (value instanceof java.util.Date) {
            return "'" + DATETIME_FORMAT.format((java.util.Date) value) + "'";
        } else if (value instanceof java.time.LocalDateTime || value instanceof java.time.LocalDate) {
            return "'" + value + "'";
        } else if (value instanceof Boolean) {
            return (Boolean) value ? "1" : "0";
        } else {
            return value.toString();
        }
    }

    /**
     * 转义 SQL 字符串中的单引号
     */
    private static String escapeString(String str) {
        if (str == null) return "";
        return str.replace("'", "''");
    }
}
