package com.theo.community_api.post.dto;

import com.theo.community_api.comment.domain.Comment;
import com.theo.community_api.user.domain.User;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@AllArgsConstructor
public class PostCommentResponse {
    private Long commentId;
    private String nickname;
    private String profileImageUrl;
    private String commentContent;
    private boolean isAuthorDeleted;
    private boolean isCommentDeleted;
    private boolean isAuthor;
    private LocalDateTime createdAt;
    private List<PostReplyResponse> replies;

    public static PostCommentResponse from(
            Comment comment,
            User user,
            Long loginUserId,
            String profileImageUrl,
            List<PostReplyResponse> replies
    ) {
        String nickname = "알 수 없음";
        String responseProfileImageUrl = null;
        boolean isAuthorDeleted = true;

        if (user != null && !user.isDeleted()) {
            nickname = user.getNickname();
            responseProfileImageUrl = profileImageUrl;
            isAuthorDeleted = false;
        }

        String content = comment.getContent();

        if (comment.isDeleted()) {
            content = "삭제된 댓글입니다.";
        }

        boolean isAuthor = false;

        if (user != null && user.getId().equals(loginUserId)) {
            isAuthor = true;
        }

        return new PostCommentResponse(
                comment.getId(),
                nickname,
                responseProfileImageUrl,
                content,
                isAuthorDeleted,
                comment.isDeleted(),
                isAuthor,
                comment.getCreatedAt(),
                replies
        );
    }
}
