package com.nano.monitor.proxy;

import com.nano.monitor.utils.TestDatabaseConfig;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import lombok.extern.slf4j.Slf4j;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.DisplayName;

import javax.sql.DataSource;
import java.sql.*;

import static org.junit.jupiter.api.Assertions.*;

@Slf4j
@TestMethodOrder(MethodOrderer.OrderAnnotation.class)
class JdbcProxyFactoryTest {

    private DataSource proxiedDataSource;


    @BeforeEach
    void setUp() {
        log.info("\n🚀 ========== 初始化代理测试环境 ==========");

        SimpleSqlCollector testCollector = new SimpleSqlCollector();

        DataSource originalDataSource = TestDatabaseConfig.createDataSourceAsInterface();

        JdbcProxyFactory proxyFactory = new JdbcProxyFactory(true);
        proxiedDataSource = proxyFactory.createDataSourceProxy(originalDataSource, testCollector);

        log.info("✅ 代理数据源创建成功");
    }


    @Test
    @Order(1)
    @DisplayName("测试 1：验证 SELECT 查询参数拦截")
    void testSelectQueryParameterCapture() throws SQLException {
        log.info("\n========== 测试 1：SELECT 查询参数拦截 ==========");

        try (Connection conn = proxiedDataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT * FROM test_user WHERE id = ? AND username = ?")) {

            stmt.setInt(1, 123);
            stmt.setString(2, "test_user");

            try (ResultSet rs = stmt.executeQuery()) {
                while (rs.next()) {
                    log.info("查询结果: id={}, username={}", rs.getInt("id"), rs.getString("username"));
                    break;
                }
            }
        }

        log.info("✅ 测试 1 完成：请检查上方日志是否显示完整 SQL（无占位符）");
    }

    @Test
    @Order(2)
    @DisplayName("测试 2：验证 INSERT 语句参数拦截")
    void testInsertStatementParameterCapture() throws SQLException {
        log.info("\n========== 测试 2：INSERT 语句参数拦截 ==========");

        String timestamp = String.valueOf(System.currentTimeMillis());
        String username = "test_insert_" + timestamp;
        String email = username + "@example.com";
        int age = 25;

        try (Connection conn = proxiedDataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "INSERT INTO test_user (username, email, age) VALUES (?, ?, ?)")) {

            stmt.setString(1, username);
            stmt.setString(2, email);
            stmt.setInt(3, age);

            int affectedRows = stmt.executeUpdate();
            assertEquals(1, affectedRows, "应该插入 1 条记录");
        }

        log.info("✅ 测试 2 完成：请检查上方日志是否显示完整的 INSERT SQL");
    }

    @Test
    @Order(3)
    @DisplayName("测试 3：验证 UPDATE 语句参数拦截")
    void testUpdateStatementParameterCapture() throws SQLException {
        log.info("\n========== 测试 3：UPDATE 语句参数拦截 ==========");

        try (Connection conn = proxiedDataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "UPDATE test_user SET age = ? WHERE username LIKE ?")) {

            stmt.setInt(1, 30);
            stmt.setString(2, "%test%");

            int affectedRows = stmt.executeUpdate();
            log.info("更新了 {} 条记录", affectedRows);
        }

        log.info("✅ 测试 3 完成：请检查上方日志是否显示 'age = 30' 和 'LIKE '%test%''");
    }

    @Test
    @Order(4)
    @DisplayName("测试 4：验证特殊字符转义")
    void testSpecialCharacterEscaping() throws SQLException {
        log.info("\n========== 测试 4：特殊字符转义 ==========");

        String specialUsername = "O'Brien";

        try (Connection conn = proxiedDataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "INSERT INTO test_user (username, email, age) VALUES (?, ?, ?)")) {

            stmt.setString(1, specialUsername);
            stmt.setString(2, "obrien@example.com");
            stmt.setInt(3, 28);

            int affectedRows = stmt.executeUpdate();
            assertEquals(1, affectedRows, "应该插入 1 条记录");
        }

        log.info("✅ 测试 4 完成：请检查上方日志中单引号是否被转义为 ''");
    }

    @Test
    @Order(5)
    @DisplayName("测试 5：验证 NULL 值处理")
    void testNullValueHandling() throws SQLException {
        log.info("\n========== 测试 5：NULL 值处理 ==========");

        try (Connection conn = proxiedDataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "INSERT INTO test_user (username, email, age) VALUES (?, ?, ?)")) {

            stmt.setString(1, "null_test_user");
            stmt.setNull(2, Types.VARCHAR);
            stmt.setInt(3, 22);

            int affectedRows = stmt.executeUpdate();
            assertEquals(1, affectedRows, "应该插入 1 条记录");
        }

        log.info("✅ 测试 5 完成：请检查上方日志中 NULL 值是否显示为 NULL");
    }

    @Test
    @Order(6)
    @DisplayName("测试 6：验证多次执行同一 Statement")
    void testMultipleExecutionsOfSameStatement() throws SQLException {
        log.info("\n========== 测试 6：多次执行同一 Statement ==========");

        try (Connection conn = proxiedDataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement("SELECT * FROM test_user WHERE id = ?")) {

            for (int i = 1; i <= 3; i++) {
                stmt.setInt(1, i);
                try (ResultSet rs = stmt.executeQuery()) {
                    if (rs.next()) {
                        log.info("第 {} 次查询: id={}, username={}", i, rs.getInt("id"), rs.getString("username"));
                    }
                }
            }
        }

        log.info("✅ 测试 6 完成：请检查上方日志是否显示 3 次不同的 SQL（id=1, id=2, id=3）");
    }

    @Test
    @Order(7)
    @DisplayName("测试 7：验证复杂查询多参数")
    void testComplexQueryWithMultipleParameters() throws SQLException {
        log.info("\n========== 测试 7：复杂查询多参数 ==========");

        try (Connection conn = proxiedDataSource.getConnection();
             PreparedStatement stmt = conn.prepareStatement(
                     "SELECT * FROM test_user WHERE age > ? AND age < ? AND username LIKE ? ORDER BY id LIMIT ?")) {

            stmt.setInt(1, 20);
            stmt.setInt(2, 30);
            stmt.setString(3, "%user%");
            stmt.setInt(4, 5);

            try (ResultSet rs = stmt.executeQuery()) {
                int count = 0;
                while (rs.next()) {
                    count++;
                }
                log.info("查询返回 {} 条记录", count);
            }
        }

        log.info("✅ 测试 7 完成：请检查上方日志是否显示所有 4 个参数都被替换");
    }

    @AfterEach
    void tearDown() {
        // ✅ 清理测试数据
        try (Connection conn = proxiedDataSource.getConnection();
             Statement stmt = conn.createStatement()) {
            stmt.execute("DELETE FROM test_user WHERE username LIKE 'test_%' OR username LIKE 'null_%' OR username LIKE 'O%'");
            stmt.execute("ALTER TABLE test_user AUTO_INCREMENT = 1");
            log.info("🧹 清理测试数据完成");
        } catch (Exception e) {
            log.warn("清理测试数据失败: {}", e.getMessage());
        }

        if (proxiedDataSource instanceof AutoCloseable) {
            try {
                ((AutoCloseable) proxiedDataSource).close();
                log.info("🔚 关闭数据源");
            } catch (Exception e) {
                log.error("关闭数据源失败", e);
            }
        }
    }
}
