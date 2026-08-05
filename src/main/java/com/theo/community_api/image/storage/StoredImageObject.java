package com.theo.community_api.image.storage;

import java.time.Instant;

// 저장된 이미지 객체에서 꺼낼 필드
public record StoredImageObject(
        String key,
        Instant lastModified,
        long size
) {
}
