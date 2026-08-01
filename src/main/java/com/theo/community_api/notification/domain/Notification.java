package com.theo.community_api.notification.domain;

import com.theo.community_api.comment.domain.Comment;
import com.theo.community_api.common.BaseTimeEntity;
import com.theo.community_api.post.domain.Post;
import com.theo.community_api.reply.domain.Reply;
import com.theo.community_api.user.domain.User;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

// 알림 엔티티
@Entity
@Table(
        name="notifications",
        uniqueConstraints={
                @UniqueConstraint( // 중복 추가를 방지
                        name = "uk_notification_receiver_source_actor",
                        columnNames = {
                                "receiver_id",
                                "type",
                                "actor_id",
                                "source_type",
                                "source_id"
                        }
                )
        },
        indexes = {
                @Index( // 수신자 인덱스처리
                        name = "idx_notification_receiver_id_id",
                        columnList = "receiver_id, id"
                ),
                @Index( // 아직 읽지 않은 수신자 인덱스 처리
                        name = "idx_notification_receiver_read_at",
                        columnList = "receiver_id, read_at"
                )
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Notification extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    // 알림 수신자
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receiver_id", nullable = false)
    private User receiver;

    // 알림 발생자
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id", nullable = false)
    private User actor;

    // 알림 유형 LIKE, COMMENT, REPLY
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationType type;

    // 알림이 발생한 데이터 유형 POST, COMMENT, REPLY
    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 20)
    private NotificationSourceType sourceType;

    // 좋아요 대상 게시글, 작성된 댓글, 작성된 답글
    @Column(name = "source_id", nullable = false)
    private Long sourceId;

    // 알림 클릭 시 이동할 게시글
    @ManyToOne(fetch = FetchType.LAZY, optional = false) // 이후에 post와 관계없는 알림이 추가되면 필수 관계 해제 필요
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    // 댓글 알림 : 작성된 댓글
    // 답글 알림 : 답글이 작성된 부모 댓글
    // 좋아요 알림 : null
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "comment_id")
    private Comment comment;

    // 읽은 날짜
    // null 이면 읽지 않고, 값이 있으면 읽는다.
    @Column(name = "read_at")
    private LocalDateTime readAt;

    private Notification(
            User receiver,
            User actor,
            NotificationType type,
            NotificationSourceType sourceType,
            Long sourceId,
            Post post,
            Comment comment
    ) {
        this.receiver = receiver;
        this.actor = actor;
        this.type = type;
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.post = post;
        this.comment = comment;
    }

    // 게시글 좋아요 (sourceId : 게시글 ID, post : 좋아요 대상 게시글, comment : null)
    public static Notification createLike(
            User receiver,
            User actor,
            Post post
    ) {
        return new Notification(
                receiver,
                actor,
                NotificationType.LIKE,
                NotificationSourceType.POST,
                post.getId(),
                post,
                null
        );
    }

    // 댓글 작성 (sourceId : 작성된 댓글ID, post : 댓글이 작성된 게시글, comment: 작성된 댓글)
    public static Notification createComment(
            User receiver,
            User actor,
            Comment comment
    ) {
        return new Notification(
                receiver,
                actor,
                NotificationType.COMMENT,
                NotificationSourceType.COMMENT,
                comment.getId(),
                comment.getPost(),
                comment
        );
    }

    // 대댓글 작성 (sourceId : 작성된 대댓글 ID, post : 부모 댓글이 속한 게시글, comment : 대댓글이 작성된 부모 댓글)
    public static Notification createReply(
            User receiver,
            User actor,
            Reply reply
    ) {
        Comment parentComment = reply.getComment();

        return new Notification(
                receiver,
                actor,
                NotificationType.REPLY,
                NotificationSourceType.REPLY,
                reply.getId(),
                parentComment.getPost(),
                parentComment
        );
    }

    public boolean isRead() {
        return readAt != null;
    }
}
