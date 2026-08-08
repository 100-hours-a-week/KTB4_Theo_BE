package com.theo.community_api.notification.sse;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.stereotype.Component;

@Component
public class SseMetrics {

    private final Counter connections;
    private final Counter connectionFailures;
    private final Counter notificationsSent;
    private final Counter notificationFailures;
    private final Counter notificationRejections;
    private final Counter heartbeatsSent;
    private final Counter heartbeatFailures;

    public SseMetrics(
            MeterRegistry meterRegistry,
            SseEmitterRepository emitterRepository
    ) {
        Gauge.builder(
                        "sse.emitters.active",
                        emitterRepository,
                        SseEmitterRepository::size
                )
                .description("현재 저장된 SSE emitter 수")
                .register(meterRegistry);

        connections = Counter.builder("sse.connections")
                .description("SSE 초기 연결 이벤트 전송 성공 수")
                .register(meterRegistry);
        connectionFailures = Counter.builder("sse.connections.failed")
                .description("SSE 초기 연결 이벤트 전송 실패 수")
                .register(meterRegistry);
        notificationsSent = Counter.builder("sse.notifications.sent")
                .description("SSE 알림 이벤트 전송 성공 수")
                .register(meterRegistry);
        notificationFailures = Counter.builder("sse.notifications.failed")
                .description("SSE 알림 이벤트 전송 실패 수")
                .register(meterRegistry);
        notificationRejections = Counter.builder("sse.notifications.rejected")
                .description("executor 포화로 거부된 SSE 알림 전송 작업 수")
                .register(meterRegistry);
        heartbeatsSent = Counter.builder("sse.heartbeats.sent")
                .description("SSE heartbeat 전송 성공 수")
                .register(meterRegistry);
        heartbeatFailures = Counter.builder("sse.heartbeats.failed")
                .description("SSE heartbeat 전송 실패 수")
                .register(meterRegistry);
    }

    public void recordConnection() {
        connections.increment();
    }

    public void recordConnectionFailure() {
        connectionFailures.increment();
    }

    public void recordNotificationSent() {
        notificationsSent.increment();
    }

    public void recordNotificationFailure() {
        notificationFailures.increment();
    }

    public void recordNotificationRejected() {
        notificationRejections.increment();
    }

    public void recordHeartbeatSent() {
        heartbeatsSent.increment();
    }

    public void recordHeartbeatFailure() {
        heartbeatFailures.increment();
    }
}
