package com.theo.community_api.post.dto;

import com.theo.community_api.post.domain.PostImage;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class PostImageResponse {
    private Long imageId;
    private String imageUrl;
    private Integer imageOrder;

    public static PostImageResponse from(PostImage postImage, String imageUrl) {
        return new PostImageResponse(
                postImage.getId(),
                imageUrl,
                postImage.getImageOrder()
        );
    }
}
