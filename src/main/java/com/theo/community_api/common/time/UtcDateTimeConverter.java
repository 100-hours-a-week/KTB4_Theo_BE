package com.theo.community_api.common.time;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

public final class UtcDateTimeConverter {

    private UtcDateTimeConverter() {
    }

    public static Instant toInstant(LocalDateTime dateTime) {
        if (dateTime == null) {
            return null;
        }

        return dateTime.toInstant(ZoneOffset.UTC);
    }
}
