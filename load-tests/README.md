# 알림 부하 테스트

미읽음 알림 개수 폴링의 요청량과 애플리케이션 자원 사용량을 측정하기 위한 파일을 역할별로 관리한다.

## 디렉터리 역할

```text
load-tests/
├── k6/
│   ├── prepare-users.js
│   ├── unread-count-polling.js
│   └── results/                 # 반복 실행 결과, Git 제외
├── monitoring/
│   ├── collect-actuator.sh
│   └── results/                 # 반복 수집 결과, Git 제외
└── reports/
    ├── polling-load-test.md     # 최종 실험 조건과 분석
    └── data/                    # 보고서에 사용한 확정 원본, Git 포함
```

- `k6/results/`: 스모크 테스트와 재실행 결과를 저장하는 작업 공간이다.
- `monitoring/results/`: Actuator 수집기가 CSV를 저장하는 작업 공간이다.
- `reports/data/`: 최종 분석에 실제로 사용한 JSON과 CSV만 선별해 보존한다.
- `reports/polling-load-test.md`: 가설, 환경, 측정값, 해석과 한계를 기록한다.

반복 실행 결과를 새로 생성해도 `reports/data/`의 확정 데이터는 자동으로 변경되지 않는다. 새로운 결과를 최종 결과로 채택할 때만 해당 파일을 선별해 복사하고 보고서를 함께 갱신한다.

테스트 준비와 실행 방법은 [k6 실행 설명서](k6/README.md), 확정 결과는 [폴링 부하 테스트 보고서](reports/polling-load-test.md)를 참고한다.
