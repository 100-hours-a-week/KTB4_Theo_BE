package com.theo.community_api.notification.sse;

import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

public record SseEmitterConnection(
        Long userId,
        String clientId,
        SseEmitter emitter
) {
}
