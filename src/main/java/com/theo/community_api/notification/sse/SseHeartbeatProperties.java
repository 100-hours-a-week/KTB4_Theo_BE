package com.theo.community_api.notification.sse;

import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "notification.sse.heartbeat")
public record SseHeartbeatProperties(
        boolean enabled,
        @NotNull Duration interval
) {
}
