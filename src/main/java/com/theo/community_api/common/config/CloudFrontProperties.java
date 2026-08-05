package com.theo.community_api.common.config;

import jakarta.validation.constraints.NotBlank;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

@Validated
@ConfigurationProperties(prefix = "image.cloud-front")
public record CloudFrontProperties (
        @NotBlank String baseUrl
) {
}
