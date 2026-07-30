package com.theo.community_api.notification.sse;

import com.theo.community_api.notification.event.NotificationCreatedEvent;
import lombok.RequiredArgsConstructor;
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
    public void sendNotification(
            NotificationCreatedEvent event
    ) {
        sseEmitterService.sendNotification(
                event.receiverId(),
                event.notification()
        );
    }
}