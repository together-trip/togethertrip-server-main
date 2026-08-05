# Issue #112 Main Outbox 동일 aggregate 순서 보장 계획

## 작업

`FOR UPDATE SKIP LOCKED` 기반 병렬 claim은 유지하면서 동일한
`aggregateType + aggregateId`에서 앞선 미발행 이벤트가 후속 이벤트보다 먼저 발행되도록 한다.
SQS FIFO를 사용할 때 필요한 group/deduplication 계약을 main sender에 명시한다.

## 배경

현재 claim 쿼리는 전체 이벤트를 `created_at, id` 순서로 조회하지만, 앞선 행이 다른 worker에 잠겨 있으면
`SKIP LOCKED`가 같은 aggregate의 후속 행을 반환할 수 있다. 이 경우 송금 확인/완료처럼 의미상 순서가 있는
알림이 역순으로 전달될 수 있다. 또한 현재 SQS 요청에는 FIFO ordering field가 없다.

## 범위

- 동일 aggregate의 앞선 `PENDING` 또는 `FAILED` 이벤트가 남아 있으면 후속 이벤트를 claim하지 않는다.
- 서로 다른 aggregate는 기존처럼 여러 worker가 병렬 claim한다.
- `created_at, id`를 aggregate 내부의 결정적 순서로 사용한다.
- FIFO queue에서는 `MessageGroupId = aggregateType:aggregateId`,
  `MessageDeduplicationId = eventId`를 설정한다.
- standard queue에서는 FIFO 전용 필드를 보내지 않아 LocalStack과 기존 개발 queue 호환성을 유지한다.
- worker 2개 claim 교집합 0, 누락 0과 앞선 이벤트 lock 중 후속 claim 차단을 PostgreSQL 통합 테스트로 검증한다.

## 제외 범위

- notification 저장소의 알림함 정렬과 push 발송 순서 변경
- SQS queue를 실제 FIFO 리소스로 교체하는 인프라 변경
- 전체 aggregate를 가로지르는 전역 순서 보장
- 실패 이벤트 자동 폐기 또는 순서 우회

## 설계

### DB claim

발행 가능한 outer event를 조회할 때 같은 aggregate에 더 오래된 미발행 이벤트가 존재하지 않는다는
`NOT EXISTS` 조건을 추가한다. 앞선 행이 다른 transaction에 잠겨 있어도 subquery에는 보이므로 후속 행은
claim 대상이 되지 않는다. outer query의 `FOR UPDATE SKIP LOCKED`는 유지해 서로 다른 aggregate의 head는
병렬 처리한다.

미발행은 `status <> 'PUBLISHED'`로 정의한다. 최대 시도 횟수에 도달한 `FAILED` 이벤트도 미발행이므로
같은 aggregate의 head-of-line을 계속 막는다. 이를 자동으로 건너뛰면 알림 역전이 다시 발생하므로,
운영자가 실패 원인을 해결하고 재시도 횟수/상태를 복구하거나 별도 보상 정책을 승인해야 한다.

### SQS ordering contract

`notification.sqs.queue-type`을 `STANDARD`와 `FIFO`로 모델링한다.

- `STANDARD`(기본): 기존 개발 환경 호환을 위해 `MessageGroupId`와
  `MessageDeduplicationId`를 설정하지 않는다. DB claim 순서는 보장하지만 SQS 전달 순서는 보장하지 않는다.
- `FIFO`: queue URL이 `.fifo`로 끝나야 하며, aggregate 단위 group ID와 event ID deduplication ID를 설정한다.
  동일 aggregate는 직렬 전달되고 서로 다른 aggregate는 SQS가 병렬 처리할 수 있다.

queue type과 URL이 맞지 않으면 시작 시점에 잘못된 설정을 조용히 허용하지 않고 실패시킨다.

## 테스트 계획

- Repository integration
  - 서로 다른 aggregate 10건을 worker 2개가 claim할 때 교집합 0, 합집합 전체 ID
  - 한 worker가 같은 aggregate의 첫 이벤트를 잠근 동안 다른 worker가 후속 이벤트를 claim하지 않음
  - 앞선 이벤트가 `PUBLISHED`가 된 뒤 후속 이벤트가 claim됨
  - 최대 재시도에 도달한 `FAILED` head가 후속 이벤트를 차단함
- SQS sender unit
  - FIFO group/deduplication ID가 결정적으로 설정됨
  - standard queue에는 FIFO 전용 field가 설정되지 않음
  - queue type/URL 불일치가 거부됨
- 전체 검증: `./gradlew check`

## 위험과 확인 사항

- 하나의 영구 실패 이벤트가 해당 aggregate의 후속 알림을 막는 head-of-line blocking이 의도적으로 생긴다.
  장기 실패 관측과 운영 복구는 필요하지만 순서를 깨는 자동 우회는 이번 범위에 포함하지 않는다.
- DB 순서 보장만으로 standard SQS의 전달 순서는 보장되지 않는다. 운영 FIFO 전환에는 notification consumer와
  infra queue 변경을 별도로 맞춰야 한다.
- `created_at` 동률은 DB PK `id`로 정렬한다.
