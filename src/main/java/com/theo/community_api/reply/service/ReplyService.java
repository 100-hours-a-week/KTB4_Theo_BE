package com.theo.community_api.reply.service;

import com.theo.community_api.comment.domain.Comment;
import com.theo.community_api.comment.repository.CommentRepository;
import com.theo.community_api.common.exception.BusinessException;
import com.theo.community_api.common.exception.ErrorCode;
import com.theo.community_api.notification.service.NotificationService;
import com.theo.community_api.reply.domain.Reply;
import com.theo.community_api.reply.dto.ReplyCreateRequest;
import com.theo.community_api.reply.dto.ReplyUpdateRequest;
import com.theo.community_api.reply.repository.ReplyRepository;
import com.theo.community_api.user.domain.User;
import com.theo.community_api.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReplyService {
    private final ReplyRepository replyRepository;
    private final CommentRepository commentRepository;
    private final UserRepository userRepository;
    private final NotificationService notificationService;

    // 대댓글 작성
    @Transactional
    public Long createReply(Long loginUserId, Long postId, Long commentId, ReplyCreateRequest request) {
        // 게시물ID, 댓글ID, 대댓글ID 모두 일치하는지 확인
        User user = userRepository.findById(loginUserId)
                .orElseThrow(()-> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if(user.isDeleted()){
            throw new BusinessException(ErrorCode.UNAUTHORIZED_REQUEST);
        }

        Comment comment = commentRepository.findActiveByPostIdAndCommentId(postId, commentId)
                .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));

        if (comment.getPost().isBlinded()) {
            throw new BusinessException(ErrorCode.POST_BLINDED);
        }

        Reply reply = new Reply(comment, user, request.getContent());

        Reply savedReply = replyRepository.save(reply);

        notificationService.createReplyNotification(savedReply, user);

        return savedReply.getId();
    }

    // 대댓글 수정
    @Transactional
    public void updateReply(Long loginUserId, Long postId, Long commentId, Long replyId, ReplyUpdateRequest request) {
        Reply reply = replyRepository.findActiveByPostIdAndCommentIdAndReplyId(postId, commentId, replyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REPLY_NOT_FOUND));

        if (!reply.getUser().getId().equals(loginUserId)) {
            throw new BusinessException(ErrorCode.REPLY_MODIFY_FORBIDDEN);
        }

        reply.update(request.getContent());
    }

    // 대댓글 삭제
    @Transactional
    public void deleteReply(Long loginUserId, Long postId, Long commentId, Long replyId) {
        Reply reply = replyRepository.findActiveByPostIdAndCommentIdAndReplyId(postId, commentId, replyId)
                .orElseThrow(() -> new BusinessException(ErrorCode.REPLY_NOT_FOUND));

        if (!reply.getUser().getId().equals(loginUserId)) {
            throw new BusinessException(ErrorCode.REPLY_DELETE_FORBIDDEN);
        }

        reply.delete();
    }
}
