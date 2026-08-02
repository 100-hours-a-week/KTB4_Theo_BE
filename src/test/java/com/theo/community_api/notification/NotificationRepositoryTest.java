package com.theo.community_api.notification;

import com.theo.community_api.comment.domain.Comment;
import com.theo.community_api.notification.domain.Notification;
import com.theo.community_api.notification.domain.NotificationSourceType;
import com.theo.community_api.notification.domain.NotificationType;
import com.theo.community_api.notification.repository.NotificationRepository;
import com.theo.community_api.post.domain.Post;
import com.theo.community_api.reply.domain.Reply;
import com.theo.community_api.user.domain.User;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.PageRequest;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
class NotificationRepositoryTest {

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private EntityManager entityManager;

    @Test
    @DisplayName("좋아요 알림을 저장한다")
    void save_like_notification() {
        // given
        User receiver = saveUser("receiver@test.com", "수신자");
        User actor = saveUser("actor@test.com", "행위자");
        Post post = savePost(receiver);

        // when
        Notification notification = Notification.createLike(receiver, actor, post);

        // then
        Notification saved = notificationRepository.saveAndFlush(notification);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getReceiver().getId()).isEqualTo(receiver.getId());
        assertThat(saved.getActor().getId()).isEqualTo(actor.getId());
        assertThat(saved.getType()).isEqualTo(NotificationType.LIKE);
        assertThat(saved.getSourceType()).isEqualTo(NotificationSourceType.POST);
        assertThat(saved.getSourceId()).isEqualTo(post.getId());
        assertThat(saved.getPost().getId()).isEqualTo(post.getId());
        assertThat(saved.getComment()).isNull();
        assertThat(saved.getReadAt()).isNull();
        assertThat(saved.getCreatedAt()).isNotNull();
    }

    @Test
    @DisplayName("댓글 알림에는 작성된 댓글을 원본과 이동 대상으로 저장한다")
    void save_comment_notification() {
        // given
        User receiver = saveUser("receiver@test.com", "수신자");
        User actor = saveUser("actor@test.com", "행위자");
        Post post = savePost(receiver);
        Comment comment = saveComment(post, actor, "댓글 내용");

        // when
        Notification notification = Notification.createComment(receiver, actor, comment);

        // then
        Notification saved = notificationRepository.saveAndFlush(notification);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getReceiver().getId()).isEqualTo(receiver.getId());
        assertThat(saved.getActor().getId()).isEqualTo(actor.getId());
        assertThat(saved.getType()).isEqualTo(NotificationType.COMMENT);
        assertThat(saved.getSourceType()).isEqualTo(NotificationSourceType.COMMENT);
        assertThat(saved.getSourceId()).isEqualTo(comment.getId());
        assertThat(saved.getPost().getId()).isEqualTo(post.getId());
        assertThat(saved.getComment().getId()).isEqualTo(comment.getId());
    }

    @Test
    @DisplayName("답글 알림에는 답글 ID와 부모 댓글을 저장한다")
    void save_reply_notification() {
        // given
        User receiver = saveUser("receiver@test.com", "수신자");
        User actor = saveUser("actor@test.com", "행위자");
        Post post = savePost(receiver);
        Comment parentComment = saveComment(post, receiver, "부모 댓글");
        Reply reply = saveReply(parentComment, actor, "답글 내용");

        // when
        Notification notification = Notification.createReply(receiver, actor, reply);

        // then
        Notification saved = notificationRepository.saveAndFlush(notification);

        assertThat(saved.getType()).isEqualTo(NotificationType.REPLY);
        assertThat(saved.getSourceType()).isEqualTo(NotificationSourceType.REPLY);
        assertThat(saved.getSourceId()).isEqualTo(reply.getId());
        assertThat(saved.getPost().getId()).isEqualTo(post.getId());
        assertThat(saved.getComment().getId()).isEqualTo(parentComment.getId());
    }

    @Test
    @DisplayName("수신자와 알림 타입과 행위자와 원본으로 기존 알림을 확인한다")
    void exists_notification_by_unique_source() {
        // given
        User receiver = saveUser("receiver@test.com", "수신자");
        User actor = saveUser("actor@test.com", "행위자");

        // when
        Post post = savePost(receiver);

        // then
        notificationRepository.saveAndFlush(Notification.createLike(receiver, actor, post));

        boolean exists = notificationRepository
                .existsByReceiverIdAndTypeAndActorIdAndSourceTypeAndSourceId(
                        receiver.getId(),
                        NotificationType.LIKE,
                        actor.getId(),
                        NotificationSourceType.POST,
                        post.getId()
                );

        assertThat(exists).isTrue();
    }

    @Test
    @DisplayName("동일한 원본의 좋아요 알림은 중복 저장할 수 없다")
    void reject_duplicate_like_notification() {
        // given
        User receiver = saveUser("receiver@test.com", "수신자");
        User actor = saveUser("actor@test.com", "행위자");

        // when
        Post post = savePost(receiver);

        // then
        notificationRepository.saveAndFlush(Notification.createLike(receiver, actor, post));

        Notification duplicate = Notification.createLike(receiver, actor, post);

        assertThatThrownBy(() -> notificationRepository.saveAndFlush(duplicate))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("원본과 행위자가 같아도 수신자가 다르면 알림을 저장할 수 있다")
    void allow_same_source_notification_for_different_receivers() {
        // given
        User firstReceiver = saveUser("receiver1@test.com", "첫 번째 수신자");
        User secondReceiver = saveUser("receiver2@test.com", "두 번째 수신자");
        User actor = saveUser("actor@test.com", "행위자");
        Post post = savePost(firstReceiver);

        // when
        notificationRepository.saveAndFlush(Notification.createLike(firstReceiver, actor, post));
        Notification second = notificationRepository.saveAndFlush(
                Notification.createLike(secondReceiver, actor, post)
        );

        // then
        assertThat(second.getId()).isNotNull();
    }

    @Test
    @DisplayName("수신자의 알림을 최신순으로 조회한다")
    void find_first_notification_page() {
        // given
        User receiver = saveUser("receiver@test.com", "수신자");
        User actor = saveUser("actor@test.com", "행위자");

        Notification first = saveLikeNotification(
                receiver,
                actor,
                savePost(receiver)
        );
        Notification second = saveLikeNotification(
                receiver,
                actor,
                savePost(receiver)
        );

        notificationRepository.flush();
        entityManager.clear();

        // when
        List<Notification> notifications =
                notificationRepository.findFirstPage(
                        receiver.getId(),
                        PageRequest.of(0, 2)
                );

        // then
        assertThat(notifications)
                .extracting(Notification::getId)
                .containsExactly(second.getId(), first.getId());
    }

    @Test
    @DisplayName("커서보다 이전 알림을 최신순으로 조회한다")
    void find_next_notification_page() {
        // given
        User receiver = saveUser("receiver@test.com", "수신자");
        User actor = saveUser("actor@test.com", "행위자");

        Notification first = saveLikeNotification(
                receiver,
                actor,
                savePost(receiver)
        );
        Notification second = saveLikeNotification(
                receiver,
                actor,
                savePost(receiver)
        );
        Notification cursor = saveLikeNotification(
                receiver,
                actor,
                savePost(receiver)
        );

        notificationRepository.flush();
        entityManager.clear();

        // when
        List<Notification> notifications =
                notificationRepository.findNextPage(
                        receiver.getId(),
                        cursor.getId(),
                        PageRequest.of(0, 2)
                );

        // then
        assertThat(notifications)
                .extracting(Notification::getId)
                .containsExactly(second.getId(), first.getId());
    }

    @Test
    @DisplayName("단건 읽음 처리는 수신자의 알림만 갱신하고 최초 읽은 시각을 유지한다")
    void mark_notification_as_read_idempotently_for_receiver() {
        // given
        User receiver = saveUser("receiver@test.com", "수신자");
        User otherReceiver = saveUser("other@test.com", "다른 수신자");
        User actor = saveUser("actor@test.com", "행위자");
        Notification notification = saveLikeNotification(
                receiver,
                actor,
                savePost(receiver)
        );
        Notification otherNotification = saveLikeNotification(
                otherReceiver,
                actor,
                savePost(otherReceiver)
        );
        notificationRepository.flush();
        entityManager.clear();

        // when
        int otherReceiverUpdated = notificationRepository.markAsRead(
                receiver.getId(),
                otherNotification.getId()
        );
        int firstUpdated = notificationRepository.markAsRead(
                receiver.getId(),
                notification.getId()
        );
        Notification firstRead = notificationRepository.findById(notification.getId()).orElseThrow();
        var firstReadAt = firstRead.getReadAt();
        int secondUpdated = notificationRepository.markAsRead(
                receiver.getId(),
                notification.getId()
        );

        // then
        Notification readAgain = notificationRepository.findById(notification.getId()).orElseThrow();
        Notification unchangedOther = notificationRepository.findById(otherNotification.getId()).orElseThrow();

        assertThat(otherReceiverUpdated).isZero();
        assertThat(firstUpdated).isEqualTo(1);
        assertThat(secondUpdated).isZero();
        assertThat(firstReadAt).isNotNull();
        assertThat(readAgain.getReadAt()).isEqualTo(firstReadAt);
        assertThat(unchangedOther.getReadAt()).isNull();
    }

    @Test
    @DisplayName("전체 읽음 처리는 해당 수신자의 미읽음 알림만 갱신한다")
    void mark_all_notifications_as_read_for_receiver() {
        // given
        User receiver = saveUser("receiver@test.com", "수신자");
        User otherReceiver = saveUser("other@test.com", "다른 수신자");
        User actor = saveUser("actor@test.com", "행위자");
        Notification alreadyRead = saveLikeNotification(
                receiver,
                actor,
                savePost(receiver)
        );
        Notification unread = saveLikeNotification(
                receiver,
                actor,
                savePost(receiver)
        );
        Notification otherNotification = saveLikeNotification(
                otherReceiver,
                actor,
                savePost(otherReceiver)
        );
        notificationRepository.flush();
        entityManager.clear();
        notificationRepository.markAsRead(receiver.getId(), alreadyRead.getId());
        var firstReadAt = notificationRepository.findById(alreadyRead.getId())
                .orElseThrow()
                .getReadAt();

        // when
        int updated = notificationRepository.markAllAsRead(receiver.getId());

        // then
        Notification unchangedRead = notificationRepository.findById(alreadyRead.getId()).orElseThrow();
        Notification newlyRead = notificationRepository.findById(unread.getId()).orElseThrow();
        Notification unchangedOther = notificationRepository.findById(otherNotification.getId()).orElseThrow();

        assertThat(updated).isEqualTo(1);
        assertThat(unchangedRead.getReadAt()).isEqualTo(firstReadAt);
        assertThat(newlyRead.getReadAt()).isNotNull();
        assertThat(unchangedOther.getReadAt()).isNull();
    }

    @Test
    @DisplayName("미읽음 알림 개수는 해당 수신자의 읽지 않은 알림만 포함한다")
    void count_unread_notifications_for_receiver() {
        // given
        User receiver = saveUser("receiver@test.com", "수신자");
        User otherReceiver = saveUser("other@test.com", "다른 수신자");
        User actor = saveUser("actor@test.com", "행위자");
        Notification read = saveLikeNotification(
                receiver,
                actor,
                savePost(receiver)
        );
        saveLikeNotification(receiver, actor, savePost(receiver));
        saveLikeNotification(otherReceiver, actor, savePost(otherReceiver));
        notificationRepository.flush();
        entityManager.clear();
        notificationRepository.markAsRead(receiver.getId(), read.getId()); // 2개 중 1개의 알림만 읽은 상태

        // when
        long unreadCount = notificationRepository
                .countByReceiverIdAndReadAtIsNull(receiver.getId());

        // then
        assertThat(unreadCount).isEqualTo(1);
    }



    private User saveUser(String email, String nickname) {
        User user = new User(email, "password", nickname, null);
        entityManager.persist(user);
        return user;
    }

    private Post savePost(User author) {
        Post post = new Post(author, "테스트 제목", "테스트 내용");
        entityManager.persist(post);
        return post;
    }

    private Comment saveComment(Post post, User author, String content) {
        Comment comment = new Comment(post, author, content);
        entityManager.persist(comment);
        return comment;
    }

    private Reply saveReply(Comment comment, User author, String content) {
        Reply reply = new Reply(comment, author, content);
        entityManager.persist(reply);
        return reply;
    }

    private Notification saveLikeNotification(
            User receiver,
            User actor,
            Post post
    ) {
        return notificationRepository.save(
                Notification.createLike(receiver, actor, post)
        );
    }
}
