package com.nano.monitor.eventbus.event;

import com.nano.monitor.eventbus.event.MonitorEvent;
import com.nano.monitor.model.SqlRecord;
import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@AllArgsConstructor
@NoArgsConstructor
public class SlowQueryEvent implements MonitorEvent {
    private SqlRecord sqlRecord;

    @Override
    public String getType() {
        return "SLOW_QUERY";
    }
}