package com.nano.monitor.eventbus.listener;

import com.nano.monitor.analysis.SqlAnalysisEngine;
import com.nano.monitor.eventbus.EventBus;
import com.nano.monitor.eventbus.event.MonitorEvent;
import com.nano.monitor.eventbus.event.SlowQueryEvent;
import com.nano.monitor.model.AnalysisResult;
import com.nano.monitor.model.SqlRecord;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class SlowQueryAnalysisListener implements MonitorListener {

    private final SqlAnalysisEngine analysisEngine;

    public SlowQueryAnalysisListener(SqlAnalysisEngine analysisEngine) {
        this.analysisEngine = analysisEngine;
    }

    @Override
    public void onEvent(MonitorEvent event) {
        if (!(event instanceof SlowQueryEvent)) {
            return;
        }

        SlowQueryEvent slowQueryEvent = (SlowQueryEvent) event;
        SqlRecord record = slowQueryEvent.getSqlRecord();

        AnalysisResult result = analysisEngine.analyze(record);

        log.info("SQL分析完成: fingerprint={}, status={}, advices={}, hasAiSummary={}",
                record.getFingerprint(),
                result.getStatus(),
                result.getAdvices() != null ? result.getAdvices().size() : 0,
                result.getAiSummary() != null);
    }

    @Override
    public boolean supports(String eventType) {
        return "SLOW_QUERY".equals(eventType);
    }
}