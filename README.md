# Nano-Monitor

<div align="center">

**轻量级智能 SQL 性能诊断工具**

[![Java](https://img.shields.io/badge/Java-17-orange.svg)](https://openjdk.java.net/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.x-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![License](https://img.shields.io/badge/License-MIT-blue.svg)](LICENSE)

</div>

---

## 🎯 项目简介

Nano-Monitor 是一款基于 **JDK 动态代理 + 事件驱动架构** 的轻量级 SQL 性能监控工具。通过**无侵入式采集**和**智能诊断分析**，帮助开发团队在上线前发现慢 SQL 问题，降低生产环境数据库性能风险。

### 核心价值

- 🔍 **提前暴露风险**: 在开发/测试阶段自动检测慢 SQL，避免联调阻塞和生产故障
- 🤖 **智能诊断建议**: 规则引擎 + AI 混合分析，自动生成可落地的优化方案
- ⚡ **零业务侵入**: 基于 JDK 动态代理拦截 SQL 执行，业务代码无需任何修改
- 📊 **精准性能洞察**: HdrHistogram 实现 P95/P99 百分位统计，准确捕捉长尾延迟

---

## ✨ 核心特性

### 🎯 无侵入式采集
- 基于 **JDK 动态代理**实现三级链路拦截（DataSource → Connection → PreparedStatement）
- 自动捕获 SQL 指纹、执行参数、耗时等关键指标
- 支持可选参数捕获模式，兼顾功能完整性与性能开销

### 📊 高性能统计
- **三层统计架构**: 全局概览 → 分组定位 → 深度分析，逐层细化
- **CAS 无锁并发**: AtomicLong + compareAndSet 实现高性能计数器
- **HdrHistogram 百分位统计**: 固定内存占用（~20KB），精准捕捉 P95/P99 长尾延迟

### 🤖 智能诊断引擎
- **EXPLAIN 执行计划解析**: 自动获取表名、访问类型、扫描行数、索引使用情况
- **责任链规则引擎**: 3 条规则覆盖全表扫描、文件排序、索引失效等 7 类常见问题
- **AI 深度诊断**: 阿里云通义千问 API 辅助分析复杂场景，提供差异化优化建议
- **智能触发机制**: 5 维度评分系统按需调用 AI，降低成本 70%+

### ⚡ 异步事件驱动
- **EventBus 解耦**: 慢查询分析与主业务逻辑完全分离，主线程零阻塞
- **背压控制**: 有界队列（capacity=1000）防止内存溢出
- **异常隔离**: 单个监听器失败不影响其他组件，确保系统稳定性

### 🔒 降级容错机制
- AI 服务异常时自动切换为纯规则引擎结果
- EXPLAIN 执行失败时优雅降级，记录日志并返回友好提示
- 超时控制（连接 10s / 请求 30s）防止外部服务挂起

### 🚀 Spring Boot Starter 零配置
- `@EnableConfigurationProperties` + `@ConditionalOnProperty` 自动装配
- 支持双模式运行：轻量监控（仅统计）vs 完整监控（统计 + 分析）
- 也支持独立 SDK 模式，灵活适配非 Spring 环境

---

## 🛠️ 技术栈

| 分类 | 技术选型 |
|------|---------|
| **核心框架** | Java 17, Spring Boot 3.x |
| **并发编程** | JDK 动态代理, AtomicLong, ConcurrentHashMap, ThreadPoolExecutor |
| **性能统计** | HdrHistogram（百分位统计） |
| **事件驱动** | EventBus（异步事件总线） |
| **AI 集成** | JDK HttpClient + 阿里云通义千问 API |
| **JSON 处理** | Jackson 2.15 |
| **日志框架** | SLF4J + Logback |
| **构建工具** | Maven 3.x |

**零依赖设计**: 除 Jackson 外，无第三方重型依赖，保持轻量化。

---

## 🏗️ 架构设计

### 整体架构图

```
┌─────────────────────────────────────────────────┐
│                  应用层                          │
│  (业务代码 - 零侵入)                             │
└────────────────┬────────────────────────────────┘
│
┌────────────────▼────────────────────────────────┐
│               代理层                             │
│  JdbcProxyFactory (三级动态代理链)               │
│  - DataSource.getConnection()                   │
│  - Connection.prepareStatement(sql)             │
│  - PreparedStatement.executeXXX()               │
└────────────────┬────────────────────────────────┘
│ collect(sql, params, cost)
┌────────────────▼────────────────────────────────┐
│               采集层                             │
│  MetricsCollector                                │
│  ├─ Layer 1: GlobalMetrics (全局统计)           │
│  ├─ Layer 2: GroupMetrics (分组统计)            │
│  └─ Layer 3: SlowQueryEvent (异步事件)          │
└────────────────┬────────────────────────────────┘
│ publish(event)
┌────────────────▼────────────────────────────────┐
│             事件总线 (异步)                       │
│  EventBus + ThreadPoolExecutor                  │
│  - core=2, max=4, queue=1000                    │
│  - 背压控制 + 异常隔离                           │
└────────────────┬────────────────────────────────┘
│ onEvent(event)
┌────────────────▼────────────────────────────────┐
│             分析引擎                             │
│  SqlAnalysisEngine                               │
│  ├─ 动态基线检测 (加权移动平均 80/20)            │
│  ├─ ExplainAnalyzer (EXPLAIN 解析)              │
│  ├─ RuleEngine (责任链: 3 条规则)               │
│  │   ├─ TypeFieldRule (全表扫描检测)            │
│  │   ├─ ExtraFieldRule (排序/临时表检测)        │
│  │   └─ IndexInvalidRule (索引失效检测)         │
│  └─ AiAnalysisService (智能触发 AI 诊断)        │
└────────────────┬────────────────────────────────┘
│
┌────────────────▼────────────────────────────────┐
│             容器管理                             │
│  MonitorContainer (单例工厂)                     │
│  ├─ HashMap 组件注册表                           │
│  ├─ DCL 双重检查锁                               │
│  └─ 双模式: 轻量监控 / 完整监控                  │
└─────────────────────────────────────────────────┘
```
### 核心数据流

```
SQL 执行 → 代理拦截 → 采集统计 → 发布事件 → 异步分析
↓
缓存检查 (30min)
↓
基线劣化检测 (2倍阈值)
↓
EXPLAIN 解析
↓
规则引擎分析 (责任链)
↓
多维度评分 (5 维度)
↓
┌─────────┴─────────┐
│                   │
评分 ≥ 3             评分 < 3
│                   │
AI 深度诊断          直接返回
│                   │
└─────────┬─────────┘
↓
缓存结果 + 日志输出
```
---

## 🚀 快速开始

### 方式 1: Spring Boot Starter（推荐）

#### 1. 添加依赖

```xml
<dependency>
<groupId>com.nano</groupId>
<artifactId>nano-monitor</artifactId>
<version>1.0.0</version>
</dependency>
```

#### 2. 配置 application.yml

```yaml
nano:
monitor:
enabled: true                    # 开启监控（默认 true）
slow-query-threshold: 500        # 慢查询阈值 ms（默认 500）
auto-analysis: true              # 开启自动分析（默认 true）
cache-expire-minutes: 30         # 缓存过期时间 min（默认 30）

    # 线程池配置
    thread-pool:
      core-size: 2                   # 核心线程数（默认 2）
      max-size: 4                    # 最大线程数（默认 4）
      queue-capacity: 1000           # 队列容量（默认 1000）
    
    # AI 配置（可选）
    ai:
      enabled: false                 # 开启 AI 诊断（默认 false）
      api-key: your-api-key          # 阿里云 API Key
      model: qwen-turbo              # 模型名称（默认 qwen-turbo）
      trigger-score-threshold: 3     # AI 触发评分阈值（默认 3）
```

#### 3. 启动应用

```java
@SpringBootApplication
public class Application {
    public static void main(String[] args) {
    SpringApplication.run(Application.class, args);
    }
}
```

**监控自动生效，无需修改业务代码！**

---

### 方式 2: 独立 SDK 模式

适用于非 Spring Boot 环境或需要自定义初始化的场景。

#### 1. 初始化监控

```java
// 方式 A: 使用默认配置
MonitorBootstrap.initialize(dataSource);

// 方式 B: 使用自定义配置
NanoMonitorProperties properties = new NanoMonitorProperties();
properties.setSlowQueryThreshold(1000);
properties.setAutoAnalysis(true);
MonitorBootstrap.initialize(dataSource, properties);

// 方式 C: 使用配置文件
MonitorBootstrap.initialize(dataSource, "custom-config.properties");
```

#### 2. 创建代理数据源

```java
MonitorContainer container = MonitorContainer.getInstance();
DataSource proxiedDs = container.createProxiedDataSource(originalDataSource);

// 业务代码使用 proxiedDs，自动拦截 SQL
Connection conn = proxiedDs.getConnection();
PreparedStatement stmt = conn.prepareStatement("SELECT * FROM users WHERE id = ?");
stmt.setInt(1, 100);
ResultSet rs = stmt.executeQuery();
```
#### 3. 应用关闭时清理资源

```java
// 注册 Shutdown Hook
Runtime.getRuntime().addShutdownHook(new Thread(() -> {
    MonitorBootstrap.shutdown();
}));
```
---

## 📊 监控指标

### 全局统计 (GlobalMetrics)

| 指标 | 说明 | 计算方法 |
|------|------|---------|
| **总查询数** | 所有 SQL 执行次数 | AtomicLong 计数 |
| **慢查询数** | 超过阈值的查询数 | AtomicLong 计数 |
| **平均耗时** | 所有查询的平均耗时 | HdrHistogram.getMean() |
| **P95 耗时** | 95% 查询的耗时上限 | HdrHistogram.getValueAtPercentile(95) |
| **P99 耗时** | 99% 查询的耗时上限 | HdrHistogram.getValueAtPercentile(99) |
| **最大耗时** | 单次查询的最大耗时 | CAS 无锁更新 |
| **慢查询比例** | 慢查询占总查询的比例 | slowQueries / totalQueries * 100% |

### 分组统计 (GroupMetrics)

按 SQL 指纹（归一化后的 SQL）分组统计：

| 指标 | 说明 |
|------|------|
| **执行次数** | 该 SQL 的执行总次数 |
| **慢查询次数** | 该 SQL 的慢查询次数 |
| **平均/最大/P95/P99 耗时** | 同全局统计 |
| **最后查询时间** | 用于基线对比和缓存过期判断 |

### 示例输出

```
========== 全局统计 ==========
总查询数: 15234
慢查询数: 127
平均耗时: 45ms
P95 耗时: 230ms
P99 耗时: 480ms
最大耗时: 1250ms
慢查询比例: 0.83%

========== 慢 SQL Top 3 ==========
1. SELECT * FROM orders WHERE user_id = ?
    - 执行次数: 45, 平均耗时: 320ms, P99: 850ms

2. SELECT u.*, o.* FROM users u JOIN orders o ON u.id = o.user_id
    - 执行次数: 12, 平均耗时: 580ms, P99: 1200ms

3. SELECT COUNT(*) FROM products WHERE category_id = ? AND status = ?
    - 执行次数: 28, 平均耗时: 210ms, P99: 450ms
```
---

## 🤖 AI 诊断示例

### 场景 1: 全表扫描 + 文件排序

```
🔍 SQL 分析结果:
================================

📊 性能诊断:
问题 1: 全表扫描 (type=ALL)
- 表: users
- 扫描行数: 15234
- 建议: 为 name 字段添加索引

问题 2: 文件排序 (Using filesort)
- 字段: create_time DESC
- 建议: 为 (name, create_time) 创建联合索引

💡 优化建议:
1. ALTER TABLE users ADD INDEX idx_name (name);
2. ALTER TABLE users ADD INDEX idx_name_time (name, create_time);
3. 预计优化后查询时间可降低 90%+

🤖 AI 深度分析:
当前查询存在两个主要性能瓶颈：

1. 全表扫描问题：
    - users 表数据量较大（15234 行），未使用索引导致全表扫描
    - 建议优先为 WHERE 条件中的 name 字段创建索引

2. 文件排序问题：
    - ORDER BY create_time DESC 触发了磁盘排序
    - 创建联合索引 (name, create_time) 可同时解决两个问题

综合评估：
- 当前查询耗时: 580ms
- 优化后预估: < 50ms
- 性能提升: 90%+

额外建议：
- 定期执行 ANALYZE TABLE users 更新统计信息
- 考虑是否需要 SELECT *，改为只查询必要字段

================================
```

### 场景 2: 规则盲区（AI 介入诊断）

```
🔍 SQL 分析结果:
================================

⚠️ 规则引擎未检测到明显问题
- 访问类型: range (索引范围扫描)
- 扫描行数: 120
- 实际耗时: 850ms (远超预期)

🤖 AI 深度分析:
虽然 EXPLAIN 显示使用了索引范围扫描，但实际耗时异常高，可能存在以下问题：

1. 索引选择性低：
    - status 字段区分度不高（只有 3-5 个值）
    - 导致索引扫描后仍需回表大量数据

2. 回表开销大：
    - SELECT * 导致需要回表获取所有字段
    - 建议改为覆盖索引或只查询必要字段

3. 数据倾斜：
    - 某些 status 值对应的数据量远大于其他值
    - 建议检查数据分布情况

优化建议：
1. 避免 SELECT *，改为 SELECT id, name, create_time
2. 创建覆盖索引: CREATE INDEX idx_status_covering ON orders (status, id, name, create_time)
3. 如果 status 区分度确实低，考虑移除该字段的索引

================================
```

---

## 📁 项目结构

```
nano-monitor/
├── src/main/java/com/nano/monitor/
│   ├── proxy/                      # 代理层
│   │   └── JdbcProxyFactory.java   # JDK 动态代理工厂（三级链路）
│   ├── collector/                  # 采集层
│   │   └── MetricsCollector.java   # 指标采集器（三层统计）
│   ├── eventbus/                   # 事件总线
│   │   ├── EventBus.java           # 异步事件总线
│   │   ├── event/                  # 事件定义
│   │   │   ├── MonitorEvent.java
│   │   │   └── SlowQueryEvent.java
│   │   └── listener/               # 监听器
│   │       ├── MonitorListener.java
│   │       └── SlowQueryAnalysisListener.java
│   ├── analysis/                   # 分析引擎
│   │   ├── SqlAnalysisEngine.java  # 统一分析入口
│   │   ├── ExplainAnalyzer.java    # EXPLAIN 解析器
│   │   ├── SqlAnalysisCache.java   # 智能缓存管理
│   │   ├── AiAnalysisService.java  # AI 诊断服务
│   │   └── rule/                   # 规则引擎
│   │       ├── AnalysisRule.java   # 规则接口
│   │       ├── RuleEngine.java     # 规则引擎（责任链）
│   │       ├── TypeFieldRule.java  # 全表扫描检测
│   │       ├── ExtraFieldRule.java # 排序/临时表检测
│   │       └── IndexInvalidRule.java # 索引失效检测
│   ├── container/                  # 容器层
│   │   ├── MonitorContainer.java   # 单例工厂容器
│   │   ├── MonitorBootstrap.java   # 快速初始化工具
│   │   └── MonitorConfigLoader.java # 配置加载器
│   ├── autoconfigure/              # 自动配置
│   │   ├── NanoMonitorAutoConfiguration.java
│   │   └── NanoMonitorProperties.java
│   ├── model/                      # 数据模型
│   │   ├── SqlRecord.java          # SQL 记录
│   │   ├── GlobalMetrics.java      # 全局统计
│   │   ├── GroupMetrics.java       # 分组统计
│   │   ├── AnalysisResult.java     # 分析结果
│   │   └── ExplainResult.java      # EXPLAIN 结果
│   ├── utils/                      # 工具类
│   │   ├── SqlFingerprintUtil.java # SQL 指纹生成
│   │   ├── BaselineUtil.java       # 动态基线计算
│   │   └── SqlParamUtil.java       # SQL 参数替换
│   └── spi/                        # SPI 接口
│       └── SqlCollector.java       # SQL 采集器接口
├── src/test/                       # 单元测试和集成测试
├── src/main/resources/
│   ├── META-INF/spring/
│   │   └── org.springframework.boot.autoconfigure.AutoConfiguration.imports
│   └── logback.xml                 # 日志配置
└── pom.xml
```
---

## 🎓 学习价值

本项目适合作为以下技术的实践案例：

### 🔧 核心技术
- ✅ **JDK 动态代理**: 三级代理链实现，理解 Proxy.newProxyInstance 工作原理
- ✅ **并发编程**: AtomicLong + CAS 无锁操作，ConcurrentHashMap 线程安全
- ✅ **设计模式**: 责任链模式、单例模式、观察者模式、工厂模式
- ✅ **性能优化**: HdrHistogram 替代全量缓存，固定内存占用

### 🚀 高级特性
- ✅ **异步编程**: EventBus + ThreadPoolExecutor 异步解耦
- ✅ **AI 集成**: JDK HttpClient 调用大模型 API + 降级容错
- ✅ **Spring Boot Starter**: 自动配置、条件化装配、配置属性绑定
- ✅ **规则引擎**: 责任链模式实现可扩展的规则系统

### 💡 工程实践
- ✅ **分层架构**: 代理层 → 采集层 → 事件总线 → 分析引擎 → 容器层
- ✅ **降级容错**: AI 异常时自动切换为规则引擎，确保系统可用性
- ✅ **背压控制**: 有界队列防止内存溢出，AbortPolicy 拒绝策略
- ✅ **零依赖设计**: 除 Jackson 外无第三方重型依赖，保持轻量化

---

## 🔧 配置说明

### 完整配置示例

```yaml
nano:
monitor:
# 基础配置
enabled: true                        # 是否启用监控
slow-query-threshold: 500            # 慢查询阈值 ms
auto-analysis: true                  # 是否开启自动分析
cache-expire-minutes: 30             # 分析结果缓存过期时间 min

    # 线程池配置
    thread-pool:
      core-size: 2                       # 核心线程数
      max-size: 4                        # 最大线程数
      queue-capacity: 1000               # 队列容量
    
    # AI 配置（可选）
    ai:
      enabled: false                     # 是否启用 AI 诊断
      api-key: sk-xxxxxxxxxxxx           # 阿里云 API Key
      api-url: https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation
      model: qwen-turbo                  # 模型名称
      temperature: 0.2                   # 温度参数（0-1，越低越确定）
      trigger-score-threshold: 3         # AI 触发评分阈值（0-8）
```

### 配置项说明

| 配置项 | 类型 | 默认值 | 说明 |
|--------|------|--------|------|
| `enabled` | boolean | true | 是否启用监控 |
| `slow-query-threshold` | long | 500 | 慢查询阈值（毫秒） |
| `auto-analysis` | boolean | true | 是否开启自动分析（false=仅统计） |
| `cache-expire-minutes` | int | 30 | 分析结果缓存过期时间 |
| `thread-pool.core-size` | int | 2 | 线程池核心线程数 |
| `thread-pool.max-size` | int | 4 | 线程池最大线程数 |
| `thread-pool.queue-capacity` | int | 1000 | 线程池队列容量 |
| `ai.enabled` | boolean | false | 是否启用 AI 诊断 |
| `ai.api-key` | string | "" | 阿里云 API Key |
| `ai.model` | string | qwen-turbo | AI 模型名称 |
| `ai.trigger-score-threshold` | int | 3 | AI 触发评分阈值（0-8） |

---

## 📝 开发指南

### 新增规则

1. 实现 `AnalysisRule` 接口

```java
public class CustomRule implements AnalysisRule {
@Override
public String getName() {
return "自定义规则名称";
}

    @Override
    public boolean canHandle(String sql) {
        // 判断是否适用当前 SQL
        return true;
    }
    
    @Override
    public void analyze(AnalysisResult result) {
        // 执行分析逻辑
        if (/* 检测到问题 */) {
            result.getAdvices().add(OptimizationAdvice.builder()
                .ruleName(getName())
                .problem("问题描述")
                .suggestion("优化建议")
                .severity(2)
                .exampleSql("ALTER TABLE ...")
                .build());
        }
    }
}
```

2. 注册规则

```java
RuleEngine ruleEngine = new RuleEngine();
ruleEngine.addRule(new TypeFieldRule());
ruleEngine.addRule(new ExtraFieldRule());
ruleEngine.addRule(new IndexInvalidRule());
ruleEngine.addRule(new CustomRule()); // 新增规则
```

### 自定义监听器

```java
public class CustomAnalysisListener implements MonitorListener {
@Override
public void onEvent(MonitorEvent event) {
if (!(event instanceof SlowQueryEvent)) {
return;
}

        SlowQueryEvent slowQueryEvent = (SlowQueryEvent) event;
        SqlRecord record = slowQueryEvent.getSqlRecord();
        
        // 自定义处理逻辑
        System.out.println("检测到慢 SQL: " + record.getFingerprint());
    }
    
    @Override
    public boolean supports(String eventType) {
        return "SLOW_QUERY".equals(eventType);
    }
}

// 注册监听器
eventBus.register(new CustomAnalysisListener());
```

---

## 🐛 常见问题

### Q1: 为什么我的 SQL 没有被监控？

**A:** 检查以下几点：
1. 确认使用的是代理数据源 `proxiedDataSource`，而非原始 `dataSource`
2. 确认 `nano.monitor.enabled=true`
3. 查看日志中是否有 "已创建代理数据源" 的输出

### Q2: AI 诊断不生效？

**A:** 检查配置：
1. 确认 `nano.monitor.ai.enabled=true`
2. 确认已配置有效的 `api-key`
3. 查看日志中是否有 "AI 分析服务已启用" 的输出
4. 检查评分是否达到阈值（默认 3 分）

### Q3: 如何调整慢查询阈值？

**A:** 修改配置文件：
```yaml
nano:
monitor:
slow-query-threshold: 1000  # 调整为 1000ms
```

### Q4: 性能开销有多大？

**A:** 
- **轻量监控模式**（仅统计）: CPU < 1%, 内存 ~50KB
- **完整监控模式**（统计 + 分析）: CPU 2-5%（取决于慢查询频率）, 内存 ~200KB
- **异步分析**: 主线程零阻塞，分析在后台线程执行

---

## 📄 许可证

MIT License

---

## 🙏 致谢

- [HdrHistogram](https://github.com/HdrHistogram/HdrHistogram) - 高性能百分位统计
- [阿里云通义千问](https://help.aliyun.com/product/334378.htm) - AI 诊断支持

---

<div align="center">

**如果你觉得这个项目对你有帮助，欢迎 ⭐ Star！**

有任何问题或建议，欢迎提交 [Issue](../../issues) 或 [PR](../../pulls)

</div>

