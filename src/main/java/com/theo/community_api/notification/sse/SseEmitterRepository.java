package com.theo.community_api.notification.sse;

import org.springframework.stereotype.Repository;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

@Repository
public class SseEmitterRepository {

    private final Map<Long, SseEmitter> emitters =
            new ConcurrentHashMap<>();

    public Optional<SseEmitter> replace(
            Long userId,
            SseEmitter newEmitter
    ) {
        return Optional.ofNullable(
                emitters.put(userId, newEmitter)
        );
    }

    public Optional<SseEmitter> findByUserId(Long userId) {
        return Optional.ofNullable(emitters.get(userId));
    }

    public boolean delete(
            Long userId,
            SseEmitter emitter
    ) {
        return emitters.remove(userId, emitter);
    }

    public int size() {
        return emitters.size();
    }
}
