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

        repository.replace(1L, first);
        repository.replace(2L, second);

        assertThat(repository.size()).isEqualTo(2);

        repository.delete(1L, first);

        assertThat(repository.size()).isEqualTo(1);
    }

    @Test
    @DisplayName("같은 사용자의 Emitter를 교체해도 전체 개수가 증가하지 않는다.")
    void replacingEmitterDoesNotIncreaseCount() {
        repository.replace(1L, new SseEmitter());
        repository.replace(1L, new SseEmitter());

        assertThat(repository.size()).isEqualTo(1);
    }
}
