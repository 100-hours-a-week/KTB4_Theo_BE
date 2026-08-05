package com.theo.community_api.draft.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class DraftUpdateRequest {
    private String title;
    private String content;
    private List<Long> retainedImageIds;
}
