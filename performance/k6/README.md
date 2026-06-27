# k6 부하 테스트

`main-read-settlement.js`는 TogetherTrip `main` API의 조회, 지출, 통계, 정산 preview, 잔액 요약 API를 섞어서 호출합니다. 정산 확정, 수정, 삭제 API는 호출하지 않습니다.

기본 요청 흐름은 아래와 같습니다.

```text
k6 -> gateway:8080 -> main:8081
```

자동 실행 스크립트의 결과는 테스트 종류별 하위 디렉터리에 저장합니다.

```text
docs/k6-results/read-settlement/
docs/k6-results/dml/
docs/k6-results/concurrent-signup/
```

## 1. 실행 전 준비

gateway Docker Compose를 먼저 실행합니다.

```bash
cd /Users/jujaewan/1_Projects/togethertrip/togethertrip-server-gateway

docker compose up --build
```

다른 터미널에서 `main` repo로 이동합니다.

```bash
cd /Users/jujaewan/1_Projects/togethertrip/togethertrip-server-main
```

## 2. 대량 데이터 삽입

대량 데이터는 아래 SQL로 삽입합니다.

```bash
docker compose -f ../togethertrip-server-gateway/docker-compose.yml \
  cp performance/seed/cleanup-load-test-data.sql postgres:/tmp/cleanup-load-test-data.sql

docker compose -f ../togethertrip-server-gateway/docker-compose.yml \
  cp performance/seed/load-test-data.sql postgres:/tmp/load-test-data.sql

docker compose -f ../togethertrip-server-gateway/docker-compose.yml \
  exec -T postgres psql -U together_trip -d together_trip -v ON_ERROR_STOP=1 \
  -f /tmp/load-test-data.sql
```

이 SQL은 시작할 때 아래 cleanup SQL을 먼저 실행합니다.

```text
performance/seed/cleanup-load-test-data.sql
```

cleanup SQL은 아래 데이터만 hard delete 합니다.

```text
여행 제목: LOADTEST_정산_대량_여행
생성 유저: LOADTEST_USER_%
```

생성되는 주요 데이터는 아래와 같습니다.

```text
ONGOING 여행 1개: LOADTEST_정산_대량_여행
참여자 10명
거래 100,000건
결제 row 100,000건
부담 row 500,000건
잔액 summary 10건
```

부하 테스트 데이터만 삭제할 때는 아래 명령을 실행합니다.

```bash
docker compose -f ../togethertrip-server-gateway/docker-compose.yml \
  cp performance/seed/cleanup-load-test-data.sql postgres:/tmp/cleanup-load-test-data.sql

docker compose -f ../togethertrip-server-gateway/docker-compose.yml \
  exec -T postgres psql -U together_trip -d together_trip -v ON_ERROR_STOP=1 \
  -f /tmp/cleanup-load-test-data.sql
```

## 3. 데이터 정합성 검증

대량 데이터 삽입 후 아래 SQL로 정합성을 확인합니다.

```bash
docker compose -f ../togethertrip-server-gateway/docker-compose.yml \
  cp performance/seed/validate-load-test-data.sql postgres:/tmp/validate-load-test-data.sql

docker compose -f ../togethertrip-server-gateway/docker-compose.yml \
  exec -T postgres psql -U together_trip -d together_trip -v ON_ERROR_STOP=1 \
  -f /tmp/validate-load-test-data.sql
```

정상이라면 마지막에 아래 notice와 count 표가 출력됩니다.

```text
NOTICE:  Load-test data validation passed for trip id ...

        item       | count
-------------------+--------
 balance_summaries |     10
 generated_users   |      9
 participants      |     10
 payments          | 100000
 shares            | 500000
 transactions      | 100000
 trips             |      1
```

이 SQL은 아래 내용을 확인합니다.

```text
부하 테스트 여행이 1개인지
생성 유저가 9명인지
참여자가 10명인지
거래가 100,000건인지
결제 row가 100,000건인지
부담 row가 500,000건인지
잔액 summary가 10건인지
각 거래마다 결제 row가 1개이고 결제 합계가 거래 금액과 같은지
각 거래마다 부담 row가 5개이고 부담 합계가 거래 금액과 같은지
결제/부담 row가 같은 여행의 거래와 참여자를 바라보는지
잔액 summary가 결제/부담 합계로 다시 계산한 값과 같은지
```

정합성 검증은 k6 실행 전과 실행 후에 모두 실행할 수 있습니다. 현재 k6 스크립트는 조회와 정산 preview만 호출하므로, 실행 전과 실행 후 결과가 같아야 합니다.

## 4. 단일 gateway 실행

대량 데이터를 대상으로 실행할 때는 `TRIP_TITLE`을 지정합니다.

```bash
BASE_URL=http://localhost:8080 \
TRIP_TITLE=LOADTEST_정산_대량_여행 \
VUS=1 \
HOLD=10s \
k6 run performance/k6/main-read-settlement.js
```

### endpoint별 지표

`main-read-settlement.js`는 기본 `http_req_duration` 외에 endpoint별 custom Trend metric도 기록한다.

```text
endpoint_trips_duration
endpoint_trip_detail_duration
endpoint_participants_duration
endpoint_transactions_duration
endpoint_transaction_statistics_duration
endpoint_common_fund_balance_duration
endpoint_settlement_preview_duration
endpoint_balance_summary_duration
```

`--summary-export` 결과에서 위 metric의 `avg`, `med`, `p(90)`, `p(95)`, `p(99)`, `max`를 비교하면 전체 p95/p99를 끌어올리는 endpoint를 분리해서 확인할 수 있다.

### tail latency 확인

`main-read-settlement.js`는 모든 HTTP 요청에 `phase` 태그를 붙인다.

```text
phase=setup: 로그인과 테스트 대상 여행 조회
phase=scenario: 부하 구간에서 반복 호출하는 API
```

따라서 전체 지표와 부하 구간 지표를 분리해서 볼 수 있다.

```text
http_req_duration
http_req_duration{phase:scenario}
http_req_duration{phase:setup}
```

summary에는 p99도 출력한다. p99는 당장 threshold로 강제하지 않고, max spike가 어떤 endpoint/phase에서 반복되는지 확인하는 관찰 지표로 사용한다.

기본적으로 200ms 이상 걸린 요청은 `slow_request` 로그로 남긴다.

```text
{"type":"slow_request","threshold_ms":200,"duration_ms":253.41,"status":200,"phase":"scenario","endpoint":"transaction-statistics",...}
```

기준은 `SLOW_REQUEST_MS`로 조정할 수 있다. 0 이하로 설정하면 slow request 로그를 끈다.

```bash
SLOW_REQUEST_MS=100 \
BASE_URL=http://localhost:8080 \
TRIP_TITLE=LOADTEST_정산_대량_여행 \
VUS=10 \
HOLD=1m \
k6 run performance/k6/main-read-settlement.js
```

부하를 올릴 때는 `VUS`와 `HOLD`를 조정합니다.

```bash
BASE_URL=http://localhost:8080 \
TRIP_TITLE=LOADTEST_정산_대량_여행 \
VUS=30 \
HOLD=2m \
k6 run performance/k6/main-read-settlement.js
```

## 5. 이중화 서버 실행

로드밸런서를 테스트할 때는 `BASE_URL`에 로드밸런서 주소 하나를 넣습니다.

```bash
BASE_URL=http://로드밸런서주소 \
TRIP_TITLE=LOADTEST_정산_대량_여행 \
VUS=50 \
HOLD=3m \
k6 run performance/k6/main-read-settlement.js
```

gateway 인스턴스 두 대를 직접 나눠 호출할 때는 `BASE_URLS`를 사용합니다.

```bash
BASE_URLS=http://localhost:8080,http://localhost:8084 \
TRIP_TITLE=LOADTEST_정산_대량_여행 \
TARGET_STRATEGY=sticky-vu \
VUS=40 \
HOLD=3m \
k6 run performance/k6/main-read-settlement.js
```

현재 Docker Compose 기준에서 `8080`은 gateway, `8081`은 main입니다. 따라서 `BASE_URLS=http://localhost:8080,http://localhost:8081`은 gateway 이중화 테스트로 사용하지 않습니다.

## 6. 환경 변수

주요 환경 변수는 아래와 같습니다.

```text
BASE_URL: 단일 대상 서버입니다. 기본값은 http://localhost:8080 입니다.
BASE_URLS: 쉼표로 구분한 다중 대상 서버입니다. 지정하면 BASE_URL보다 우선합니다.
TARGET_STRATEGY: 다중 서버 선택 방식입니다. 기본값은 sticky-vu 입니다.
KAKAO_ACCESS_TOKEN: 로컬 로그인용 카카오 토큰입니다. 기본값은 local-test:verified:hana 입니다.
TRIP_STATUS: setup에서 고를 여행 상태입니다. 기본값은 ONGOING 입니다.
TRIP_TITLE: setup에서 고를 여행 제목입니다.
VUS: 목표 VU 수입니다. 기본값은 10 입니다.
RAMP_UP: 증가 시간입니다. 기본값은 30s 입니다.
HOLD: 유지 시간입니다. 기본값은 1m 입니다.
RAMP_DOWN: 감소 시간입니다. 기본값은 30s 입니다.
SLEEP: 반복 사이 대기 시간입니다. 기본값은 1초입니다.
SLOW_REQUEST_MS: 이 시간 이상 걸린 요청을 slow_request 로그로 남깁니다. 기본값은 200ms이고, 0 이하면 비활성화합니다.
```

`TARGET_STRATEGY` 값은 아래와 같습니다.

```text
sticky-vu: 같은 VU는 같은 서버를 계속 호출합니다.
round-robin: 반복마다 대상 서버를 바꿉니다.
random: 반복마다 임의 서버를 고릅니다.
```

## 7. 호출 API와 호출 수

`setup` 단계는 대상 서버마다 2번 호출합니다.

```text
POST /api/auth/oauth/kakao
GET  /api/trips
```

부하 구간의 반복 1회는 API 8개를 각각 1번씩 호출합니다.

```text
GET  /api/trips
GET  /api/trips/{tripId}
GET  /api/trips/{tripId}/participants
GET  /api/trips/{tripId}/transactions
GET  /api/trips/{tripId}/transaction-statistics
GET  /api/trips/{tripId}/common-fund-balance
POST /api/trips/{tripId}/settlement-preview
GET  /api/trips/{tripId}/balance-summary
```

기본 `RAMP_UP=30s`, `RAMP_DOWN=30s`, `SLEEP=1` 기준 대략 요청 수는 아래와 같습니다.

```text
VUS=1,  HOLD=10s: 약 320회 + setup 2회
VUS=10, HOLD=1m:  약 7,200회 + setup 2회
VUS=30, HOLD=2m:  약 36,000회 + setup 2회
VUS=50, HOLD=3m:  약 84,000회 + setup 2회
```

실제 요청 수는 API 응답 시간이 포함되므로 위 값보다 적게 나올 수 있습니다.

## 8. 결과 확인

우선 확인할 값은 아래와 같습니다.

```text
checks: 스크립트 확인 조건 통과율입니다.
http_req_failed: HTTP 요청 실패율입니다.
http_req_duration p(95): 전체 요청의 95%가 끝난 시간입니다.
http_reqs: 총 요청 수입니다.
iterations: 스크립트 반복 횟수입니다.
```

현재 threshold는 아래와 같습니다.

```text
http_req_failed < 1%
전체 http_req_duration p95 < 500ms
부하 구간 http_req_duration{phase:scenario} p95 < 500ms
settlement-preview http_req_duration p95 < 800ms
```

기준을 넘으면 k6는 실패로 종료됩니다.

`checks=100%`이고 `http_req_failed=0%`인데 threshold만 실패했다면 API 호출은 정상이고 응답 시간이 기준보다 느린 상태입니다. 이 경우에는 실패한 endpoint의 p95를 성능 개선 대상으로 봅니다.

## 9. 동일 조건 재실행

동일한 조건으로 비교하려면 아래 순서로 실행합니다.

```bash
docker compose -f ../togethertrip-server-gateway/docker-compose.yml \
  cp performance/seed/cleanup-load-test-data.sql postgres:/tmp/cleanup-load-test-data.sql

docker compose -f ../togethertrip-server-gateway/docker-compose.yml \
  cp performance/seed/load-test-data.sql postgres:/tmp/load-test-data.sql

docker compose -f ../togethertrip-server-gateway/docker-compose.yml \
  exec -T postgres psql -U together_trip -d together_trip -v ON_ERROR_STOP=1 \
  -f /tmp/load-test-data.sql

docker compose -f ../togethertrip-server-gateway/docker-compose.yml \
  cp performance/seed/validate-load-test-data.sql postgres:/tmp/validate-load-test-data.sql

docker compose -f ../togethertrip-server-gateway/docker-compose.yml \
  exec -T postgres psql -U together_trip -d together_trip -v ON_ERROR_STOP=1 \
  -f /tmp/validate-load-test-data.sql

BASE_URL=http://localhost:8080 \
TRIP_TITLE=LOADTEST_정산_대량_여행 \
VUS=10 \
HOLD=1m \
k6 run performance/k6/main-read-settlement.js
```

seed SQL이 기존 부하 테스트 데이터를 hard delete 후 다시 삽입하므로 데이터가 계속 누적되지 않습니다.

## 10. DML 부하 테스트

조회/정산 preview가 아니라 실제 DB 변경을 확인할 때는 아래 스크립트를 사용합니다.

```bash
cd /Users/jujaewan/1_Projects/togethertrip/togethertrip-server-main

./performance/k6/run-dml-load-test.sh
```

이 스크립트는 아래 흐름을 반복합니다.

```text
새 여행 생성
거래 등록
거래 수정
정산 preview
정산 확정
송금 목록 조회
송금자 확인
수신자 확인
DML 정합성 검증
```

기본값은 아래와 같습니다.

```text
VUS=1
RAMP_UP=10s
HOLD=30s
RAMP_DOWN=10s
```

부하를 올릴 때는 아래처럼 실행합니다.

```bash
VUS=5 HOLD=1m ./performance/k6/run-dml-load-test.sh
```

DML 테스트는 `DML_LOADTEST_`로 시작하는 여행을 생성합니다. 다음 DML 실행 시 시작 단계에서 이전 DML 테스트 데이터를 삭제합니다.

DML 정합성 검증은 아래 내용을 확인합니다.

```text
여행 SETTLED 상태
참여자 3명
거래 1건
수정 후 거래 금액 33,000원
결제 합계 33,000원
부담 합계 33,000원
정산 CONFIRMED 상태
송금 2건
송금 2건 모두 COMPLETED 상태
송금자/수신자 확인 시간 존재
```

작성 시점 짧은 검증은 아래 조건으로 통과했습니다.

```bash
VUS=1 RAMP_UP=1s HOLD=1s RAMP_DOWN=1s ./performance/k6/run-dml-load-test.sh
```

결과 요약:

```text
checks: 100.00%
http_req_failed: 0.00%
http_reqs: 26
iterations: 2
dml_trips: 2
dml_transactions: 2
dml_settlements: 2
dml_transfers: 4
dml_completed_transfers: 4
k6 exit code: 0
```

## 11. 동시 회원가입 부하 테스트

회원가입 동시성 정합성을 확인할 때는 아래 스크립트를 사용합니다.

```bash
cd /Users/jujaewan/1_Projects/togethertrip/togethertrip-server-main

./performance/k6/run-concurrent-signup.sh
```

이 스크립트는 gateway를 통해 `main` 서버에 접근하고, `auth.local-test.enabled=true` 상태에서 로컬 테스트용 OAuth 세션과 전화번호 인증 상태를 준비합니다.

실행 시나리오는 아래 두 가지입니다.

```text
same-session: 동일 temporaryToken으로 동시에 회원가입 confirm
same-phone: 서로 다른 temporaryToken이 같은 전화번호로 동시에 회원가입 confirm
```

기본값은 아래와 같습니다.

```text
BASE_URL=http://localhost:8080
CONFIRM_CODE=123456
SAME_SESSION_VUS=20
SAME_PHONE_VUS=10
MAX_DURATION=5s
READY_TIMEOUT_SECONDS=60
```

부하를 조정할 때는 아래처럼 실행합니다.

```bash
SAME_SESSION_VUS=50 SAME_PHONE_VUS=30 MAX_DURATION=10s ./performance/k6/run-concurrent-signup.sh
```

직접 k6만 실행할 수도 있습니다.

```bash
BASE_URL=http://localhost:8080 \
RUN_ID=manual-signup-001 \
SCENARIO=same-session \
VUS=20 \
k6 run performance/k6/concurrent-signup.js
```

```bash
BASE_URL=http://localhost:8080 \
RUN_ID=manual-signup-001 \
SCENARIO=same-phone \
VUS=10 \
k6 run performance/k6/concurrent-signup.js
```

사후 DB 검증 SQL은 아래 파일을 사용합니다.

```text
performance/seed/validate-concurrent-signup.sql
```

자동 실행 스크립트 결과는 아래 디렉터리에 저장합니다.

```text
docs/k6-results/concurrent-signup/
```
