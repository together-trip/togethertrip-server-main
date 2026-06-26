# settlement-preview 성능 튜닝 사례

## 1. 목적

이 문서는 이슈 #87 `settlement-preview 성능 개선`에서 어떤 근거로 쿼리와 계산 구조를 변경했는지 정리합니다.

주요 질문은 아래와 같습니다.

- 왜 payments/shares 원본 row 조회를 DB 집계로 바꿨는가?
- 왜 단순 인덱스 추가만으로 충분하지 않다고 판단했는가?
- 왜 `trip_participant_balance_summaries` projection을 preview 계산에 사용했는가?
- 왜 `transaction-statistics(groupBy=category)` 쿼리를 first-post derived subquery와 posts 인덱스로 바꿨는가?
- 실행계획 기준으로 어떤 병목이 있었고 어떤 효과가 있었는가?

## 2. 테스트 데이터와 부하 조건

대량 데이터 조건은 아래와 같습니다.

| 항목 | 값 |
|------|---:|
| trips | 1 |
| generated_users | 9 |
| participants | 10 |
| transactions | 100,000 |
| transaction_payments | 100,000 |
| transaction_shares | 500,000 |
| balance_summaries | 10 |

k6 조건은 아래와 같습니다.

| 항목 | 값 |
|------|----|
| VUS | 10 |
| RAMP_UP | 30s |
| HOLD | 1m |
| RAMP_DOWN | 30s |
| SLEEP | 1s |
| 반복당 API | 8개 |

테스트는 운영 SLA 측정이 아니라, 로컬 Docker Compose 환경에서 대량 데이터 성능 회귀를 확인하기 위한 재현 테스트입니다.

## 3. 최초 병목

초기 실행 `20260626-135224` 결과는 아래와 같았습니다.

| 지표 | 결과 |
|------|-----:|
| 전체 http p95 | 3.10s |
| settlement-preview avg | 2.30s |
| settlement-preview p95 | 3.75s |
| settlement-preview max | 4.48s |
| http_req_failed | 0.00% |
| checks | 100% |

API는 실패하지 않았지만, 대량 정산 데이터에서 응답 시간이 초 단위로 증가했습니다. 따라서 기능 오류가 아니라 정산 preview 계산 경로의 데이터 접근 비용이 문제라고 판단했습니다.

## 4. 기존 구조의 문제

기존 `SettlementCalculationService.calculate()` 흐름은 아래와 같았습니다.

1. 해당 여행의 활성 거래를 찾습니다.
2. 연결된 payment 원본 row를 모두 조회합니다.
3. 연결된 share 원본 row를 모두 조회합니다.
4. 애플리케이션에서 participant별 paid/share 합계를 만듭니다.
5. paid/share/net/transfer 응답을 계산합니다.

이 구조는 AI가 기능 구현 과정에서 정산 preview의 최종 필요 데이터와 조회 단위를 제대로 분리하지 못해 만들어진 설계 실수였습니다. 정산 preview는 원본 payment/share 목록을 화면에 그대로 보여주는 기능이 아니라 participant별 합계만 필요한 기능입니다. 그런데 기존 구현은 "정산 계산에 payment/share가 필요하다"를 "payment/share 엔티티 전체를 모두 조회해야 한다"로 잘못 해석했습니다.

기존 payment 조회 JPQL은 아래 형태였습니다.

```kotlin
@Query(
    """
    select payment
    from TransactionPayment payment
    join fetch payment.tripParticipant participant
    join fetch payment.transaction transaction
    where payment.deletedAt is null
      and transaction.deletedAt is null
      and transaction.trip.id = :tripId
      and transaction.status = :status
    order by payment.id asc
    """
)
fun findSettlementPayments(
    @Param("tripId") tripId: Long,
    @Param("status") status: TransactionStatus = TransactionStatus.ACTIVE,
): List<TransactionPayment>
```

기존 share 조회 JPQL도 동일한 패턴이었습니다.

```kotlin
@Query(
    """
    select share
    from TransactionShare share
    join fetch share.tripParticipant participant
    join fetch share.transaction transaction
    where share.deletedAt is null
      and transaction.deletedAt is null
      and transaction.trip.id = :tripId
      and transaction.status = :status
    order by share.id asc
    """
)
fun findSettlementShares(
    @Param("tripId") tripId: Long,
    @Param("status") status: TransactionStatus = TransactionStatus.ACTIVE,
): List<TransactionShare>
```

실행계획에서 확인한 SQL 관점으로 보면 각각 아래와 같은 원본 row 조회였습니다.

```sql
select payment.*
from transaction_payments payment
join transactions transaction on transaction.id = payment.transaction_id
where payment.deleted_at is null
  and transaction.deleted_at is null
  and transaction.trip_id = :tripId
  and transaction.status = 'ACTIVE'
order by payment.id asc;
```

```sql
select share.*
from transaction_shares share
join transactions transaction on transaction.id = share.transaction_id
where share.deleted_at is null
  and transaction.deleted_at is null
  and transaction.trip_id = :tripId
  and transaction.status = 'ACTIVE'
order by share.id asc;
```

문제는 `order by payment.id asc`, `order by share.id asc`까지 포함해 원본 엔티티 목록을 안정적인 순서로 반환하도록 만들었다는 점입니다. 이 정렬은 정산 preview 계산 결과에는 필요하지 않습니다. 특히 shares는 500,000 row를 `share.id` 기준으로 정렬하면서 external merge sort와 temp file I/O를 만들었습니다.

대량 데이터에서는 애플리케이션으로 넘어오는 row 수가 아래와 같았습니다.

| 조회 대상 | row 수 |
|----------|------:|
| payments | 100,000 |
| shares | 500,000 |
| 합계 | 600,000 |

정산 preview에 실제로 필요한 값은 각 원본 row가 아니라 참여자별 총액입니다.

```text
필요한 최종 입력:
participant별 paid total
participant별 share total
```

참여자가 10명인 테스트 데이터에서는 최종적으로 필요한 row가 payment 10개, share 10개 수준입니다. 따라서 600,000 row를 애플리케이션으로 넘겨 Kotlin에서 `groupBy`하는 구조는 데이터 전송량, JDBC materialization, JVM 메모리, Kotlin 컬렉션 집계 비용을 모두 키웁니다.

## 5. 1차 판단: DB 집계로 변경

### 5.1 판단 근거

기존 방식은 인덱스를 추가해도 원본 row를 600,000개 반환하는 구조 자체가 유지됩니다. 인덱스가 scan/sort 비용을 일부 줄일 수는 있지만, 아래 비용은 계속 남습니다.

- DB에서 600,000 row를 결과로 만들어야 함
- JDBC로 600,000 row를 전송해야 함
- 애플리케이션에서 600,000 row 객체를 만들어야 함
- Kotlin에서 participant별 그룹 집계를 해야 함

따라서 핵심은 “원본 row 조회를 빠르게”가 아니라 “원본 row를 반환하지 않게” 만드는 것이었습니다.

개선 방향은 아래와 같습니다.

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

동일하게 payments도 participant별 sum만 반환하도록 바꿨습니다.

### 5.2 추가 인덱스 선정 근거

DB 집계 쿼리의 조인과 집계를 받치기 위해 아래 인덱스를 추가했습니다.

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

인덱스는 단순히 "조회 조건에 있는 컬럼을 넣는 것"이 아니라, 쿼리의 접근 순서와 비용 모델을 기준으로 선정했습니다.

#### 5.2.1 `transactions(trip_id, status, id)`

settlement-preview의 driving 조건은 `transactions`의 `trip_id`, `status`, `deleted_at`입니다.

```sql
where transaction.deleted_at is null
  and transaction.trip_id = :tripId
  and transaction.status = 'ACTIVE'
```

따라서 선행 컬럼은 선택도가 있는 동등 조건인 `trip_id`, `status`가 되어야 합니다. `id`는 where 조건에서 직접 필터링하지 않지만, 이후 `transaction_payments.transaction_id`, `transaction_shares.transaction_id`와 조인되는 키입니다. 즉 이 인덱스의 목적은 특정 여행의 활성 거래를 먼저 좁힌 뒤, 조인에 필요한 transaction id 목록을 인덱스 순서에서 바로 공급하는 것입니다.

`deleted_at IS NULL`은 partial index 조건으로 분리했습니다. 서비스의 read path는 soft delete된 row를 항상 제외하므로, 삭제 row를 인덱스에 넣으면 인덱스 크기와 cache footprint만 커집니다. partial index로 active row만 유지하면 동일한 쿼리에서 더 적은 page를 읽을 가능성이 높아지고, autovacuum 이후 visibility map이 충분히 관리되는 환경에서는 index-only scan 가능성도 더 좋아집니다.

컬럼 순서는 아래 이유로 결정했습니다.

| 위치 | 컬럼 | 이유 |
|------|------|------|
| 1 | `trip_id` | API가 특정 여행 단위로 조회하므로 가장 먼저 탐색 범위를 제한합니다. |
| 2 | `status` | 정산 계산 대상은 활성 거래이므로 `trip_id` 내부에서 ACTIVE 거래만 추가로 좁힙니다. |
| 3 | `id` | 조인 키입니다. 필터 후 child table 접근에 필요한 값을 정렬된 상태로 제공합니다. |

`status, trip_id, id` 순서로 두지 않은 이유는 이 API의 모든 호출이 여행 단위이기 때문입니다. `status='ACTIVE'`는 서비스 전반에서 흔한 값일 가능성이 높아 단독 선행 컬럼으로서 선택도가 낮습니다. 반면 `trip_id`를 선행시키면 특정 여행의 거래 범위로 먼저 좁혀지므로, 이후 `status` 필터와 child table 조인 비용이 예측 가능해집니다.

#### 5.2.2 `transaction_payments(transaction_id, trip_participant_id) INCLUDE (base_amount)`

payments 집계 쿼리의 핵심은 `transactions`에서 얻은 활성 거래 id와 `transaction_payments.transaction_id`를 조인한 뒤, `trip_participant_id`별 `sum(base_amount)`를 계산하는 것입니다.

```sql
join transaction_payments payment
  on payment.transaction_id = transaction.id
where payment.deleted_at is null
group by payment.trip_participant_id
```

선행 컬럼을 `transaction_id`로 둔 이유는 조인 조건이 먼저 적용되기 때문입니다. 이 쿼리는 특정 participant의 payment를 찾는 쿼리가 아니라, 특정 여행의 활성 transaction 집합에 딸린 payment를 모두 모아 participant별로 집계하는 쿼리입니다. 따라서 `trip_participant_id`를 선행시키면 participant별 범위는 잡을 수 있지만, 조인 대상 transaction 집합을 찾는 접근에는 불리합니다.

`trip_participant_id`를 두 번째 컬럼으로 둔 이유는 집계 키이기 때문입니다. PostgreSQL이 항상 이 인덱스를 이용해 group by 정렬을 제거한다고 보장할 수는 없지만, 조인으로 읽은 payment row에서 집계 키를 인덱스 tuple 안에서 바로 얻을 수 있습니다. 또한 데이터 분포나 통계가 바뀌어 nested loop 또는 merge 계열 계획을 선택하는 경우에는 `transaction_id`로 좁힌 뒤 participant key를 읽는 비용이 낮아집니다.

`base_amount`는 key 컬럼이 아니라 `INCLUDE`로 두었습니다. `base_amount`는 범위 탐색이나 정렬에 쓰이지 않고 `sum()`의 입력값으로만 필요합니다. 이를 key 컬럼에 포함하면 btree ordering 유지 비용과 인덱스 fan-out 측면에서 불필요한 부담이 생깁니다. `INCLUDE`로 둠으로써 covering index 효과는 얻고, 비교 key 크기는 최소화했습니다.

#### 5.2.3 `transaction_shares(transaction_id, trip_participant_id) INCLUDE (base_share_amount)`

shares도 payments와 같은 접근 패턴입니다.

```sql
join transaction_shares share
  on share.transaction_id = transaction.id
where share.deleted_at is null
group by share.trip_participant_id
```

차이는 row 수입니다. 테스트 데이터 기준 shares는 500,000 row로 payments의 5배입니다. 따라서 shares에서는 인덱스의 page 수, temp sort 발생, 애플리케이션 전송량이 더 민감하게 나타났습니다.

기존 실행계획에서 가장 명확한 문제는 `share.id` 기준 정렬이었습니다.

```text
Sort Method: external merge  Disk: 17944kB
temp read=2243 written=2251
```

이는 단순히 정렬 시간이 길다는 의미를 넘어, `work_mem` 안에서 정렬을 끝내지 못하고 temp file I/O가 발생했다는 의미입니다. 기존 쿼리는 원본 row를 `share.id` 순서로 500,000건 반환해야 했기 때문에, 인덱스를 추가하더라도 "500,000건을 정렬해서 반환하는 문제"가 남습니다.

개선 쿼리는 `share.id` 정렬 요구를 제거하고, `trip_participant_id` 기준 집계 결과만 반환합니다. 실행계획도 아래처럼 바뀌었습니다.

```text
Partial HashAggregate
Group Key: share.trip_participant_id
Sort Method: quicksort  Memory: 25kB
rows=10
```

즉 인덱스는 `transaction_id` 조인을 받치고, 쿼리 형태 변경은 대량 sort와 반환 row 수를 제거합니다. 이 둘을 같이 적용해야 효과가 안정적입니다.

#### 5.2.4 왜 인덱스만으로는 부족했는가?

이 케이스의 병목은 단일 table lookup이 아니라 "대량 row를 끝까지 반환하는 쿼리 shape"였습니다. 기존 shares 계획은 DB 내부 실행만 보더라도 500,000 row 반환과 external merge sort가 있었고, 이후에도 JDBC 전송, ResultSet materialization, Kotlin collection 생성, 애플리케이션 group by 비용이 이어졌습니다.

따라서 인덱스의 역할은 조인 후보를 줄이고 필요한 컬럼을 index tuple에서 공급하는 것이며, 가장 큰 개선은 쿼리 shape을 원본 row 반환에서 DB 집계 반환으로 바꾼 점입니다. 인덱스는 그 집계 쿼리가 운영 데이터 분포에서도 불필요한 heap 접근과 조인 비용을 줄일 수 있도록 받치는 장치입니다.

## 6. 실행계획 근거: shares

### 6.1 기존 shares 원본 조회

기존 shares 조회는 500,000 row를 반환하고 `share.id` 기준 external merge sort가 발생했습니다.

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

핵심 문제는 아래와 같습니다.

- 반환 row가 500,000개입니다.
- `share.id` 정렬이 external merge로 디스크를 사용했습니다.
- DB 실행 시간도 307ms지만, 더 큰 문제는 500,000 row가 애플리케이션으로 전달된다는 점입니다.

### 6.2 개선 shares 집계 조회

개선 후에는 participant 10명 기준 10 row만 반환합니다.

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

판단은 아래와 같습니다.

- DB 실행 시간은 약 60.87% 감소했습니다.
- 더 중요한 변화는 반환 row가 500,000개에서 10개로 줄어든 것입니다.
- 따라서 애플리케이션 전송/객체화/집계 비용까지 함께 줄어듭니다.

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

기존 payments도 100,000 row를 반환하고 external merge sort가 발생했습니다.

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

판단은 아래와 같습니다.

- DB 실행 시간은 약 44.63% 감소했습니다.
- 반환 row가 100,000개에서 10개로 줄어 애플리케이션 비용이 크게 줄었습니다.

## 8. 1차 개선 결과

1차 개선 후 k6 결과는 아래와 같았습니다.

| 지표 | 개선 전 `135224` | 1차 개선 `151157` | 변화 |
|------|-----------------:|------------------:|-----:|
| 전체 p95 | 3.10s | 545.70ms | -82.40% |
| settlement-preview p95 | 3.75s | 724.79ms | -80.67% |
| iterations | 134 | 397 | +196.27% |
| http_reqs | 1,074 | 3,178 | +195.90% |

1차 개선은 `settlement-preview p95 < 800ms` 기준을 통과하게 만들었습니다. 다만 전체 p95는 `545.70ms`로 500ms 기준을 근소하게 초과했습니다.

## 9. 2차 판단: projection 우선 사용

### 9.1 왜 projection을 도입했는가?

1차 개선은 600,000 row 반환을 20 row 반환으로 줄였지만, 여전히 매 요청마다 원본 payments/shares를 집계해야 했습니다.

정산 preview가 필요한 값은 참여자별 paid/share/net입니다. 이미 `trip_participant_balance_summaries` 테이블이 이 목적의 summary 구조를 갖고 있으므로, 최신성만 보장된다면 원본 거래 테이블을 매번 집계할 필요가 없습니다.

따라서 판단은 아래와 같았습니다.

```text
projection이 최신이면 summary row 10개로 계산합니다.
projection이 없거나 stale이면 기존 DB 집계 쿼리로 fallback합니다.
```

### 9.2 정합성 안전장치

projection 사용은 성능에는 유리하지만 정합성이 더 중요합니다. 그래서 아래 조건을 둡니다.

- trip participant 수와 balance summary 수가 맞아야 합니다.
- 각 summary의 `projectionVersion`이 `trip.expenseVersion`과 같아야 합니다.
- 조건이 맞지 않으면 projection을 사용하지 않고 기존 DB 집계 쿼리로 fallback합니다.

거래 생성/수정/삭제 시에는 `TripParticipantBalanceSummaryProjectionService`가 paid/share/net delta를 반영하고, `projectionVersion`을 여행의 expense version과 맞춥니다.

### 9.3 결과

| 지표 | 1차 개선 `151157` | projection 개선 `160032` | 변화 |
|------|------------------:|-------------------------:|-----:|
| 전체 p95 | 545.70ms | 279.01ms | -48.87% |
| settlement-preview p95 | 724.79ms | 19.81ms | -97.27% |
| iterations | 397 | 673 | +69.52% |
| http_reqs | 3,178 | 5,386 | +69.48% |

projection 사용 후 settlement-preview p95는 19.81ms로 내려갔습니다. 이 결과로 settlement-preview 자체는 병목에서 제거됐습니다.

## 10. 3차 판단: transaction-statistics category 쿼리 개선

### 10.1 새 병목

endpoint별 Trend metric을 추가한 뒤 `20260626-160032` 결과에서 새 병목이 보였습니다.

```text
endpoint_transaction_statistics_duration
avg = 300.62ms
p95 = 405.02ms
max = 1.63s
```

k6 호출은 아래 endpoint였습니다.

```text
GET /api/trips/{tripId}/transaction-statistics?groupBy=category
```

부하 테스트 seed는 posts를 만들지 않습니다. 즉 결과는 대부분 `UNCATEGORIZED`인데, 기존 category 통계 쿼리는 transaction마다 첫 post를 찾는 correlated subquery를 수행했습니다.

기존 category 통계 쿼리는 아래 형태였습니다.

```sql
select coalesce(nullif(trim(post.category), ''), 'UNCATEGORIZED') as item_key,
       coalesce(nullif(trim(post.category), ''), 'UNCATEGORIZED') as item_label,
       count(tx.id) as transaction_count,
       coalesce(sum(tx.base_amount), 0) as total_base_amount
from transactions tx
left join posts post on post.id = (
    select min(candidate.id)
    from posts candidate
    where candidate.transaction_id = tx.id
      and candidate.deleted_at is null
)
where tx.deleted_at is null
  and tx.trip_id = :tripId
  and tx.status = :status
  and (:fromFilterEnabled = false or coalesce(post.occurred_at, tx.created_at) >= :from)
  and (:toFilterEnabled = false or coalesce(post.occurred_at, tx.created_at) < :toExclusive)
group by coalesce(nullif(trim(post.category), ''), 'UNCATEGORIZED')
order by total_base_amount desc, item_key asc;
```

이 쿼리는 `transactions` 후보 row마다 `posts candidate`에서 `min(candidate.id)`를 찾습니다. 실행계획에서는 이 부분이 `SubPlan 1`로 분리되고, 테스트 데이터 기준 `loops=100000`으로 반복 실행됐습니다.

### 10.2 왜 first-post derived subquery로 바꿨는가?

기존 방식은 transactions 100,000건 기준으로 posts 탐색이 transaction 수만큼 반복될 수 있습니다.

개선 방향은 여행의 posts 후보를 먼저 좁힌 뒤, transaction과 조인하는 것입니다.

```sql
select coalesce(nullif(trim(post.category), ''), 'UNCATEGORIZED') as item_key,
       coalesce(nullif(trim(post.category), ''), 'UNCATEGORIZED') as item_label,
       count(tx.id) as transaction_count,
       coalesce(sum(tx.base_amount), 0) as total_base_amount
from transactions tx
left join (
    select distinct on (candidate.transaction_id)
           candidate.transaction_id,
           candidate.category,
           candidate.occurred_at
    from posts candidate
    where candidate.deleted_at is null
      and candidate.trip_id = :tripId
      and candidate.transaction_id is not null
    order by candidate.transaction_id, candidate.id
) post on post.transaction_id = tx.id
where tx.deleted_at is null
  and tx.trip_id = :tripId
  and tx.status = :status
  and (:fromFilterEnabled = false or coalesce(post.occurred_at, tx.created_at) >= :from)
  and (:toFilterEnabled = false or coalesce(post.occurred_at, tx.created_at) < :toExclusive)
group by coalesce(nullif(trim(post.category), ''), 'UNCATEGORIZED')
order by total_base_amount desc, item_key asc;
```

대표 post 기준은 기존과 동일하게 `transaction_id`별 가장 작은 `post.id`입니다. 기능 의미는 유지하고, 접근 방식만 transaction별 반복 탐색에서 trip 단위 후보 조인으로 바꿨습니다.

### 10.3 실행계획 비교

#### 10.3.1 기존 correlated subquery 실행계획

```text
Sort  (cost=11340.74..11340.79 rows=20 width=104) (actual time=250.792..250.794 rows=1 loops=1)
  Sort Key: (COALESCE(sum(tx.base_amount), '0'::numeric)) DESC, (COALESCE(NULLIF(TRIM(BOTH FROM post.category), ''::text), 'UNCATEGORIZED'::text))
  Sort Method: quicksort  Memory: 25kB
  Buffers: shared hit=101726
  ->  HashAggregate  (cost=11339.86..11340.31 rows=20 width=104) (actual time=250.784..250.785 rows=1 loops=1)
        Group Key: COALESCE(NULLIF(TRIM(BOTH FROM post.category), ''::text), 'UNCATEGORIZED'::text)
        Batches: 1  Memory Usage: 24kB
        Buffers: shared hit=101726
        ->  Hash Left Join  (cost=1.45..10590.00 rows=99981 width=124) (actual time=0.072..236.576 rows=100000 loops=1)
              Hash Cond: ((SubPlan 1) = post.id)
              Buffers: shared hit=101726
              ->  Seq Scan on transactions tx  (cost=0.00..3225.27 rows=99981 width=14) (actual time=0.022..15.538 rows=100000 loops=1)
                    Filter: ((deleted_at IS NULL) AND (trip_id = 14) AND ((status)::text = 'ACTIVE'::text))
                    Rows Removed by Filter: 18
                    Buffers: shared hit=1725
              ->  Hash  (cost=1.20..1.20 rows=20 width=86) (actual time=0.025..0.026 rows=20 loops=1)
                    Buckets: 1024  Batches: 1  Memory Usage: 9kB
                    Buffers: shared hit=1
                    ->  Seq Scan on posts post  (cost=0.00..1.20 rows=20 width=86) (actual time=0.007..0.012 rows=20 loops=1)
                          Buffers: shared hit=1
              SubPlan 1
                ->  Aggregate  (cost=1.25..1.26 rows=1 width=8) (actual time=0.002..0.002 rows=1 loops=100000)
                      Buffers: shared hit=100000
                      ->  Seq Scan on posts candidate  (cost=0.00..1.25 rows=1 width=8) (actual time=0.002..0.002 rows=0 loops=100000)
                            Filter: ((deleted_at IS NULL) AND (transaction_id = tx.id))
                            Rows Removed by Filter: 20
                            Buffers: shared hit=100000
Planning Time: 0.606 ms
Execution Time: 250.848 ms
```

기존 쿼리의 핵심 병목은 `SubPlan 1`입니다. `transactions`에서 100,000 row를 읽은 뒤 각 row마다 `posts candidate`를 다시 확인했습니다. 테스트 데이터에서는 posts가 거의 없어서 각 probe는 작지만, `loops=100000`으로 반복되면서 `Buffers: shared hit=100000`이 추가됐습니다.

#### 10.3.2 개선 derived subquery 실행계획

```text
Sort  (cost=4498.54..4498.55 rows=1 width=104) (actual time=37.338..38.285 rows=1 loops=1)
  Sort Key: (COALESCE(sum(tx.base_amount), '0'::numeric)) DESC, (COALESCE(NULLIF(TRIM(BOTH FROM post.category), ''::text), 'UNCATEGORIZED'::text))
  Sort Method: quicksort  Memory: 25kB
  Buffers: shared hit=1755
  ->  Finalize GroupAggregate  (cost=4498.38..4498.53 rows=1 width=104) (actual time=37.322..38.269 rows=1 loops=1)
        Group Key: (COALESCE(NULLIF(TRIM(BOTH FROM post.category), ''::text), 'UNCATEGORIZED'::text))
        Buffers: shared hit=1755
        ->  Gather Merge  (cost=4498.38..4498.50 rows=1 width=150) (actual time=37.291..38.247 rows=2 loops=1)
              Workers Planned: 1
              Workers Launched: 1
              Buffers: shared hit=1755
              ->  Sort  (cost=3498.37..3498.38 rows=1 width=150) (actual time=29.891..29.893 rows=1 loops=2)
                    Sort Key: (COALESCE(NULLIF(TRIM(BOTH FROM post.category), ''::text), 'UNCATEGORIZED'::text))
                    Sort Method: quicksort  Memory: 25kB
                    Buffers: shared hit=1755
                    Worker 0:  Sort Method: quicksort  Memory: 25kB
                    ->  Partial HashAggregate  (cost=3498.34..3498.36 rows=1 width=150) (actual time=29.794..29.795 rows=1 loops=2)
                          Group Key: COALESCE(NULLIF(TRIM(BOTH FROM post.category), ''::text), 'UNCATEGORIZED'::text)
                          Batches: 1  Memory Usage: 24kB
                          Buffers: shared hit=1747
                          Worker 0:  Batches: 1  Memory Usage: 24kB
                          ->  Hash Left Join  (cost=1.29..3057.25 rows=58812 width=124) (actual time=0.288..20.795 rows=50000 loops=2)
                                Hash Cond: (tx.id = post.transaction_id)
                                Buffers: shared hit=1747
                                ->  Parallel Seq Scan on transactions tx  (cost=0.00..2607.51 rows=58812 width=14) (actual time=0.039..15.893 rows=50000 loops=2)
                                      Filter: ((deleted_at IS NULL) AND (trip_id = 14) AND ((status)::text = 'ACTIVE'::text))
                                      Rows Removed by Filter: 9
                                      Buffers: shared hit=1725
                                ->  Hash  (cost=1.28..1.28 rows=1 width=86) (actual time=0.215..0.215 rows=0 loops=2)
                                      Buckets: 1024  Batches: 1  Memory Usage: 8kB
                                      Buffers: shared hit=10
                                      ->  Subquery Scan on post  (cost=1.26..1.28 rows=1 width=86) (actual time=0.214..0.215 rows=0 loops=2)
                                            Buffers: shared hit=10
                                            ->  Unique  (cost=1.26..1.27 rows=1 width=102) (actual time=0.214..0.214 rows=0 loops=2)
                                                  Buffers: shared hit=10
                                                  ->  Sort  (cost=1.26..1.26 rows=1 width=102) (actual time=0.213..0.214 rows=0 loops=2)
                                                        Sort Key: candidate.transaction_id, candidate.id
                                                        Sort Method: quicksort  Memory: 25kB
                                                        Buffers: shared hit=10
                                                        Worker 0:  Sort Method: quicksort  Memory: 25kB
                                                        ->  Seq Scan on posts candidate  (cost=0.00..1.25 rows=1 width=102) (actual time=0.151..0.151 rows=0 loops=2)
                                                              Filter: ((deleted_at IS NULL) AND (transaction_id IS NOT NULL) AND (trip_id = 14))
                                                              Rows Removed by Filter: 20
                                                              Buffers: shared hit=2
Planning Time: 1.275 ms
Execution Time: 38.458 ms
```

개선 쿼리는 `posts candidate`를 transaction마다 반복 탐색하지 않습니다. `trip_id = 14`인 posts 후보를 subquery에서 먼저 만들고, 그 결과를 `tx.id = post.transaction_id`로 hash join합니다. 테스트 데이터처럼 해당 여행에 연결 post가 없으면 subquery 결과가 빠르게 비고, 이후에는 transactions 100,000건 집계 비용만 남습니다.

비교 결과는 아래와 같습니다.

| 항목 | 기존 correlated subquery | 개선 derived subquery |
|------|-------------------------:|----------------------:|
| Execution Time | 250.848ms | 38.458ms |
| Buffers shared hit | 101,726 | 1,755 |
| posts 반복 탐색 | `SubPlan 1`, `loops=100000` | 없음 |
| transactions scan | 100,000 rows, 단일 seq scan | 100,000 rows, parallel seq scan |
| 최종 sort memory | 25kB | 25kB |
| aggregate memory | 24kB | 24kB |

따라서 이 튜닝의 핵심은 derived subquery 문법 자체가 아니라, `posts` 첫 row 탐색을 transaction별 반복 실행에서 여행 단위 후보 집합 1회 구성으로 바꾼 점입니다.

### 10.4 posts 인덱스 선정 근거

derived subquery가 효율적으로 동작하려면 posts를 `trip_id`로 먼저 좁히고, `transaction_id, id` 순서로 첫 post를 찾을 수 있어야 합니다.

그래서 아래 인덱스를 추가했습니다.

```sql
CREATE INDEX IF NOT EXISTS idx_posts_trip_transaction_summary_active
    ON posts (trip_id, transaction_id, id)
    INCLUDE (occurred_at, category)
    WHERE deleted_at IS NULL
      AND transaction_id IS NOT NULL;
```

이 인덱스도 컬럼 존재 여부가 아니라 쿼리 shape 기준으로 선정했습니다.

#### 10.4.1 `trip_id`를 선행 컬럼으로 둔 이유

category 통계 API는 특정 여행의 거래 통계를 계산합니다. 따라서 posts를 읽을 때도 전체 posts에서 `transaction_id`를 찾는 것보다, 먼저 `trip_id = :tripId`로 해당 여행의 post 후보만 좁히는 편이 비용 모델상 유리합니다.

기존 `posts(transaction_id, id)` 인덱스는 transaction 한 건의 첫 post를 찾는 접근에는 맞지만, 이번 병목처럼 여행 단위 통계에서 transaction 100,000건을 한 번에 집계하는 접근에는 충분하지 않습니다. transaction별로 posts를 반복 탐색하는 구조가 되면, posts가 없거나 매우 적은 데이터에서도 transaction 수만큼 index probe가 발생할 수 있습니다.

새 쿼리는 반대로 접근합니다.

```text
기존: transactions 100,000건 -> 각 transaction마다 posts 첫 row 탐색
개선: trip posts 후보 1회 스캔 -> transaction_id별 첫 post 구성 -> transactions와 조인
```

이 구조에서는 `trip_id`가 선행 컬럼이어야 subquery가 필요한 여행의 posts 범위만 읽을 수 있습니다.

#### 10.4.2 `transaction_id, id` 순서가 필요한 이유

derived subquery는 아래 문법을 사용합니다.

```sql
select distinct on (post.transaction_id)
       post.transaction_id,
       post.category,
       post.occurred_at
from posts post
where post.deleted_at is null
  and post.trip_id = :tripId
  and post.transaction_id is not null
order by post.transaction_id, post.id
```

PostgreSQL의 `distinct on (transaction_id) order by transaction_id, id`는 `transaction_id` 그룹마다 가장 작은 `id`를 대표 row로 선택합니다. 따라서 인덱스가 `(trip_id, transaction_id, id)` 순서를 제공하면, `trip_id` 범위 안에서 이미 `transaction_id, id` 순서로 정렬된 stream을 읽을 수 있습니다.

이 정렬 순서가 없으면 DB는 subquery 내부에서 `transaction_id, id` 기준 정렬을 별도로 수행해야 할 가능성이 커집니다. 대량 posts 데이터에서는 이 정렬이 `Sort`, `Unique`, temp file I/O로 이어질 수 있습니다. 즉 이 인덱스의 핵심은 `distinct on`이 요구하는 ordering을 인덱스 순서와 맞추는 것입니다.

#### 10.4.3 INCLUDE 컬럼과 partial 조건

`category`, `occurred_at`은 대표 post를 만든 뒤 통계 응답을 계산하는 데 필요하지만, 탐색 조건이나 정렬 조건은 아닙니다. 따라서 key 컬럼이 아니라 `INCLUDE`로 넣었습니다. 이렇게 하면 btree 비교 key는 `(trip_id, transaction_id, id)`로 유지하면서, subquery가 필요한 payload 컬럼을 인덱스에서 바로 읽을 수 있습니다.

partial 조건도 의도적으로 좁혔습니다.

```sql
WHERE deleted_at IS NULL
  AND transaction_id IS NOT NULL
```

category 통계에서 필요한 것은 거래에 연결된 활성 post뿐입니다. 삭제된 post와 거래에 연결되지 않은 post를 인덱스에 포함하면, 이번 쿼리에서는 사용하지 않는 row 때문에 인덱스 크기와 cache pressure만 증가합니다. partial index로 필요한 row만 유지하면 동일한 workload에서 더 작은 인덱스를 읽고, vacuum 이후 index-only scan 가능성도 높아집니다.

#### 10.4.4 왜 correlated subquery를 유지하지 않았는가?

correlated subquery는 단건 조회나 소량 transaction에는 직관적이고 충분히 빠를 수 있습니다. 하지만 이번 부하 조건은 transaction 100,000건을 통계로 묶는 경로입니다. 이 경우 transaction별 첫 post 탐색은 N번 반복되는 접근이 되며, posts가 없더라도 "없음을 확인하는 probe"가 반복됩니다.

derived subquery 방식은 posts 후보를 여행 단위로 한 번 구성한 뒤 transactions와 조인합니다. 이는 transaction 수가 증가할수록 유리한 형태입니다. 특히 seed처럼 posts가 없는 경우에도 subquery 결과가 빠르게 비어 있음을 확인하고, transactions 집계는 `UNCATEGORIZED`로 처리할 수 있습니다.

### 10.5 결과

| 지표 | 개선 전 `160032` | 개선 후 `210635` | 변화 |
|------|-----------------:|-----------------:|-----:|
| 전체 p95 | 279.01ms | 41.95ms | -84.96% |
| transaction-statistics p95 | 405.02ms | 70.23ms | -82.66% |
| transaction-statistics max | 1.63s | 304.88ms | -81.30% |
| iterations | 673 | 836 | +24.22% |
| http_reqs | 5,386 | 6,690 | +24.21% |

이 개선 후 전체 p95와 transaction-statistics p95 모두 안정권으로 내려왔습니다.

## 11. tail latency 관측

쿼리 개선 후에도 max spike를 확인하기 위해 k6에 아래를 추가했습니다.

- p99 summary 출력
- `phase=setup`, `phase=scenario` 태그
- `http_req_duration{phase:scenario}` threshold
- `slow_request` JSON 로그
- `SLOW_REQUEST_MS`

최종 실행 `20260626-181106` 결과는 아래와 같습니다.

| 지표 | 값 |
|------|---:|
| scenario p95 | 45.38ms |
| scenario p99 | 77.31ms |
| scenario max | 319.26ms |
| settlement-preview p99 | 34.30ms |
| transaction-statistics p99 | 120.70ms |

`SLOW_REQUEST_MS=100` 기준 slow request는 총 40건이었습니다.

| endpoint | 건수 | max |
|----------|----:|----:|
| transaction-statistics | 24 | 193.71ms |
| trips | 5 | 175.38ms |
| participants | 4 | 310.35ms |
| transactions | 3 | 319.26ms |
| balance-summary | 2 | 175.16ms |
| settlement-preview | 1 | 133.01ms |
| trip-detail | 1 | 130.04ms |

해석은 아래와 같습니다.

- 100ms 이상 tail 빈도는 `transaction-statistics`가 가장 높습니다.
- 하지만 최대값은 특정 시각 `18:12:31`에 `transactions`, `participants`, `transaction-statistics`가 같이 튄 구간에서 발생했습니다.
- 단일 쿼리만의 고정 병목이라기보다 로컬 Docker/JVM/DB 공유 리소스 순간 지연 가능성이 있습니다.

## 12. 최종 판단

이번 튜닝의 핵심 판단은 아래와 같습니다.

| 판단 | 근거 |
|------|------|
| 원본 row 조회를 유지한 채 인덱스만 추가하는 것은 부족합니다 | payments/shares 600,000 row 전송과 JVM 집계 비용이 남습니다. |
| DB 집계로 바꿔야 합니다 | 필요한 값은 participant별 합계이며, 반환 row를 600,000개에서 20개로 줄일 수 있습니다. |
| projection을 사용하되 fallback이 필요합니다 | 최신 summary row 10개로 계산 가능하지만, stale projection은 정합성 위험이 있으므로 version 검증 후 fallback합니다. |
| category 통계는 transaction별 posts 탐색을 줄여야 합니다 | posts가 없는 데이터에서도 transaction 100,000건 기준 반복 탐색 구조가 tail을 만들었습니다. |
| p99는 hard threshold가 아니라 관찰 지표입니다 | 로컬 Docker에서는 순간 spike가 재현되지 않을 수 있고 p99는 샘플 상위 일부에 민감합니다. |

## 13. 최신 결과

최초 실행과 최신 실행 비교는 아래와 같습니다.

| 지표 | 최초 `135224` | 최신 `181106` | 변화 |
|------|--------------:|--------------:|-----:|
| 전체 p95 | 3.10s | 45.39ms | -98.54% |
| settlement-preview p95 | 3.75s | 16.11ms | -99.57% |
| iteration p95 | 9.58s | 1.22s | -87.22% |
| iterations | 134 | 822 | +513.43% |
| http_reqs | 1,074 | 6,578 | +512.48% |

최신 상태에서는 추가 코드 최적화보다 아래 작업이 우선입니다.

1. 동일 조건 반복 실행으로 tail 재현성 확인
2. spike 시각의 gateway/main/DB 로그 매칭
3. 실제 데이터에 가까운 posts 포함 seed 추가 검토
4. p99는 관찰 지표로 유지하고, p95 threshold 중심으로 회귀 방지

## 14. 결론

이번 튜닝은 단순 인덱스 추가가 아니라 데이터 흐름 자체를 줄이는 방향으로 진행했습니다.

```text
600,000 원본 row 반환
-> DB participant별 집계 20 row 반환
-> 최신 projection 10 row 우선 사용
```

이 판단은 실행계획에서 확인된 대량 row 반환, external merge sort, 애플리케이션 집계 비용을 근거로 합니다.

그 결과 최초 `settlement-preview p95 3.75s`는 최신 `16.11ms`까지 줄었고, 전체 p95도 `3.10s`에서 `45.39ms`로 개선됐습니다.
