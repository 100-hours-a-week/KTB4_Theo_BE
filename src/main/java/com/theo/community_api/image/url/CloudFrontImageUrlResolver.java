package com.theo.community_api.image.url;

import com.theo.community_api.common.config.CloudFrontProperties;
import com.theo.community_api.image.validation.ImageKeyValidator;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class CloudFrontImageUrlResolver
        implements ImageUrlResolver {

    private final CloudFrontProperties cloudFrontProperties;
    private final ImageKeyValidator imageKeyValidator;

    @Override
    public String resolve(String imageKey) {
        if (imageKey == null || imageKey.isBlank()) {
            return null;
        }

        imageKeyValidator.validateEnvironment(imageKey);

        String baseUrl = removeTrailingSlash(cloudFrontProperties.baseUrl());

        String normalizedImageKey =
                removeLeadingSlash(imageKey);

        return baseUrl + "/" + normalizedImageKey;
    }

    private String removeTrailingSlash(String value) {
        if (value.endsWith("/")) {
            return value.substring(0, value.length() - 1);
        }

        return value;
    }

    private String removeLeadingSlash(String value) {
        if (value.startsWith("/")) {
            return value.substring(1);
        }

        return value;
    }
}
