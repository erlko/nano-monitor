package com.nano.monitor.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ExplainResult {
    private String table;
    private String type;
    private String key;
    private Long rows;
    private String extra;
    private String possibleKeys;
    private Long keyLen;
}