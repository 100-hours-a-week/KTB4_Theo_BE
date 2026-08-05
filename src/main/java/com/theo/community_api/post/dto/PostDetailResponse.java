package com.theo.community_api.post.dto;

import com.theo.community_api.post.domain.Post;
import com.theo.community_api.user.domain.User;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class PostDetailResponse { // 게시물 상세조회
    private Long postId;
    private String title;
    private String content;
    private String nickname;
    private String profileImageUrl;

    private int likeCount;
    private boolean liked;
    private int commentCount;
    private int viewCount;

    private boolean isEdited;
    private boolean isAuthorDeleted;
    private boolean isBlinded;
    private boolean isAuthor;

    private List<PostImageResponse> images;
    private List<PostCommentResponse> comments;


    public static PostDetailResponse from(
            Post post
            , User user
            , Long loginUserId
            , boolean liked
            , String profileImageUrl
            , List<PostImageResponse> images
            , List<PostCommentResponse> comments) {
        String nickname = "알 수 없음";
        String responseProfileImageUrl = null;
        boolean isAuthorDeleted = true;

        if(user != null && !user.isDeleted()){
            nickname = user.getNickname();
            responseProfileImageUrl = profileImageUrl;
            isAuthorDeleted = false;
        }

        boolean isAuthor = false;

        if(user != null && user.getId().equals(loginUserId)){ // 로그인한 사용자가 게시글 작성자인지 확인
            isAuthor = true;
        }

        return new PostDetailResponse(
                post.getId(),
                post.getTitle(),
                post.getContent(),
                nickname,
                responseProfileImageUrl,
                post.getLikeCount(),
                liked,
                post.getCommentCount(),
                post.getViewCount(),
                post.isEdited(),
                isAuthorDeleted,
                post.isBlinded(),
                isAuthor,
                images,
                comments
        );
    }
}
