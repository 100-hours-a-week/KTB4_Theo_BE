package com.theo.community_api.image.service;

import com.theo.community_api.common.config.OrphanImageCleanupProperties;
import com.theo.community_api.image.cleanup.OrphanImageScanResult;
import com.theo.community_api.image.cleanup.ReferencedImageKeyReader;
import com.theo.community_api.image.storage.ImageStorage;
import com.theo.community_api.image.storage.StoredImageObject;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Set;

@Slf4j
@Service
@RequiredArgsConstructor
public class OrphanImageCleanupService {

    private final ImageStorage imageStorage;
    private final ReferencedImageKeyReader referencedImageKeyReader;
    private final OrphanImageCleanupProperties properties;

    public OrphanImageScanResult cleanup() {
        if (properties.dryRun()) {
            return dryRun();
        }

        OrphanImageScanResult result = scan();
        int deletedCount = 0;
        int failedCount = 0;

        for (StoredImageObject candidate : result.candidates()) {
            try {
                imageStorage.delete(candidate.key());
                deletedCount++;

                log.info(
                        "[ORPHAN IMAGE CLEANUP] deleted. key={}",
                        candidate.key()
                );
            } catch (RuntimeException exception) {
                failedCount++;

                log.error(
                        "[ORPHAN IMAGE CLEANUP] delete failed. key={}",
                        candidate.key(),
                        exception
                );
            }
        }

        log.info(
                "[ORPHAN IMAGE CLEANUP] completed. "
                        + "cutoff={}, candidates={}, deleted={}, failed={}",
                result.cutoff(),
                result.candidates().size(),
                deletedCount,
                failedCount
        );

        return result;
    }

    public OrphanImageScanResult dryRun() {
        OrphanImageScanResult result = scan();

        log.info(
                "[ORPHAN IMAGE DRY-RUN] scan completed. "
                        + "cutoff={}, totalObjects={}, referencedKeys={}, candidates={}",
                result.cutoff(),
                result.totalObjectCount(),
                result.referencedKeyCount(),
                result.candidates().size()
        );

        for (StoredImageObject candidate : result.candidates()) {
            log.info(
                    "[ORPHAN IMAGE DRY-RUN] candidate. "
                            + "key={}, lastModified={}, size={}",
                    candidate.key(),
                    candidate.lastModified(),
                    candidate.size()
            );
        }

        return result;
    }

    public OrphanImageScanResult scan() {
        Instant cutoff = Instant.now()
                .minus(properties.gracePeriod());

        List<StoredImageObject> storedObjects =
                imageStorage.listObjects();

        Set<String> referencedKeys =
                referencedImageKeyReader.readAll();

        List<StoredImageObject> candidates = storedObjects.stream()
                .filter(object -> isOlderThanGracePeriod(object, cutoff))
                .filter(object -> !referencedKeys.contains(object.key()))
                .sorted(
                        Comparator.comparing(StoredImageObject::lastModified)
                                .thenComparing(StoredImageObject::key)
                )
                .toList();

        return new OrphanImageScanResult(
                cutoff,
                storedObjects.size(),
                referencedKeys.size(),
                candidates
        );
    }

    private boolean isOlderThanGracePeriod(
            StoredImageObject object,
            Instant cutoff
    ) {
        return object.lastModified() != null
                && !object.lastModified().isAfter(cutoff);
    }
}
