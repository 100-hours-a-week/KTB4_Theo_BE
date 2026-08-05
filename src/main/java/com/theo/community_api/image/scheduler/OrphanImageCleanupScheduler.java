package com.theo.community_api.image.scheduler;

import com.theo.community_api.image.service.OrphanImageCleanupService;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
@ConditionalOnProperty(
        prefix = "image.orphan-cleanup",
        name = "enabled",
        havingValue = "true"
)
public class OrphanImageCleanupScheduler {

    private final OrphanImageCleanupService cleanupService;

    @Scheduled(
            cron = "${image.orphan-cleanup.cron}",
            zone = "Asia/Seoul"
    )
    public void cleanupOrphanImages() {
        cleanupService.cleanup();
    }
}
