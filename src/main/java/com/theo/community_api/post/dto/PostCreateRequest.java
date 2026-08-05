package com.theo.community_api.post.dto;

import com.theo.community_api.common.ValidationConst;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

@NoArgsConstructor
@Getter
public class PostCreateRequest {

    @NotBlank(message = "blank_title")
    @Size(max = ValidationConst.POST_TITLE_MAX_LENGTH, message = "invalid_post_title")
    private String title;

    @NotBlank(message = "blank_content")
    private String content;
}
