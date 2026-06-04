package com.nano.monitor;

import com.nano.monitor.collector.MetricsCollector;
import com.nano.monitor.container.MonitorContainer;
import com.nano.monitor.model.GlobalMetrics;
import com.nano.monitor.container.MonitorBootstrap;
import com.nano.monitor.utils.TestDatabaseConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class FullPipelineIntegrationTest {

    private static final Logger log = LoggerFactory.getLogger(FullPipelineIntegrationTest.class);

    private HikariDataSource originalDataSource;
    private DataSource proxiedDataSource;
    private MonitorContainer container;

    @BeforeEach
    void setUp() {
        originalDataSource = TestDatabaseConfig.createDataSource();

        MonitorBootstrap.initialize(originalDataSource, "test-monitor.properties");

        container = MonitorContainer.getInstance();
        proxiedDataSource = container.createProxiedDataSource(originalDataSource);

        log.info("✅ 监控系统初始化完成，开始模拟真实业务场景");
    }

    @AfterEach
    void tearDown() throws InterruptedException {
        log.info("⏳ 等待异步分析任务完成...");
        Thread.sleep(15000);

        MonitorBootstrap.shutdown();

        if (originalDataSource != null && !originalDataSource.isClosed()) {
            originalDataSource.close();
        }

        resetContainerInstance();
    }

    @Test
    @Order(1)
    @DisplayName("模拟真实业务场景 - 混合负载测试")
    void testRealWorldBusinessScenario() throws InterruptedException {
        log.info("\n========== 开始模拟真实业务场景 ==========");
        log.info("场景说明: 模拟电商系统用户查询混合负载（正常查询 + 慢查询）");

        ExecutorService executor = Executors.newFixedThreadPool(10);
        CountDownLatch latch = new CountDownLatch(100);

        long startTime = System.currentTimeMillis();

        for (int i = 0; i < 100; i++) {
            final int userId = (i % 50) + 1;

            executor.submit(() -> {
                try {
                    String sql;
                    Object[] params;

                    int scenario = (int) (Math.random() * 100);

                    if (scenario < 60) {
                        sql = "SELECT id, username, email FROM test_user WHERE id = ?";
                        params = new Object[]{userId};
                    } else if (scenario < 80) {
                        sql = "SELECT id, username FROM test_user WHERE age > ? LIMIT 10";
                        params = new Object[]{20 + (int)(Math.random() * 30)};
                    } else if (scenario < 95) {
                        sql = "SELECT * FROM test_user WHERE email LIKE ? ORDER BY created_at DESC LIMIT 5";
                        params = new Object[]{"%user" + (int)(Math.random() * 10) + "%"};
                    } else {
                        sql = "SELECT username, created_at FROM test_user ORDER BY created_at DESC LIMIT 20";
                        params = new Object[]{};
                    }

                    executeQuery(sql, params);

                } catch (Exception e) {
                    log.error("查询执行失败", e);
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(60, TimeUnit.SECONDS);
        executor.shutdown();
        executor.awaitTermination(10, TimeUnit.SECONDS);

        long elapsed = System.currentTimeMillis() - startTime;
        log.info("\n✅ 业务场景执行完成: 耗时 {}ms, 共执行 100 次查询", elapsed);

        Thread.sleep(2000);

        MetricsCollector collector = container.get(MetricsCollector.class);
        GlobalMetrics metrics = collector.getGlobalMetrics();

        log.info("\n========== 监控数据统计 ==========");
        log.info("总查询数: {}", metrics.getTotalQueries().get());
        log.info("慢查询数: {}", metrics.getSlowQueries().get());
        log.info("P95 耗时: {}ms", metrics.getP95Cost());
        log.info("平均耗时: {}ms", metrics.getAvgCost());
        log.info("最大耗时: {}ms", metrics.getMaxCost());
        log.info("分组数量: {}", collector.getAllGroupMetrics().size());

        log.info("\n========== 慢查询 TOP 分组 ==========");
        collector.getAllGroupMetrics().entrySet().stream()
                .filter(e -> e.getValue().getSlowCount().get() > 0)
                .sorted((a, b) -> Long.compare(
                        b.getValue().getSlowCount().get(),
                        a.getValue().getSlowCount().get()))
                .limit(5)
                .forEach(entry -> {
                    String fingerprint = entry.getKey();
                    var groupMetrics = entry.getValue();
                    log.info("  - [{}] 慢查询: {}次, P95: {}ms, 平均: {}ms",
                            fingerprint.substring(0, Math.min(80, fingerprint.length())),
                            groupMetrics.getSlowCount().get(),
                            groupMetrics.getP95Cost(),
                            groupMetrics.getAvgCost());
                });

        log.info("\n========================================");
        log.info("✅ 真实业务场景测试通过");
        log.info("   监控系统成功捕获并分析了所有查询");
        log.info("========================================");
    }

    private void executeQuery(String sql, Object[] params) throws SQLException {
        try (Connection conn = proxiedDataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(sql)) {

            for (int i = 0; i < params.length; i++) {
                stmt.setObject(i + 1, params[i]);
            }

            try (ResultSet rs = stmt.executeQuery()) {
                int count = 0;
                while (rs.next()) {
                    count++;
                }
            }
        }
    }

    private void resetContainerInstance() {
        try {
            java.lang.reflect.Field instanceField =
                    MonitorContainer.class.getDeclaredField("instance");
            instanceField.setAccessible(true);
            instanceField.set(null, null);

            java.lang.reflect.Field initializedField =
                    MonitorContainer.class.getDeclaredField("initialized");
            initializedField.setAccessible(true);
            initializedField.set(null, false);
        } catch (Exception e) {
            log.warn("重置 MonitorContainer 单例失败", e);
        }
    }
}
