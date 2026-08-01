package com.theo.community_api.notification;

import com.theo.community_api.comment.domain.Comment;
import com.theo.community_api.common.exception.BusinessException;
import com.theo.community_api.common.exception.ErrorCode;
import com.theo.community_api.notification.domain.Notification;
import com.theo.community_api.notification.domain.NotificationSourceType;
import com.theo.community_api.notification.domain.NotificationType;
import com.theo.community_api.notification.dto.NotificationListResponse;
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
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
public class NotificationServiceTest {
    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private ApplicationEventPublisher eventPublisher;

    private NotificationService notificationService;

    private User postAuthor;
    private Post post;
    private User actor;
    private Comment comment;

    @BeforeEach
    void setUp() { // 게시글 작성자, 게시글, 행위자 설정
        notificationService = new NotificationService(notificationRepository, eventPublisher);

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
                .existsByReceiverIdAndTypeAndActorIdAndSourceTypeAndSourceId(
                        postAuthor.getId(),
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
                .existsByReceiverIdAndTypeAndActorIdAndSourceTypeAndSourceId(
                        postAuthor.getId(),
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
                .existsByReceiverIdAndTypeAndActorIdAndSourceTypeAndSourceId(
                        postAuthor.getId(),
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

    @Test
    @DisplayName("첫 페이지는 요청 크기보다 하나 더 조회하여 다음 페이지 여부를 반환한다")
    void reads_first_notification_page() {
        // given
        Notification first = createLikeNotification(30L);
        Notification second = createLikeNotification(20L);
        Notification lookAhead = createLikeNotification(10L);
        PageRequest pageRequest = PageRequest.of(0, 3);

        given(notificationRepository.findFirstPage(
                postAuthor.getId(),
                pageRequest
        )).willReturn(List.of(first, second, lookAhead));

        // when
        NotificationListResponse response =
                notificationService.readNotifications(
                        postAuthor.getId(),
                        null,
                        2
                );

        // then
        verify(notificationRepository)
                .findFirstPage(postAuthor.getId(), pageRequest);

        verify(notificationRepository, never())
                .findNextPage(anyLong(), anyLong(), any());

        assertThat(response.getNotifications()).hasSize(2);
        assertThat(response.isHasNext()).isTrue();
        assertThat(response.getNextCursor()).isEqualTo(20L);
    }

    @Test
    @DisplayName("커서가 있으면 다음 페이지를 조회하고, 마지막 페이지의 다음 커서는 없다")
    void reads_next_notification_page() {
        // given
        Notification notification = createLikeNotification(20L);
        PageRequest pageRequest = PageRequest.of(0, 3);

        given(notificationRepository.findNextPage(
                postAuthor.getId(),
                30L,
                pageRequest
        )).willReturn(List.of(notification));

        // when
        NotificationListResponse response =
                notificationService.readNotifications(
                        postAuthor.getId(),
                        30L,
                        2
                );

        // then
        verify(notificationRepository)
                .findNextPage(postAuthor.getId(), 30L, pageRequest);
        verify(notificationRepository, never())
                .findFirstPage(anyLong(), any());

        assertThat(response.getNotifications()).hasSize(1);
        assertThat(response.isHasNext()).isFalse();
        assertThat(response.getNextCursor()).isNull();
    }

    @Test
    @DisplayName("미읽음 알림을 단건 읽음 처리한다")
    void reads_notification() {
        // given
        given(notificationRepository.markAsRead(
                postAuthor.getId(),
                10L
        )).willReturn(1);

        // when
        notificationService.readNotification(
                postAuthor.getId(),
                10L
        );

        // then
        verify(notificationRepository)
                .markAsRead(postAuthor.getId(), 10L);
        verify(notificationRepository, never())
                .existsByIdAndReceiverId(anyLong(), anyLong());
    }

    @Test
    @DisplayName("이미 읽은 자신의 알림은 성공 처리한다")
    void reads_already_read_notification_idempotently() {
        // given
        given(notificationRepository.markAsRead(
                postAuthor.getId(),
                10L
        )).willReturn(0);
        given(notificationRepository.existsByIdAndReceiverId(
                10L,
                postAuthor.getId()
        )).willReturn(true);

        // when
        notificationService.readNotification(
                postAuthor.getId(),
                10L
        );

        // then
        verify(notificationRepository)
                .existsByIdAndReceiverId(10L, postAuthor.getId());
    }

    @Test
    @DisplayName("존재하지 않거나 다른 사용자의 알림은 NOTIFICATION_NOT_FOUND가 발생한다")
    void fails_when_notification_is_not_owned() {
        // given
        given(notificationRepository.markAsRead(
                postAuthor.getId(),
                10L
        )).willReturn(0);
        given(notificationRepository.existsByIdAndReceiverId(
                10L,
                postAuthor.getId()
        )).willReturn(false);

        // when
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> notificationService.readNotification(
                        postAuthor.getId(),
                        10L
                )
        );

        // then
        assertThat(exception.getErrorCode())
                .isEqualTo(ErrorCode.NOTIFICATION_NOT_FOUND);
    }

    private Notification createLikeNotification(Long id) {
        Notification notification =
                Notification.createLike(postAuthor, actor, post);

        ReflectionTestUtils.setField(notification, "id", id);

        return notification;
    }
}
