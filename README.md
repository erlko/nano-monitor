# Nano-Monitor

轻量级 SQL 性能监控工具，基于 JDK 动态代理实现无侵入式慢 SQL 检测与分析。

## 🎯 核心价值

在日常开发中，慢 SQL 经常导致联调阻塞和生产环境数据库 CPU 飙升。Nano-Monitor 通过**无侵入式采集 + 智能诊断**，帮助开发团队在上线前发现性能问题，减少生产环境故障。

## ✨ 核心功能

- 🎯 **无侵入采集**: 基于 JDK 动态代理拦截 SQL 执行，业务代码零修改
- 📊 **性能统计**: HdrHistogram 实现高精度 P95/P99 百分位统计，内存占用大幅降低
- 🤖 **智能诊断**: 规则引擎 + AI 混合分析，自动生成可落地的优化建议
- ⚡ **异步处理**: EventBus 事件总线，慢查询分析与主业务逻辑解耦
- 🔒 **降级容错**: AI 异常时自动切换为纯规则引擎结果，确保系统稳定性

## 🛠️ 技术栈

- **核心框架**: Java 17, Spring Boot 3.x
- **并发编程**: JDK 动态代理, AtomicLong, ConcurrentHashMap, CompletableFuture
- **性能统计**: HdrHistogram（百分位统计）
- **事件驱动**: EventBus（异步事件总线）
- **AI 集成**: 阿里云通义千问 API

## 🏗️ 架构设计

```
┌─────────────────────────────────────────────────┐
│                  应用层                          │
│  (业务代码 - 零侵入)                             │
└────────────────┬────────────────────────────────┘
                 │
┌────────────────▼────────────────────────────────┐
│               代理层                             │
│  JdbcProxyFactory (DataSource/Connection/PS)    │
│  - 拦截 SQL 执行                                 │
│  - 记录耗时                                      │
└────────────────┬────────────────────────────────┘
                 │
┌────────────────▼────────────────────────────────┐
│               统计层                             │
│  MetricsCollector                                │
│  - 全局统计 (GlobalMetrics)                      │
│  - 分组统计 (GroupMetrics)                       │
│  - HdrHistogram P95/P99                         │
└────────────────┬────────────────────────────────┘
                 │
────────────────▼────────────────────────────────┐
│             事件总线                             │
│  EventBus (异步)                                 │
│  - SlowQueryEvent                                │
│  - 线程池分发                                    │
└────────────────────────────────────────────────┘
                 │
┌────────────────▼────────────────────────────────┐
│             分析引擎                             │
│  SqlAnalysisEngine                               │
│  - EXPLAIN 解析                                  │
│  - 规则引擎 (责任链模式)                         │
│  - AI 辅助诊断                                   │
└────────────────┬────────────────────────────────┘
                 │
┌────────────────▼────────────────────────────────┐
│             容器管理                             │
│  MonitorContainer (单例)                         │
│  - 组件生命周期管理                              │
│  - 双模式接入 (Starter + SDK)                   │
└─────────────────────────────────────────────────┘
```

## 🚀 快速开始

### 方式 1: Spring Boot Starter

**1. 添加依赖**

```xml
<dependency>
    <groupId>com.nano</groupId>
    <artifactId>nano-monitor</artifactId>
    <version>1.0.0</version>
</dependency>
```

**2. 配置 application.yml**

```yaml
nano:
  monitor:
    enabled: true                    # 开启监控
    slow-query-threshold: 500        # 慢查询阈值 (ms)
    ai:
      enabled: true                  # 开启 AI 诊断
      api-key: your-api-key          # 阿里云 API Key
```

**3. 启动应用**

监控自动生效，无需修改业务代码！

---

### 方式 2: 独立 SDK 模式

```java
// 1. 初始化监控
MonitorBootstrap.initialize(dataSource, properties);

// 2. 业务代码正常执行 SQL
// 监控自动拦截并分析

// 3. 应用关闭时清理资源
MonitorBootstrap.shutdown();
```

## 📊 监控指标

### 全局统计
- 总查询数、慢查询数
- 平均耗时、最大耗时
- P95/P99 百分位

### 分组统计（按 SQL 指纹）
- 每条 SQL 的执行次数
- 平均耗时、最大耗时
- 历史基线对比

## 🤖 AI 诊断示例

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

 优化建议:
  1. ALTER TABLE users ADD INDEX idx_name (name);
  2. ALTER TABLE users ADD INDEX idx_name_time (name, create_time);
  3. 预计优化后查询时间可降低 90%+

================================
```

## 📁 项目结构

```
nano-monitor/
├── src/main/java/com/nano/monitor/
│   ├── proxy/          # 代理层 (JdbcProxyFactory)
│   ├── collector/      # 统计层 (MetricsCollector)
│   ├── eventbus/       # 事件总线 (EventBus)
│   ├── analysis/       # 分析引擎 (SqlAnalysisEngine)
│   │   └── rule/       # 规则引擎 (RuleEngine + 3 条规则)
│   ├── container/      # 容器管理 (MonitorContainer)
│   ├── model/          # 数据模型 (SqlRecord, AnalysisResult)
│   └── utils/          # 工具类 (SqlFingerprintUtil, BaselineUtil)
├── src/test/           # 单元测试和集成测试
└── pom.xml
```

## 🎓 学习价值

本项目适合作为以下技术的实践案例：
- ✅ **JDK 动态代理**: 三级代理链实现
- ✅ **并发编程**: AtomicLong + CAS 无锁操作
- ✅ **设计模式**: 责任链模式、单例模式、观察者模式
- ✅ **性能优化**: HdrHistogram 替代全量缓存
- ✅ **异步编程**: EventBus + CompletableFuture
- ✅ **AI 集成**: 大模型 API 调用 + 降级容错

##  许可证

MIT License

---

**如果你觉得这个项目对你有帮助，欢迎 ⭐ Star！**
