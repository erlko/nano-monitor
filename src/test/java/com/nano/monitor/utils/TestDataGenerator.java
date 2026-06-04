package com.nano.monitor.utils;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.junit.jupiter.api.*;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;

import static org.junit.jupiter.api.Assertions.assertTrue;

@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class TestDataGenerator {

    private static final Logger log = LoggerFactory.getLogger(TestDataGenerator.class);
    private HikariDataSource dataSource;

    @BeforeEach
    void setUp() {
        dataSource = TestDatabaseConfig.createDataSource();
        log.info("✅ 数据源初始化完成");
    }

    @AfterEach
    void tearDown() {
        if (dataSource != null && !dataSource.isClosed()) {
            dataSource.close();
            log.info("🔚 关闭数据源");
        }
    }

    @Test
    @Order(1)
    @DisplayName("生成基础测试数据 - 1000 条记录")
    void generateBasicTestData() throws SQLException {
        log.info("\n========== 生成基础测试数据 ==========");

        try (Connection conn = dataSource.getConnection()) {
            conn.createStatement().executeUpdate(
                    "CREATE TABLE IF NOT EXISTS test_user (" +
                            "id INT AUTO_INCREMENT PRIMARY KEY, " +
                            "username VARCHAR(100), " +
                            "email VARCHAR(200), " +
                            "age INT, " +
                            "created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP" +
                            ")"
            );

            conn.createStatement().executeUpdate("DELETE FROM test_user");
            conn.createStatement().executeUpdate("ALTER TABLE test_user AUTO_INCREMENT = 1");

            PreparedStatement stmt = conn.prepareStatement(
                    "INSERT INTO test_user (username, email, age) VALUES (?, ?, ?)");

            int totalRecords = 1000;
            for (int i = 1; i <= totalRecords; i++) {
                stmt.setString(1, "user_" + i);
                stmt.setString(2, "user_" + i + "@example.com");
                stmt.setInt(3, 18 + (i % 50));
                stmt.addBatch();

                if (i % 100 == 0) {
                    stmt.executeBatch();
                    log.info("已插入 {} / {} 条记录...", i, totalRecords);
                }
            }
            stmt.executeBatch();

            ResultSet rs = conn.createStatement().executeQuery("SELECT COUNT(*) FROM test_user");
            if (rs.next()) {
                log.info("✅ 成功插入 {} 条测试数据", rs.getInt(1));
            }

            rs = conn.createStatement().executeQuery("SELECT MIN(id), MAX(id) FROM test_user");
            if (rs.next()) {
                log.info("📊 ID 范围: {} - {}", rs.getInt(1), rs.getInt(2));
            }
        }
    }

    @Test
    @Order(2)
    @DisplayName("验证测试数据完整性")
    void verifyTestDataIntegrity() throws SQLException {
        log.info("\n========== 验证测试数据完整性 ==========");

        try (Connection conn = dataSource.getConnection()) {
            ResultSet rs = conn.createStatement().executeQuery(
                    "SELECT COUNT(*), MIN(age), MAX(age), AVG(age) FROM test_user");

            if (rs.next()) {
                int count = rs.getInt(1);
                int minAge = rs.getInt(2);
                int maxAge = rs.getInt(3);
                double avgAge = rs.getDouble(4);

                log.info("📊 数据统计:");
                log.info("   - 总记录数: {}", count);
                log.info("   - 年龄范围: {} - {}", minAge, maxAge);
                log.info("   - 平均年龄: {}", String.format("%.2f", avgAge));

                assertTrue(count >= 1000, "至少应有 1000 条记录");
            }

            rs = conn.createStatement().executeQuery("SELECT * FROM test_user WHERE id = 1");
            assertTrue(rs.next(), "id=1 的记录应该存在");
            log.info("✅ id=1 的记录存在: username={}", rs.getString("username"));

            rs = conn.createStatement().executeQuery("SELECT * FROM test_user LIMIT 5");
            log.info("📋 前 5 条记录:");
            while (rs.next()) {
                log.info("   - id={}, username={}, age={}",
                        rs.getInt("id"), rs.getString("username"), rs.getInt("age"));
            }
        }

        log.info("✅ 测试数据完整性验证通过");
    }

    @Test
    @Order(3)
    @DisplayName("清空所有测试数据")
    void clearAllTestData() throws SQLException {
        log.info("\n========== 清空所有测试数据 ==========");

        try (Connection conn = dataSource.getConnection()) {
            conn.createStatement().executeUpdate("DELETE FROM test_user");
            conn.createStatement().executeUpdate("ALTER TABLE test_user AUTO_INCREMENT = 1");

            ResultSet rs = conn.createStatement().executeQuery("SELECT COUNT(*) FROM test_user");
            if (rs.next()) {
                log.info("✅ 已清空测试表，当前记录数: {}", rs.getInt(1));
            }
        }
    }
}
