package com.theo.community_api.common.config;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.validation.annotation.Validated;

import java.time.Duration;

@Validated
@ConfigurationProperties(prefix = "image.orphan-cleanup")
public record OrphanImageCleanupProperties(
        boolean enabled,
        boolean dryRun,
        @NotNull Duration gracePeriod,
        @NotBlank String cron
) {
}
