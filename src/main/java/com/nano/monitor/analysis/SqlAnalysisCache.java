package com.nano.monitor.analysis;

import com.nano.monitor.model.AnalysisResult;
import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
public class SqlAnalysisCache {

    private static final long CACHE_EXPIRE_MS = 30 * 60 * 1000;

    private final Map<String, CacheEntry> cache = new ConcurrentHashMap<>();

    public AnalysisResult get(String fingerprint) {
        CacheEntry entry = cache.get(fingerprint);
        if (entry == null) {
            return null;  // 缓存未命中
        }

        // 检查是否过期
        if (System.currentTimeMillis() - entry.timestamp > CACHE_EXPIRE_MS) {
            cache.remove(fingerprint); // 惰性删除过期数据
            log.debug("缓存过期: fingerprint={}", fingerprint);
            return null;
        }

        log.debug("缓存命中: fingerprint={}", fingerprint);
        return entry.result;
    }

    public void put(String fingerprint, AnalysisResult result) {
        cache.put(fingerprint, new CacheEntry(result));
        log.debug("缓存写入: fingerprint={}", fingerprint);
    }

    public void invalidate(String fingerprint) {
        cache.remove(fingerprint);
        log.info("缓存失效: fingerprint={}", fingerprint);
    }

    public void updateAiAdvice(String fingerprint, String aiAdvice) {
        CacheEntry entry = cache.get(fingerprint);
        if (entry != null) {
            // 由于 AnalysisResult 是 Lombok @Data，我们直接更新其属性
            entry.result.setAiSummary(aiAdvice);
            entry.result.setStatus(AnalysisResult.AnalysisStatus.AI_ANALYZED);
            log.debug("AI 建议已更新到缓存: fingerprint={}", fingerprint);
        } else {
            log.warn("尝试更新 AI 建议但缓存条目不存在: fingerprint={}", fingerprint);
        }
    }

    private static class CacheEntry {
        private final AnalysisResult result;
        private final long timestamp;

        public CacheEntry(AnalysisResult result) {
            this.result = result;
            this.timestamp = System.currentTimeMillis();
        }
    }
}