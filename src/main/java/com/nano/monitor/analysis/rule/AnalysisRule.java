package com.nano.monitor.analysis.rule;

import com.nano.monitor.model.AnalysisResult;

public interface AnalysisRule {
    String getName();
    boolean canHandle(String sql);

    void analyze(AnalysisResult result);
}