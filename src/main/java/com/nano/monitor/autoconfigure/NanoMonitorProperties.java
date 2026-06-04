package com.nano.monitor.autoconfigure;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;

@Data
@ConfigurationProperties(prefix = "nano.monitor")
public class NanoMonitorProperties {

    private boolean enabled = true;

    private long slowQueryThreshold = 500;

    private boolean autoAnalysis = true;

    private int cacheExpireMinutes = 30;

    private int threadPoolCoreSize = 2;

    private int threadPoolMaxSize = 4;

    private int threadPoolQueueCapacity = 1000;

    /**
     * AI Agent 相关配置
     */
    private Ai ai = new Ai();

    @Data
    public static class Ai {
        private boolean enabled = false;
        private String apiKey = "";
        private String apiUrl = "https://dashscope.aliyuncs.com/api/v1/services/aigc/text-generation/generation";
        private String model = "qwen-turbo";
        private double temperature = 0.2;
        private int triggerScoreThreshold = 3; // AI 触发评分阈值(默认 3 分)
    }
}
