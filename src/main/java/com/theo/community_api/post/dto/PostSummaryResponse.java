package com.theo.community_api.post.dto;

import com.theo.community_api.post.domain.Post;
import com.theo.community_api.user.domain.User;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@AllArgsConstructor
public class PostSummaryResponse { // 게시물 목록 조회에서 하나의 게시물의 DTO
    private Long postId;
    private String title;
    private String nickname;
    private String profileImageUrl;

    private int likeCount;
    private int commentCount;
    private int viewCount;

    private boolean isEdited;
    private boolean isAuthorDeleted;
    private boolean isBlinded;

    private LocalDateTime createdAt;

    public static PostSummaryResponse from(
            Post post,
            User user,
            String profileImageUrl
    ) {
        String title = post.isBlinded() ? "숨김 처리된 게시글" : post.getTitle();
        String nickname = "알 수 없음";
        String responseProfileImageUrl = null;
        boolean isAuthorDeleted = true;

        if (user != null && !user.isDeleted()) {
            nickname = user.getNickname();
            responseProfileImageUrl = profileImageUrl;
            isAuthorDeleted = false;
        }

        return new PostSummaryResponse(
                post.getId(),
                title,
                nickname,
                responseProfileImageUrl,
                post.getLikeCount(),
                post.getCommentCount(),
                post.getViewCount(),
                post.isEdited(),
                isAuthorDeleted,
                post.isBlinded(),
                post.getCreatedAt()
        );
    }
}
