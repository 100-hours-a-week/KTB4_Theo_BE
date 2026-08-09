package com.theo.community_api.notification.sse;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class SseEmitterRepositoryTest {

    private final SseEmitterRepository repository =
            new SseEmitterRepository();

    @Test
    @DisplayName("새 클라이언트의 Emitter를 저장하면 교체된 Emitter가 없어야 한다.")
    void returnsEmptyWhenSavingNewClientEmitter() {
        SseEmitter emitter = new SseEmitter();

        assertThat(repository.replace(1L, "client-a", emitter)).isEmpty();
        assertThat(repository.findByUserId(1L))
                .containsExactlyEntriesOf(
                        Map.of("client-a", emitter)
                );
    }

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

    @Test
    @DisplayName("교체된 이전 Emitter의 늦은 삭제 요청은 현재 Emitter를 제거하지 않는다.")
    void doesNotDeleteCurrentEmitterWithStaleEmitterReference() {
        SseEmitter previousEmitter = new SseEmitter();
        SseEmitter currentEmitter = new SseEmitter();
        repository.replace(1L, "client-a", previousEmitter);
        repository.replace(1L, "client-a", currentEmitter);

        boolean deleted = repository.delete(
                1L,
                "client-a",
                previousEmitter
        );

        assertThat(deleted).isFalse();
        assertThat(repository.size()).isEqualTo(1);
        assertThat(repository.findByUserId(1L))
                .containsEntry("client-a", currentEmitter);
    }

    @Test
    @DisplayName("현재 Emitter를 삭제하면 성공을 반환하고 빈 사용자 항목도 정리한다.")
    void deletesCurrentEmitterAndRemovesEmptyUserEntry() {
        SseEmitter emitter = new SseEmitter();
        repository.replace(1L, "client-a", emitter);

        boolean deleted = repository.delete(1L, "client-a", emitter);

        assertThat(deleted).isTrue();
        assertThat(repository.size()).isZero();
        assertThat(repository.findByUserId(1L)).isEmpty();
        assertThat(repository.findAll()).isEmpty();
    }

    @Test
    @DisplayName("전체 사용자의 Emitter 연결 정보를 조회한다.")
    void findsAllEmitterConnections() {
        SseEmitter first = new SseEmitter();
        SseEmitter second = new SseEmitter();

        repository.replace(1L, "client-a", first);
        repository.replace(2L, "client-b", second);

        assertThat(repository.findAll())
                .containsExactlyInAnyOrder(
                        new SseEmitterConnection(1L, "client-a", first),
                        new SseEmitterConnection(2L, "client-b", second)
                );
    }
}
