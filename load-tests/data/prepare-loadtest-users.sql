-- 미읽음 알림 폴링 한계 테스트용 사용자를 준비한다.
--
-- 기본 목표는 loadtest1@example.com부터 loadtest10000@example.com까지다.
-- 실행 전에 같은 MySQL 세션에서 @target_user_count를 지정하면 목표 수를 변경할 수 있다.
--
-- 기존 loadtest1@example.com의 BCrypt 비밀번호 해시를 모든 테스트 사용자에게 재사용한다.
-- 로그인 API는 부하 측정에 포함하지 않지만, 실제 users 스키마와 동일한 데이터를 유지하기 위함이다.
--
-- email과 nickname의 unique 제약을 기준으로 INSERT IGNORE를 사용하므로 재실행할 수 있다.

SET @target_user_count = COALESCE(@target_user_count, 10000);
SET SESSION cte_max_recursion_depth = 20000;

SET @loadtest_password_hash = COALESCE(
    @loadtest_password_hash,
    (
        SELECT password
        FROM users
        WHERE email = 'loadtest1@example.com'
          AND deleted_at IS NULL
        LIMIT 1
    )
);

-- 기준 계정이 없거나 비밀번호 해시가 null이면 여기서 실패시킨다.
CREATE TEMPORARY TABLE loadtest_password_guard (
    password_hash VARCHAR(255) NOT NULL
);

INSERT INTO loadtest_password_guard (password_hash)
VALUES (@loadtest_password_hash);

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

START TRANSACTION;

INSERT IGNORE INTO users (
    role,
    email,
    password,
    nickname,
    profile_image,
    deleted_at
)
SELECT
    'USER',
    CONCAT('loadtest', sequence.account_number, '@example.com'),
    password_guard.password_hash,
    CONCAT('load', LPAD(sequence.account_number, 5, '0')),
    NULL,
    NULL
FROM loadtest_account_sequence sequence
CROSS JOIN loadtest_password_guard password_guard;

SET @inserted_user_count = ROW_COUNT();

COMMIT;

SELECT
    @target_user_count AS target_user_count,
    @inserted_user_count AS inserted_user_count,
    COUNT(user.id) AS prepared_user_count
FROM loadtest_account_sequence sequence
LEFT JOIN users user
  ON user.email = CONCAT(
      'loadtest',
      sequence.account_number,
      '@example.com'
  )
 AND user.deleted_at IS NULL;

SELECT
    sequence.account_number AS missing_account_number
FROM loadtest_account_sequence sequence
LEFT JOIN users user
  ON user.email = CONCAT(
      'loadtest',
      sequence.account_number,
      '@example.com'
  )
 AND user.deleted_at IS NULL
WHERE user.id IS NULL
ORDER BY sequence.account_number
LIMIT 20;

DROP TEMPORARY TABLE loadtest_account_sequence;
DROP TEMPORARY TABLE loadtest_password_guard;
