package com.nano.monitor.utils;


import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import javax.sql.DataSource;
import java.io.IOException;
import java.io.InputStream;
import java.util.Properties;

public class TestDatabaseConfig {

    private static final Logger log = LoggerFactory.getLogger(TestDatabaseConfig.class);
    private static final Properties properties = new Properties();

    static {
        try (InputStream input = TestDatabaseConfig.class.getClassLoader()
                .getResourceAsStream("test-database.properties")) {
            if (input == null) {
                throw new RuntimeException("无法找到 test-database.properties 文件");
            }
            properties.load(input);
            log.info("✅ 测试数据库配置加载成功");
        } catch (IOException e) {
            throw new RuntimeException("加载测试数据库配置失败", e);
        }
    }

    public static HikariConfig createHikariConfig() {
        HikariConfig config = new HikariConfig();
        config.setJdbcUrl(properties.getProperty("test.db.url"));
        config.setUsername(properties.getProperty("test.db.username"));
        config.setPassword(properties.getProperty("test.db.password"));
        config.setDriverClassName(properties.getProperty("test.db.driver-class-name"));
        config.setMinimumIdle(Integer.parseInt(properties.getProperty("test.db.minimum-idle", "1")));
        config.setMaximumPoolSize(Integer.parseInt(properties.getProperty("test.db.maximum-pool-size", "5")));
        config.setConnectionTimeout(Long.parseLong(properties.getProperty("test.db.connection-timeout", "30000")));
        return config;
    }

    public static HikariDataSource createDataSource() {
        return new HikariDataSource(createHikariConfig());
    }

    public static DataSource createDataSourceAsInterface() {
        return createDataSource();
    }

    public static String getJdbcUrl() {
        return properties.getProperty("test.db.url");
    }

    public static String getUsername() {
        return properties.getProperty("test.db.username");
    }

    public static String getPassword() {
        return properties.getProperty("test.db.password");
    }
}
