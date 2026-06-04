package com.nano.monitor;

import com.nano.monitor.collector.MetricsCollector;
import com.nano.monitor.eventbus.EventBus;
import com.nano.monitor.model.GlobalMetrics;
import com.nano.monitor.model.GroupMetrics;
import com.nano.monitor.proxy.JdbcProxyFactory;
import com.nano.monitor.utils.TestDatabaseConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;

import javax.sql.DataSource;
import java.sql.*;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class JdbcProxyFactoryNoCaptureTest {

    private DataSource proxiedDataSource;
    private MetricsCollector collector;
    private EventBus eventBus;

    @BeforeEach
    void setUp() {
        log.info("\n🚀 初始化测试环境（captureParams = false）");

        eventBus = new EventBus();
        collector = new MetricsCollector(eventBus, 40);

        DataSource originalDataSource = TestDatabaseConfig.createDataSourceAsInterface();

        JdbcProxyFactory proxyFactory = new JdbcProxyFactory(false);
        proxiedDataSource = proxyFactory.createDataSourceProxy(originalDataSource, collector);

        log.info("✅ 代理数据源创建成功");
        log.info("✅ 慢查询阈值: 1ms（所有查询都会被分组统计）");
    }

    @Test
    @Order(1)
    @DisplayName("测试 SQL 拦截功能")
    void testSqlInterception() throws SQLException {
        log.info("\n========== 测试 1：SQL 拦截 ==========");

        long beforeQueries = collector.getGlobalMetrics().getTotalQueries().get();

        try (Connection conn = proxiedDataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT * FROM test_user WHERE id = ?")) {

            stmt.setInt(1, 1);
            stmt.executeQuery();
        }

        long afterQueries = collector.getGlobalMetrics().getTotalQueries().get();

        assertEquals(beforeQueries + 1, afterQueries, "应该拦截并统计 1 次查询");
        log.info("✅ SQL 拦截成功: totalQueries={}", afterQueries);
    }

    @Test
    @Order(2)
    @DisplayName("测试全局指标统计")
    void testGlobalMetrics() throws SQLException {
        log.info("\n========== 测试 2：全局指标 ==========");

        for (int i = 1; i <= 5; i++) {
            try (Connection conn = proxiedDataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement("SELECT * FROM test_user WHERE id = ?")) {

                stmt.setInt(1, i);
                stmt.executeQuery();
            }
        }

        GlobalMetrics globalMetrics = collector.getGlobalMetrics();

        log.info("✅ 全局指标:");
        log.info("   - totalQueries: {}", globalMetrics.getTotalQueries().get());
        log.info("   - slowQueries: {}", globalMetrics.getSlowQueries().get());
        log.info("   - maxCost: {}ms", globalMetrics.getMaxCost());
        log.info("   - avgCost: {}ms", globalMetrics.getAvgCost());
        log.info("   - p95Cost: {}ms", globalMetrics.getP95Cost());
        log.info("   - p99Cost: {}ms", globalMetrics.getP99Cost());
        log.info("   - slowQueryRatio: {}%", String.format("%.2f", globalMetrics.getSlowQueryRatio()));
    }

    @Test
    @Order(3)
    @DisplayName("测试分组指标统计")
    void testGroupMetrics() throws SQLException {
        log.info("\n========== 测试 3：分组指标 ==========");

        for (int i = 1; i <= 3; i++) {
            try (Connection conn = proxiedDataSource.getConnection();
                 PreparedStatement stmt = conn.prepareStatement(
                         "SELECT * FROM test_user WHERE age > ? AND age < ?")) {

                stmt.setInt(1, 20);
                stmt.setInt(2, 30);
                stmt.executeQuery();
            }
        }

        Map<String, GroupMetrics> allGroups = collector.getAllGroupMetrics();

        if (allGroups.isEmpty()) {
            log.warn("⚠️ 没有分组数据");
            return;
        }

        for (Map.Entry<String, GroupMetrics> entry : allGroups.entrySet()) {
            GroupMetrics group = entry.getValue();
            log.info("✅ 分组指标 [{}]:", entry.getKey());
            log.info("   - queryCount: {}", group.getQueryCount().get());
            log.info("   - slowCount: {}", group.getSlowCount().get());
            log.info("   - maxCost: {}ms", group.getMaxCost());
            log.info("   - avgCost: {}ms", group.getAvgCost());
            log.info("   - p95Cost: {}ms", group.getP95Cost());
            log.info("   - p99Cost: {}ms", group.getP99Cost());
            log.info("   - lastQueryTime: {}", group.getLastQueryTime());
            log.info("   - slowQueryRatio: {}%", String.format("%.2f", group.getSlowQueryRatio()));
        }
    }


    @AfterEach
    void tearDown() {
        try (Connection conn = proxiedDataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM test_user WHERE username LIKE 'test_%'");
        } catch (Exception e) {
            log.warn("清理失败: {}", e.getMessage());
        }

        if (proxiedDataSource instanceof AutoCloseable) {
            try {
                ((AutoCloseable) proxiedDataSource).close();
            } catch (Exception e) {
                log.error("关闭数据源失败", e);
            }
        }

        if (eventBus != null) {
            eventBus.shutdown();
        }
    }
}

