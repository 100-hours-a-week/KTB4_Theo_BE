# 미읽음 알림 폴링 부하 테스트

## 구성

- `prepare-users.js`: 부하 테스트용 회원을 미리 생성한다.
- `unread-count-polling.js`: 각 가상 사용자가 자기 계정으로 로그인한 뒤 미읽음 알림 개수를 주기적으로 조회한다.
- `unread-count-polling-limit.js`: 사전 발급한 사용자별 JWT를 `SharedArray`로 읽고 로그인 없이 다중 사용자의 폴링 API 처리 한계를 측정한다.
- `results/`: 반복 실행하는 k6 결과 파일을 저장한다. Git에는 포함하지 않는다.
- `../reports/data/`: 최종 분석에 사용한 결과만 선별해 보존한다. Git에 포함한다.
- `../reports/polling-load-test.md`: 확정 결과와 분석을 기록한다.

계정은 아래 규칙으로 생성된다.

```text
이메일: loadtest1@example.com ~ loadtest500@example.com
닉네임: load00001 ~ load00500
```

비밀번호는 파일에 저장하지 않고 `LOAD_TEST_PASSWORD` 환경변수로 전달한다.

## 1. 사전 준비

MySQL과 Spring Boot를 실행한다.

```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

별도 터미널에서 비밀번호를 현재 셸에만 설정한다. 비밀번호는 백엔드의 유효성 조건을 만족해야 한다.

```bash
export LOAD_TEST_PASSWORD='테스트용 비밀번호'
```

결과 디렉터리를 만든다.

```bash
mkdir -p load-tests/k6/results
```

## 2. 테스트 계정 500개 준비

```bash
k6 run \
  -e USER_COUNT=500 \
  -e LOAD_TEST_PASSWORD="$LOAD_TEST_PASSWORD" \
  load-tests/k6/prepare-users.js
```

이미 동일한 계정이 존재하면 409 응답을 정상적인 준비 상태로 처리하므로 다시 실행할 수 있다.

이 스크립트는 회원가입 준비용이다. 회원가입 성능은 폴링 테스트 결과에 포함하지 않는다.

## 3. 알림 데이터 준비

테스트 사용자마다 게시글 1건, 좋아요 알림 100건을 생성한다. 알림 중 20건은 미읽음, 80건은 읽음 상태로 만든다.

```bash
./gradlew bootRun \
  --args='--spring.profiles.active=local,loadtest-seed'
```

`loadtest-seed`는 임의 포트를 사용하므로 기존 8080 서버가 실행 중이어도 충돌하지 않는다. 데이터 생성과 검증이 끝나면 자동으로 종료된다.

예상 결과:

```text
테스트 게시글 합계: 500
테스트 좋아요 합계: 50000
테스트 알림 합계: 50000
미읽음 알림 합계: 10000
```

동일한 명령을 다시 실행해도 이미 생성된 게시글·좋아요·알림은 중복 생성되지 않는다.

## 4. 10명 × 30초 스모크 테스트

```bash
k6 run \
  --summary-export=load-tests/k6/results/smoke-10vu-30s.json \
  -e TARGET_VUS=10 \
  -e POLL_INTERVAL_SECONDS=30 \
  -e RAMP_UP_SECONDS=30 \
  -e STEADY_SECONDS=120 \
  -e RAMP_DOWN_SECONDS=30 \
  -e LOAD_TEST_PASSWORD="$LOAD_TEST_PASSWORD" \
  load-tests/k6/unread-count-polling.js
```

스모크 테스트에서 확인할 내용:

1. 10개 계정의 로그인이 모두 성공하는가
2. `GET /notifications/unread-count`가 200을 반환하는가
3. `data.unreadCount`가 숫자인가
4. Steady state의 실패율이 1% 미만인가
5. Steady state의 p95가 500ms, p99가 1,000ms 미만인가
6. Actuator에서 CPU, JVM, HikariCP 지표가 조회되는가

## 5. 본 테스트 실행

본 테스트에서는 Spring Boot, Actuator 수집기, k6를 각각 다른 터미널에서 실행한다.

### 터미널 1: Spring Boot

```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

### 터미널 2: Actuator CSV 수집

탐색 테스트는 Ramp-up 30초, Steady 120초, Ramp-down 30초로 총 180초다. 시작 전후 여유 시간을 포함해 200초 동안 5초 간격으로 수집한다.

```bash
load-tests/monitoring/collect-actuator.sh \
  polling-100vu-30s \
  5 \
  200
```

결과는 다음 경로에 저장된다.

```text
load-tests/monitoring/results/polling-100vu-30s-actuator.csv
```

### 터미널 3: k6

본 테스트의 공통 실행 형식이다.

```bash
k6 run \
  --summary-export="load-tests/k6/results/polling-${TARGET_VUS}vu-${POLL_INTERVAL_SECONDS}s.json" \
  -e TARGET_VUS="$TARGET_VUS" \
  -e POLL_INTERVAL_SECONDS="$POLL_INTERVAL_SECONDS" \
  -e RAMP_UP_SECONDS=30 \
  -e STEADY_SECONDS=120 \
  -e RAMP_DOWN_SECONDS=30 \
  -e LOAD_TEST_PASSWORD="$LOAD_TEST_PASSWORD" \
  load-tests/k6/unread-count-polling.js
```

실행하려는 조건을 먼저 지정한다.

```bash
export TARGET_VUS=100
export POLL_INTERVAL_SECONDS=30
```

최종 비교에는 아래 3개 조합을 사용한다.

| 시나리오 | `TARGET_VUS` | `POLL_INTERVAL_SECONDS` | 예상 평균 RPS |
|---:|---:|---:|---:|
| 1 | 100 | 30 | 약 3.3 |
| 2 | 500 | 30 | 약 16.7 |
| 3 | 500 | 5 | 약 100 |

각 실행 사이에는 애플리케이션의 CPU, 메모리, GC와 DB 커넥션이 안정 상태로 돌아왔는지 확인한다. 테스트를 쉬지 않고 연속 실행하지 않는다.

Actuator 수집기를 먼저 실행하고 곧바로 k6를 실행한다. 수집기의 `duration-seconds`를 0으로 지정하면 `Ctrl+C`를 누를 때까지 계속 수집한다.

```bash
load-tests/monitoring/collect-actuator.sh \
  polling-100vu-30s \
  5 \
  0
```

CSV에서 CPU·힙·스레드·HikariCP 값은 각 시점의 상태다. GC 횟수·시간과 미읽음 API 요청 수·시간은 애플리케이션 시작 이후의 누적값이므로, 시나리오 구간의 증가량은 마지막 값에서 첫 값을 빼서 계산한다.

## 6. 스크립트의 사용자 동작

각 VU는 다음 순서로 동작한다.

1. `__VU` 번호에 대응하는 서로 다른 테스트 계정으로 한 번 로그인한다.
2. 첫 폴링 시점만 `0초 이상, 폴링 주기 미만` 범위에서 무작위로 지연한다.
3. 이후 요청은 30초, 15초 또는 5초의 고정 tick을 따른다.
4. 이전 응답이 폴링 주기보다 오래 걸리면 겹치는 요청을 만들지 않고 지나간 tick을 건너뛴다.
5. 응답 상태가 200이고 `data.unreadCount`가 숫자인지 검사한다.

이는 프론트엔드의 고정 폴링과 중복 요청 방지 동작을 근사한다.

## 7. 주요 결과 지표

k6 기본 지표:

- `http_reqs`: 로그인과 폴링을 포함한 전체 HTTP 요청
- `http_req_failed`: 전체 HTTP 실패율
- `http_req_duration`: 전체 HTTP 응답시간
- `http_req_waiting`: 서버 응답 대기시간

폴링 전용 커스텀 지표:

- `polling_requests`: 미읽음 개수 요청 수와 초당 요청 수
- `polling_failed`: 응답 검증 실패율
- `polling_duration`: 미읽음 개수 API 전체 응답시간
- `polling_waiting`: 미읽음 개수 API 서버 대기시간

최종 비교에는 Ramp-up과 Ramp-down을 제외한 아래 Steady state 지표를 사용한다.

```text
polling_requests{phase:steady}
polling_failed{phase:steady}
polling_duration{phase:steady}
polling_waiting{phase:steady}
checks{scope:polling,phase:steady}
```

`polling_requests{phase:steady}`의 `count`를 Steady 시간으로 나누면 폴링 전용 실제 RPS를 계산할 수 있다.

```text
Steady 실제 RPS = polling_requests{phase:steady} count / STEADY_SECONDS
```

로그인 요청은 `endpoint=login`, 폴링 요청은 `endpoint=unread-count` 태그로 구분된다.

## 8. 환경변수

| 이름 | 기본값 | 설명 |
|---|---:|---|
| `BASE_URL` | `http://localhost:8080` | 테스트 대상 서버 |
| `TARGET_VUS` | `10` | 목표 가상 사용자 수 |
| `POLL_INTERVAL_SECONDS` | `30` | 폴링 주기 |
| `RAMP_UP_SECONDS` | `30` | Ramp-up 시간 |
| `STEADY_SECONDS` | `120` | Steady state 시간 |
| `RAMP_DOWN_SECONDS` | `30` | Ramp-down 시간 |
| `P95_LIMIT_MS` | `500` | Steady state p95 통과 기준 |
| `P99_LIMIT_MS` | `1000` | Steady state p99 통과 기준 |
| `ACCOUNT_PREFIX` | `loadtest` | 테스트 계정 이메일 접두사 |
| `ACCOUNT_DOMAIN` | `example.com` | 테스트 계정 이메일 도메인 |
| `LOAD_TEST_PASSWORD` | 없음 | 테스트 계정 비밀번호, 필수 |

EC2를 테스트할 때는 `BASE_URL`만 실제 서버 주소로 변경한다. 부하 발생기와 테스트 대상 애플리케이션은 같은 인스턴스에서 실행하지 않는다.

## 9. Actuator 측정 주의사항

k6 스크립트에서 Actuator 엔드포인트를 반복 호출하면 모니터링 요청 자체가 테스트 트래픽에 섞인다. 특히 예상 RPS가 3.3인 저부하 시나리오에서는 결과 왜곡이 커질 수 있다.

따라서 k6는 사용자 요청만 생성하고, CPU·JVM·GC·HikariCP 지표는 별도의 수집기에서 동일한 시간 구간으로 기록한다. 본 테스트 전에는 다음 데이터 조건도 고정한다.

- 사용자별 알림 데이터 개수
- 사용자별 미읽음 알림 개수
- `notifications` 테이블 전체 행 수
- 인덱스 구성

계정만 생성하고 알림이 전혀 없는 상태의 결과는 스크립트 검증용으로만 사용한다.

## 10. 결과 파일 관리

`k6/results/`와 `monitoring/results/`는 반복 테스트를 위한 작업 공간이므로 Git에서 제외한다. 스모크 테스트나 재측정 과정에서 파일이 자주 추가되고 덮어써져도 소스 변경사항과 섞이지 않는다.

최종 분석에 채택한 결과는 아래처럼 `reports/data/`에 복사하고 `reports/polling-load-test.md`를 함께 갱신한다.

```text
load-tests/reports/data/
├── polling-100vu-30s-k6.json
├── polling-100vu-30s-actuator.csv
├── polling-500vu-30s-k6.json
├── polling-500vu-30s-actuator.csv
├── polling-500vu-5s-k6.json
└── polling-500vu-5s-actuator.csv
```

## 11. 사전 발급 고유 JWT 기반 폴링 한계 테스트

2,000 VU 이상에서는 `unread-count-polling-limit.js`를 사용한다. 측정 전에 테스트 사용자별 JWT를 직접 생성하고, k6는 해당 JSON 파일을 `SharedArray`로 한 번만 읽는다.

```text
VU 1     → loadtest1 사용자의 JWT
VU 2     → loadtest2 사용자의 JWT
...
VU 10000 → loadtest10000 사용자의 JWT
```

부하 측정 중 로그인 요청과 BCrypt 검증, JWT 발급은 발생하지 않는다. 각 폴링 요청의 JWT 검증, 사용자 조회, 미읽음 알림 count 쿼리와 Spring Security 처리는 그대로 포함한다.

### JWT 파일 생성

먼저 `prepare-loadtest-users.sql`과 `prepare-loadtest-notifications.sql`로 필요한 DB 데이터를 준비한다.

`.env`의 `JWT_SECRET`을 현재 Node.js 프로세스에만 읽혀 사용자별 JWT를 생성한다.

```bash
node --env-file=.env \
  load-tests/token/generate-loadtest-tokens.mjs \
  --count 10000
```

기본 출력:

```text
load-tests/k6/.tokens/polling-users.json
```

토큰 파일은 권한 `600`으로 생성되며 `.gitignore`에 의해 Git에서 제외된다. 기본 만료시간은 애플리케이션 Access Token과 동일한 30분이므로 생성 직후 테스트를 실행한다.

스크립트는 `loadtest1@example.com`부터 요청한 수만큼 계정이 빠짐없이 존재하는지 검증한다. 다음 환경변수로 DB 접속과 JWT 설정을 변경할 수 있다.

| 환경변수 | 기본값 |
|---|---|
| `DB_HOST` | `localhost` |
| `DB_PORT` | `3306` |
| `DB_USERNAME` | `root` |
| `DB_PASSWORD` | 빈 문자열 |
| `DB_NAME` | `community_api` |
| `JWT_ISSUER` | `community-api` |
| `JWT_ACCESS_TOKEN_EXPIRATION_MS` | `1800000` |

공통 조건:

```text
폴링 주기: 5초
Ramp-up: 60초
Steady state: 120초
Ramp-down: 30초
```

| VU | 예상 Steady RPS | 예상 Steady 요청 수 |
|---:|---:|---:|
| 2,000 | 400 | 48,000 |
| 5,000 | 1,000 | 120,000 |
| 10,000 | 2,000 | 240,000 |

### 10 VU 사전 검증

```bash
k6 run \
  -e TARGET_VUS=10 \
  -e POLL_INTERVAL_SECONDS=5 \
  -e RAMP_UP_SECONDS=10 \
  -e STEADY_SECONDS=20 \
  -e RAMP_DOWN_SECONDS=5 \
  load-tests/k6/unread-count-polling-limit.js
```

### 2,000 VU

Actuator 수집:

```bash
load-tests/monitoring/collect-actuator.sh \
  polling-limit-2000vu-5s \
  5 \
  0
```

k6 실행:

```bash
k6 run \
  --summary-export=load-tests/k6/results/polling-limit-2000vu-5s.json \
  -e TARGET_VUS=2000 \
  -e POLL_INTERVAL_SECONDS=5 \
  -e RAMP_UP_SECONDS=60 \
  -e STEADY_SECONDS=120 \
  -e RAMP_DOWN_SECONDS=30 \
  load-tests/k6/unread-count-polling-limit.js
```

### 5,000 VU

Actuator 수집:

```bash
load-tests/monitoring/collect-actuator.sh \
  polling-limit-5000vu-5s \
  5 \
  0
```

k6 실행:

```bash
k6 run \
  --summary-export=load-tests/k6/results/polling-limit-5000vu-5s.json \
  -e TARGET_VUS=5000 \
  -e POLL_INTERVAL_SECONDS=5 \
  -e RAMP_UP_SECONDS=60 \
  -e STEADY_SECONDS=120 \
  -e RAMP_DOWN_SECONDS=30 \
  load-tests/k6/unread-count-polling-limit.js
```

### 10,000 VU

Actuator 수집:

```bash
load-tests/monitoring/collect-actuator.sh \
  polling-limit-10000vu-5s \
  5 \
  0
```

k6 실행:

```bash
k6 run \
  --summary-export=load-tests/k6/results/polling-limit-10000vu-5s.json \
  -e TARGET_VUS=10000 \
  -e POLL_INTERVAL_SECONDS=5 \
  -e RAMP_UP_SECONDS=60 \
  -e STEADY_SECONDS=120 \
  -e RAMP_DOWN_SECONDS=30 \
  load-tests/k6/unread-count-polling-limit.js
```

각 단계에서 k6 종료 후 Actuator 수집기를 `Ctrl+C`로 종료한다. 실패율 1% 이상, p95 500ms 이상, p99 1초 이상, CPU 80% 지속 또는 HikariCP pending 발생 시 다음 VU 단계로 진행하지 않고 해당 구간을 분석한다.

전체 HTTP 요청은 폴링 요청만 포함한다. 최종 비교에는 아래 사전 발급 고유 인증 폴링 전용 지표를 사용한다.

```text
polling_requests{phase:steady,auth_mode:preissued-unique}
polling_failed{phase:steady,auth_mode:preissued-unique}
polling_duration{phase:steady,auth_mode:preissued-unique}
polling_waiting{phase:steady,auth_mode:preissued-unique}
checks{scope:polling,phase:steady,auth_mode:preissued-unique}
```
