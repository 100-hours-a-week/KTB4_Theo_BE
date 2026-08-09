package com.theo.community_api.notification.sse;

import com.theo.community_api.notification.dto.NotificationResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedConstruction;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Consumer;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.mockConstruction;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class SseEmitterServiceTest {

    private static final Long USER_ID = 1L;
    private static final String CLIENT_ID =
            "2d87324b-c658-4ccb-8e63-86c7ce2c31e3";

    @Mock
    private SseEmitterRepository emitterRepository;

    @Mock
    private SseMetrics sseMetrics;

    @InjectMocks
    private SseEmitterService sseEmitterService;

    @Test
    @DisplayName("같은 사용자가 같은 클라이언트로 다시 구독하면 이전 Emitter를 완료한다.")
    void completesPreviousEmitterWhenSameClientSubscribesAgain() {
        SseEmitter previousEmitter = mock(SseEmitter.class);
        given(emitterRepository.replace(
                eq(USER_ID),
                eq(CLIENT_ID),
                any(SseEmitter.class)
        )).willReturn(Optional.of(previousEmitter));

        sseEmitterService.subscribe(USER_ID, CLIENT_ID);

        verify(previousEmitter).complete();
    }

    @Test
    @DisplayName("Emitter가 완료되면 저장소에서 해당 연결을 제거한다.")
    void removesEmitterOnCompletion() {
        SubscribedEmitter subscribed = subscribeAndCaptureCallbacks();

        subscribed.completionCallback().run();

        verify(emitterRepository).delete(
                USER_ID,
                CLIENT_ID,
                subscribed.emitter()
        );
    }

    @Test
    @DisplayName("Emitter가 타임아웃되면 연결을 제거하고 완료한다.")
    void removesAndCompletesEmitterOnTimeout() {
        SubscribedEmitter subscribed = subscribeAndCaptureCallbacks();

        subscribed.timeoutCallback().run();

        verify(emitterRepository).delete(
                USER_ID,
                CLIENT_ID,
                subscribed.emitter()
        );
        verify(subscribed.emitter()).complete();
    }

    @Test
    @DisplayName("Emitter에서 오류가 발생하면 저장소에서 해당 연결을 제거한다.")
    void removesEmitterOnError() {
        SubscribedEmitter subscribed = subscribeAndCaptureCallbacks();

        subscribed.errorCallback().accept(
                new IOException("connection closed")
        );

        verify(emitterRepository).delete(
                USER_ID,
                CLIENT_ID,
                subscribed.emitter()
        );
    }

    @Test
    @DisplayName("알림 전송이 IOException으로 실패하면 해당 Emitter를 제거한다.")
    void removesEmitterWhenNotificationSendFailsWithIOException()
            throws IOException {
        Long userId = 1L;
        String clientId = "client-a";
        SseEmitter emitter = mock(SseEmitter.class);
        NotificationResponse notification = new NotificationResponse(
                10L, null, null, null, null, null, null, false, null
        );
        given(emitterRepository.findByUserId(userId))
                .willReturn(Map.of(clientId, emitter));
        doThrow(new IOException("connection closed"))
                .when(emitter)
                .send(any(SseEmitter.SseEventBuilder.class));

        sseEmitterService.sendNotification(userId, notification);

        verify(emitterRepository).delete(userId, clientId, emitter);
    }

    private SubscribedEmitter subscribeAndCaptureCallbacks() {
        AtomicReference<Runnable> completionCallback =
                new AtomicReference<>();
        AtomicReference<Runnable> timeoutCallback = new AtomicReference<>();
        AtomicReference<Consumer<Throwable>> errorCallback =
                new AtomicReference<>();

        try (MockedConstruction<SseEmitter> ignored = mockConstruction(
                SseEmitter.class,
                (emitter, context) -> {
                    doAnswer(invocation -> {
                        completionCallback.set(invocation.getArgument(0));
                        return null;
                    }).when(emitter).onCompletion(any(Runnable.class));
                    doAnswer(invocation -> {
                        timeoutCallback.set(invocation.getArgument(0));
                        return null;
                    }).when(emitter).onTimeout(any(Runnable.class));
                    doAnswer(invocation -> {
                        errorCallback.set(invocation.getArgument(0));
                        return null;
                    }).when(emitter).onError(any());
                }
        )) {
            SseEmitter emitter = sseEmitterService.subscribe(
                    USER_ID,
                    CLIENT_ID
            );

            return new SubscribedEmitter(
                    emitter,
                    completionCallback.get(),
                    timeoutCallback.get(),
                    errorCallback.get()
            );
        }
    }

    private record SubscribedEmitter(
            SseEmitter emitter,
            Runnable completionCallback,
            Runnable timeoutCallback,
            Consumer<Throwable> errorCallback
    ) {
    }
}
