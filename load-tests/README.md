# 알림 부하 테스트

미읽음 알림 개수 폴링의 요청량과 애플리케이션 자원 사용량을 측정하기 위한 파일을 역할별로 관리한다.

## 디렉터리 역할

```text
load-tests/
├── data/
│   ├── prepare-loadtest-users.sql
│   └── prepare-loadtest-notifications.sql
├── k6/
│   ├── prepare-users.js
│   ├── unread-count-polling.js
│   ├── unread-count-polling-limit.js
│   └── results/                 # 반복 실행 결과, Git 제외
├── monitoring/
│   ├── collect-actuator.sh
│   └── results/                 # 반복 수집 결과, Git 제외
├── token/
│   └── generate-loadtest-tokens.mjs
└── reports/
    ├── polling-load-test.md     # 최종 실험 조건과 분석
    └── data/                    # 보고서에 사용한 확정 원본, Git 포함
```

- `k6/results/`: 스모크 테스트와 재실행 결과를 저장하는 작업 공간이다.
- `monitoring/results/`: Actuator 수집기가 CSV를 저장하는 작업 공간이다.
- `reports/data/`: 최종 분석에 실제로 사용한 JSON과 CSV만 선별해 보존한다.
- `reports/polling-load-test.md`: 가설, 환경, 측정값, 해석과 한계를 기록한다.
- `data/prepare-loadtest-users.sql`: 고유 JWT 기반 한계 테스트에서 사용할 계정을 DB에 직접 준비한다.
- `data/prepare-loadtest-notifications.sql`: 각 테스트 사용자에게 게시글과 미읽음 좋아요 알림을 선형 복잡도로 준비한다.
- `token/generate-loadtest-tokens.mjs`: DB의 테스트 사용자 ID로 애플리케이션과 같은 형식의 JWT를 사전 발급한다.

`unread-count-polling.js`는 VU마다 별도 계정으로 로그인하는 사용자 시나리오용이다. `unread-count-polling-limit.js`는 사전 발급한 사용자별 JWT를 `SharedArray`로 읽어 로그인 발급 비용을 제외하고 다중 사용자의 폴링 API 처리 한계를 측정한다.

반복 실행 결과를 새로 생성해도 `reports/data/`의 확정 데이터는 자동으로 변경되지 않는다. 새로운 결과를 최종 결과로 채택할 때만 해당 파일을 선별해 복사하고 보고서를 함께 갱신한다.

테스트 준비와 실행 방법은 [k6 실행 설명서](k6/README.md), 확정 결과는 [폴링 부하 테스트 보고서](reports/polling-load-test.md)를 참고한다.

## 대규모 테스트 사용자 준비

기존 `loadtest1@example.com` 계정의 BCrypt 비밀번호 해시를 재사용해 최대 10,000개의 테스트 사용자를 준비한다.

```bash
mysql -u root -p 데이터베이스명 \
  < load-tests/data/prepare-loadtest-users.sql
```

기본 목표는 10,000명이다. 더 적은 수를 준비하려면 동일한 MySQL 세션에서 `@target_user_count`를 먼저 설정하고 SQL 파일을 실행한다.

```bash
mysql -u root -p 데이터베이스명 \
  --execute="
    SET @target_user_count = 2000;
    SOURCE load-tests/data/prepare-loadtest-users.sql;
  "
```

SQL은 `INSERT IGNORE`를 사용하므로 다시 실행해도 기존 계정을 중복 생성하지 않는다. 결과의 `prepared_user_count`가 `target_user_count`와 같고, `missing_account_number` 조회 결과가 비어 있어야 한다.

## 대규모 테스트 알림 준비

사용자 준비가 끝난 다음 사용자별 게시글 1건과 미읽음 좋아요 알림 20건을 준비한다.

```bash
mysql -u root -p 데이터베이스명 \
  < load-tests/data/prepare-loadtest-notifications.sql
```

기본 10,000명 조건의 예상 결과:

```text
prepared_post_count: 10,000
prepared_like_count: 200,000
prepared_unread_notification_count: 200,000
```

SQL은 각 수신자에게 필요한 actor만 원형으로 매핑한다. 사용자 전체 조합을 만들지 않으므로 기본 조건에서 처리할 핵심 행 수는 `10,000 × 20 = 200,000`에 비례한다. 다시 실행하면 existing post, `post_like` unique 제약과 알림 unique 제약을 이용해 중복 생성을 피한다.

actor 매핑은 전체 사용자 수를 기준으로 결정되므로 이 SQL은 `10,000명 × 20건`의 고정 조건으로 실행한다. 2,000 VU와 5,000 VU 테스트에서는 준비된 10,000명 중 앞의 사용자와 JWT만 사용한다.
