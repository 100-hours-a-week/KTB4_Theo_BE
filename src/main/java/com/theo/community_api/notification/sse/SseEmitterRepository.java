package com.theo.community_api.notification.sse;

import org.springframework.stereotype.Repository;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

@Repository
public class SseEmitterRepository {

    private final Map<Long, ConcurrentMap<String,SseEmitter>> emitters =
            new ConcurrentHashMap<>();

    public Optional<SseEmitter> replace(
            Long userId,
            String clientId,
            SseEmitter newEmitter
    ) {
        AtomicReference<SseEmitter> previousEmitter = new AtomicReference<>();

        // userId 값 확인 후 생성 및 수정 과정을 하나의 원자적인 연산으로 처리
        emitters.compute(userId, (id, userEmitters) -> {
            ConcurrentMap<String, SseEmitter> updatedEmitters =
                    userEmitters == null
                            ? new ConcurrentHashMap<>()
                            : userEmitters;

            previousEmitter.set(updatedEmitters.put(clientId, newEmitter));

            return updatedEmitters;
        });

        return Optional.ofNullable(previousEmitter.get());
    }


    public Map<String, SseEmitter> findByUserId(Long userId) {
        Map<String, SseEmitter> userEmitters = emitters.get(userId);

        if(userEmitters == null || userEmitters.isEmpty()){
            return Map.of();
        }

        return Map.copyOf(userEmitters);
    }

    public List<SseEmitterConnection> findAll() {
        List<SseEmitterConnection> connections = new ArrayList<>();

        emitters.forEach((userId, userEmitters) ->
                userEmitters.forEach((clientId, emitter) ->
                        connections.add(
                                new SseEmitterConnection(
                                        userId,
                                        clientId,
                                        emitter
                                )
                        )
                )
        );

        return List.copyOf(connections);
    }

    public boolean delete(
            Long userId,
            String clientId,
            SseEmitter emitter
    ) {
        AtomicBoolean removed = new AtomicBoolean(false);

        emitters.computeIfPresent(userId, (id, userEmitters) -> {
            removed.set(
                    userEmitters.remove(clientId, emitter)
            );

            return userEmitters.isEmpty()
                    ? null
                    : userEmitters;
        });

        return removed.get();
    }

    // 현재 저장된 전체 SSE 연결 개수 계산
    public int size() {
        return emitters.values().stream().mapToInt(Map::size).sum();
    }
}
