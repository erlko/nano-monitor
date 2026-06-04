package com.nano.monitor.container;

import com.nano.monitor.analysis.AiAnalysisService;
import com.nano.monitor.analysis.SqlAnalysisEngine;
import com.nano.monitor.analysis.rule.*;
import com.nano.monitor.autoconfigure.NanoMonitorProperties;
import com.nano.monitor.collector.MetricsCollector;
import com.nano.monitor.eventbus.EventBus;
import com.nano.monitor.eventbus.listener.SlowQueryAnalysisListener;
import com.nano.monitor.proxy.JdbcProxyFactory;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class MonitorContainer {

    private static volatile MonitorContainer instance;
    private static volatile boolean initialized = false;
    private final Map<Class<?>, Object> components = new HashMap<>();

    private MonitorContainer() {
    }

    public static MonitorContainer getInstance() {
        if (instance == null) {
            synchronized (MonitorContainer.class) {
                if (instance == null) {
                    instance = new MonitorContainer();
                }
            }
        }
        return instance;
    }

    public static void initialize(DataSource dataSource, NanoMonitorProperties properties) {
        if (initialized) {
            log.warn("⚠️ MonitorContainer 已经初始化，跳过重复初始化");
            return;
        }

        synchronized (MonitorContainer.class) {
            if (initialized) {
                return;
            }

            MonitorContainer container = getInstance();

            log.info("🚀 初始化 Nano-Monitor 容器...");
            log.info("   慢 SQL 阈值: {}ms", properties.getSlowQueryThreshold());
            log.info("   自动分析: {}", properties.isAutoAnalysis());
            log.info("   缓存过期: {}分钟", properties.getCacheExpireMinutes());

            EventBus eventBus = new EventBus(
                    properties.getThreadPoolCoreSize(),
                    properties.getThreadPoolMaxSize(),
                    properties.getThreadPoolQueueCapacity()
            );
            container.register(EventBus.class, eventBus);

            MetricsCollector collector = new MetricsCollector(eventBus, properties.getSlowQueryThreshold());
            container.register(MetricsCollector.class, collector);

            if (properties.isAutoAnalysis()) {
                log.info("   📊 开启自动分析模式（完整监控）");

                RuleEngine ruleEngine = new RuleEngine();
                ruleEngine.addRule(new TypeFieldRule());
                ruleEngine.addRule(new ExtraFieldRule());
                ruleEngine.addRule(new IndexInvalidRule());
                container.register(RuleEngine.class, ruleEngine);

                AiAnalysisService aiAnalysisService = null;
                if (properties.getAi().isEnabled()) {
                    aiAnalysisService = new AiAnalysisService(
                            properties.getAi().getApiKey(),
                            properties.getAi().getApiUrl(),
                            properties.getAi().getModel()
                    );
                    container.register(AiAnalysisService.class, aiAnalysisService);
                    log.info("   ✅ AI 分析服务已启用");
                } else {
                    log.info("   ⚠️ AI Agent 未启用，仅使用规则引擎");
                }

                SqlAnalysisEngine analysisEngine = new SqlAnalysisEngine(
                        dataSource,
                        ruleEngine,
                        aiAnalysisService,
                        properties.getSlowQueryThreshold(),
                        properties.getAi().getTriggerScoreThreshold()
                );
                container.register(SqlAnalysisEngine.class, analysisEngine);

                SlowQueryAnalysisListener slowQueryAnalysisListener = new SlowQueryAnalysisListener(analysisEngine);
                eventBus.register(slowQueryAnalysisListener);
                log.info("   ✅ 注册 AnalysisListener（慢查询分析监听器）");

            } else {
                log.info("   📈 开启轻量监控模式（仅统计，不分析）");
            }

            JdbcProxyFactory proxyFactory = new JdbcProxyFactory(properties.isAutoAnalysis());
            container.register(JdbcProxyFactory.class, proxyFactory);

            initialized = true;
            log.info("✅ Nano-Monitor 容器初始化完成");
        }
    }

    public <T> void register(Class<T> type, T instance) {
        components.put(type, instance);
        log.debug("注册组件: {}", type.getSimpleName());
    }

    public <T> T get(Class<T> type) {
        Object instance = components.get(type);
        if (instance == null) {
            throw new IllegalStateException("组件未注册: " + type.getSimpleName());
        }
        return type.cast(instance);
    }

    public DataSource createProxiedDataSource(DataSource original) {
        MetricsCollector collector = get(MetricsCollector.class);
        JdbcProxyFactory proxyFactory = get(JdbcProxyFactory.class);
        return proxyFactory.createDataSourceProxy(original, collector);
    }

    public void shutdown() {
        log.info("🔒 关闭 Nano-Monitor 容器...");
        EventBus eventBus = get(EventBus.class);
        eventBus.shutdown();
        log.info("✅ Nano-Monitor 容器已关闭");
    }
}
