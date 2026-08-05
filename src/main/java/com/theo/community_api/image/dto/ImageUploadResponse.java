package com.theo.community_api.image.dto;

import java.util.List;

public record ImageUploadResponse(
        List<String> imageKeys
) {
}
