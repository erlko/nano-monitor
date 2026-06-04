package com.nano.monitor.model;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;
import java.time.LocalDateTime;
import java.util.Map;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SqlRecord {
    private String fingerprint;   // 带占位符的原始 SQL: SELECT * FROM user WHERE id = ?
    private long cost;
    private LocalDateTime timestamp;
    private Map<Integer, Object> params;  // 新增：参数映射（延迟构建用）
}