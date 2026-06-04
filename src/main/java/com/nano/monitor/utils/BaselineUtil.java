package com.nano.monitor.utils;


import lombok.extern.slf4j.Slf4j;

@Slf4j
public class BaselineUtil {

    private static final double BASELINE_EXCEED_RATIO = 2.0;

    /**
     * 判断实际耗时是否超过基线阈值
     *
     * @param actualCost   实际耗时（ms）
     * @param baselineCost 基线耗时（ms），0 表示首次执行
     * @return true-超过基线，false-未超过
     */
    public static boolean isBaselineExceeded(long actualCost, long baselineCost) {
        if (baselineCost == 0) {
            return false;
        }

        boolean exceeded = actualCost > (baselineCost * BASELINE_EXCEED_RATIO);

        if (exceeded) {
            log.warn("性能劣化检测: 实际耗时={}ms, 基准耗时={}ms, 劣化倍数={}",
                    actualCost, baselineCost, (double) actualCost / baselineCost);
        }

        return exceeded;
    }

    /**
     * 计算新的基线值（加权平均）
     *
     * @param oldBaseline 旧基线（ms）
     * @param actualCost  实际耗时（ms）
     * @return 新基线值（ms）
     */
    public static long calculateNewBaseline(long oldBaseline, long actualCost) {
        if (oldBaseline == 0) {
            return actualCost;
        }

        long newBaseline = (long) (oldBaseline * 0.8 + actualCost * 0.2);
        log.debug("基线更新: 旧基线={}ms, 新基线={}ms", oldBaseline, newBaseline);
        return newBaseline;
    }
}