package com.theo.community_api.notification.sse;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import java.util.concurrent.Executor;

@Slf4j
@EnableAsync
@Configuration
public class SseAsyncConfig {

    public static final String SSE_NOTIFICATION_EXECUTOR = "sseNotificationExecutor";

    @Bean(name = SSE_NOTIFICATION_EXECUTOR)
    public Executor sseNotificationExecutor(SseMetrics sseMetrics) {
        ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();

        executor.setCorePoolSize(2);
        executor.setMaxPoolSize(4);
        executor.setQueueCapacity(500);
        executor.setThreadNamePrefix("sse-notification-");
        executor.setRejectedExecutionHandler((task, rejectedExecutor) -> {
            sseMetrics.recordNotificationRejected();
            log.warn(
                    "SSE 알림 전송 작업이 거부되었습니다. activeThreads={}, queuedTasks={}",
                    rejectedExecutor.getActiveCount(),
                    rejectedExecutor.getQueue().size()
            );
        });
        executor.initialize();

        return executor;
    }
}
