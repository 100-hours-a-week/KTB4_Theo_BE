package com.theo.community_api.image.cleanup;

import com.theo.community_api.image.storage.StoredImageObject;

import java.time.Instant;
import java.util.List;

public record OrphanImageScanResult(
        Instant cutoff,
        int totalObjectCount,
        int referencedKeyCount,
        List<StoredImageObject> candidates
) {
}
