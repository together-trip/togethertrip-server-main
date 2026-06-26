# settlement-preview 성능 튜닝 사례

## 1. 목적

이 문서는 이슈 #87 `settlement-preview 성능 개선`에서 어떤 근거로 쿼리와 계산 구조를 변경했는지 정리한다.

주요 질문은 아래와 같다.

- 왜 payments/shares 원본 row 조회를 DB 집계로 바꿨는가?
- 왜 단순 인덱스 추가만으로 충분하지 않다고 판단했는가?
- 왜 `trip_participant_balance_summaries` projection을 preview 계산에 사용했는가?
- 왜 `transaction-statistics(groupBy=category)` 쿼리를 CTE와 posts 인덱스로 바꿨는가?
- 실행계획 기준으로 어떤 병목이 있었고 어떤 효과가 있었는가?

## 2. 테스트 데이터와 부하 조건

대량 데이터 조건은 아래와 같다.

| 항목 | 값 |
|------|---:|
| trips | 1 |
| generated_users | 9 |
| participants | 10 |
| transactions | 100,000 |
| transaction_payments | 100,000 |
| transaction_shares | 500,000 |
| balance_summaries | 10 |

k6 조건은 아래와 같다.

| 항목 | 값 |
|------|----|
| VUS | 10 |
| RAMP_UP | 30s |
| HOLD | 1m |
| RAMP_DOWN | 30s |
| SLEEP | 1s |
| 반복당 API | 8개 |

테스트는 운영 SLA 측정이 아니라, 로컬 Docker Compose 환경에서 대량 데이터 성능 회귀를 확인하기 위한 재현 테스트다.

## 3. 최초 병목

초기 실행 `20260626-135224` 결과는 아래와 같았다.

| 지표 | 결과 |
|------|-----:|
| 전체 http p95 | 3.10s |
| settlement-preview avg | 2.30s |
| settlement-preview p95 | 3.75s |
| settlement-preview max | 4.48s |
| http_req_failed | 0.00% |
| checks | 100% |

API는 실패하지 않았지만, 대량 정산 데이터에서 응답 시간이 초 단위로 증가했다. 따라서 기능 오류가 아니라 정산 preview 계산 경로의 데이터 접근 비용이 문제라고 판단했다.

## 4. 기존 구조의 문제

기존 `SettlementCalculationService.calculate()` 흐름은 아래와 같았다.

1. 해당 여행의 활성 거래를 찾는다.
2. 연결된 payment 원본 row를 모두 조회한다.
3. 연결된 share 원본 row를 모두 조회한다.
4. 애플리케이션에서 participant별 paid/share 합계를 만든다.
5. paid/share/net/transfer 응답을 계산한다.

대량 데이터에서는 애플리케이션으로 넘어오는 row 수가 아래와 같았다.

| 조회 대상 | row 수 |
|----------|------:|
| payments | 100,000 |
| shares | 500,000 |
| 합계 | 600,000 |

정산 preview에 실제로 필요한 값은 각 원본 row가 아니라 참여자별 총액이다.

```text
필요한 최종 입력:
participant별 paid total
participant별 share total
```

참여자가 10명인 테스트 데이터에서는 최종적으로 필요한 row가 payment 10개, share 10개 수준이다. 따라서 600,000 row를 애플리케이션으로 넘겨 Kotlin에서 `groupBy`하는 구조는 데이터 전송량, JDBC materialization, JVM 메모리, Kotlin 컬렉션 집계 비용을 모두 키운다.

## 5. 1차 판단: DB 집계로 변경

### 5.1 판단 근거

기존 방식은 인덱스를 추가해도 원본 row를 600,000개 반환하는 구조 자체가 유지된다. 인덱스가 scan/sort 비용을 일부 줄일 수는 있지만, 아래 비용은 계속 남는다.

- DB에서 600,000 row를 결과로 만들어야 함
- JDBC로 600,000 row를 전송해야 함
- 애플리케이션에서 600,000 row 객체를 만들어야 함
- Kotlin에서 participant별 그룹 집계를 해야 함

따라서 핵심은 “원본 row 조회를 빠르게”가 아니라 “원본 row를 반환하지 않게” 만드는 것이었다.

개선 방향은 아래와 같다.

```sql
select share.trip_participant_id,
       sum(share.base_share_amount) as amount
from transaction_shares share
join transactions transaction on transaction.id = share.transaction_id
where share.deleted_at is null
  and transaction.deleted_at is null
  and transaction.trip_id = :tripId
  and transaction.status = 'ACTIVE'
group by share.trip_participant_id
order by share.trip_participant_id asc;
```

동일하게 payments도 participant별 sum만 반환하도록 바꿨다.

### 5.2 추가 인덱스

DB 집계 쿼리의 조인과 집계를 받치기 위해 아래 인덱스를 추가했다.

```sql
CREATE INDEX IF NOT EXISTS idx_transactions_settlement_preview_active
    ON transactions (trip_id, status, id)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_transaction_payments_settlement_preview_active
    ON transaction_payments (transaction_id, trip_participant_id)
    INCLUDE (base_amount)
    WHERE deleted_at IS NULL;

CREATE INDEX IF NOT EXISTS idx_transaction_shares_settlement_preview_active
    ON transaction_shares (transaction_id, trip_participant_id)
    INCLUDE (base_share_amount)
    WHERE deleted_at IS NULL;
```

의도는 아래와 같다.

- `transactions(trip_id, status, id)`: 특정 여행의 활성 거래 id 후보를 좁힌다.
- `transaction_payments(transaction_id, trip_participant_id) INCLUDE (base_amount)`: 거래별 payment 조인과 participant별 paid 집계에 필요한 값을 인덱스에서 읽을 수 있게 한다.
- `transaction_shares(transaction_id, trip_participant_id) INCLUDE (base_share_amount)`: 거래별 share 조인과 participant별 share 집계에 필요한 값을 인덱스에서 읽을 수 있게 한다.

## 6. 실행계획 근거: shares

### 6.1 기존 shares 원본 조회

기존 shares 조회는 500,000 row를 반환하고 `share.id` 기준 external merge sort가 발생했다.

```text
Sort  (cost=75438.86..76688.50 rows=499856 width=23) (actual time=216.733..278.446 rows=500000 loops=1)
  Sort Key: share.id
  Sort Method: external merge  Disk: 17944kB
"  Buffers: shared hit=8182 read=628, temp read=2243 written=2251"
  ->  Hash Join  (cost=4475.03..17873.11 rows=499856 width=23) (actual time=39.689..142.392 rows=500000 loops=1)
        Hash Cond: (share.transaction_id = transaction.id)
        Buffers: shared hit=8182 read=628
        ->  Seq Scan on transaction_shares share  (cost=0.00..12085.41 rows=500041 width=31) (actual time=0.044..35.135 rows=500041 loops=1)
              Filter: (deleted_at IS NULL)
              Buffers: shared hit=6457 read=628
        ->  Hash  (cost=3225.27..3225.27 rows=99981 width=8) (actual time=39.516..39.516 rows=100000 loops=1)
              Buckets: 131072  Batches: 1  Memory Usage: 4931kB
              Buffers: shared hit=1725
              ->  Seq Scan on transactions transaction  (cost=0.00..3225.27 rows=99981 width=8) (actual time=0.018..24.232 rows=100000 loops=1)
                    Filter: ((deleted_at IS NULL) AND (trip_id = 14) AND ((status)::text = 'ACTIVE'::text))
                    Rows Removed by Filter: 18
                    Buffers: shared hit=1725
Planning:
  Buffers: shared hit=16
Planning Time: 0.551 ms
Execution Time: 307.399 ms
```

핵심 문제:

- 반환 row가 500,000개다.
- `share.id` 정렬이 external merge로 디스크를 사용했다.
- DB 실행 시간도 307ms지만, 더 큰 문제는 500,000 row가 애플리케이션으로 전달된다는 점이다.

### 6.2 개선 shares 집계 조회

개선 후에는 participant 10명 기준 10 row만 반환한다.

```text
Finalize GroupAggregate  (cost=15099.86..15102.99 rows=12 width=40) (actual time=119.050..120.189 rows=10 loops=1)
  Group Key: share.trip_participant_id
  Buffers: shared hit=8076 read=756
  ->  Gather Merge  (cost=15099.86..15102.66 rows=24 width=40) (actual time=119.043..120.175 rows=30 loops=1)
        Workers Planned: 2
        Workers Launched: 2
        Buffers: shared hit=8076 read=756
        ->  Sort  (cost=14099.84..14099.87 rows=12 width=40) (actual time=102.758..102.760 rows=10 loops=3)
              Sort Key: share.trip_participant_id
              Sort Method: quicksort  Memory: 25kB
              Buffers: shared hit=8076 read=756
              Worker 0:  Sort Method: quicksort  Memory: 25kB
              Worker 1:  Sort Method: quicksort  Memory: 25kB
              ->  Partial HashAggregate  (cost=14099.47..14099.62 rows=12 width=40) (actual time=102.728..102.731 rows=10 loops=3)
                    Group Key: share.trip_participant_id
                    Batches: 1  Memory Usage: 24kB
                    Buffers: shared hit=8060 read=756
                    Worker 0:  Batches: 1  Memory Usage: 24kB
                    Worker 1:  Batches: 1  Memory Usage: 24kB
                    ->  Parallel Hash Join  (cost=3342.66..13058.11 rows=208273 width=15) (actual time=22.170..80.565 rows=166667 loops=3)
                          Hash Cond: (share.transaction_id = transaction.id)
                          Buffers: shared hit=8060 read=756
                          ->  Parallel Seq Scan on transaction_shares share  (cost=0.00..9168.50 rows=208350 width=23) (actual time=0.012..27.813 rows=166680 loops=3)
                                Filter: (deleted_at IS NULL)
                                Buffers: shared hit=6329 read=756
                          ->  Parallel Hash  (cost=2607.51..2607.51 rows=58812 width=8) (actual time=21.693..21.693 rows=33333 loops=3)
                                Buckets: 131072  Batches: 1  Memory Usage: 4960kB
                                Buffers: shared hit=1725
                                ->  Parallel Seq Scan on transactions transaction  (cost=0.00..2607.51 rows=58812 width=8) (actual time=0.025..16.050 rows=33333 loops=3)
                                      Filter: ((deleted_at IS NULL) AND (trip_id = 14) AND ((status)::text = 'ACTIVE'::text))
                                      Rows Removed by Filter: 6
                                      Buffers: shared hit=1725
Planning:
  Buffers: shared hit=16
Planning Time: 0.520 ms
Execution Time: 120.302 ms
```

개선 효과:

| 항목 | 기존 | 개선 후 |
|------|-----:|--------:|
| 반환 row | 500,000 | 10 |
| Execution Time | 307.399ms | 120.302ms |
| Sort | external merge, Disk 17,944kB | quicksort, Memory 25kB |
| 애플리케이션 집계 | 필요 | 불필요 |

판단:

- DB 실행 시간은 약 60.87% 감소했다.
- 더 중요한 변화는 반환 row가 500,000개에서 10개로 줄어든 것이다.
- 따라서 애플리케이션 전송/객체화/집계 비용까지 함께 줄어든다.

## 7. 실행계획 근거: payments

### 7.1 기존 payments 원본 조회

```text
Sort  (cost=17425.67..17675.62 rows=99979 width=22) (actual time=116.883..121.631 rows=100000 loops=1)
  Sort Key: payment.id
  Sort Method: external merge  Disk: 3336kB
"  Buffers: shared hit=3059, temp read=417 written=418"
  ->  Hash Join  (cost=4475.03..7071.75 rows=99979 width=22) (actual time=65.443..99.032 rows=100000 loops=1)
        Hash Cond: (payment.transaction_id = transaction.id)
        Buffers: shared hit=3059
        ->  Seq Scan on transaction_payments payment  (cost=0.00..2334.16 rows=100016 width=30) (actual time=0.119..12.558 rows=100016 loops=1)
              Filter: (deleted_at IS NULL)
              Buffers: shared hit=1334
        ->  Hash  (cost=3225.27..3225.27 rows=99981 width=8) (actual time=64.438..64.438 rows=100000 loops=1)
              Buckets: 131072  Batches: 1  Memory Usage: 4931kB
              Buffers: shared hit=1725
              ->  Seq Scan on transactions transaction  (cost=0.00..3225.27 rows=99981 width=8) (actual time=0.089..46.491 rows=100000 loops=1)
                    Filter: ((deleted_at IS NULL) AND (trip_id = 14) AND ((status)::text = 'ACTIVE'::text))
                    Rows Removed by Filter: 18
                    Buffers: shared hit=1725
Planning:
  Buffers: shared hit=16
Planning Time: 1.855 ms
Execution Time: 125.821 ms
```

기존 payments도 100,000 row를 반환하고 external merge sort가 발생했다.

### 7.2 개선 payments 집계 조회

```text
Finalize GroupAggregate  (cost=6714.10..6716.53 rows=18 width=40) (actual time=68.090..69.573 rows=10 loops=1)
  Group Key: payment.trip_participant_id
  Buffers: shared hit=3070
  ->  Gather Merge  (cost=6714.10..6716.17 rows=18 width=40) (actual time=68.081..69.555 rows=20 loops=1)
        Workers Planned: 1
        Workers Launched: 1
        Buffers: shared hit=3070
        ->  Sort  (cost=5714.09..5714.14 rows=18 width=40) (actual time=56.518..56.520 rows=10 loops=2)
              Sort Key: payment.trip_participant_id
              Sort Method: quicksort  Memory: 25kB
              Buffers: shared hit=3070
              Worker 0:  Sort Method: quicksort  Memory: 25kB
              ->  Partial HashAggregate  (cost=5713.49..5713.72 rows=18 width=40) (actual time=56.485..56.489 rows=10 loops=2)
                    Group Key: payment.trip_participant_id
                    Batches: 1  Memory Usage: 24kB
                    Buffers: shared hit=3062
                    Worker 0:  Batches: 1  Memory Usage: 24kB
                    ->  Parallel Hash Join  (cost=3342.66..5419.44 rows=58811 width=14) (actual time=32.810..50.114 rows=50000 loops=2)
                          Hash Cond: (payment.transaction_id = transaction.id)
                          Buffers: shared hit=3062
                          ->  Parallel Seq Scan on transaction_payments payment  (cost=0.00..1922.33 rows=58833 width=22) (actual time=0.013..7.066 rows=50008 loops=2)
                                Filter: (deleted_at IS NULL)
                                Buffers: shared hit=1334
                          ->  Parallel Hash  (cost=2607.51..2607.51 rows=58812 width=8) (actual time=32.544..32.544 rows=50000 loops=2)
                                Buckets: 131072  Batches: 1  Memory Usage: 4960kB
                                Buffers: shared hit=1725
                                ->  Parallel Seq Scan on transactions transaction  (cost=0.00..2607.51 rows=58812 width=8) (actual time=0.053..22.972 rows=50000 loops=2)
                                      Filter: ((deleted_at IS NULL) AND (trip_id = 14) AND ((status)::text = 'ACTIVE'::text))
                                      Rows Removed by Filter: 9
                                      Buffers: shared hit=1725
Planning:
  Buffers: shared hit=16
Planning Time: 0.490 ms
Execution Time: 69.674 ms
```

개선 효과:

| 항목 | 기존 | 개선 후 |
|------|-----:|--------:|
| 반환 row | 100,000 | 10 |
| Execution Time | 125.821ms | 69.674ms |
| Sort | external merge, Disk 3,336kB | quicksort, Memory 25kB |
| 애플리케이션 집계 | 필요 | 불필요 |

판단:

- DB 실행 시간은 약 44.63% 감소했다.
- 반환 row가 100,000개에서 10개로 줄어 애플리케이션 비용이 크게 줄었다.

## 8. 1차 개선 결과

1차 개선 후 k6 결과는 아래와 같았다.

| 지표 | 개선 전 `135224` | 1차 개선 `151157` | 변화 |
|------|-----------------:|------------------:|-----:|
| 전체 p95 | 3.10s | 545.70ms | -82.40% |
| settlement-preview p95 | 3.75s | 724.79ms | -80.67% |
| iterations | 134 | 397 | +196.27% |
| http_reqs | 1,074 | 3,178 | +195.90% |

1차 개선은 `settlement-preview p95 < 800ms` 기준을 통과하게 만들었다. 다만 전체 p95는 `545.70ms`로 500ms 기준을 근소하게 초과했다.

## 9. 2차 판단: projection 우선 사용

### 9.1 왜 projection을 도입했는가?

1차 개선은 600,000 row 반환을 20 row 반환으로 줄였지만, 여전히 매 요청마다 원본 payments/shares를 집계해야 했다.

정산 preview가 필요한 값은 참여자별 paid/share/net이다. 이미 `trip_participant_balance_summaries` 테이블이 이 목적의 summary 구조를 갖고 있으므로, 최신성만 보장된다면 원본 거래 테이블을 매번 집계할 필요가 없다.

따라서 판단은 아래와 같았다.

```text
projection이 최신이면 summary row 10개로 계산한다.
projection이 없거나 stale이면 기존 DB 집계 쿼리로 fallback한다.
```

### 9.2 정합성 안전장치

projection 사용은 성능에는 유리하지만 정합성이 더 중요하다. 그래서 아래 조건을 둔다.

- trip participant 수와 balance summary 수가 맞아야 한다.
- 각 summary의 `projectionVersion`이 `trip.expenseVersion`과 같아야 한다.
- 조건이 맞지 않으면 projection을 사용하지 않고 기존 DB 집계 쿼리로 fallback한다.

거래 생성/수정/삭제 시에는 `TripParticipantBalanceSummaryProjectionService`가 paid/share/net delta를 반영하고, `projectionVersion`을 여행의 expense version과 맞춘다.

### 9.3 결과

| 지표 | 1차 개선 `151157` | projection 개선 `160032` | 변화 |
|------|------------------:|-------------------------:|-----:|
| 전체 p95 | 545.70ms | 279.01ms | -48.87% |
| settlement-preview p95 | 724.79ms | 19.81ms | -97.27% |
| iterations | 397 | 673 | +69.52% |
| http_reqs | 3,178 | 5,386 | +69.48% |

projection 사용 후 settlement-preview p95는 19.81ms로 내려갔다. 이 결과로 settlement-preview 자체는 병목에서 제거됐다.

## 10. 3차 판단: transaction-statistics category 쿼리 개선

### 10.1 새 병목

endpoint별 Trend metric을 추가한 뒤 `20260626-160032` 결과에서 새 병목이 보였다.

```text
endpoint_transaction_statistics_duration
avg = 300.62ms
p95 = 405.02ms
max = 1.63s
```

k6 호출은 아래 endpoint였다.

```text
GET /api/trips/{tripId}/transaction-statistics?groupBy=category
```

부하 테스트 seed는 posts를 만들지 않는다. 즉 결과는 대부분 `UNCATEGORIZED`인데, 기존 category 통계 쿼리는 transaction마다 첫 post를 찾는 correlated subquery를 수행했다.

### 10.2 왜 CTE로 바꿨는가?

기존 방식은 transactions 100,000건 기준으로 posts 탐색이 transaction 수만큼 반복될 수 있다.

개선 방향은 여행의 posts 후보를 먼저 좁힌 뒤, transaction과 조인하는 것이다.

```sql
with first_posts as (
    select distinct on (post.transaction_id)
           post.transaction_id,
           post.category,
           post.occurred_at
    from posts post
    where post.deleted_at is null
      and post.trip_id = :tripId
      and post.transaction_id is not null
    order by post.transaction_id, post.id
)
```

대표 post 기준은 기존과 동일하게 `transaction_id`별 가장 작은 `post.id`다. 기능 의미는 유지하고, 접근 방식만 transaction별 반복 탐색에서 trip 단위 후보 조인으로 바꿨다.

### 10.3 왜 posts 인덱스를 추가했는가?

CTE가 효율적으로 동작하려면 posts를 `trip_id`로 먼저 좁히고, `transaction_id, id` 순서로 첫 post를 찾을 수 있어야 한다.

그래서 아래 인덱스를 추가했다.

```sql
CREATE INDEX IF NOT EXISTS idx_posts_trip_transaction_summary_active
    ON posts (trip_id, transaction_id, id)
    INCLUDE (occurred_at, category)
    WHERE deleted_at IS NULL
      AND transaction_id IS NOT NULL;
```

의도:

- `trip_id`로 해당 여행의 posts 후보를 좁힌다.
- `transaction_id, id` 정렬로 `distinct on (transaction_id) order by transaction_id, id`를 받친다.
- `category`, `occurred_at`은 INCLUDE로 담아 통계 쿼리에서 추가 heap 접근 가능성을 줄인다.
- 거래에 연결된 활성 posts만 partial index에 포함해 인덱스 크기를 줄인다.

### 10.4 결과

| 지표 | 개선 전 `160032` | 개선 후 `162218` | 변화 |
|------|-----------------:|-----------------:|-----:|
| 전체 p95 | 279.01ms | 46.72ms | -83.25% |
| transaction-statistics p95 | 405.02ms | 78.41ms | -80.64% |
| transaction-statistics max | 1.63s | 183.17ms | -88.76% |
| iterations | 673 | 818 | +21.55% |
| http_reqs | 5,386 | 6,546 | +21.54% |

이 개선 후 전체 p95와 transaction-statistics p95 모두 안정권으로 내려왔다.

## 11. tail latency 관측

쿼리 개선 후에도 max spike를 확인하기 위해 k6에 아래를 추가했다.

- p99 summary 출력
- `phase=setup`, `phase=scenario` 태그
- `http_req_duration{phase:scenario}` threshold
- `slow_request` JSON 로그
- `SLOW_REQUEST_MS`

최종 실행 `20260626-181106` 결과는 아래와 같다.

| 지표 | 값 |
|------|---:|
| scenario p95 | 45.38ms |
| scenario p99 | 77.31ms |
| scenario max | 319.26ms |
| settlement-preview p99 | 34.30ms |
| transaction-statistics p99 | 120.70ms |

`SLOW_REQUEST_MS=100` 기준 slow request는 총 40건이었다.

| endpoint | 건수 | max |
|----------|----:|----:|
| transaction-statistics | 24 | 193.71ms |
| trips | 5 | 175.38ms |
| participants | 4 | 310.35ms |
| transactions | 3 | 319.26ms |
| balance-summary | 2 | 175.16ms |
| settlement-preview | 1 | 133.01ms |
| trip-detail | 1 | 130.04ms |

해석:

- 100ms 이상 tail 빈도는 `transaction-statistics`가 가장 높다.
- 하지만 최대값은 특정 시각 `18:12:31`에 `transactions`, `participants`, `transaction-statistics`가 같이 튄 구간에서 발생했다.
- 단일 쿼리만의 고정 병목이라기보다 로컬 Docker/JVM/DB 공유 리소스 순간 지연 가능성이 있다.

## 12. 최종 판단

이번 튜닝의 핵심 판단은 아래와 같다.

| 판단 | 근거 |
|------|------|
| 원본 row 조회를 유지한 채 인덱스만 추가하는 것은 부족하다 | payments/shares 600,000 row 전송과 JVM 집계 비용이 남는다. |
| DB 집계로 바꿔야 한다 | 필요한 값은 participant별 합계이며, 반환 row를 600,000개에서 20개로 줄일 수 있다. |
| projection을 사용하되 fallback이 필요하다 | 최신 summary row 10개로 계산 가능하지만, stale projection은 정합성 위험이 있으므로 version 검증 후 fallback한다. |
| category 통계는 transaction별 posts 탐색을 줄여야 한다 | posts가 없는 데이터에서도 transaction 100,000건 기준 반복 탐색 구조가 tail을 만들었다. |
| p99는 hard threshold가 아니라 관찰 지표다 | 로컬 Docker에서는 순간 spike가 재현되지 않을 수 있고 p99는 샘플 상위 일부에 민감하다. |

## 13. 최신 결과

최초 실행과 최신 실행 비교는 아래와 같다.

| 지표 | 최초 `135224` | 최신 `181106` | 변화 |
|------|--------------:|--------------:|-----:|
| 전체 p95 | 3.10s | 45.39ms | -98.54% |
| settlement-preview p95 | 3.75s | 16.11ms | -99.57% |
| iteration p95 | 9.58s | 1.22s | -87.22% |
| iterations | 134 | 822 | +513.43% |
| http_reqs | 1,074 | 6,578 | +512.48% |

최신 상태에서는 추가 코드 최적화보다 아래 작업이 우선이다.

1. 동일 조건 반복 실행으로 tail 재현성 확인
2. spike 시각의 gateway/main/DB 로그 매칭
3. 실제 데이터에 가까운 posts 포함 seed 추가 검토
4. p99는 관찰 지표로 유지하고, p95 threshold 중심으로 회귀 방지

## 14. 결론

이번 튜닝은 단순 인덱스 추가가 아니라 데이터 흐름 자체를 줄이는 방향으로 진행했다.

```text
600,000 원본 row 반환
-> DB participant별 집계 20 row 반환
-> 최신 projection 10 row 우선 사용
```

이 판단은 실행계획에서 확인된 대량 row 반환, external merge sort, 애플리케이션 집계 비용을 근거로 한다.

그 결과 최초 `settlement-preview p95 3.75s`는 최신 `16.11ms`까지 줄었고, 전체 p95도 `3.10s`에서 `45.39ms`로 개선됐다.
