package com.theo.community_api.post.dto;

import com.theo.community_api.reply.domain.Reply;
import com.theo.community_api.user.domain.User;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class PostReplyResponse {
    private Long replyId;
    private String nickname;
    private String profileImageUrl;
    private String replyContent;
    private boolean isAuthorDeleted;
    private boolean isReplyDeleted;
    private boolean isAuthor;
    private LocalDateTime createdAt;

    public static PostReplyResponse from(
            Reply reply,
            User user,
            Long loginUserId,
            String profileImageUrl
    ) {
        String nickname = "알 수 없음";
        String responseProfileImageUrl = null;
        boolean isAuthorDeleted = true;

        if (user != null && !user.isDeleted()) {
            nickname = user.getNickname();
            responseProfileImageUrl = profileImageUrl;
            isAuthorDeleted = false;
        }

        boolean isAuthor = false;

        if (user != null && user.getId().equals(loginUserId)) {
            isAuthor = true;
        }

        return new PostReplyResponse(
                reply.getId(),
                nickname,
                responseProfileImageUrl,
                reply.getContent(),
                isAuthorDeleted,
                reply.isDeleted(),
                isAuthor,
                reply.getCreatedAt()
        );
    }
}
