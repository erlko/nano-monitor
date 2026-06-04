package com.nano.monitor.container;

import com.nano.monitor.autoconfigure.NanoMonitorProperties;
import lombok.extern.slf4j.Slf4j;

import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

/**
 * Nano-Monitor 配置加载工具
 * 用于非 Spring Boot 环境下的快速初始化
 */
@Slf4j
public class MonitorConfigLoader {

    private static final String DEFAULT_CONFIG_FILE = "nano-monitor.properties";
    private static final String CONFIG_PREFIX = "nano.monitor.";

    /**
     * 从默认配置文件加载配置
     * 默认读取 classpath:nano-monitor.properties
     */
    public static NanoMonitorProperties load() {
        return load(DEFAULT_CONFIG_FILE);
    }

    /**
     * 从指定配置文件加载配置
     *
     * @param configFile 配置文件路径（classpath 相对路径）
     */
    public static NanoMonitorProperties load(String configFile) {
        NanoMonitorProperties properties = new NanoMonitorProperties();

        try (InputStream input = MonitorConfigLoader.class.getClassLoader()
                .getResourceAsStream(configFile)) {

            if (input == null) {
                log.warn("未找到配置文件: {}, 使用默认配置", configFile);
                return applyDefaults(properties);
            }

            Properties props = new Properties();
            props.load(input);

            // 映射配置项
            properties.setEnabled(getBoolean(props, "enabled", true));
            properties.setSlowQueryThreshold(getLong(props, "slow-query-threshold", 500));
            properties.setAutoAnalysis(getBoolean(props, "auto-analysis", true));
            properties.setCacheExpireMinutes(getInt(props, "cache-expire-minutes", 30));
            properties.setThreadPoolCoreSize(getInt(props, "thread-pool.core-size", 2));
            properties.setThreadPoolMaxSize(getInt(props, "thread-pool.max-size", 4));
            properties.setThreadPoolQueueCapacity(getInt(props, "thread-pool.queue-capacity", 1000));


            // 映射 AI Agent 配置
            NanoMonitorProperties.Ai ai = new NanoMonitorProperties.Ai();
            ai.setEnabled(getBoolean(props, "ai.enabled", false));
            ai.setApiKey(props.getProperty(CONFIG_PREFIX + "ai.api-key", ""));
            ai.setApiUrl(props.getProperty(CONFIG_PREFIX + "ai.api-url", "https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation"));
            ai.setModel(props.getProperty(CONFIG_PREFIX + "ai.model", "qwen-turbo"));
            ai.setTemperature(Double.parseDouble(props.getProperty(CONFIG_PREFIX + "ai.temperature", "0.2")));
            ai.setTriggerScoreThreshold(getInt(props, "ai.trigger-score-threshold", 3));
            properties.setAi(ai);

            log.info("✅ 配置加载成功: {}", configFile);
            return properties;

        } catch (IOException e) {
            log.error("加载配置文件失败: {}", configFile, e);
            return applyDefaults(properties);
        }
    }

    /**
     * 应用默认配置
     */
    private static NanoMonitorProperties applyDefaults(NanoMonitorProperties properties) {
        properties.setEnabled(true);
        properties.setSlowQueryThreshold(500);
        properties.setAutoAnalysis(true);
        properties.setCacheExpireMinutes(30);
        properties.setThreadPoolCoreSize(2);
        properties.setThreadPoolMaxSize(4);
        properties.setThreadPoolQueueCapacity(1000);

        NanoMonitorProperties.Ai ai = new NanoMonitorProperties.Ai();
        ai.setEnabled(false);
        ai.setApiKey("");
        ai.setApiUrl("https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation");
        ai.setModel("qwen-turbo");
        ai.setTemperature(0.2);
        ai.setTriggerScoreThreshold(3);
        properties.setAi(ai);

        log.info("使用默认配置");
        return properties;
    }

    private static boolean getBoolean(Properties props, String key, boolean defaultValue) {
        String value = props.getProperty(CONFIG_PREFIX + key);
        return value != null ? Boolean.parseBoolean(value) : defaultValue;
    }

    private static int getInt(Properties props, String key, int defaultValue) {
        String value = props.getProperty(CONFIG_PREFIX + key);
        return value != null ? Integer.parseInt(value) : defaultValue;
    }

    private static long getLong(Properties props, String key, long defaultValue) {
        String value = props.getProperty(CONFIG_PREFIX + key);
        return value != null ? Long.parseLong(value) : defaultValue;
    }
}
