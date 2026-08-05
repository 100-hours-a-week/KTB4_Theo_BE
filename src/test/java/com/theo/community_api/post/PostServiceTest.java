package com.theo.community_api.post;

import com.theo.community_api.common.exception.BusinessException;
import com.theo.community_api.common.exception.ErrorCode;
import com.theo.community_api.image.url.ImageUrlResolver;
import com.theo.community_api.notification.service.NotificationService;
import com.theo.community_api.post.domain.Post;
import com.theo.community_api.post.domain.PostLike;
import com.theo.community_api.post.dto.PostLikeResponse;
import com.theo.community_api.post.repository.*;
import com.theo.community_api.post.service.PostService;
import com.theo.community_api.user.domain.User;
import com.theo.community_api.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class PostServiceTest {

    @Mock
    PostRepository postRepository;

    @Mock
    UserRepository userRepository;

    @Mock
    PostLikeRepository postLikeRepository;

    @Mock
    NotificationService notificationService;

    @Mock
    ImageUrlResolver imageUrlResolver;

    @InjectMocks
    PostService postService;

    @Test
    @DisplayName("작성자가 아닌 사용자가 게시글을 수정하면 POST_MODIFY_FORBIDDEN이 발생한다")
    void updatePost_forbidden_when_user_is_not_author() {
        // given
        User author = new User("author@test.com", "password", "theo", null);
        ReflectionTestUtils.setField(author, "id", 1L);

        Post post = new Post(author, "기존 제목", "기존 내용");
        given(postRepository.findByIdWithUser(1L)).willReturn(Optional.of(post));

        // when
        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> postService.updatePost(2L, 1L, null, null)
        );

        // then
        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.POST_MODIFY_FORBIDDEN);
    }

    @Test
    @DisplayName("좋아요를 등록하면 좋아요 알림 생성을 요청한다")
    void creates_notification_when_post_like_is_added() {
        // given
        User author = createUser(
                1L,
                "author@test.com",
                "작성자"
        );

        User actor = createUser(
                2L,
                "actor@test.com",
                "행위자"
        );

        Post post = createPost(10L, author);

        given(postRepository.findByIdWithUserForUpdate(post.getId()))
                .willReturn(Optional.of(post));

        given(userRepository.findById(actor.getId()))
                .willReturn(Optional.of(actor));

        given(postLikeRepository.findByPostIdAndUserId(
                post.getId(),
                actor.getId()
        )).willReturn(Optional.empty());

        // when
        PostLikeResponse response =
                postService.togglePostLike(
                        actor.getId(),
                        post.getId()
                );

        // then
        verify(postLikeRepository)
                .save(any(PostLike.class));

        verify(notificationService)
                .createLikeNotification(post, actor);

        assertThat(response.isLiked()).isTrue();
        assertThat(response.getLikeCount()).isEqualTo(1);
    }

    @Test
    @DisplayName("좋아요를 취소하면 좋아요 알림 생성을 요청하지 않는다")
    void does_not_create_notification_when_post_like_is_removed() {
        // given
        User author = createUser(
                1L,
                "author@test.com",
                "작성자"
        );

        User actor = createUser(
                2L,
                "actor@test.com",
                "행위자"
        );

        Post post = createPost(10L, author);
        post.increaseLikeCount();

        PostLike postLike = new PostLike(post, actor);

        given(postRepository.findByIdWithUserForUpdate(post.getId()))
                .willReturn(Optional.of(post));

        given(userRepository.findById(actor.getId()))
                .willReturn(Optional.of(actor));

        given(postLikeRepository.findByPostIdAndUserId(
                post.getId(),
                actor.getId()
        )).willReturn(Optional.of(postLike));

        // when
        PostLikeResponse response =
                postService.togglePostLike(
                        actor.getId(),
                        post.getId()
                );

        // then
        verify(postLikeRepository).delete(postLike);

        verify(notificationService, never())
                .createLikeNotification(
                        any(Post.class),
                        any(User.class)
                );

        assertThat(response.isLiked()).isFalse();
        assertThat(response.getLikeCount()).isZero();
    }

    private User createUser(
            Long id,
            String email,
            String nickname
    ) {
        User user = new User(
                email,
                "password",
                nickname,
                null
        );

        ReflectionTestUtils.setField(user, "id", id);

        return user;
    }

    private Post createPost(
            Long id,
            User author
    ) {
        Post post = new Post(
                author,
                "테스트 제목",
                "테스트 내용"
        );

        ReflectionTestUtils.setField(post, "id", id);

        return post;
    }
}
