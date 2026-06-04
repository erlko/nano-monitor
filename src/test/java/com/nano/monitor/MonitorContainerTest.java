package com.nano.monitor;

import com.nano.monitor.autoconfigure.NanoMonitorProperties;
import com.nano.monitor.collector.MetricsCollector;
import com.nano.monitor.container.MonitorContainer;
import com.nano.monitor.eventbus.EventBus;
import com.nano.monitor.proxy.JdbcProxyFactory;
import com.nano.monitor.utils.TestDatabaseConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;

import javax.sql.DataSource;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class MonitorContainerTest {

    private DataSource testDataSource;

    @BeforeEach
    void setUp() {
        testDataSource = TestDatabaseConfig.createDataSourceAsInterface();
        log.info("✅ 测试数据源创建成功");
    }

    @Test
    @Order(1)
    @DisplayName("测试 1：验证单例模式")
    void testSingletonPattern() {
        log.info("\n========== 测试 1：验证单例模式 ==========");

        MonitorContainer instance1 = MonitorContainer.getInstance();
        MonitorContainer instance2 = MonitorContainer.getInstance();

        assertSame(instance1, instance2, "应该返回同一个实例");
        log.info("✅ 单例模式验证通过");
    }

    @Test
    @Order(2)
    @DisplayName("测试 2：验证轻量监控模式初始化")
    void testLightweightModeInitialization() {
        log.info("\n========== 测试 2：验证轻量监控模式初始化 ==========");

        NanoMonitorProperties properties = new NanoMonitorProperties();
        properties.setSlowQueryThreshold(1000);
        properties.setAutoAnalysis(false);
        properties.setCacheExpireMinutes(30);

        MonitorContainer.initialize(testDataSource, properties);
        MonitorContainer container = MonitorContainer.getInstance();

        EventBus eventBus = container.get(EventBus.class);
        assertNotNull(eventBus, "EventBus 应该被注册");

        MetricsCollector collector = container.get(MetricsCollector.class);
        assertNotNull(collector, "MetricsCollector 应该被注册");

        JdbcProxyFactory proxyFactory = container.get(JdbcProxyFactory.class);
        assertNotNull(proxyFactory, "JdbcProxyFactory 应该被注册");

        log.info("✅ 轻量监控模式初始化验证通过");
    }

    @Test
    @Order(3)
    @DisplayName("测试 3：验证自动分析模式初始化")
    void testAutoAnalysisModeInitialization() {
        log.info("\n========== 测试 3：验证自动分析模式初始化 ==========");

        NanoMonitorProperties properties = new NanoMonitorProperties();
        properties.setSlowQueryThreshold(500);
        properties.setAutoAnalysis(true);
        properties.setCacheExpireMinutes(60);

        MonitorContainer.initialize(testDataSource, properties);
        MonitorContainer container = MonitorContainer.getInstance();

        EventBus eventBus = container.get(EventBus.class);
        assertNotNull(eventBus, "EventBus 应该被注册");

        MetricsCollector collector = container.get(MetricsCollector.class);
        assertNotNull(collector, "MetricsCollector 应该被注册");

        JdbcProxyFactory proxyFactory = container.get(JdbcProxyFactory.class);
        assertNotNull(proxyFactory, "JdbcProxyFactory 应该被注册");

        log.info("✅ 自动分析模式初始化验证通过");
    }

    @Test
    @Order(4)
    @DisplayName("测试 4：验证代理数据源创建")
    void testProxiedDataSourceCreation() {
        log.info("\n========== 测试 4：验证代理数据源创建 ==========");

        NanoMonitorProperties properties = new NanoMonitorProperties();
        properties.setSlowQueryThreshold(1000);
        properties.setAutoAnalysis(false);

        MonitorContainer.initialize(testDataSource, properties);
        MonitorContainer container = MonitorContainer.getInstance();

        DataSource proxiedDataSource = container.createProxiedDataSource(testDataSource);
        assertNotNull(proxiedDataSource, "代理数据源不应该为 null");
        assertNotSame(testDataSource, proxiedDataSource, "代理数据源应该是新的实例");

        log.info("✅ 代理数据源创建验证通过");
    }

    @Test
    @Order(5)
    @DisplayName("测试 5：验证未注册组件抛出异常")
    void testUnregisteredComponentThrowsException() {
        log.info("\n========== 测试 5：验证未注册组件抛出异常 ==========");

        MonitorContainer container = MonitorContainer.getInstance();

        IllegalStateException exception = assertThrows(
                IllegalStateException.class,
                () -> container.get(String.class),
                "获取未注册的组件应该抛出 IllegalStateException"
        );

        assertTrue(exception.getMessage().contains("String"), "异常消息应该包含组件名称");
        log.info("✅ 未注册组件异常验证通过: {}", exception.getMessage());
    }

    @Test
    @Order(6)
    @DisplayName("测试 6：验证容器关闭")
    void testContainerShutdown() {
        log.info("\n========== 测试 6：验证容器关闭 ==========");

        NanoMonitorProperties properties = new NanoMonitorProperties();
        properties.setSlowQueryThreshold(1000);
        properties.setAutoAnalysis(false);

        MonitorContainer.initialize(testDataSource, properties);
        MonitorContainer container = MonitorContainer.getInstance();

        container.shutdown();

        log.info("✅ 容器关闭验证通过");
    }

    @AfterEach
    void tearDown() {
        if (testDataSource instanceof AutoCloseable) {
            try {
                ((AutoCloseable) testDataSource).close();
                log.info("🧹 测试数据源已关闭");
            } catch (Exception e) {
                log.warn("关闭测试数据源失败: {}", e.getMessage());
            }
        }
    }
}
