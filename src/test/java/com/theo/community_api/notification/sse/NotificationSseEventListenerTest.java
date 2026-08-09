package com.theo.community_api.notification.sse;

import com.theo.community_api.notification.dto.NotificationResponse;
import com.theo.community_api.notification.event.NotificationCreatedEvent;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

import java.lang.reflect.Method;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationSseEventListenerTest {

    @Mock
    private SseEmitterService sseEmitterService;

    @InjectMocks
    private NotificationSseEventListener eventListener;

    @Test
    @DisplayName("알림 생성 이벤트는 트랜잭션 커밋 후 처리하도록 설정한다.")
    void handlesNotificationCreatedEventAfterCommit() throws Exception {
        Method listenerMethod = NotificationSseEventListener.class
                .getDeclaredMethod(
                        "sendNotification",
                        NotificationCreatedEvent.class
                );

        TransactionalEventListener annotation = listenerMethod
                .getAnnotation(TransactionalEventListener.class);

        assertThat(annotation).isNotNull();
        assertThat(annotation.phase()).isEqualTo(TransactionPhase.AFTER_COMMIT);
    }

    @Test
    @DisplayName("알림 생성 이벤트를 받으면 수신자에게 SSE 알림 전송을 요청한다.")
    void delegatesNotificationToSseEmitterService() {
        NotificationResponse notification = new NotificationResponse(
                10L, null, null, null, null, null, null, false, null
        );
        NotificationCreatedEvent event = new NotificationCreatedEvent(
                1L,
                notification
        );

        eventListener.sendNotification(event);

        verify(sseEmitterService).sendNotification(1L, notification);
    }
}
