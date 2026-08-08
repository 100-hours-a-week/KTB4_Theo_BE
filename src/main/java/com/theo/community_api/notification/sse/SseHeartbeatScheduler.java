package com.theo.community_api.notification.sse;

import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "notification.sse.heartbeat",
        name = "enabled",
        havingValue = "true"
)
public class SseHeartbeatScheduler {

    private final SseEmitterService sseEmitterService;

    @Scheduled(
            fixedDelayString = "${notification.sse.heartbeat.interval}",
            initialDelayString = "${notification.sse.heartbeat.interval}"
    )
    public void sendHeartbeats() {
        sseEmitterService.sendHeartbeats();
    }
}
