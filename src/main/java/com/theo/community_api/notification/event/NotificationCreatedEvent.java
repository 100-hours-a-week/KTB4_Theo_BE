package com.theo.community_api.notification.event;

import com.theo.community_api.notification.dto.NotificationResponse;

// 알림이 생성된 사실, SSE 전송에 필요한 데이터 전달하는 내부 이벤트
public record NotificationCreatedEvent(
        Long receiverId,
        NotificationResponse notification
) {
}