package com.nano.monitor.proxy;

import com.nano.monitor.spi.SqlCollector;
import lombok.extern.slf4j.Slf4j;

import java.lang.reflect.Proxy;
import java.sql.Connection;
import java.sql.PreparedStatement;
import javax.sql.DataSource;
import java.util.HashMap;
import java.util.Map;

@Slf4j
public class JdbcProxyFactory {

    private final boolean captureParams;

    public JdbcProxyFactory(boolean captureParams) {
        this.captureParams = captureParams;
    }

    public DataSource createDataSourceProxy(DataSource target, SqlCollector collector) {
        return (DataSource) Proxy.newProxyInstance(
                DataSource.class.getClassLoader(),
                new Class<?>[]{DataSource.class},
                (proxy, method, args) -> {
                    if ("getConnection".equals(method.getName())) {
                        Connection conn = (Connection) method.invoke(target, args);
                        return createConnectionProxy(conn, collector);
                    }
                    return method.invoke(target, args);
                }
        );
    }

    private Connection createConnectionProxy(Connection target, SqlCollector collector) {
        return (Connection) Proxy.newProxyInstance(
                Connection.class.getClassLoader(),
                new Class<?>[]{Connection.class},
                (proxy, method, args) -> {
                    if (method.getName().startsWith("prepareStatement") && args != null && args.length > 0) {
                        PreparedStatement stmt = (PreparedStatement) method.invoke(target, args);
                        String sql = (String) args[0];
                        return createStatementProxy(stmt, sql, collector);
                    }
                    return method.invoke(target, args);
                }
        );
    }

    private PreparedStatement createStatementProxy(PreparedStatement target, String sql, SqlCollector collector) {
        // ✅ 根据配置决定是否创建 params Map
        Map<Integer, Object> params = captureParams ? new HashMap<>() : null;

        return (PreparedStatement) Proxy.newProxyInstance(
                PreparedStatement.class.getClassLoader(),
                new Class<?>[]{PreparedStatement.class},
                (proxy, method, args) -> {
                    String methodName = method.getName();

                    // ✅ 仅在开启参数捕获时拦截 setXXX
                    if (captureParams && methodName.startsWith("set") && args != null && args.length >= 2) {
                        try {
                            if (!(args[0] instanceof Integer)) {
                                log.warn("参数索引类型错误: method={}, argType={}",
                                        methodName, args[0].getClass().getName());
                                return method.invoke(target, args);
                            }

                            int paramIndex = (Integer) args[0];
                            Object paramValue = args[1];
                            params.put(paramIndex, paramValue);

                            return method.invoke(target, args);
                        } catch (Exception e) {
                            log.warn("参数捕获失败: method={}, error={}", methodName, e.getMessage());
                            return method.invoke(target, args);
                        }
                    }

                    if (isExecuteMethod(methodName)) {
                        long start = System.currentTimeMillis();
                        try {
                            return method.invoke(target, args);
                        } finally {
                            long cost = System.currentTimeMillis() - start;

                            // ✅ 根据配置决定调用哪个 collect 方法
                            if (captureParams) {
                                collector.collect(sql, params, cost);

                                // 执行后立即清理
                                params.clear();
                            } else {
                                collector.collect(sql, cost);
                            }
                        }
                    }

                    return method.invoke(target, args);
                }
        );
    }

    private static boolean isExecuteMethod(String methodName) {
        return "executeQuery".equals(methodName)
                || "executeUpdate".equals(methodName)
                || "execute".equals(methodName);
    }
}
