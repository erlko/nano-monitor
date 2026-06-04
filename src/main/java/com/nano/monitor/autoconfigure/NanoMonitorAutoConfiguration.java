package com.nano.monitor.autoconfigure;

import com.nano.monitor.container.MonitorContainer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import javax.sql.DataSource;

@Slf4j
@Configuration
@EnableConfigurationProperties(NanoMonitorProperties.class)  // ✅ 添加这行
@ConditionalOnProperty(prefix = "nano.monitor", name = "enabled", havingValue = "true", matchIfMissing = true)
public class NanoMonitorAutoConfiguration {

    private final DataSource dataSource;
    private final NanoMonitorProperties properties;  // ✅ 注入配置属性

    public NanoMonitorAutoConfiguration(DataSource dataSource, NanoMonitorProperties properties) {
        this.dataSource = dataSource;
        this.properties = properties;
    }

    @Bean
    @ConditionalOnMissingBean(MonitorContainer.class)
    public MonitorContainer monitorContainer() {
        log.info("🚀 自动配置 Nano-Monitor 容器...");
        MonitorContainer.initialize(dataSource, properties);  // ✅ 传入配置
        return MonitorContainer.getInstance();
    }

    @Bean
    @ConditionalOnMissingBean(name = "proxiedDataSource")
    public DataSource proxiedDataSource(MonitorContainer container) {
        DataSource proxied = container.createProxiedDataSource(dataSource);
        log.info("✅ 已创建代理数据源，SQL 监控已启用");
        return proxied;
    }
}
