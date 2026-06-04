package com.nano.monitor.eventbus.listener;

import com.nano.monitor.eventbus.event.MonitorEvent;

public interface MonitorListener {
    void onEvent(MonitorEvent event);

    boolean supports(String eventType);
}