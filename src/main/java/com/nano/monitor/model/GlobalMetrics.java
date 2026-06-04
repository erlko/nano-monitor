package com.nano.monitor.model;

import lombok.Data;
import org.HdrHistogram.Histogram;

import java.util.concurrent.atomic.AtomicLong;

@Data
public class GlobalMetrics {
    private final AtomicLong totalQueries = new AtomicLong(0);
    private final AtomicLong slowQueries = new AtomicLong(0);
    private final AtomicLong maxCost = new AtomicLong(0);
    private final Histogram costHistogram;

    public GlobalMetrics() {
        this.costHistogram = new Histogram(3600000, 3);
    }

    public void recordQuery(long cost, boolean isSlow) {
        totalQueries.incrementAndGet();
        costHistogram.recordValue(cost);

        if (isSlow) {
            slowQueries.incrementAndGet();
        }

        updateMaxCost(cost);
    }

    private void updateMaxCost(long cost) {
        long currentMax = maxCost.get();
        while (cost > currentMax && !maxCost.compareAndSet(currentMax, cost)) {
            currentMax = maxCost.get();
        }
    }

    public long getAvgCost() {
        long total = totalQueries.get();
        return total == 0 ? 0 : (long) costHistogram.getMean();
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
        long total = totalQueries.get();
        return total == 0 ? 0.0 : (double) slowQueries.get() / total * 100;
    }
}