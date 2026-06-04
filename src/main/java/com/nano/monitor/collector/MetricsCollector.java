package com.nano.monitor.collector;

import com.nano.monitor.eventbus.EventBus;
import com.nano.monitor.eventbus.event.SlowQueryEvent;
import com.nano.monitor.model.GlobalMetrics;
import com.nano.monitor.model.GroupMetrics;
import com.nano.monitor.model.SqlRecord;
import com.nano.monitor.spi.SqlCollector;
import com.nano.monitor.utils.SqlFingerprintUtil;
import lombok.extern.slf4j.Slf4j;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class MetricsCollector implements SqlCollector {

    private final long slowQueryThreshold;

    private final EventBus eventBus;
    private final GlobalMetrics globalMetrics = new GlobalMetrics();
    private final Map<String, GroupMetrics> groupMetricsMap = new ConcurrentHashMap<>();

    public MetricsCollector(EventBus eventBus) {
        this(eventBus, 500);
    }

    public MetricsCollector(EventBus eventBus, long slowQueryThreshold) {
        this.eventBus = eventBus;
        this.slowQueryThreshold = slowQueryThreshold;

        log.info("MetricsCollector 初始化: 慢 SQL 阈值={}ms", slowQueryThreshold);
    }

    @Override
    public void collect(String originalSql, Map<Integer, Object> params, long cost) {
        boolean isSlow = cost > slowQueryThreshold;

        // Layer 1: 全局统计（所有查询，轻量级）
        globalMetrics.recordQuery(cost, isSlow);

        if (isSlow) {
            // ✅ 深拷贝 params，防止代理层回收后丢失
            Map<Integer, Object> paramsCopy = params != null ? new HashMap<>(params) : null;

            // Layer 2: 分组统计（仅慢查询）
            // ✅ prepareStatement 传入的 SQL 已带 ? 占位符，直接作为指纹使用
            String fingerprint = originalSql;

            groupMetricsMap.computeIfAbsent(fingerprint, GroupMetrics::new)
                    .recordQuery(cost, true);

            // Layer 3: 深度分析（异步）
            SqlRecord record = new SqlRecord(
                    fingerprint,  // 传递指纹（带 ? 占位符）
                    cost,
                    LocalDateTime.now(),
                    paramsCopy    // 传递参数副本
            );
            publishSlowQueryEvent(record);
        }
    }

    @Override
    public void collect(String sql, long cost) {
        // 只进行统计模式
        boolean isSlow = cost > slowQueryThreshold;

        // Layer 1: 全局统计
        globalMetrics.recordQuery(cost, isSlow);

        if (isSlow && sql != null) {
            // Layer 2: 分组统计
            // ✅ Statement 传入的是完整 SQL，需要归一化处理
            String fingerprint = SqlFingerprintUtil.generate(sql);

            groupMetricsMap.computeIfAbsent(fingerprint, GroupMetrics::new)
                    .recordQuery(cost, true);
        }
    }

    private void publishSlowQueryEvent(SqlRecord record) {
        try {
            SlowQueryEvent event = new SlowQueryEvent(record);
            eventBus.publish(event);
            log.debug("发布慢 SQL 事件: fingerprint={}, cost={}ms", record.getFingerprint(), record.getCost());
        } catch (Exception e) {
            log.error("发布慢 SQL 事件失败: fingerprint={}", record.getFingerprint(), e);
        }
    }

    public GlobalMetrics getGlobalMetrics() {
        return globalMetrics;
    }

    public GroupMetrics getGroupMetrics(String fingerprint) {
        return groupMetricsMap.get(fingerprint);
    }

    public Map<String, GroupMetrics> getAllGroupMetrics() {
        return new HashMap<>(groupMetricsMap);
    }

}
