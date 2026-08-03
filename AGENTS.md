# Project guidance

## SSE 부하 테스트

SSE 또는 폴링 부하 테스트 작업을 시작하기 전에 다음 기준 문서를 읽는다.

- `load-tests/reports/sse-connection-test-plan.md`

현재 SSE 부하 테스트 범위는 연결 유지 테스트로 한정한다.

고정 조건은 다음과 같다.

- Ramp-up: 60초
- Steady State: 120초
- Ramp-down: 30초
- 사용자별 고유 JWT 사용
- 대규모 알림 전달 테스트는 범위에서 제외
- 기존 폴링 결과와 CPU, Heap, 스레드, DB 커넥션, GC, 안정 사용자 수를 비교

