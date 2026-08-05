package com.theo.community_api.post.dto;

import com.theo.community_api.common.ValidationConst;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class PostUpdateRequest {
    @NotBlank(message = "blank_title")
    @Size(max = ValidationConst.POST_TITLE_MAX_LENGTH, message = "invalid_post_title")
    private String title;

    @NotBlank(message = "blank_content")
    private String content;

    private List<Long> retainedImageIds;
}
