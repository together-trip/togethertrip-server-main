# #37 SQS 후속 확장 경계 확인

- 작업 브랜치: `feature/issue-37-outbox-notification-events`
- 확인 범위: 알림 outbox 이벤트 저장 구현 이후 SQS 연동 경계
- 확인일: 2026-06-22

## 결론

#37 구현 범위는 `outbox_events`에 `PENDING` 이벤트를 저장하는 것까지로 유지되어 있다.
현재 코드에는 SQS, 알림 서버, 발행 작업자 의존성이 들어가지 않았다.

이번 구현에서는 실제 외부 전송 대신 로그만 남기는 sender와 수동 dispatcher를 추가했다.
스케줄러는 아직 붙이지 않았으므로, 자동으로 outbox가 발행되지는 않는다.

후속 SQS 연동은 도메인 서비스 수정 없이 다음 요소를 교체하거나 추가하는 방식으로 붙일 수 있다.

- 현재 `LoggingOutboxEventSender` 대신 `SqsOutboxEventSender` 구현체 추가
- 현재 `OutboxEventDispatchService`를 호출하는 스케줄러 또는 배치 작업자 추가
- SQS 설정과 AWS SDK 의존성

## 현재 경계

도메인 서비스는 `OutboxEventPublisher`만 호출한다.
`OutboxEventPublisher`는 같은 DB 트랜잭션 안에서 `OutboxEvent`를 저장한다.
저장된 `PENDING` 이벤트는 `OutboxEventDispatchService.dispatchPending(...)`가 읽는다.
현재 sender는 `LoggingOutboxEventSender`이며, 외부 전송 없이 로그만 남긴다.

현재 저장되는 outbox 행은 논리 이벤트 기준이다.
수신자가 여러 명이어도 outbox 행을 수신자별로 쪼개지 않고, payload의 `recipients` 배열에 담는다.

수신자가 없는 경우 `OutboxEventPublisher`가 outbox 행을 저장하지 않는다.
수신자 중복은 저장 직전에 `userId` 기준으로 제거한다.

## 확인한 항목

- `build.gradle.kts`에 `software.amazon.awssdk:sqs` 의존성이 없다.
- `src/main/kotlin`에 SQS client, queue sender, 발행 작업자 타입 참조가 없다.
- `OutboxEventSender` 인터페이스가 있어 후속 SQS sender가 같은 경계로 붙을 수 있다.
- `LoggingOutboxEventSender`는 외부 전송 없이 로그만 남긴다.
- `OutboxEventDispatchService`는 `PENDING` 이벤트를 읽어 sender에 넘긴다.
- sender 호출이 성공하면 `PUBLISHED`, 실패하면 `FAILED`로 변경하고 `retryCount`를 증가시킨다.
- 도메인 서비스는 SQS나 알림 서버를 직접 알지 않는다.
- 이벤트 payload DTO는 `global/outbox/payload` 아래에 모여 있다.
- 모든 알림 payload는 `recipients`와 `eventVersion`을 포함한다.
- `OutboxEventRepository.findByStatusOrderByCreatedAtAsc(...)`가 후속 발행 작업자 조회 경계로 사용 가능하다.
- 알림 서버 멱등 키는 `(sourceService, sourceEventId, userId)` 계약으로 문서화되어 있다.

## 현재 상태 값 의미

현재 `PUBLISHED`는 notification 서버 처리 성공을 의미하지 않는다.
`PUBLISHED`는 main 서버가 `OutboxEventSender.send(event)` 호출을 성공으로 처리했다는 뜻이다.

지금은 `LoggingOutboxEventSender`가 예외 없이 로그를 남기면 `PUBLISHED`가 된다.
후속 SQS 연동 이후에는 SQS `sendMessage` 성공이 `PUBLISHED` 기준이 된다.

notification 서버의 알림 생성, FCM 발송 성공/실패, 사용자별 읽음 상태는 main 서버 outbox 상태와 분리한다.

## 후속 SQS 연동 시 추가할 코드 형태

```kotlin
interface OutboxEventSender {
    fun send(event: OutboxEvent)
}
```

```kotlin
@Component
@ConditionalOnProperty(
    prefix = "outbox.sqs",
    name = ["enabled"],
    havingValue = "true",
)
class SqsOutboxEventSender(
    private val sqsClient: SqsClient,
    private val objectMapper: ObjectMapper,
    @Value("\${outbox.sqs.queue-url}")
    private val queueUrl: String,
) : OutboxEventSender {

    override fun send(event: OutboxEvent) {
        val message = mapOf(
            "eventId" to event.id,
            "aggregateType" to event.aggregateType,
            "aggregateId" to event.aggregateId,
            "eventType" to event.eventType,
            "payload" to objectMapper.readTree(event.payload),
            "createdAt" to event.createdAt,
        )

        sqsClient.sendMessage {
            it.queueUrl(queueUrl)
            it.messageBody(objectMapper.writeValueAsString(message))
        }
    }
}
```

## 후속 발행 작업자 역할

1. `OutboxEventDispatchService.dispatchPending(...)`를 주기적으로 호출한다.
2. dispatcher는 `OutboxEventRepository.findByStatusOrderByCreatedAtAsc(PENDING, pageable)`로 대기 이벤트를 읽는다.
3. dispatcher는 `OutboxEventSender.send(event)`를 호출한다.
4. 성공하면 `PUBLISHED`로 변경한다.
5. 실패하면 retry count를 증가시키고 `FAILED`로 변경한다.
6. 같은 event가 재전송될 수 있으므로 알림 서버는 `(sourceService, sourceEventId, userId)`로 멱등 처리한다.

## 주의할 점

SQS 메시지는 outbox 행 1개를 그대로 담아야 한다.
main 서버 발행 작업자에서 recipients를 수신자별 메시지로 나누지 않는다.

FCM 발송 성공/실패, 사용자별 읽음 상태, 사용자별 알림 행 생성은 알림 서버 책임으로 둔다.
main 서버 outbox는 도메인 이벤트 원장과 전송 상태만 관리한다.
