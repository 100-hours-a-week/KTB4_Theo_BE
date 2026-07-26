package com.theo.community_api.notification.service;

import com.theo.community_api.comment.domain.Comment;
import com.theo.community_api.notification.domain.Notification;
import com.theo.community_api.notification.domain.NotificationSourceType;
import com.theo.community_api.notification.domain.NotificationType;
import com.theo.community_api.notification.repository.NotificationRepository;
import com.theo.community_api.post.domain.Post;
import com.theo.community_api.reply.domain.Reply;
import com.theo.community_api.user.domain.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional
public class NotificationService {
    private final NotificationRepository notificationRepository;

    public void createLikeNotification(Post post, User actor) {
        User receiver = post.getUser();

        // 행위자와 수신자가 동일하면 알림 전달 X
        if (receiver.getId().equals(actor.getId())) {
            return;
        }

        // 이미 좋아요 알림 이력이 있으면 생성하지 않는다.
        boolean alreadyExists = notificationRepository
                .existsByTypeAndActorIdAndSourceTypeAndSourceId(
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
    }

    public void createCommentNotification(Comment comment, User actor){
        User receiver = comment.getPost().getUser();

        // 댓글 작성자가 게시글 작성자와 동일한 경우
        if (receiver.getId().equals(actor.getId())) {
            return;
        }

        Notification notification = Notification.createComment(receiver, actor, comment);

        notificationRepository.save(notification);
    }

    public void createReplyNotification(Reply reply, User actor) {
        User receiver = reply.getComment().getUser();

        // 답글 작성자가 부모 댓글 작성자와 동일한 경우
        if (receiver.getId().equals(actor.getId())) {
            return;
        }

        Notification notification = Notification.createReply(receiver, actor, reply);

        notificationRepository.save(notification);
    }
}
