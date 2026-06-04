package com.nano.monitor.proxy;

import com.nano.monitor.spi.SqlCollector;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.util.Map;

/**
 * 简单的 SQL 采集器实现（仅用于测试）
 *
 * 职责：
 * - 接收代理层传递的 SQL 执行信息
 * - 日志打印原始数据
 * - 不缓存、不分析、不告警
 */
public class SimpleSqlCollector implements SqlCollector {

    private static final Logger log = LoggerFactory.getLogger(SimpleSqlCollector.class);

    @Override
    public void collect(String originalSql, Map<Integer, Object> params, long cost) {

    }

    @Override
    public void collect(String sql, long cost) {
        log.info("📊 [SQL监控] SQL: {} | 耗时: {} ms", sql, cost);
    }
}

