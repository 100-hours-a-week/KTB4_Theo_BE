package com.theo.community_api.comment.service;

import com.theo.community_api.comment.domain.Comment;
import com.theo.community_api.comment.dto.CommentCreateRequest;
import com.theo.community_api.comment.dto.CommentUpdateRequest;
import com.theo.community_api.comment.repository.CommentRepository;
import com.theo.community_api.common.exception.BusinessException;
import com.theo.community_api.common.exception.ErrorCode;
import com.theo.community_api.notification.service.NotificationService;
import com.theo.community_api.post.domain.Post;
import com.theo.community_api.post.repository.PostRepository;
import com.theo.community_api.reply.repository.ReplyRepository;
import com.theo.community_api.user.domain.User;
import com.theo.community_api.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class CommentService {

    private final CommentRepository commentRepository;
    private final PostRepository postRepository;
    private final UserRepository userRepository;
    private final ReplyRepository replyRepository;
    private final NotificationService notificationService;

    // 댓글 작성
    @Transactional
    public Long createComment(Long loginUserId, Long postId, CommentCreateRequest request) {
        User user = userRepository.findById(loginUserId)
                .orElseThrow(()->new BusinessException(ErrorCode.USER_NOT_FOUND));

        if(user.isDeleted()){
            throw new BusinessException(ErrorCode.UNAUTHORIZED_REQUEST);
        }

        Post post = postRepository.findByIdWithUser(postId)
                .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));

        if(post.isBlinded()){
            throw new BusinessException(ErrorCode.POST_BLINDED);
        }

        Comment comment = new Comment(post, user, request.getContent());

        Comment savedComment = commentRepository.save(comment);

        post.increaseCommentCount();

        notificationService.createCommentNotification(savedComment, user);

        return savedComment.getId();
    }

    // 댓글 수정
    @Transactional
    public void updateComment(Long loginUserId, Long postId, Long commentId, CommentUpdateRequest request) {
        // 게시물ID와 댓글ID가 제대로 매핑되어있는지 확인
        Comment comment = commentRepository.findActiveByPostIdAndCommentId(postId, commentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));

        // 댓글 작성자가 아닌 작성자가 수정시도하는 경우
        if (!comment.getUser().getId().equals(loginUserId)) {
            throw new BusinessException(ErrorCode.COMMENT_UPDATE_FORBIDDEN);
        }

        comment.update(request.getContent());
    }

    // 댓글 삭제
    @Transactional
    public void deleteComment(Long loginUserId, Long postId, Long commentId) {
        Comment comment = commentRepository.findActiveByPostIdAndCommentId(postId, commentId)
                .orElseThrow(()-> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));

        if (!comment.getUser().getId().equals(loginUserId)) {
            throw new BusinessException(ErrorCode.COMMENT_DELETE_FORBIDDEN);
        }

        Post post = comment.getPost();

//        // 대댓글이 존재하는 댓글인지 확인
//        boolean hasReplies = replyRepository.existsActiveByCommentId(commentId);
//
//        if (hasReplies) {
//            comment.delete();
//            return;
//        }

        comment.delete();
        post.decreaseCommentCount();
    }
}
