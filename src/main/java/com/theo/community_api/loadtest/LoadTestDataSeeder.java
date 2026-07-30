package com.theo.community_api.loadtest;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.ApplicationArguments;
import org.springframework.boot.ApplicationRunner;
import org.springframework.boot.SpringApplication;
import org.springframework.context.ConfigurableApplicationContext;
import org.springframework.context.annotation.Profile;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Slf4j
@Component
@Profile("loadtest-seed")
@RequiredArgsConstructor
public class LoadTestDataSeeder implements ApplicationRunner {

    private static final String LOAD_TEST_POST_TITLE = "[LOADTEST] 알림 부하 테스트";
    private static final String LOAD_TEST_POST_CONTENT =
            "미읽음 알림 개수 API 부하 테스트를 위한 게시글입니다.";

    private final JdbcTemplate jdbcTemplate;
    private final TransactionTemplate transactionTemplate;
    private final LoadTestSeedProperties properties;
    private final ConfigurableApplicationContext applicationContext;

    @Override
    public void run(ApplicationArguments args) {
        try {
            transactionTemplate.executeWithoutResult(status -> seed());
        } catch (RuntimeException exception) {
            log.error("부하 테스트 데이터 생성에 실패했습니다.", exception);
            throw exception;
        } finally {
            SpringApplication.exit(applicationContext);
        }
    }

    private void seed() {
        String accountPattern =
                properties.accountPrefix() + "%@" + properties.accountDomain();

        int actualUserCount = countLoadTestUsers(accountPattern);
        validateUserCount(actualUserCount);

        log.info(
                "부하 테스트 데이터 생성을 시작합니다. users={}, notificationsPerUser={}, unreadPerUser={}",
                actualUserCount,
                properties.notificationsPerUser(),
                properties.unreadNotificationsPerUser()
        );

        int insertedPosts = insertPosts(accountPattern);
        int insertedLikes = insertPostLikes(accountPattern);
        int insertedNotifications = insertNotifications(accountPattern);

        SeedResult result = readSeedResult(accountPattern);
        validateSeedResult(result);

        log.info(
                """
                부하 테스트 데이터 생성 완료
                - 새 게시글: {}
                - 새 좋아요: {}
                - 새 알림: {}
                - 테스트 게시글 합계: {}
                - 테스트 좋아요 합계: {}
                - 테스트 알림 합계: {}
                - 미읽음 알림 합계: {}
                """,
                insertedPosts,
                insertedLikes,
                insertedNotifications,
                result.postCount(),
                result.likeCount(),
                result.notificationCount(),
                result.unreadNotificationCount()
        );
    }

    private int countLoadTestUsers(String accountPattern) {
        Integer count = jdbcTemplate.queryForObject(
                """
                select count(*)
                from users
                where email like ?
                  and deleted_at is null
                """,
                Integer.class,
                accountPattern
        );

        return count == null ? 0 : count;
    }

    private void validateUserCount(int actualUserCount) {
        if (actualUserCount != properties.userCount()) {
            throw new IllegalStateException(
                    "부하 테스트 계정 수가 일치하지 않습니다. expected="
                            + properties.userCount()
                            + ", actual="
                            + actualUserCount
            );
        }
    }

    private int insertPosts(String accountPattern) {
        return jdbcTemplate.update(
                """
                insert into posts (
                    created_at,
                    comment_count,
                    content,
                    deleted_at,
                    is_blinded,
                    like_count,
                    reported_count,
                    title,
                    updated_at,
                    view_count,
                    user_id
                )
                select
                    current_timestamp(6),
                    0,
                    ?,
                    null,
                    false,
                    ?,
                    0,
                    ?,
                    null,
                    0,
                    receiver.id
                from users receiver
                where receiver.email like ?
                  and receiver.deleted_at is null
                  and not exists (
                      select 1
                      from posts existing_post
                      where existing_post.user_id = receiver.id
                        and existing_post.title = ?
                  )
                """,
                LOAD_TEST_POST_CONTENT,
                properties.notificationsPerUser(),
                LOAD_TEST_POST_TITLE,
                accountPattern,
                LOAD_TEST_POST_TITLE
        );
    }

    private int insertPostLikes(String accountPattern) {
        return jdbcTemplate.update(
                """
                insert ignore into post_like (
                    created_at,
                    post_id,
                    user_id
                )
                select
                    current_timestamp(6),
                    ranked.post_id,
                    ranked.actor_id
                from (
                    select
                        post.id as post_id,
                        actor.id as actor_id,
                        row_number() over (
                            partition by post.id
                            order by actor.id
                        ) as actor_order
                    from posts post
                    join users receiver
                      on receiver.id = post.user_id
                    cross join users actor
                    where post.title = ?
                      and receiver.email like ?
                      and receiver.deleted_at is null
                      and actor.email like ?
                      and actor.deleted_at is null
                      and actor.id <> receiver.id
                ) ranked
                where ranked.actor_order <= ?
                """,
                LOAD_TEST_POST_TITLE,
                accountPattern,
                accountPattern,
                properties.notificationsPerUser()
        );
    }

    private int insertNotifications(String accountPattern) {
        return jdbcTemplate.update(
                """
                insert ignore into notifications (
                    created_at,
                    read_at,
                    source_id,
                    source_type,
                    type,
                    actor_id,
                    comment_id,
                    post_id,
                    receiver_id
                )
                select
                    current_timestamp(6),
                    case
                        when ranked.notification_order <= ? then null
                        else current_timestamp(6)
                    end,
                    ranked.post_id,
                    'POST',
                    'LIKE',
                    ranked.actor_id,
                    null,
                    ranked.post_id,
                    ranked.receiver_id
                from (
                    select
                        post_like.post_id,
                        post_like.user_id as actor_id,
                        post.user_id as receiver_id,
                        row_number() over (
                            partition by post_like.post_id
                            order by post_like.user_id
                        ) as notification_order
                    from post_like
                    join posts post
                      on post.id = post_like.post_id
                    join users receiver
                      on receiver.id = post.user_id
                    where post.title = ?
                      and receiver.email like ?
                      and receiver.deleted_at is null
                ) ranked
                where ranked.notification_order <= ?
                """,
                properties.unreadNotificationsPerUser(),
                LOAD_TEST_POST_TITLE,
                accountPattern,
                properties.notificationsPerUser()
        );
    }

    private SeedResult readSeedResult(String accountPattern) {
        Long postCount = jdbcTemplate.queryForObject(
                """
                select count(*)
                from posts post
                join users receiver
                  on receiver.id = post.user_id
                where post.title = ?
                  and receiver.email like ?
                  and receiver.deleted_at is null
                """,
                Long.class,
                LOAD_TEST_POST_TITLE,
                accountPattern
        );

        Long likeCount = jdbcTemplate.queryForObject(
                """
                select count(*)
                from post_like
                join posts post
                  on post.id = post_like.post_id
                join users receiver
                  on receiver.id = post.user_id
                where post.title = ?
                  and receiver.email like ?
                  and receiver.deleted_at is null
                """,
                Long.class,
                LOAD_TEST_POST_TITLE,
                accountPattern
        );

        return jdbcTemplate.queryForObject(
                """
                select
                    count(*) as notification_count,
                    coalesce(sum(notification.read_at is null), 0) as unread_count
                from notifications notification
                join posts post
                  on post.id = notification.post_id
                join users receiver
                  on receiver.id = notification.receiver_id
                where post.title = ?
                  and receiver.email like ?
                  and receiver.deleted_at is null
                """,
                (resultSet, rowNumber) -> new SeedResult(
                        postCount == null ? 0 : postCount,
                        likeCount == null ? 0 : likeCount,
                        resultSet.getLong("notification_count"),
                        resultSet.getLong("unread_count")
                ),
                LOAD_TEST_POST_TITLE,
                accountPattern
        );
    }

    private void validateSeedResult(SeedResult result) {
        long expectedPostCount = properties.userCount();
        long expectedLikeCount =
                (long) properties.userCount() * properties.notificationsPerUser();
        long expectedNotificationCount = expectedLikeCount;
        long expectedUnreadCount =
                (long) properties.userCount()
                        * properties.unreadNotificationsPerUser();

        if (result.postCount() != expectedPostCount
                || result.likeCount() != expectedLikeCount
                || result.notificationCount() != expectedNotificationCount
                || result.unreadNotificationCount() != expectedUnreadCount) {
            throw new IllegalStateException(
                    """
                    생성된 부하 테스트 데이터 수가 예상과 다릅니다.
                    posts: expected=%d, actual=%d
                    likes: expected=%d, actual=%d
                    notifications: expected=%d, actual=%d
                    unreadNotifications: expected=%d, actual=%d
                    """.formatted(
                            expectedPostCount,
                            result.postCount(),
                            expectedLikeCount,
                            result.likeCount(),
                            expectedNotificationCount,
                            result.notificationCount(),
                            expectedUnreadCount,
                            result.unreadNotificationCount()
                    )
            );
        }
    }

    private record SeedResult(
            long postCount,
            long likeCount,
            long notificationCount,
            long unreadNotificationCount
    ) {
    }
}
