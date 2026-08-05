package com.theo.community_api.notification.service;

import com.theo.community_api.comment.domain.Comment;
import com.theo.community_api.common.exception.BusinessException;
import com.theo.community_api.common.exception.ErrorCode;
import com.theo.community_api.image.url.ImageUrlResolver;
import com.theo.community_api.notification.domain.Notification;
import com.theo.community_api.notification.domain.NotificationSourceType;
import com.theo.community_api.notification.domain.NotificationType;
import com.theo.community_api.notification.dto.NotificationListResponse;
import com.theo.community_api.notification.dto.NotificationResponse;
import com.theo.community_api.notification.dto.NotificationUnreadCountResponse;
import com.theo.community_api.notification.event.NotificationCreatedEvent;
import com.theo.community_api.notification.repository.NotificationRepository;
import com.theo.community_api.post.domain.Post;
import com.theo.community_api.reply.domain.Reply;
import com.theo.community_api.user.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {
    private static final int MAX_PAGE_SIZE = 100;

    private final NotificationRepository notificationRepository;
    private final ApplicationEventPublisher eventPublisher;
    private final ImageUrlResolver imageUrlResolver;

    @Transactional
    public void createLikeNotification(Post post, User actor) {
        User receiver = post.getUser();

        // 행위자와 수신자가 동일하면 알림 전달 X
        if (receiver.getId().equals(actor.getId())) {
            return;
        }

        // 이미 좋아요 알림 이력이 있으면 생성하지 않는다.
        boolean alreadyExists = notificationRepository
                .existsByReceiverIdAndTypeAndActorIdAndSourceTypeAndSourceId(
                        receiver.getId(),
                        NotificationType.LIKE,
                        actor.getId(),
                        NotificationSourceType.POST,
                        post.getId()
                );

        // 동일 좋아요 알림이 이미 있으면 생성하지 않음
        if (alreadyExists) {
            return;
        }

        Notification notification = Notification.createLike(receiver, actor, post);
        notificationRepository.save(notification);
        publishNotificationCreatedEvent(notification);
    }

    @Transactional
    public void createCommentNotification(Comment comment, User actor){
        User receiver = comment.getPost().getUser();

        // 댓글 작성자가 게시글 작성자와 동일한 경우
        if (receiver.getId().equals(actor.getId())) {
            return;
        }

        Notification notification = Notification.createComment(receiver, actor, comment);
        notificationRepository.save(notification);
        publishNotificationCreatedEvent(notification);
    }

    @Transactional
    public void createReplyNotification(Reply reply, User actor) {
        User receiver = reply.getComment().getUser();

        // 답글 작성자가 부모 댓글 작성자와 동일한 경우
        if (receiver.getId().equals(actor.getId())) {
            return;
        }

        Notification notification = Notification.createReply(receiver, actor, reply);
        notificationRepository.save(notification);
        publishNotificationCreatedEvent(notification);
    }

    public NotificationListResponse readNotifications(
            Long receiverId,
            Long lastNotificationId,
            int size
    ) {
        validatePageRequest(lastNotificationId, size);

        PageRequest pageRequest = PageRequest.of(0, size + 1);

        List<Notification> notifications;

        if (lastNotificationId == null) {
            notifications = notificationRepository.findFirstPage(
                    receiverId,
                    pageRequest
            );
        } else {
            notifications = notificationRepository.findNextPage(
                    receiverId,
                    lastNotificationId,
                    pageRequest
            );
        }

        boolean hasNext = notifications.size() > size;
        int contentSize = hasNext ? size : notifications.size();

        List<NotificationResponse> responses = new ArrayList<>(contentSize);

        for (int index = 0; index < contentSize; index++) {
            Notification notification = notifications.get(index);
            responses.add(toNotificationResponse(notification));
        }

        Long nextCursor = hasNext
                ? responses.getLast().getNotificationId()
                : null;

        return new NotificationListResponse(
                responses,
                hasNext,
                nextCursor
        );
    }

    public NotificationUnreadCountResponse readUnreadNotificationsCount(
            Long receiverId
    ){
        long unreadCount = notificationRepository.countByReceiverIdAndReadAtIsNull(receiverId);

        return new NotificationUnreadCountResponse(unreadCount);
    }

    @Transactional
    public void readNotification(
            Long receiverId,
            Long notificationId
    ){
        if (notificationId == null || notificationId < 1) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        int updatedCount = notificationRepository.markAsRead(
                receiverId,
                notificationId
        );

        if (updatedCount > 0) {
            return;
        }

        boolean exists = notificationRepository.existsByIdAndReceiverId(
                notificationId,
                receiverId
        );

        if (!exists) {
            throw new BusinessException(ErrorCode.NOTIFICATION_NOT_FOUND);
        }
    }

    @Transactional
    public void readAllNotifications(
            Long receiverId
    ){
        notificationRepository.markAllAsRead(
                receiverId
        );
    }

    private void validatePageRequest(Long lastNotificationId, int size) {
        if (size < 1 || size > MAX_PAGE_SIZE) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }

        if (lastNotificationId != null && lastNotificationId < 1) {
            throw new BusinessException(ErrorCode.INVALID_REQUEST);
        }
    }

    // 사용자에게 전달할 새로운 알림이 생성되었다는 것을 스프링에게 알리기
    private void publishNotificationCreatedEvent(
            Notification notification
    ) {
        eventPublisher.publishEvent(
                new NotificationCreatedEvent(
                        notification.getReceiver().getId(),
                        toNotificationResponse(notification)
                )
        );
    }

    private NotificationResponse toNotificationResponse(
            Notification notification
    ) {
        User actor = notification.getActor();

        String actorProfileImageUrl = actor.isDeleted()
                ? null
                : imageUrlResolver.resolve(
                        actor.getProfileImageKey()
                );

        return NotificationResponse.from(
                notification,
                actorProfileImageUrl
        );
    }
}
