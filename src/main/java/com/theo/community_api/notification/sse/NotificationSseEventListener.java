package com.theo.community_api.notification.sse;

import com.theo.community_api.notification.event.NotificationCreatedEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
public class NotificationSseEventListener {

    private final SseEmitterService sseEmitterService;

    @TransactionalEventListener(
            phase = TransactionPhase.AFTER_COMMIT
    )
    @Async(SseAsyncConfig.SSE_NOTIFICATION_EXECUTOR)
    public void sendNotification(
            NotificationCreatedEvent event
    ) {
        sseEmitterService.sendNotification(
                event.receiverId(),
                event.notification()
        );
    }
}
