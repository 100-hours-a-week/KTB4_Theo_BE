package com.theo.community_api.comment;

import com.theo.community_api.comment.domain.Comment;
import com.theo.community_api.comment.dto.CommentCreateRequest;
import com.theo.community_api.comment.repository.CommentRepository;
import com.theo.community_api.comment.service.CommentService;
import com.theo.community_api.notification.service.NotificationService;
import com.theo.community_api.post.domain.Post;
import com.theo.community_api.post.repository.PostRepository;
import com.theo.community_api.reply.repository.ReplyRepository;
import com.theo.community_api.user.domain.User;
import com.theo.community_api.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class CommentServiceTest {

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private PostRepository postRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private CommentService commentService;

    @Test
    @DisplayName("댓글을 작성하면 댓글 수를 증가시키고 알림 생성을 요청한다")
    void creates_comment_and_requests_notification() {
        // given
        User postAuthor = createUser(1L, "author@test.com", "작성자");
        User actor = createUser(2L, "actor@test.com", "행위자");
        Post post = createPost(10L, postAuthor);
        CommentCreateRequest request = createRequest("테스트 댓글");

        given(userRepository.findById(actor.getId()))
                .willReturn(Optional.of(actor));
        given(postRepository.findByIdWithUser(post.getId()))
                .willReturn(Optional.of(post));
        given(commentRepository.save(any(Comment.class)))
                .willAnswer(invocation -> {
                    Comment comment = invocation.getArgument(0);
                    ReflectionTestUtils.setField(comment, "id", 20L);
                    return comment;
                });

        // when
        Long commentId = commentService.createComment(
                actor.getId(),
                post.getId(),
                request
        );

        // then
        ArgumentCaptor<Comment> commentCaptor =
                ArgumentCaptor.forClass(Comment.class);

        verify(commentRepository).save(commentCaptor.capture());

        Comment savedComment = commentCaptor.getValue();

        verify(notificationService)
                .createCommentNotification(savedComment, actor);

        assertThat(commentId).isEqualTo(20L);
        assertThat(savedComment.getPost()).isEqualTo(post);
        assertThat(savedComment.getUser()).isEqualTo(actor);
        assertThat(savedComment.getContent()).isEqualTo("테스트 댓글");
        assertThat(post.getCommentCount()).isEqualTo(1);
    }

    private User createUser(Long id, String email, String nickname) {
        User user = new User(email, "password", nickname, null);
        ReflectionTestUtils.setField(user, "id", id);
        return user;
    }

    private Post createPost(Long id, User author) {
        Post post = new Post(author, "테스트 제목", "테스트 내용");
        ReflectionTestUtils.setField(post, "id", id);
        return post;
    }

    private CommentCreateRequest createRequest(String content) {
        CommentCreateRequest request = new CommentCreateRequest();
        ReflectionTestUtils.setField(request, "content", content);
        return request;
    }
}
