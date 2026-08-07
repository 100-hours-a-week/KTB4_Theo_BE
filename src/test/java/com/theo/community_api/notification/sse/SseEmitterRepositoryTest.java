package com.theo.community_api.notification.sse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.assertj.core.api.Assertions.assertThat;

class SseEmitterRepositoryTest {

    private final SseEmitterRepository repository =
            new SseEmitterRepository();

    @Test
    @DisplayName("Emitter 하나를 제거하면 size()가 1로 감소한다.")
    void reportsCurrentEmitterCount() {
        SseEmitter first = new SseEmitter();
        SseEmitter second = new SseEmitter();

        repository.replace(1L, "client-a", first);
        repository.replace(2L, "client-b", second);

        assertThat(repository.size()).isEqualTo(2);

        repository.delete(1L, "client-a", first);

        assertThat(repository.size()).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 사용자의 서로 다른 클라이언트 연결을 모두 저장한다.")
    void storesMultipleClientEmittersForSameUser() {
        repository.replace(1L, "client-a", new SseEmitter());
        repository.replace(1L, "client-b", new SseEmitter());

        assertThat(repository.size()).isEqualTo(2);
        assertThat(repository.findByUserId(1L)).hasSize(2);
    }

    @Test
    @DisplayName("같은 클라이언트가 재연결하면 기존 Emitter만 교체한다.")
    void replacesEmitterForSameClient() {
        SseEmitter first = new SseEmitter();
        SseEmitter second = new SseEmitter();

        repository.replace(1L, "client-a", first);
        SseEmitter replaced = repository
                .replace(1L, "client-a", second)
                .orElseThrow();

        assertThat(repository.size()).isEqualTo(1);
        assertThat(replaced).isSameAs(first);
        assertThat(repository.findByUserId(1L))
                .containsEntry("client-a", second);
    }
}
