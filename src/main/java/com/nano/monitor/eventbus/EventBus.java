package com.nano.monitor.eventbus;

import com.nano.monitor.eventbus.event.MonitorEvent;
import com.nano.monitor.eventbus.listener.MonitorListener;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.concurrent.atomic.AtomicInteger;

@Slf4j
public class EventBus {

    private static final int DEFAULT_CORE_POOL_SIZE = 2;
    private static final int DEFAULT_MAX_POOL_SIZE = 4;
    private static final long KEEP_ALIVE_TIME = 60L;
    private static final int DEFAULT_QUEUE_CAPACITY = 1000;

    private final List<MonitorListener> listeners = new ArrayList<>();
    private final ThreadPoolExecutor executor;

    public EventBus() {
        this(DEFAULT_CORE_POOL_SIZE, DEFAULT_MAX_POOL_SIZE, DEFAULT_QUEUE_CAPACITY);
    }

    public EventBus(int corePoolSize, int maxPoolSize, int queueCapacity) {
        this.executor = new ThreadPoolExecutor(
                corePoolSize,
                maxPoolSize,
                KEEP_ALIVE_TIME,
                TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(queueCapacity),
                new MonitorThreadFactory(),
                new ThreadPoolExecutor.AbortPolicy()
        );

        log.info("EventBus 线程池初始化: core={}, max={}, queue={}",
                corePoolSize, maxPoolSize, queueCapacity);
    }

    public void register(MonitorListener listener) {
        listeners.add(listener);
        log.info("注册监听器: {}", listener.getClass().getSimpleName());
    }

    public void publish(MonitorEvent event) {
        for (MonitorListener listener : listeners) {
            if (listener.supports(event.getType())) {
                try {
                    executor.submit(() -> {
                        try {
                            listener.onEvent(event);
                        } catch (Exception e) {
                            log.error("监听器处理事件失败: {}", event.getType(), e);
                        }
                    });
                } catch (RejectedExecutionException e) {
                    log.warn("事件总线线程池已满，丢弃事件: {}", event.getType());
                }
            }
        }
    }

    public void shutdown() {
        executor.shutdown();
        try {
            if (!executor.awaitTermination(10, TimeUnit.SECONDS)) {
                log.warn("部分异步任务未在 10 秒内完成，强制关闭");
                executor.shutdownNow();
            }
        } catch (InterruptedException e) {
            executor.shutdownNow();
            Thread.currentThread().interrupt();
        }
    }

    private static class MonitorThreadFactory implements ThreadFactory {
        private static final AtomicInteger threadNumber = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable r) {
            Thread t = new Thread(r, "monitor-eventbus-" + threadNumber.getAndIncrement());
            t.setDaemon(false);
            return t;
        }
    }
}
