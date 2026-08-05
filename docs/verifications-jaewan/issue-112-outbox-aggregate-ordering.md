# Issue #112 Main Outbox 순서 보장 검증

## 검증 대상

- 동일 aggregate의 앞선 미발행 이벤트가 후속 claim을 차단하는지
- 앞선 이벤트가 다른 worker에 잠긴 상태에서도 후속 이벤트를 건너뛰지 않는지
- worker 2개의 claim ID 교집합이 없고 전체 이벤트 누락이 없는지
- 최대 시도 횟수에 도달한 FAILED head의 정책이 유지되는지
- FIFO/standard SQS 요청 field와 설정 검증이 계약대로 동작하는지
- Flyway V27 partial index가 빈 PostGIS DB에 적용되는지

## 실행한 명령

```bash
./gradlew test \
  --tests 'com.togethertrip.main.global.outbox.infrastructure.sqs.*' \
  --tests 'com.togethertrip.main.global.outbox.service.OutboxEventDispatchServiceTest'

./gradlew integrationTest \
  --tests 'com.togethertrip.main.global.outbox.repository.OutboxEventRepositoryConcurrencyTest' \
  --rerun-tasks

./gradlew --no-daemon check --console=plain
git diff --check origin/develop...HEAD
```

## 결과

### 회귀 테스트

- standard queue 요청: `MessageGroupId`, `MessageDeduplicationId` 미설정
- FIFO queue 요청:
  - `MessageGroupId = SETTLEMENT_TRANSFER:203`
  - `MessageDeduplicationId = 15`
- queue type과 `.fifo` URL suffix 불일치: 설정 생성 실패
- worker 2개 동시 claim: ID 교집합 0, 합집합 전체 이벤트 ID
- 같은 aggregate의 head lock 중 후속 claim: 0건
- head가 PUBLISHED로 커밋된 뒤 후속 claim: 다음 event 1건
- retry 5회 FAILED head: 후속 claim 0건

### 전체 품질 게이트

- `./gradlew check`: BUILD SUCCESSFUL, 1분 18초
- 단위 테스트: 600개, failure/error 0
- Testcontainers 통합 테스트: 85개, failure/error 0, 기존 조건부 1개 skip
- 빈 PostGIS DB: Flyway V1~V27 적용 성공
- JaCoCo: 기존 LINE 90%, BRANCH 80% gate 통과
- PIT: 115 mutations, killed/timeout 95, mutation score 83%, mutated line coverage 92%
- query convention gate 통과

## 실패 또는 미검증 항목

- 첫 전체 `check`는 V27 추가 뒤 인프라 테스트가 최신 version을 26으로 고정하고 있어 1건 실패했다.
  기대값을 27로 갱신한 뒤 전체 gate를 다시 실행해 통과했다.
- 실제 AWS FIFO queue 전송은 infra/notification 변경이 제외 범위라 실행하지 않았다.
- notification 알림함 역순 소비와 push 순서는 이 저장소에서 검증하지 않았다.

## 운영 계약

- `NOTIFICATION_SQS_QUEUE_TYPE=STANDARD`가 기본값이다.
- FIFO 전환 시 `NOTIFICATION_SQS_QUEUE_TYPE=FIFO`와 `.fifo` queue URL을 함께 설정한다.
- 최대 시도 횟수를 소진한 FAILED head는 자동 건너뛰지 않는다. 원인 해결 후 운영자가 재처리해야 한다.
- 이 정책은 한 aggregate의 후속 알림을 지연시킬 수 있지만, 사용자에게 역순 알림을 노출하지 않는 것을 우선한다.

## 다음 조치

1. infra에서 notification queue의 FIFO 전환과 DLQ/경보 정책을 별도 적용한다.
2. notification consumer가 같은 aggregate의 group contract와 `occurredAt` 정렬을 검증한다.
3. 운영 outbox backlog, exhausted FAILED head와 claim query latency를 관측한다.
