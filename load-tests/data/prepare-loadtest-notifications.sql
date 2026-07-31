-- 미읽음 알림 개수 폴링 한계 테스트용 데이터를 준비한다.
--
-- 기본값:
--   대상 사용자: loadtest1@example.com ~ loadtest10000@example.com
--   사용자별 미읽음 좋아요 알림: 20건
--
-- 사용자 전체를 서로 cross join하지 않는다.
-- 각 수신자에게 필요한 actor만 원형으로 매핑하므로 처리할 후보 행 수는
-- target_user_count * unread_notifications_per_user에 비례한다.
--
-- 재실행 시 게시글, 좋아요, 알림을 중복 생성하지 않는다.
-- actor의 원형 매핑이 사용자 수에 의존하므로 이 파일은 아래의 고정된
-- 10,000명 * 20건 조건으로 실행한다.

SET @target_user_count = 10000;
SET @unread_notifications_per_user = 20;
SET @loadtest_post_title = '[POLL-LIMIT] unread seed';
SET @loadtest_post_content =
    '미읽음 알림 폴링 한계 테스트를 위한 게시글입니다.';
SET SESSION cte_max_recursion_depth = 20000;

CREATE TEMPORARY TABLE loadtest_account_sequence (
    account_number INT NOT NULL PRIMARY KEY
);

INSERT INTO loadtest_account_sequence (account_number)
WITH RECURSIVE sequence_generator (account_number) AS (
    SELECT 1

    UNION ALL

    SELECT account_number + 1
    FROM sequence_generator
    WHERE account_number < @target_user_count
)
SELECT account_number
FROM sequence_generator;

CREATE TEMPORARY TABLE loadtest_target_users (
    account_number INT NOT NULL PRIMARY KEY,
    user_id BIGINT NOT NULL UNIQUE
);

INSERT INTO loadtest_target_users (
    account_number,
    user_id
)
SELECT
    sequence.account_number,
    user.id
FROM loadtest_account_sequence sequence
JOIN users user
  ON user.email = CONCAT(
      'loadtest',
      sequence.account_number,
      '@example.com'
  )
 AND user.deleted_at IS NULL;

SET @prepared_user_count = (
    SELECT COUNT(*)
    FROM loadtest_target_users
);

-- 계정이 부족하거나 알림 수가 유효하지 않으면 NOT NULL 제약으로 실패시킨다.
CREATE TEMPORARY TABLE loadtest_notification_seed_guard (
    validated_user_count INT NOT NULL
);

INSERT INTO loadtest_notification_seed_guard (validated_user_count)
SELECT
    CASE
        WHEN @prepared_user_count = @target_user_count
         AND @unread_notifications_per_user >= 1
         AND @unread_notifications_per_user < @target_user_count
        THEN @prepared_user_count
        ELSE NULL
    END;

CREATE TEMPORARY TABLE loadtest_notification_sequence (
    notification_number INT NOT NULL PRIMARY KEY
);

INSERT INTO loadtest_notification_sequence (notification_number)
WITH RECURSIVE sequence_generator (notification_number) AS (
    SELECT 1

    UNION ALL

    SELECT notification_number + 1
    FROM sequence_generator
    WHERE notification_number < @unread_notifications_per_user
)
SELECT notification_number
FROM sequence_generator;

START TRANSACTION;

INSERT INTO posts (
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
SELECT
    CURRENT_TIMESTAMP(6),
    0,
    @loadtest_post_content,
    NULL,
    FALSE,
    0,
    0,
    @loadtest_post_title,
    NULL,
    0,
    receiver.user_id
FROM loadtest_target_users receiver
WHERE NOT EXISTS (
    SELECT 1
    FROM posts existing_post
    WHERE existing_post.user_id = receiver.user_id
      AND existing_post.title = @loadtest_post_title
);

SET @inserted_post_count = ROW_COUNT();

CREATE TEMPORARY TABLE loadtest_target_posts (
    account_number INT NOT NULL PRIMARY KEY,
    receiver_id BIGINT NOT NULL UNIQUE,
    post_id BIGINT NOT NULL UNIQUE
);

INSERT INTO loadtest_target_posts (
    account_number,
    receiver_id,
    post_id
)
SELECT
    receiver.account_number,
    receiver.user_id,
    post.id
FROM loadtest_target_users receiver
JOIN posts post
  ON post.user_id = receiver.user_id
 AND post.title = @loadtest_post_title
 AND post.deleted_at IS NULL;

INSERT IGNORE INTO post_like (
    created_at,
    post_id,
    user_id
)
SELECT
    CURRENT_TIMESTAMP(6),
    receiver_post.post_id,
    actor.user_id
FROM loadtest_target_posts receiver_post
CROSS JOIN loadtest_notification_sequence notification
JOIN loadtest_target_users actor
  ON actor.account_number =
     MOD(
         receiver_post.account_number - 1
         + notification.notification_number,
         @target_user_count
     ) + 1;

SET @inserted_like_count = ROW_COUNT();

INSERT IGNORE INTO notifications (
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
SELECT
    CURRENT_TIMESTAMP(6),
    NULL,
    receiver_post.post_id,
    'POST',
    'LIKE',
    actor.user_id,
    NULL,
    receiver_post.post_id,
    receiver_post.receiver_id
FROM loadtest_target_posts receiver_post
CROSS JOIN loadtest_notification_sequence notification
JOIN loadtest_target_users actor
  ON actor.account_number =
     MOD(
         receiver_post.account_number - 1
         + notification.notification_number,
         @target_user_count
     ) + 1;

SET @inserted_notification_count = ROW_COUNT();

UPDATE posts post
JOIN (
    SELECT
        post_like.post_id,
        COUNT(*) AS like_count
    FROM post_like
    JOIN loadtest_target_posts target_post
      ON target_post.post_id = post_like.post_id
    GROUP BY post_like.post_id
) counted_like
  ON counted_like.post_id = post.id
SET post.like_count = counted_like.like_count;

COMMIT;

SET @prepared_post_count = (
    SELECT COUNT(*)
    FROM loadtest_target_posts
);

SET @prepared_like_count = (
    SELECT COUNT(*)
    FROM post_like
    JOIN loadtest_target_posts target_post
      ON target_post.post_id = post_like.post_id
);

SET @prepared_unread_notification_count = (
    SELECT COUNT(*)
    FROM notifications notification
    JOIN loadtest_target_posts target_post
      ON target_post.post_id = notification.post_id
     AND target_post.receiver_id = notification.receiver_id
    WHERE notification.type = 'LIKE'
      AND notification.source_type = 'POST'
      AND notification.read_at IS NULL
);

SELECT
    @target_user_count AS target_user_count,
    @unread_notifications_per_user AS unread_per_user,
    @inserted_post_count AS inserted_post_count,
    @inserted_like_count AS inserted_like_count,
    @inserted_notification_count AS inserted_notification_count,
    @prepared_post_count AS prepared_post_count,
    @prepared_like_count AS prepared_like_count,
    @prepared_unread_notification_count
        AS prepared_unread_notification_count;

DROP TEMPORARY TABLE loadtest_target_posts;
DROP TEMPORARY TABLE loadtest_notification_sequence;
DROP TEMPORARY TABLE loadtest_notification_seed_guard;
DROP TEMPORARY TABLE loadtest_target_users;
DROP TEMPORARY TABLE loadtest_account_sequence;
