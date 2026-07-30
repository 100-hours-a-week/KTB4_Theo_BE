package com.theo.community_api.loadtest;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Profile;

@Profile("loadtest-seed")
@ConfigurationProperties(prefix = "loadtest.seed")
public record LoadTestSeedProperties(
        int userCount,
        int notificationsPerUser,
        int unreadNotificationsPerUser,
        String accountPrefix,
        String accountDomain
) {
    public LoadTestSeedProperties {
        if (userCount < 2) {
            throw new IllegalArgumentException("loadtest.seed.user-count는 2 이상이어야 합니다.");
        }

        if (notificationsPerUser < 1 || notificationsPerUser >= userCount) {
            throw new IllegalArgumentException(
                    "loadtest.seed.notifications-per-user는 1 이상이고 user-count보다 작아야 합니다."
            );
        }

        if (unreadNotificationsPerUser < 0
                || unreadNotificationsPerUser > notificationsPerUser) {
            throw new IllegalArgumentException(
                    "loadtest.seed.unread-notifications-per-user는 0 이상이고 notifications-per-user 이하여야 합니다."
            );
        }

        if (accountPrefix == null || accountPrefix.isBlank()) {
            throw new IllegalArgumentException("loadtest.seed.account-prefix가 필요합니다.");
        }

        if (accountDomain == null || accountDomain.isBlank()) {
            throw new IllegalArgumentException("loadtest.seed.account-domain이 필요합니다.");
        }
    }
}
