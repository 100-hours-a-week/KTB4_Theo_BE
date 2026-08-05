package com.theo.community_api.notification.dto;

import com.theo.community_api.notification.domain.Notification;
import com.theo.community_api.notification.domain.NotificationType;
import com.theo.community_api.user.domain.User;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class NotificationResponse {
    private Long notificationId;
    private NotificationType type;
    private Long actorId;
    private String actorNickname;
    private String actorProfileImageUrl;
    private Long postId;
    private Long commentId;
    private boolean read;
    private LocalDateTime createdAt;

    public static NotificationResponse from(
            Notification notification,
            String actorProfileImageUrl
    ) {
        User actor = notification.getActor();

        boolean isActorDeleted = actor.isDeleted();

        String actorNickname = isActorDeleted ? "알 수 없음" : actor.getNickname();

        String responseActorProfileImageUrl = isActorDeleted
                ? null
                : actorProfileImageUrl;

        Long commentId = notification.getComment() == null ? null : notification.getComment().getId();

        return new NotificationResponse(
                notification.getId(),
                notification.getType(),
                actor.getId(),
                actorNickname,
                responseActorProfileImageUrl,
                notification.getPost().getId(),
                commentId,
                notification.isRead(),
                notification.getCreatedAt()
        );
    }
}
