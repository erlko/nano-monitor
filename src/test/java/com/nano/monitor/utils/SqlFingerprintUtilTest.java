package com.nano.monitor.utils;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static org.junit.jupiter.api.Assertions.*;

@DisplayName("SQL 指纹生成器测试")
class SqlFingerprintUtilTest {

    private static final Logger log = LoggerFactory.getLogger(SqlFingerprintUtilTest.class);

    @Test
    @DisplayName("测试 1：相同 SQL 不同参数应生成相同指纹")
    void testSameSqlDifferentParameters() {
        log.info("\n========== 测试 1：相同 SQL 不同参数 ==========");

        String sql1 = "SELECT * FROM users WHERE id = 123";
        String sql2 = "SELECT * FROM users WHERE id = 456";
        String sql3 = "SELECT * FROM users WHERE id = 9999";

        String fp1 = SqlFingerprintUtil.generate(sql1);
        String fp2 = SqlFingerprintUtil.generate(sql2);
        String fp3 = SqlFingerprintUtil.generate(sql3);

        log.info("原始 SQL 1: {}", sql1);
        log.info("原始 SQL 2: {}", sql2);
        log.info("原始 SQL 3: {}", sql3);
        log.info("指纹 1: {}", fp1);
        log.info("指纹 2: {}", fp2);
        log.info("指纹 3: {}", fp3);

        assertEquals(fp1, fp2, "相同结构的 SQL 应生成相同指纹");
        assertEquals(fp2, fp3);
        assertTrue(fp1.contains("?"), "指纹应包含参数占位符");

        log.info("✅ 测试通过：所有指纹相同 = {}", fp1);
    }

    @Test
    @DisplayName("测试 2：字符串参数应被替换为 ?")
    void testStringParameterReplacement() {
        log.info("\n========== 测试 2：字符串参数替换 ==========");

        String sql1 = "SELECT * FROM users WHERE username = 'alice'";
        String sql2 = "SELECT * FROM users WHERE username = 'bob'";
        String sql3 = "SELECT * FROM users WHERE username = 'charlie'";

        String fp1 = SqlFingerprintUtil.generate(sql1);
        String fp2 = SqlFingerprintUtil.generate(sql2);
        String fp3 = SqlFingerprintUtil.generate(sql3);

        log.info("原始 SQL 1: {}", sql1);
        log.info("指纹 1: {}", fp1);
        log.info("原始 SQL 2: {}", sql2);
        log.info("指纹 2: {}", fp2);
        log.info("原始 SQL 3: {}", sql3);
        log.info("指纹 3: {}", fp3);

        assertEquals(fp1, fp2, "字符串参数不同应生成相同指纹");
        assertEquals(fp2, fp3);
        assertFalse(fp1.contains("alice"), "不应包含原始字符串值");
        assertFalse(fp1.contains("bob"), "不应包含原始字符串值");

        log.info("✅ 测试通过：字符串参数已被替换，指纹 = {}", fp1);
    }

    @Test
    @DisplayName("测试 3：空白字符应被规范化")
    void testWhitespaceNormalization() {
        log.info("\n========== 测试 3：空白字符规范化 ==========");

        String sql1 = "SELECT  *  FROM  users";
        String sql2 = "SELECT * FROM users";
        String sql3 = "SELECT\n*\nFROM\nusers";
        String sql4 = "SELECT\t*\tFROM\tusers";

        String fp1 = SqlFingerprintUtil.generate(sql1);
        String fp2 = SqlFingerprintUtil.generate(sql2);
        String fp3 = SqlFingerprintUtil.generate(sql3);
        String fp4 = SqlFingerprintUtil.generate(sql4);

        log.info("原始 SQL 1 (多空格): {}", sql1.replace(" ", "·"));
        log.info("指纹 1: {}", fp1);
        log.info("原始 SQL 2 (标准): {}", sql2);
        log.info("指纹 2: {}", fp2);
        log.info("原始 SQL 3 (换行): {}", sql3.replace("\n", "\\n"));
        log.info("指纹 3: {}", fp3);
        log.info("原始 SQL 4 (制表符): {}", sql4.replace("\t", "\\t"));
        log.info("指纹 4: {}", fp4);

        assertEquals(fp1, fp2);
        assertEquals(fp2, fp3);
        assertEquals(fp3, fp4);

        log.info("✅ 测试通过：所有空白字符规范化后指纹相同 = {}", fp1);
    }

    @Test
    @DisplayName("测试 4：NULL 和布尔值应被替换")
    void testNullAndBooleanReplacement() {
        log.info("\n========== 测试 4：NULL 和布尔值替换 ==========");

        String sql1 = "SELECT * FROM users WHERE status IS NULL";
        String sql2 = "SELECT * FROM users WHERE status IS NOT NULL";
        String sql3 = "SELECT * FROM users WHERE active = TRUE";
        String sql4 = "SELECT * FROM users WHERE active = FALSE";

        String fp1 = SqlFingerprintUtil.generate(sql1);
        String fp2 = SqlFingerprintUtil.generate(sql2);
        String fp3 = SqlFingerprintUtil.generate(sql3);
        String fp4 = SqlFingerprintUtil.generate(sql4);

        log.info("原始 SQL 1: {}", sql1);
        log.info("指纹 1: {}", fp1);
        log.info("原始 SQL 2: {}", sql2);
        log.info("指纹 2: {}", fp2);
        log.info("原始 SQL 3: {}", sql3);
        log.info("指纹 3: {}", fp3);
        log.info("原始 SQL 4: {}", sql4);
        log.info("指纹 4: {}", fp4);

        assertTrue(fp1.contains("IS ?"), "NULL 应被替换为 ?");
        assertTrue(fp2.contains("IS NOT ?"), "NOT NULL 应正确处理");
        assertTrue(fp3.contains("= ?"), "TRUE 应被替换为 ?");
        assertTrue(fp4.contains("= ?"), "FALSE 应被替换为 ?");

        log.info("✅ 测试通过：NULL 和布尔值已被替换");
    }

    @Test
    @DisplayName("测试 5：不同 SQL 应生成不同指纹")
    void testDifferentSqlGeneratesDifferentFingerprints() {
        log.info("\n========== 测试 5：不同 SQL 不同指纹 ==========");

        String sql1 = "SELECT * FROM users";
        String sql2 = "SELECT * FROM orders";
        String sql3 = "INSERT INTO users VALUES (?)";
        String sql4 = "UPDATE users SET name = ?";
        String sql5 = "DELETE FROM users WHERE id = ?";

        String fp1 = SqlFingerprintUtil.generate(sql1);
        String fp2 = SqlFingerprintUtil.generate(sql2);
        String fp3 = SqlFingerprintUtil.generate(sql3);
        String fp4 = SqlFingerprintUtil.generate(sql4);
        String fp5 = SqlFingerprintUtil.generate(sql5);

        log.info("SELECT users 指纹: {}", fp1);
        log.info("SELECT orders 指纹: {}", fp2);
        log.info("INSERT 指纹: {}", fp3);
        log.info("UPDATE 指纹: {}", fp4);
        log.info("DELETE 指纹: {}", fp5);

        assertNotEquals(fp1, fp2, "不同表的 SQL 应有不同指纹");
        assertNotEquals(fp1, fp3, "不同操作的 SQL 应有不同指纹");
        assertNotEquals(fp3, fp4, "INSERT 和 UPDATE 应有不同指纹");

        log.info("✅ 测试通过：所有不同 SQL 生成了不同指纹");
    }

    @Test
    @DisplayName("测试 6：大小写不敏感")
    void testCaseInsensitive() {
        log.info("\n========== 测试 6：大小写不敏感 ==========");

        String sql1 = "select * from users where id = 1";
        String sql2 = "SELECT * FROM USERS WHERE ID = 2";
        String sql3 = "Select * From Users Where Id = 3";
        String sql4 = "SeLeCt * FrOm UsErS WhErE Id = 4";

        String fp1 = SqlFingerprintUtil.generate(sql1);
        String fp2 = SqlFingerprintUtil.generate(sql2);
        String fp3 = SqlFingerprintUtil.generate(sql3);
        String fp4 = SqlFingerprintUtil.generate(sql4);

        log.info("原始 SQL 1 (全小写): {}", sql1);
        log.info("指纹 1: {}", fp1);
        log.info("原始 SQL 2 (全大写): {}", sql2);
        log.info("指纹 2: {}", fp2);
        log.info("原始 SQL 3 (首字母大写): {}", sql3);
        log.info("指纹 3: {}", fp3);
        log.info("原始 SQL 4 (混合大小写): {}", sql4);
        log.info("指纹 4: {}", fp4);

        assertEquals(fp1, fp2, "大小写不同的 SQL 应生成相同指纹");
        assertEquals(fp2, fp3);
        assertEquals(fp3, fp4);

        log.info("✅ 测试通过：所有大小写变体生成相同指纹 = {}", fp1);
    }

    @Test
    @DisplayName("测试 7：复杂 SQL 场景（JOIN、多条件）")
    void testComplexSqlScenarios() {
        log.info("\n========== 测试 7：复杂 SQL 场景 ==========");

        String sql1 = "SELECT u.name, o.total FROM users u JOIN orders o ON u.id = o.user_id WHERE u.age > 18 AND o.status = 'PAID' ORDER BY o.total DESC LIMIT 10";
        String sql2 = "SELECT u.name, o.total FROM users u JOIN orders o ON u.id = o.user_id WHERE u.age > 25 AND o.status = 'PENDING' ORDER BY o.total DESC LIMIT 20";

        String fp1 = SqlFingerprintUtil.generate(sql1);
        String fp2 = SqlFingerprintUtil.generate(sql2);

        log.info("原始 SQL 1:");
        log.info("  {}", sql1);
        log.info("指纹 1:");
        log.info("  {}", fp1);
        log.info("原始 SQL 2:");
        log.info("  {}", sql2);
        log.info("指纹 2:");
        log.info("  {}", fp2);

        assertEquals(fp1, fp2, "复杂 SQL 但结构相同应生成相同指纹");
        assertTrue(fp1.contains("JOIN ORDERS O ON U.ID = O.USER_ID"));
        assertTrue(fp1.contains("WHERE U.AGE > ? AND O.STATUS = ?"));

        log.info("✅ 测试通过：复杂 SQL 指纹生成正确");
    }

    @Test
    @DisplayName("测试 8：实际业务 SQL 示例")
    void testRealWorldSqlExamples() {
        log.info("\n========== 测试 8：实际业务 SQL 示例 ==========");

        String[] sqlExamples = {
                "SELECT * FROM orders WHERE user_id = 1001 AND status = 'PAID' ORDER BY create_time DESC LIMIT 20",
                "SELECT * FROM orders WHERE user_id = 2002 AND status = 'PENDING' ORDER BY create_time DESC LIMIT 20",
                "INSERT INTO order_items (order_id, product_id, quantity, price) VALUES (123, 456, 2, 99.99)",
                "UPDATE users SET last_login = '2024-01-15 10:30:00', login_count = 15 WHERE id = 1001",
                "DELETE FROM cart_items WHERE user_id = 1001 AND product_id = 456",
                "SELECT COUNT(*) as total, AVG(amount) as avg_amount FROM orders WHERE create_time > '2024-01-01'"
        };

        for (int i = 0; i < sqlExamples.length; i++) {
            String sql = sqlExamples[i];
            String fingerprint = SqlFingerprintUtil.generate(sql);
            log.info("示例 {}:", i + 1);
            log.info("  原始 SQL: {}", sql);
            log.info("  指纹: {}", fingerprint);
        }

        assertEquals(
                SqlFingerprintUtil.generate(sqlExamples[0]),
                SqlFingerprintUtil.generate(sqlExamples[1]),
                "相同结构的查询应生成相同指纹"
        );

        log.info("✅ 测试通过：实际业务 SQL 指纹生成正常");
    }

    @Test
    @DisplayName("测试 9：边界情况处理")
    void testEdgeCases() {
        log.info("\n========== 测试 9：边界情况 ==========");

        log.info("测试 null 输入");
        assertNull(SqlFingerprintUtil.generate(null), "null 输入应返回 null");

        log.info("测试空字符串");
        assertEquals("", SqlFingerprintUtil.generate(""), "空字符串应返回空字符串");

        log.info("测试只包含空格的字符串");
        String spaceOnly = "   ";
        String fp = SqlFingerprintUtil.generate(spaceOnly);
        log.info("  原始: '{}'", spaceOnly);
        log.info("  指纹: '{}'", fp);
        assertEquals("", fp, "只包含空格的字符串应返回空字符串");

        log.info("测试没有参数的简单 SQL");
        String simpleSql = "SELECT * FROM users";
        String simpleFp = SqlFingerprintUtil.generate(simpleSql);
        log.info("  原始: {}", simpleSql);
        log.info("  指纹: {}", simpleFp);
        assertEquals("SELECT * FROM USERS", simpleFp);

        log.info("✅ 测试通过：边界情况处理正确");
    }

    @Test
    @DisplayName("测试 10：指纹统计效果验证")
    void testFingerprintGroupingEffectiveness() {
        log.info("\n========== 测试 10：指纹统计效果验证 ==========");

        String[] sqlArray = {
                "SELECT * FROM users WHERE id = 1",
                "SELECT * FROM users WHERE id = 2",
                "SELECT * FROM orders WHERE user_id = 100",
                "SELECT * FROM orders WHERE user_id = 200",
                "INSERT INTO logs VALUES (1, 'info', 'test')",
                "INSERT INTO logs VALUES (2, 'error', 'fail')",
        };

        java.util.Map<String, Integer> fingerprintCount = new java.util.HashMap<>();
        for (String sql : sqlArray) {
            String fp = SqlFingerprintUtil.generate(sql);
            fingerprintCount.put(fp, fingerprintCount.getOrDefault(fp, 0) + 1);
            log.info("SQL: {} -> 指纹: {}", sql, fp);
        }

        log.info("\n指纹分组统计结果:");
        fingerprintCount.forEach((fp, count) ->
                log.info("  指纹: {} (出现 {} 次)", fp, count)
        );

        assertEquals(3, fingerprintCount.size(), "应生成 3 个不同的指纹组");

        for (Integer count : fingerprintCount.values()) {
            assertEquals(2, count, "每个指纹组应该有 2 条 SQL");
        }

        log.info("✅ 测试通过：指纹分组统计效果正确");
    }
}
