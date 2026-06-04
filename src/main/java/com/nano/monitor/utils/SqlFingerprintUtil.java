package com.nano.monitor.utils;

import java.util.regex.Pattern;

public class SqlFingerprintUtil {

    private static final Pattern WHITESPACE_PATTERN = Pattern.compile("\\s+");
    private static final Pattern STRING_PATTERN = Pattern.compile("'(?:[^'\\\\]|\\\\.)*'");
    private static final Pattern NUMBER_PATTERN = Pattern.compile("\\b\\d+(?:\\.\\d+)?(?:[eE][+-]?\\d+)?\\b");
    private static final Pattern NULL_PATTERN = Pattern.compile("\\bNULL\\b", Pattern.CASE_INSENSITIVE);
    private static final Pattern BOOLEAN_PATTERN = Pattern.compile("\\b(TRUE|FALSE)\\b", Pattern.CASE_INSENSITIVE);

    public static String generate(String sql) {
        if (sql == null || sql.isEmpty()) {
            return sql;
        }

        String normalized = WHITESPACE_PATTERN.matcher(sql).replaceAll(" ").trim();
        normalized = STRING_PATTERN.matcher(normalized).replaceAll("?");
        normalized = NUMBER_PATTERN.matcher(normalized).replaceAll("?");
        normalized = NULL_PATTERN.matcher(normalized).replaceAll("?");
        normalized = BOOLEAN_PATTERN.matcher(normalized).replaceAll("?");

        return normalized.toUpperCase();
    }
}
