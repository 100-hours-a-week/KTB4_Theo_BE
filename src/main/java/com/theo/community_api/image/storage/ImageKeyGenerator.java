package com.theo.community_api.image.storage;

import com.theo.community_api.common.config.S3Properties;
import com.theo.community_api.image.domain.ImageCategory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class ImageKeyGenerator {

    private final S3Properties properties;

    public String generate(
            ImageCategory category,
            Long userId,
            String extension
    ) {
        return "%s/%s/%d/%s.%s".formatted(
                properties.prefix(),
                category.directory(),
                userId,
                UUID.randomUUID(),
                extension
        );
    }
}