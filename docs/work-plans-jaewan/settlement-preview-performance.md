# settlement-preview 대량 정산 데이터 응답 지연 개선

## GitHub Issue

GitHub Issue는 공개 가능한 수준의 요약만 포함해 생성했다.

- https://github.com/together-trip/togethertrip-server-main/issues/87

초기 생성 시도에서는 외부 저장소로 k6 부하 테스트 상세를 업로드하는 작업이 데이터 공개 위험으로 차단되었다.

## Issue 초안

### 제목

settlement-preview 대량 정산 데이터 응답 지연 개선

### 배경

`docs/k6-load-test-result-report.md`의 k6 부하 테스트 결과, 기능 호출과 데이터 정합성은 통과했지만 응답 시간 threshold가 실패했다.

- 실행 ID: `20260626-135224`
- 대상 환경: 로컬 Docker Compose, gateway 경유 `http://localhost:8080`
- 데이터 규모: `transactions=100,000`, `payments=100,000`, `shares=500,000`, `participants=10`
- HTTP 실패율: `0.00%`
- checks: `100.00%`
- 전체 `http_req_duration p95`: `3.10s` (목표 `< 500ms`)
- `settlement-preview p95`: `3.74s` (목표 `< 800ms`)
- `settlement-preview avg`: `2.30s`
- `settlement-preview max`: `4.48s`
- k6 exit code: `99` (threshold 실패)

### 문제

대량 정산 데이터에서 `POST /api/trips/{tripId}/settlement-preview`가 주요 병목으로 확인된다. API 자체는 성공하지만 일부 무거운 요청이 전체 p90/p95 꼬리 지연을 크게 만들고 있다.

### 작업 범위

1. `settlement-preview` 쿼리와 계산 로직 분석
2. `transactions`, `transaction_payments`, `transaction_shares`, `trip_participants` 관련 조인/인덱스 확인
3. 100,000건 거래와 500,000건 부담 row 조건에서 full scan, 비효율적인 sort/group 가능성 개선
4. endpoint별 성능 지표 수집이 부족하면 k6 스크립트 보강
5. 개선 전/후 비교가 가능하도록 테스트 또는 문서 갱신

### 완료 기준

- 대량 데이터 기준 `settlement-preview` 병목 원인이 코드/쿼리 관점에서 설명된다.
- 필요한 인덱스, 쿼리, 계산 로직 개선이 반영된다.
- 관련 테스트 또는 검증 명령을 실행하고 결과를 남긴다.
- 커밋/PR에는 이슈 번호를 연결한다.

## 구현 메모

- 기존 `SettlementCalculationService.calculate()`는 payments와 shares 원본 row를 모두 애플리케이션으로 가져온 뒤 JVM에서 participant별 합계를 계산했다.
- 이번 변경은 원본 데이터 정합성은 유지하면서 DB에서 participant별 합계만 반환하도록 바꾼다.
- 조회 결과 row 수는 대량 데이터 기준 `payments 100,000 + shares 500,000`에서 `participant별 payment 합계 + participant별 share 합계`로 줄어든다.
- 1차 개선에서는 `trip_participant_balance_summaries` projection이 거래 생성/수정 흐름에서 갱신되는 코드가 확인되지 않아 preview 응답의 원천으로 사용하지 않았다.

## 후속 개선 메모

- `performance/k6/main-read-settlement.js`에 endpoint별 custom Trend metric을 추가해 다음 실행부터 `trips`, `trip-detail`, `participants`, `transactions`, `transaction-statistics`, `common-fund-balance`, `settlement-preview`, `balance-summary`의 avg/med/p90/p95/max를 summary JSON에서 직접 비교할 수 있게 했다.
- `GET /balance-summary`는 기존에 `previewSettlement()`를 재사용해 송금 응답까지 생성했다. balance summary 응답에 필요한 참여자별 잔액만 만들도록 분리해 불필요한 transfer response 생성을 제거했다.
- `common-fund-balance`는 `FUND_CHARGE`, `FUND_USE`만 잔액 계산에 필요하므로 해당 transaction type만 집계하도록 조건을 좁혔다.
- 기간 필터가 없는 type/participant 통계는 post 발생일 조회가 필요하지 않으므로 posts 조인을 생략하는 전용 쿼리로 분기했다.
- 거래 목록, 공동경비 잔액, posts 기반 통계 조회를 위한 인덱스를 `V16__add_read_load_query_indexes.sql`에 추가했다.
- `TripParticipantBalanceSummaryProjectionService`를 추가해 거래 생성/수정/삭제 시 participant별 paid/share/net summary를 `trip.expenseVersion`에 맞춰 갱신한다.
- `SettlementCalculationService.calculate(tripId, expectedProjectionVersion)`는 `trip_participant_balance_summaries`가 모두 최신 version이면 원본 `transaction_payments`, `transaction_shares` row 대신 projection에서 계산한다.
- projection이 비어 있거나 version이 맞지 않으면 기존 DB 집계 쿼리로 fallback해 정합성을 우선한다.

## transaction-statistics 후속 개선 메모

- `20260626-160032` k6 결과에서 전체 threshold는 통과했지만, endpoint별 지표상 `transaction-statistics`가 p95 `405.02ms`로 가장 큰 잔여 병목이었다.
- 부하 테스트 호출은 `groupBy=category`이며 기간 필터가 없다. seed 데이터는 `transactions=100,000`을 생성하지만 posts는 생성하지 않아 모든 거래가 `UNCATEGORIZED`로 집계된다.
- 기존 category 통계 쿼리는 각 거래마다 연결된 첫 게시글을 correlated subquery로 찾았다. posts가 없거나 적은 데이터에서도 거래 100,000건 기준 반복 탐색 비용이 발생한다.
- category 통계를 first-post derived subquery로 변경해 여행의 연결 게시글 후보를 먼저 `trip_id`로 좁힌 뒤 transactions에 조인하도록 바꿨다. 대표 게시글은 기존과 동일하게 가장 작은 post id를 사용한다.
- `V17__add_transaction_statistics_query_indexes.sql`에 `posts(trip_id, transaction_id, id) INCLUDE (occurred_at, category)` partial index를 추가해 subquery가 여행 단위 post 후보를 효율적으로 읽도록 했다.
