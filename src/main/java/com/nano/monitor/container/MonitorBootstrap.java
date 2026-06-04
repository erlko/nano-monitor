package com.nano.monitor.container;

import com.nano.monitor.autoconfigure.NanoMonitorProperties;
import lombok.extern.slf4j.Slf4j;

import javax.sql.DataSource;

/**
 * Nano-Monitor 快速初始化工具
 * 用于非 Spring Boot 环境下的简化接入
 */
@Slf4j
public class MonitorBootstrap {

    /**
     * 使用默认配置初始化监控容器
     *
     * @param dataSource 数据源
     */
    public static void initialize(DataSource dataSource) {
        NanoMonitorProperties properties = MonitorConfigLoader.load();
        initialize(dataSource, properties);
    }

    /**
     * 使用自定义配置初始化监控容器
     *
     * @param dataSource 数据源
     * @param properties 监控配置
     */
    public static void initialize(DataSource dataSource, NanoMonitorProperties properties) {
        try {
            MonitorContainer.initialize(dataSource, properties);
            log.info("✅ Nano-Monitor 初始化成功");
        } catch (Exception e) {
            log.error("❌ Nano-Monitor 初始化失败", e);
            throw new RuntimeException("监控容器初始化失败", e);
        }
    }

    /**
     * 使用指定配置文件初始化监控容器
     *
     * @param dataSource   数据源
     * @param configFile   配置文件路径
     */
    public static void initialize(DataSource dataSource, String configFile) {
        NanoMonitorProperties properties = MonitorConfigLoader.load(configFile);
        initialize(dataSource, properties);
    }

    /**
     * 关闭监控容器
     */
    public static void shutdown() {
        try {
            MonitorContainer container = MonitorContainer.getInstance();
            container.shutdown();
            log.info("✅ Nano-Monitor 已关闭");
        } catch (Exception e) {
            log.warn("关闭监控容器时发生异常", e);
        }
    }
}
