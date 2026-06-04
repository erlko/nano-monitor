package com.nano.monitor.spi;

import java.util.Map;

public interface SqlCollector {
    // 方法 1：带参数的 SQL
    void collect(String originalSql, Map<Integer, Object> params, long cost);

    // 方法 2：无参数的 SQL
    void collect(String sql, long cost);
}