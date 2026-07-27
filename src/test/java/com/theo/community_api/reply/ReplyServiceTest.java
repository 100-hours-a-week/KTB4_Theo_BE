package com.theo.community_api.reply;

import com.theo.community_api.comment.domain.Comment;
import com.theo.community_api.comment.repository.CommentRepository;
import com.theo.community_api.notification.service.NotificationService;
import com.theo.community_api.post.domain.Post;
import com.theo.community_api.reply.domain.Reply;
import com.theo.community_api.reply.dto.ReplyCreateRequest;
import com.theo.community_api.reply.repository.ReplyRepository;
import com.theo.community_api.reply.service.ReplyService;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class ReplyServiceTest {

    @Mock
    private ReplyRepository replyRepository;

    @Mock
    private CommentRepository commentRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private NotificationService notificationService;

    @InjectMocks
    private ReplyService replyService;

    @Test
    @DisplayName("답글을 작성하면 저장하고 알림 생성을 요청한다")
    void creates_reply_and_requests_notification() {
        // given
        User postAuthor = createUser(1L, "author@test.com", "게시글작성자");
        User commentAuthor = createUser(2L, "comment@test.com", "댓글작성자");
        User actor = createUser(3L, "actor@test.com", "답글작성자");
        Post post = createPost(10L, postAuthor);
        Comment comment = createComment(20L, post, commentAuthor);
        ReplyCreateRequest request = createRequest("테스트 답글");

        given(userRepository.findById(actor.getId()))
                .willReturn(Optional.of(actor));

        given(commentRepository.findActiveByPostIdAndCommentId(
                post.getId(),
                comment.getId()
        )).willReturn(Optional.of(comment));

        given(replyRepository.save(any(Reply.class)))
                .willAnswer(invocation -> {
                    Reply reply = invocation.getArgument(0);
                    ReflectionTestUtils.setField(reply, "id", 30L);
                    return reply;
                });

        // when
        Long replyId = replyService.createReply(
                actor.getId(),
                post.getId(),
                comment.getId(),
                request
        );

        // then
        ArgumentCaptor<Reply> replyCaptor =
                ArgumentCaptor.forClass(Reply.class);

        verify(replyRepository).save(replyCaptor.capture());

        Reply savedReply = replyCaptor.getValue();

        verify(notificationService)
                .createReplyNotification(savedReply, actor);

        assertThat(replyId).isEqualTo(30L);
        assertThat(savedReply.getComment()).isEqualTo(comment);
        assertThat(savedReply.getUser()).isEqualTo(actor);
        assertThat(savedReply.getContent()).isEqualTo("테스트 답글");
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

    private Comment createComment(Long id, Post post, User author) {
        Comment comment = new Comment(post, author, "테스트 댓글");
        ReflectionTestUtils.setField(comment, "id", id);
        return comment;
    }

    private ReplyCreateRequest createRequest(String content) {
        ReplyCreateRequest request = new ReplyCreateRequest();
        ReflectionTestUtils.setField(request, "content", content);
        return request;
    }
}
