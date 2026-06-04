package com.nano.monitor.model;

import lombok.Data;
import org.HdrHistogram.Histogram;

import java.util.concurrent.atomic.AtomicLong;

@Data
public class GroupMetrics {
    private final String fingerprint;
    private final AtomicLong queryCount = new AtomicLong(0);
    private final AtomicLong slowCount = new AtomicLong(0);
    private final AtomicLong maxCost = new AtomicLong(0);
    private volatile long lastQueryTime = 0;
    private final Histogram costHistogram;

    public GroupMetrics(String fingerprint) {
        this.fingerprint = fingerprint;
        this.costHistogram = new Histogram(3600000, 3);
    }

    public void recordQuery(long cost, boolean isSlow) {
        queryCount.incrementAndGet();
        costHistogram.recordValue(cost);

        if (isSlow) {
            slowCount.incrementAndGet();
        }

        updateMaxCost(cost);
        lastQueryTime = System.currentTimeMillis();
    }

    private void updateMaxCost(long cost) {
        long currentMax = maxCost.get();
        while (cost > currentMax && !maxCost.compareAndSet(currentMax, cost)) {
            currentMax = maxCost.get();
        }
    }

    public long getAvgCost() {
        long count = queryCount.get();
        return count == 0 ? 0 : (long) costHistogram.getMean();
    }

    public long getP95Cost() {
        return costHistogram.getValueAtPercentile(95);
    }

    public long getP99Cost() {
        return costHistogram.getValueAtPercentile(99);
    }

    public long getMaxCost() {
        return maxCost.get();
    }

    public double getSlowQueryRatio() {
        long count = queryCount.get();
        return count == 0 ? 0.0 : (double) slowCount.get() / count * 100;
    }
}