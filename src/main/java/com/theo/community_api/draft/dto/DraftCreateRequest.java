package com.theo.community_api.draft.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class DraftCreateRequest {
    private String title;
    private String content;
}
