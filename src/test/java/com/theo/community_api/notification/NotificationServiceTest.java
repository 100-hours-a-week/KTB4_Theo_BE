package com.theo.community_api.notification;

import com.theo.community_api.comment.domain.Comment;
import com.theo.community_api.notification.domain.Notification;
import com.theo.community_api.notification.domain.NotificationSourceType;
import com.theo.community_api.notification.domain.NotificationType;
import com.theo.community_api.notification.repository.NotificationRepository;
import com.theo.community_api.notification.service.NotificationService;
import com.theo.community_api.post.domain.Post;
import com.theo.community_api.reply.domain.Reply;
import com.theo.community_api.user.domain.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class NotificationServiceTest {
    @Mock
    private NotificationRepository notificationRepository;

    private NotificationService notificationService;

    private User postAuthor;
    private Post post;
    private User actor;
    private Comment comment;

    @BeforeEach
    void setUp() { // 게시글 작성자, 게시글, 행위자 설정
        notificationService =
                new NotificationService(notificationRepository);

        postAuthor = new User(
                "author@test.com",
                "password",
                "작성자",
                null
        );

        ReflectionTestUtils.setField(postAuthor, "id", 1L);

        post = new Post(
                postAuthor,
                "테스트 제목",
                "테스트 내용"
        );

        ReflectionTestUtils.setField(post, "id", 10L);

        actor = new User(
                "actor@test.com",
                "password",
                "행위자",
                null
        );

        ReflectionTestUtils.setField(actor, "id", 2L);
    }

    @Test
    @DisplayName("자신의 게시글에 좋아요를 누르면 알림을 생성하지 않는다")
    void does_not_create_like_notification_for_own_post() {
        notificationService.createLikeNotification(
                post,
                postAuthor
        );

        verify(notificationRepository, never())
                .save(any(Notification.class));
    }

    @Test
    @DisplayName("다른 사용자의 게시글에 좋아요를 누르면 알림을 생성한다")
    void creates_like_notification_for_another_post() {
        given(notificationRepository
                .existsByTypeAndActorIdAndSourceTypeAndSourceId(
                        NotificationType.LIKE,
                        actor.getId(),
                        NotificationSourceType.POST,
                        post.getId()
                )
        ).willReturn(false);

        notificationService.createLikeNotification(post, actor);

        ArgumentCaptor<Notification> captor =
                ArgumentCaptor.forClass(Notification.class);

        verify(notificationRepository).save(captor.capture());

        Notification savedNotification = captor.getValue();

        assertThat(savedNotification.getReceiver()).isEqualTo(postAuthor);
        assertThat(savedNotification.getActor()).isEqualTo(actor);
        assertThat(savedNotification.getType()).isEqualTo(NotificationType.LIKE);
    }

    // 이미 동일한 좋아요 알림 이력이 존재하면 새로 생성하지 않는다.
    @Test
    @DisplayName("동일한 좋아요 알림이 존재하면 다시 생성하지 않는다")
    void does_not_create_duplicate_like_notification() {
        // given : 이미 좋아요 알림이 주어진 상태
        given(notificationRepository
                .existsByTypeAndActorIdAndSourceTypeAndSourceId(
                        NotificationType.LIKE,
                        actor.getId(),
                        NotificationSourceType.POST,
                        post.getId()
                )
        ).willReturn(true);

        // when : 좋아요 알림 생성
        notificationService.createLikeNotification(post, actor);

        // then
        verify(notificationRepository)
                .existsByTypeAndActorIdAndSourceTypeAndSourceId(
                        NotificationType.LIKE,
                        actor.getId(),
                        NotificationSourceType.POST,
                        post.getId()
                );

        verify(notificationRepository, never())
                .save(any(Notification.class));
    }

    @Test
    @DisplayName("다른 사용자의 게시글에 댓글을 작성하면 알림을 생성한다")
    void creates_comment_notification_for_another_users_post() {
        // given
        comment = new Comment(
                post,
                actor,
                "테스트 댓글"
        );

        ReflectionTestUtils.setField(comment, "id", 20L);

        // when
        notificationService.createCommentNotification(comment, actor);

        // then
        ArgumentCaptor<Notification> captor = ArgumentCaptor.forClass(Notification.class);

        verify(notificationRepository).save(captor.capture());

        Notification savedNotification = captor.getValue();

        assertThat(savedNotification.getReceiver()).isEqualTo(postAuthor);
        assertThat(savedNotification.getActor()).isEqualTo(actor);
        assertThat(savedNotification.getType())
                .isEqualTo(NotificationType.COMMENT);
    }

    @Test
    @DisplayName("자신의 게시글에 댓글을 작성하면 알림을 생성하지 않는다")
    void does_not_create_comment_notification_for_own_post() {
        // given
        Comment comment = new Comment(
                post,
                postAuthor,
                "작성자 본인의 댓글"
        );

        ReflectionTestUtils.setField(comment, "id", 20L);

        // when
        notificationService.createCommentNotification(
                comment,
                postAuthor
        );

        // then
        verify(notificationRepository, never())
                .save(any(Notification.class));
    }

    @Test
    @DisplayName("다른 사용자의 댓글에 답글을 작성하면 댓글 작성자에게 알림을 생성한다")
    void creates_reply_notification_for_another_users_comment() {
        // given
        User commentAuthor = new User(
                "comment-author@test.com",
                "password",
                "댓글작성자",
                null
        );
        ReflectionTestUtils.setField(commentAuthor, "id", 3L);

        Comment parentComment = new Comment(post, commentAuthor, "부모 댓글");
        ReflectionTestUtils.setField(parentComment, "id", 20L);

        Reply reply = new Reply(parentComment, actor, "테스트 답글");
        ReflectionTestUtils.setField(reply, "id", 30L);

        // when
        notificationService.createReplyNotification(reply, actor);

        // then
        ArgumentCaptor<Notification> captor =
                ArgumentCaptor.forClass(Notification.class);

        verify(notificationRepository).save(captor.capture());

        Notification savedNotification = captor.getValue();

        assertThat(savedNotification.getReceiver()).isEqualTo(commentAuthor);
        assertThat(savedNotification.getActor()).isEqualTo(actor);
        assertThat(savedNotification.getType()).isEqualTo(NotificationType.REPLY);
    }

    @Test
    @DisplayName("자신의 댓글에 답글을 작성하면 알림을 생성하지 않는다")
    void does_not_create_reply_notification_for_own_comment() {
        // given
        Comment parentComment = new Comment(post, actor, "본인의 댓글");
        ReflectionTestUtils.setField(parentComment, "id", 20L);

        Reply reply = new Reply(parentComment, actor, "본인의 답글");
        ReflectionTestUtils.setField(reply, "id", 30L);

        // when
        notificationService.createReplyNotification(reply, actor);

        // then
        verify(notificationRepository, never())
                .save(any(Notification.class));
    }
}
