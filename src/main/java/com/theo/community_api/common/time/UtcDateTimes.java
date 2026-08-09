package com.theo.community_api.common.time;

import java.time.Clock;
import java.time.LocalDateTime;

public final class UtcDateTimes {

    private static final Clock UTC_CLOCK = Clock.systemUTC();

    private UtcDateTimes() {
    }

    public static LocalDateTime now() {
        return LocalDateTime.now(UTC_CLOCK);
    }
}
