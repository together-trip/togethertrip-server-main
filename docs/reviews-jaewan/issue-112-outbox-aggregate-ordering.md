# Issue #112 Main Outbox 순서 보장 리뷰

## 요약

동일 aggregate의 head 이벤트만 claim하는 DB 정책과 SQS FIFO ordering contract를 검토했다.
main 저장소 범위에서 병렬 처리와 aggregate 내부 순서 보장의 경계가 분리되어 있고,
standard queue 호환 정책과 head-of-line blocking 결정도 코드·설정·문서에 일치한다.

## 발견 사항

| 심각도 | 파일 | 내용 | 제안 |
| --- | --- | --- | --- |
| 없음 | 전체 변경 | 수정이 필요한 정확성·보안·회귀 문제를 발견하지 못했다. | notification/infra 후속 작업에서 FIFO queue와 consumer 계약을 함께 검증한다. |

## 아키텍처 판단

- 영향받는 영역: `global/outbox` repository와 SQS infrastructure adapter
- 의존성 규칙: 도메인 이벤트 payload와 각 feature service는 변경하지 않았다.
- 순서 단위: `aggregate_type + aggregate_id`
- 결정적 순서: `created_at + id`
- 병렬성: 서로 다른 aggregate의 head는 기존 `FOR UPDATE SKIP LOCKED`로 병렬 claim한다.
- 절충점: 최대 시도 횟수에 도달한 FAILED head도 후속 이벤트를 막는다. 자동 우회보다 순서 보장을 우선한다.
- queue 호환: STANDARD는 FIFO field를 보내지 않고, FIFO만 aggregate group/event deduplication ID를 보낸다.

## 확인한 명령

- `./gradlew test --tests 'com.togethertrip.main.global.outbox.infrastructure.sqs.*' --tests 'com.togethertrip.main.global.outbox.service.OutboxEventDispatchServiceTest'`
- `./gradlew integrationTest --tests 'com.togethertrip.main.global.outbox.repository.OutboxEventRepositoryConcurrencyTest' --rerun-tasks`
- `./gradlew --no-daemon check --console=plain`
- `git diff --check origin/develop...HEAD`

## 남은 위험

- standard SQS는 DB claim 이후 broker 전달 순서를 보장하지 않는다. 운영 순서 보장은 FIFO 전환이 필요하다.
- 실패 한도를 소진한 head는 같은 aggregate를 계속 막는다. 장기 실패 탐지와 수동 복구 절차가 필요하다.
- notification의 `occurredAt` 정렬과 역순 push 방지는 이 변경에 포함되지 않는다.
- 새 partial index는 미발행 이벤트만 포함하지만, 운영 데이터 증가 후 query plan과 index 크기를 관측해야 한다.
