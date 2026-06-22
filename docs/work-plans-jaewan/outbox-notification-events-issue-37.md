# 알림 연동용 아웃박스 이벤트 구현 계획

## 작업 정보

- 이슈: [#37 feat: 알림 연동용 Outbox 이벤트 구현](https://github.com/together-trip/togethertrip-server-main/issues/37)
- 브랜치: `feature/issue-37-outbox-notification-events`
- 관련 앱 이슈: [client #25 feat: 소비 등록 통합 API 연동](https://github.com/together-trip/togethertrip-client-flutter/issues/25)
- 목표: `main` 서버의 주요 기능 변화가 생겼을 때 `outbox_events`에 알림 후보를 남겨, 알림 서버가 이후 수신자별 알림과 FCM 발송을 처리할 수 있게 한다.

## 현재 코드 기준

`main` 서버에는 이미 다음 아웃박스 기반 코드가 있다.

- `global/outbox/domain/OutboxEvent.kt`
- `OutboxStatus`: `PENDING`, `PUBLISHED`, `FAILED`
- `OutboxEventRepository.findByStatusOrderByCreatedAtAsc(...)`

하지만 실제 도메인 기능이 실행될 때 알림용 아웃박스 데이터를 저장하는 흐름은 아직 연결되어 있지 않다.

현재 코드 기준으로 사용하는 주요 enum 값은 다음과 같다.

- `TripInvitationType`: `CODE`, `LINK`
- `TripInvitationStatus`: `ACTIVE`, `USED`, `EXPIRED`, `CANCELLED`
- `TripParticipantStatus`: `ACTIVE`, `LEFT`, `REMOVED`
- `PostType`: `RECORD`, `EXPENSE`
- `SettlementStatus`: `DRAFT`, `CONFIRMED`, `CANCELLED`
- `SettlementTransferStatus`: `PENDING`, `SENDER_CONFIRMED`, `RECEIVER_CONFIRMED`, `COMPLETED`, `CANCELLED`
- `OutboxStatus`: `PENDING`, `PUBLISHED`, `FAILED`

현재 `main` 서버에는 AWS/SQS 의존성이 없다.
따라서 이번 이슈에서는 실제 SQS 전송을 구현하지 않는다.
대신 이후 SQS를 붙일 때 도메인 서비스 코드를 거의 수정하지 않도록 발행 경계와 payload 계약을 먼저 잡는다.

## 작업 범위

이번 #37의 알림 연동 범위는 `PENDING` outbox 이벤트 저장까지로 제한한다.
알림 서버 내부 구현, 사용자별 알림 저장, FCM 발송, 발송 실패 재시도는 이번 범위가 아니다.

큰 기능 축은 다음 3개로 제한한다.

1. 초대/참여 알림
2. 게시글/댓글/소비 알림
3. 정산/송금 알림

다만 소비 알림은 현재 API 구조의 정합성 문제와 연결되어 있어 #37 안에서 함께 정리한다.
현재 앱은 거래 생성 API를 먼저 호출하고, 성공한 `transactionId`로 소비 게시글 생성 API를 다시 호출한다.
이 구조에서는 거래 생성은 성공했지만 게시글 생성이 실패해 거래만 남는 상태가 생길 수 있다.
따라서 #37 구현 안에서 소비 게시글 통합 생성 API를 먼저 만들고, 해당 API 성공 시에만 `EXPENSE_POST_CREATED`를 발행한다.
앱은 이 통합 API로 전환해야 하므로 client #25에서 별도로 추적한다.

## 제외 범위

- 알림 서버의 알림 발송/저장 구현
- 푸시, 카카오 알림톡, 문자 발송 연동
- 메시지 큐나 Kafka 도입
- SQS 실제 연동 코드와 운영 설정
- 아웃박스 발행 작업자 구현
- 운영자용 환율 수집 성공/실패 알림
- 프로필 수정, 약관 동의, 로그인 같은 개인 상태 변경 알림
- 게시글/거래 수정 및 삭제 알림
- 앱의 소비 등록 통합 API 전환

## 확정 정책

### 1. 아웃박스 행 단위

아웃박스 행은 수신자별로 쪼개지 않는다.
`main` 서버는 도메인에서 발생한 논리 이벤트 1개를 outbox 행 1개로 저장하고, 이벤트 데이터 안에 수신자 목록을 담는다.
알림 서버는 이 논리 이벤트를 받아 수신자별 알림, FCM 발송, 발송 실패 재시도, 읽음 상태를 관리한다.

논리 이벤트의 기준은 도메인 행위 1번이다.
예를 들어 여행 생성 요청 1번에 기존 사용자 동행자가 여러 명 포함되면 `TRIP_PARTICIPANTS_ADDED` outbox 행은 1개만 만들고, `recipients`에 여러 사용자를 담는다.
반대로 `participant-connections`처럼 요청 자체가 한 명을 연결하는 행위라면 outbox 행 1개에 recipient 1명만 담긴다.

수신자가 0명인 경우에는 알림 후보가 아니므로 outbox 행을 저장하지 않는다.
수신자 중복 제거는 `OutboxEventPublisher`에서 최종 수행한다.
도메인 서비스가 대상자를 한 번 걸러도, 저장 직전에 `userId` 기준으로 중복을 제거해 같은 outbox 행 안에 동일 사용자가 두 번 들어가지 않게 한다.
중복 제거 후 수신자 순서는 처음 등장한 순서를 유지한다.

outbox 저장 실패는 도메인 트랜잭션 실패로 본다.
알림 누락을 허용하지 않고 transactional outbox의 일관성을 우선한다.
따라서 도메인 변경과 outbox 저장은 같은 트랜잭션 안에서 커밋되어야 하며, outbox 저장이 실패하면 도메인 변경도 롤백되어야 한다.

### 2. 수신자와 자기 자신 제외

기본 `recipients` 항목에는 `userId`만 넣는다.
수신자 본인의 표시명은 알림 문구의 핵심 데이터가 아니므로 main 이벤트에 담지 않는다.
다만 정산 확정처럼 수신자마다 내용이 달라지는 이벤트는 예외적으로 해당 수신자에게 필요한 송금 항목 요약을 `recipients` 안에 함께 담는다.

자기 자신이 발생시킨 행동에는 알림을 보내지 않는 정책을 기본값으로 둔다.
정산 확정처럼 전체 참여자에게 의미가 있는 이벤트도 행위자 본인은 제외한다.

임시 참여자처럼 `userId`가 없는 대상은 알림 대상에서 제외한다.
탈퇴/삭제 상태 참여자도 사용자 알림 대상에서 제외한다.

### 3. 이벤트 데이터 스냅샷

알림 문구에 필요한 표시 정보는 이벤트 발생 시점의 스냅샷으로 이벤트 데이터에 포함한다.
예를 들어 `tripName`, `actorDisplayName`, 금액, 통화는 알림 서버가 main 서버를 다시 조회하지 않아도 문구를 만들 수 있도록 함께 담는다.
이후 사용자가 닉네임이나 여행명을 바꿔도 이미 생성된 알림은 발생 당시 정보를 기준으로 유지한다.

모든 이벤트 데이터에는 공통으로 다음 값을 우선 포함한다.

- `eventVersion`
- `actorUserId`
- `tripId`
- 알림 서버가 수신자별 알림을 만들 때 사용할 `recipients`
- 도메인 원장 식별자: `invitationId`, `participantId`, `postId`, `commentId`, `transactionId`, `settlementId`, `settlementTransferId`
- 알림 서버가 문구를 만들 때 사용할 최소 표시 정보: `tripName`, `actorDisplayName`, 금액/통화 등
- `occurredAt`

`eventVersion`은 각 payload DTO의 기본값으로 둔다.
처음 버전은 `val eventVersion: Int = 1`로 시작한다.
이벤트 타입과 이벤트 데이터 버전은 알림 서버와 공유하는 약속이므로, payload 구조가 변경되면 해당 이벤트 버전을 올린다.

`occurredAt`은 도메인 이벤트 발생 시점 값을 payload DTO에 담는다.
저장 직전 시각이 아니라, 해당 도메인 행위가 성공한 시점을 기준으로 기록한다.

### 4. 이벤트 타입과 원장 타입

이벤트 타입은 알림 문구가 아니라 도메인 사건 기준으로 이름을 붙인다.
예를 들어 기존 사용자가 여행에 추가된 사건은 `TRIP_INVITATION_RECEIVED`가 아니라 `TRIP_PARTICIPANTS_ADDED`로 기록하고, 알림 서버가 이 이벤트를 “여행에 추가됐어요” 문구로 해석한다.

이벤트 타입은 문자열 직접 입력이 아니라 `OutboxEventType` enum으로 정의한다.
도메인 서비스는 enum 값을 넘기고, `OutboxEventPublisher`가 저장 시 필요한 문자열 값으로 변환한다.

초기 `OutboxEventType` 값은 다음과 같다.

- `TRIP_PARTICIPANTS_ADDED`
- `TRIP_PARTICIPANT_JOINED`
- `TRIP_PARTICIPANT_REMOVED`
- `POST_CREATED`
- `EXPENSE_POST_CREATED`
- `POST_COMMENT_CREATED`
- `SETTLEMENT_CONFIRMED`
- `SETTLEMENT_TRANSFER_CONFIRMED_BY_SENDER`
- `SETTLEMENT_TRANSFER_COMPLETED`

`aggregateType`도 `OutboxAggregateType` enum으로 정의해 원장 기준 문자열 오타를 막는다.
초기 `OutboxAggregateType` 값은 다음과 같다.

- `TRIP`
- `POST`
- `SETTLEMENT`
- `SETTLEMENT_TRANSFER`

도메인 서비스는 문자열 대신 `OutboxAggregateType`을 사용하고, 저장 시 enum의 `name`을 `OutboxEvent.aggregateType`에 넣는다.

### 5. Payload DTO 계약

payload는 `Map<String, Any>`로 직접 조립하지 않고 이벤트별 data class로 정의한다.
DTO는 `global/outbox` 하위 패키지에 모아 아웃박스 이벤트 계약을 한 곳에서 관리한다.

알림용 payload DTO는 공통 인터페이스를 구현한다.
예를 들어 `OutboxNotificationPayload`가 `recipients`를 노출하고, `OutboxEventPublisher`는 JSON을 파싱하지 않고 이 인터페이스로 수신자 0명 처리와 중복 제거를 수행한다.

기본 구조 예시는 다음과 같다.

```kotlin
interface OutboxNotificationPayload {
    val recipients: List<OutboxRecipientPayload>
}

data class OutboxRecipientPayload(
    val userId: Long,
)
```

정산 확정 payload의 `recipients`만 송금 항목 요약을 포함하는 별도 recipient DTO를 사용한다.
이 DTO도 `userId`를 노출해 publisher의 중복 제거 대상이 되게 한다.

## 설계

### 1. 초대/참여 알림

#### 이벤트 후보

- `TRIP_PARTICIPANTS_ADDED`
- `TRIP_PARTICIPANT_JOINED`
- `TRIP_PARTICIPANT_REMOVED`

#### 발생 위치

- `TripService.createTrip(...)`
- `TripInviteService.createInviteCode(...)`
- `TripInviteService.createInviteLink(...)`
- `TripInviteService.joinTrip(...)`
- `TripParticipantService.linkTemporaryParticipant(...)`
- `TripParticipantService.removeParticipant(...)`

#### 정책

현재 `TripInviteService`는 코드/링크 생성 중심이다.
코드/링크는 생성 시점에 특정 수신자가 없으므로 `TRIP_PARTICIPANTS_ADDED`를 발행하지 않는다.
사용자가 말한 “초대 알림”은 코드/링크 초대가 아니라 기존 사용자가 여행에 추가되는 흐름을 의미한다.

기존 사용자를 지정하는 1차 흐름은 여행 생성 API에 있다.
`CreateTripRequest.participants`의 각 `TripCompanionInput`은 `userId`를 가질 수 있고, `TripService.createTrip(...)`는 여행 생성과 함께 해당 사용자를 `TripParticipant(user != null)`로 저장한다.
여행 생성이 성공하면 요청에 포함된 기존 사용자 동행자를 `TRIP_PARTICIPANTS_ADDED`로 기록한다.
알림 문구는 초대 수락/거절을 암시하지 않도록 “OO님이 '여행명'에 나를 추가했어요.” 계열로 잡는다.

- 대상: 여행 생성 요청에 포함된 기존 사용자 동행자 `userId`
- 행위자: 여행을 생성한 방장 `userId`
- 기준 원장: 생성된 `Trip`
- 코드/링크 초대 생성은 대상 사용자가 없으므로 알림 제외

방장이 여행 생성 이후 기존 사용자를 추가하는 보조 흐름도 있다.
`POST /api/trips/{tripId}/participant-connections`에서 임시 참여자와 기존 회원을 연결하면, 이 역시 `TRIP_PARTICIPANTS_ADDED`로 기록한다.

`TRIP_PARTICIPANT_JOINED`는 코드/링크 초대로 사용자가 직접 참여 완료한 경우에 발행한다.
참여 완료 시 기존 활성 회원 참여자에게 알림을 보내며, 새로 참여한 본인은 대상에서 제외한다.

`TRIP_PARTICIPANT_REMOVED`는 제거된 참여자가 회원인 경우에만 대상이 있다.
임시 참여자는 알림 대상 `userId`가 없으므로 이벤트를 만들지 않는다.
제거 알림은 제거된 사용자에게만 보낸다.
남은 참여자에게는 민감한 상황을 불필요하게 알리지 않는다.

`TripInviteService.joinTrip(...)` 안에서도 `participantId`를 전달하면 임시 참여자를 현재 사용자와 연결할 수 있다.
이 경우는 사용자가 초대를 수락해 직접 참여한 흐름이므로 `TRIP_PARTICIPANT_JOINED`로 다룬다.

#### 이벤트 데이터 초안

```json
{
  "eventVersion": 1,
  "actorUserId": 10,
  "tripId": 20,
  "participantId": 30,
  "recipients": [
    { "userId": 11 },
    { "userId": 12 }
  ],
  "tripName": "일본 여행",
  "actorDisplayName": "재완",
  "occurredAt": "2026-06-22T12:00:00Z"
}
```

### 2. 게시글/댓글/소비 알림

#### 이벤트 후보

- `POST_CREATED`
- `EXPENSE_POST_CREATED`
- `POST_COMMENT_CREATED`

#### 발생 위치

- `PostService.createPost(...)`
- 신규 소비 게시글 통합 생성 서비스
- `PostService.createComment(...)`

#### 정책

`PostService.createPost(...)`는 `transactionId` 유무로 `RECORD`와 `EXPENSE`를 구분한다.
하지만 현재 앱 흐름은 `TransactionService.createTransaction(...)` 호출 후 `PostService.createPost(...)`를 따로 호출한다.
이 사이에서 실패하면 거래만 있고 소비 게시글은 없는 상태가 생길 수 있다.

따라서 #37 안에서 소비 등록을 별도 API로 묶는다.

- 예시: `POST /api/trips/{tripId}/expense-posts`
- 요청 형식: `multipart/form-data`
- 요청 내용: 게시글 필드, 첨부 파일, 거래 생성 필드를 함께 받는다.
- 서버 내부: 거래 생성, 결제자/부담자 저장, 소비 게시글 생성, 첨부 저장, outbox 저장을 하나의 DB 트랜잭션으로 처리한다.
- 응답: 게시글 상세 응답을 기준으로 하되, 연결된 거래 요약을 함께 포함한다.
- 알림 기준: 거래 생성 시점이 아니라 소비 게시글까지 생성된 `EXPENSE_POST_CREATED` 시점으로 둔다.

거래 생성 로직은 복제하지 않는다.
현재 `TransactionService.createTransaction(...)` 안에 있는 원장 생성, 결제자/부담자 저장, 환율 스냅샷, 거래 이벤트 기록 로직을 공통 내부 서비스로 분리한다.
예를 들어 `TransactionCreationService` 또는 `TransactionCommandService` 같은 내부 서비스를 두고, 기존 거래 생성 API와 소비 게시글 통합 생성 API가 같은 로직을 사용하게 한다.
기존 거래 생성 API는 계속 `TransactionDetailResponse`를 반환하고, 통합 생성 API는 생성된 `Transaction`과 저장된 payment/share를 받아 소비 게시글 생성과 outbox 저장을 이어서 처리한다.

통합 생성 API 응답은 게시글이 중심이다.
앱은 소비 등록 후 게시글 화면을 갱신해야 하므로, 기존 게시글 상세 응답을 기준으로 하되 연결된 거래 요약을 포함해 별도 거래 상세 재조회 없이 화면을 구성할 수 있게 한다.

일반 기록은 기존 `PostService.createPost(...)`에서 `POST_CREATED`를 저장한다.
일반 기록도 이번 범위에서는 알림을 보낸다.

게시글/소비 등록 알림 대상은 여행의 활성 회원 참여자이며 작성자는 제외한다.
임시 참여자는 `userId`가 없으므로 알림 대상에서 제외한다.

댓글 알림은 우선 게시글 작성자에게만 보낸다.
댓글 작성자가 게시글 작성자와 같거나 게시글 작성자가 임시 참여자인 경우 이벤트를 만들지 않는다.
기존 댓글 작성자나 여행 참여자 전체에게는 보내지 않는다.
대댓글/멘션 구조가 생기면 그때 별도 정책으로 확장한다.

소비 금액은 `Post` 자체 필드가 아니라 연결된 `Transaction`의 `amount`, `currency`, `baseAmount`, `baseCurrency`에 있다.
신규 소비 게시글 통합 생성 서비스는 방금 저장한 `Transaction` 값을 사용해 `EXPENSE_POST_CREATED` 이벤트 데이터를 만든다.

거래 단독 생성(`TRANSACTION_CREATED`)은 사용자 알림 대상으로 보지 않는다.
거래 원장 이벤트는 내부 감사/정산 기준으로 유지하되, 사용자에게 보이는 소비 알림은 `EXPENSE_POST_CREATED` 하나로 맞춘다.

#### 이벤트 데이터 초안

```json
{
  "eventVersion": 1,
  "actorUserId": 10,
  "tripId": 20,
  "postId": 30,
  "transactionId": 40,
  "recipients": [
    { "userId": 11 },
    { "userId": 12 }
  ],
  "tripName": "일본 여행",
  "actorDisplayName": "재완",
  "postType": "EXPENSE",
  "title": "라멘",
  "amount": "30000.00",
  "currency": "KRW",
  "occurredAt": "2026-06-22T12:00:00Z"
}
```

### 3. 정산/송금 알림

#### 이벤트 후보

- `SETTLEMENT_CONFIRMED`
- `SETTLEMENT_TRANSFER_CONFIRMED_BY_SENDER`
- `SETTLEMENT_TRANSFER_COMPLETED`

#### 발생 위치

- `SettlementService.confirmSettlement(...)`
- `SettlementTransferService.confirmAsSender(...)`
- `SettlementTransferService.confirmAsReceiver(...)`

#### 정책

정산 확정 시 `SETTLEMENT_CONFIRMED`를 여행 활성 회원 참여자에게 보낸다.
확정자는 대상에서 제외한다.

정산 확정 알림은 송금 요청 알림과 분리하지 않는다.
`main` 서버는 `SETTLEMENT_CONFIRMED` outbox 행을 하나만 만들고, `recipients` 안에 사용자별 송금 항목 요약을 함께 담는다.
알림 서버가 이 데이터를 받아 사용자별 알림 행을 만든다.
이 송금 항목 요약에는 수신자 본인 이름이 아니라, 해당 사용자가 돈을 보내야 하는 상대 참여자의 표시명과 금액을 담는다.

예시는 다음과 같다.

- 보낼 송금이 있는 사용자: “일본 여행 정산이 완료됐어요. 송금할 항목 2건이 있어요.”
- 보낼 송금이 없는 사용자: “일본 여행 정산이 완료됐어요.”

송금자가 탈퇴/임시 참여자라 `userId`가 없으면 해당 송금 요약의 알림 대상에서 제외한다.
별도 `SETTLEMENT_TRANSFER_CREATED` 이벤트는 만들지 않는다.

송금자가 보냈다고 확인하면 `SETTLEMENT_TRANSFER_CONFIRMED_BY_SENDER`를 수금자에게 보낸다.
수금자가 확인해 양쪽 확인이 완료되면 `SETTLEMENT_TRANSFER_COMPLETED`를 필요한 대상에게 보낸다.
두 알림은 모두 필요하다.

- `SETTLEMENT_TRANSFER_CONFIRMED_BY_SENDER`: 수금자가 확인해야 할 액션을 알린다.
- `SETTLEMENT_TRANSFER_COMPLETED`: 송금자와 수금자 모두에게 해당 송금 항목이 최종 완료됐음을 알린다.

탈퇴 사용자 자동 확인은 사용자 액션이 아니므로 사용자 알림 이벤트를 만들지 않는다.

현재 `SettlementTransferService.confirmAsSender(...)`와 `confirmAsReceiver(...)`는 `SettlementTransferRow`만 반환한다.
`SettlementTransferRow`에는 sender/receiver 참여자 id와 표시명, user status는 있지만 sender/receiver user id가 없다.
송금 확인 알림 대상 `userId`가 필요하므로 다음 중 하나를 함께 구현한다.

- `SettlementTransferRow` projection에 `senderUserId`, `receiverUserId`를 추가한다.
- 또는 이벤트 발행 시 `SettlementTransfer` entity를 다시 조회해 `sender.user?.id`, `receiver.user?.id`를 사용한다.

또한 현재 `SettlementTransferConfirmationProcessor` 내부의 native update 결과(`updatedCount`)가 서비스 밖으로 나오지 않는다.
이미 확인된 송금을 다시 확인한 no-op 요청에서도 응답은 성공할 수 있으므로, 알림 중복을 막으려면 확인 처리 결과에 실제 변경 여부를 포함해야 한다.

- 송금자 확인 이벤트는 `senderConfirmedAt`이 이번 요청에서 새로 기록된 경우에만 발행한다.
- 송금 완료 이벤트는 이번 요청으로 상태가 `COMPLETED`가 된 경우에만 발행한다.

#### 이벤트 데이터 초안

```json
{
  "eventVersion": 1,
  "actorUserId": 10,
  "tripId": 20,
  "settlementId": 30,
  "recipients": [
    {
      "userId": 11,
      "transferSummary": {
        "sendCount": 2,
        "totalSendAmount": "27000.00",
        "currency": "KRW",
        "items": [
          {
            "settlementTransferId": 40,
            "receiverParticipantId": 60,
            "receiverParticipantDisplayName": "동현",
            "amount": "12000.00"
          },
          {
            "settlementTransferId": 41,
            "receiverParticipantId": 61,
            "receiverParticipantDisplayName": "민지",
            "amount": "15000.00"
          }
        ]
      }
    }
  ],
  "tripName": "일본 여행",
  "occurredAt": "2026-06-22T12:00:00Z"
}
```

## SQS 연동을 고려한 구조

이번 이슈에서는 실제 SQS 전송을 구현하지 않는다.
핵심은 도메인 서비스가 SQS를 직접 알지 않게 하는 것이다.
도메인 서비스는 `OutboxEventPublisher`만 호출하고, `OutboxEventPublisher`는 DB에 `PENDING` 이벤트를 저장한다.
이후 SQS를 사용할 수 있게 되면 별도 발행 작업자가 `PENDING` 이벤트를 읽어 `OutboxEventSender`로 넘긴다.

```kotlin
interface OutboxEventSender {
    fun send(event: OutboxEvent)
}
```

처음 구현에서는 실제 전송 구현체가 필요 없다.
발행 작업자를 아직 만들지 않는다면 `OutboxEventSender`도 바로 만들 필요는 없고, 문서화만 해둔다.
다만 SQS를 붙일 때는 다음 구현체를 추가하는 방식이 가장 작다.

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

SQS 전송을 붙일 때 필요한 Gradle 의존성 예시는 다음과 같다.

```kotlin
implementation("software.amazon.awssdk:sqs")
```

운영 설정 예시는 다음과 같다.

```yaml
outbox:
  sqs:
    enabled: false
    queue-url: ""
```

발행 작업자는 다음 역할만 맡는다.

1. `OutboxEventRepository.findByStatusOrderByCreatedAtAsc(PENDING, pageable)`로 대기 이벤트를 읽는다.
2. `OutboxEventSender.send(event)`를 호출한다.
3. 성공하면 `PUBLISHED`, 실패하면 `FAILED` 또는 retry count 증가로 바꾼다.

SQS 메시지는 outbox 행 1개를 그대로 논리 이벤트 1개로 보낸다.
수신자가 여러 명이어도 메시지를 수신자별로 쪼개지 않는다.
알림 서버는 SQS 메시지의 `eventId`를 `sourceEventId`로 사용하고, `recipients`를 순회하며 수신자별 알림 행을 만든다.

알림 서버 쪽 권장 저장 정책은 다음과 같다.

- 알림 생성 unique key: `(sourceService, sourceEventId, userId)`
- `sourceService`: `main`
- `sourceEventId`: `OutboxEvent.id`
- `userId`: recipient의 사용자 id
- FCM 발송 실패/재시도는 알림 서버의 발송 이력 행에서 관리
- 읽음 상태도 알림 서버에서 사용자별로 관리

이 구조에서는 다음 상황을 안전하게 처리할 수 있다.

- SQS가 같은 메시지를 두 번 전달해도 알림 서버 unique key로 중복 생성 방지
- outbox 발행 작업자가 실패 후 같은 이벤트를 재전송해도 중복 생성 방지
- FCM 토큰이 여러 개이거나 일부 발송 실패가 있어도 main 서버 outbox 상태와 분리해 알림 서버에서 재시도
- 사용자별 읽음 상태를 main 서버에 저장하지 않아도 됨

`OutboxEventPublisher`와 이벤트 payload는 SQS 메시지로 바로 감쌀 수 있는 형태로 유지한다.
SQS 도입 시 도메인 서비스 코드를 수정하지 않고, 발행 작업자와 `OutboxEventSender` 구현체만 추가하는 방향을 유지한다.

## 구현 계획

구현은 한 번에 모두 묶지 않고, 아래 단위로 나누어 진행한다.
앞 단위가 뒤 단위의 기반이 되므로 순서대로 진행하는 것을 기본으로 한다.
각 단위는 가능하면 별도 커밋 또는 작은 PR 단위로 분리할 수 있게 잡는다.

### 구현 단위 1. 아웃박스 발행 기반 만들기

목표는 도메인 서비스들이 알림 이벤트를 저장할 수 있는 공통 발행 기반을 먼저 만드는 것이다.
이 단위에서는 실제 도메인 기능에 이벤트를 연결하지 않고, publisher와 payload 계약만 준비한다.

포함 작업은 다음과 같다.

- `OutboxEventPublisher` 추가
- `OutboxEventType` enum 추가
- `OutboxAggregateType` enum 추가
- `OutboxNotificationPayload` 공통 인터페이스 추가
- `OutboxRecipientPayload` 추가
- 이벤트별 payload DTO 추가
- payload 직렬화와 `PENDING` 저장 처리
- 수신자 0명일 때 저장하지 않는 처리
- `userId` 기준 recipient dedup 처리
- dedup 후 처음 등장한 수신자 순서 유지
- publisher 단위 테스트 작성

이 단위가 끝나면 아직 실제 서비스에서 outbox가 발생하지는 않지만, 각 도메인 서비스가 호출할 공통 진입점은 준비된 상태가 된다.

### 구현 단위 2. 알림 대상자 조회 기반 만들기

목표는 여러 도메인에서 반복해서 필요한 “여행 활성 회원 참여자 조회”와 자기 자신 제외 기준을 공통으로 사용할 수 있게 만드는 것이다.

포함 작업은 다음과 같다.

- 여행 활성 회원 참여자 `userId` 목록 조회 추가
- 행위자 제외 처리
- nullable `userId` 제외 처리
- 탈퇴/삭제 참여자 제외 처리
- `TripParticipantRepository` query method 또는 native projection 추가
- 댓글처럼 단일 대상만 필요한 경우에도 `userId` 존재 여부와 자기 자신 여부를 확인하는 helper 추가
- 대상자 조회 테스트 작성

이 단위가 끝나면 초대/참여, 게시글/소비, 정산 알림에서 동일한 대상자 조회 기준을 사용할 수 있다.

### 구현 단위 3. 초대/참여 이벤트 연결

목표는 여행 참여자 상태 변화 중 알림 대상이 명확한 흐름부터 outbox에 연결하는 것이다.
소비 API나 정산 로직보다 구조 변경 위험이 작으므로, 공통 발행 기반을 검증하기 좋은 첫 도메인 연결 단위로 둔다.

포함 작업은 다음과 같다.

- `TripService.createTrip(...)` 성공 후 기존 사용자 동행자 대상 `TRIP_PARTICIPANTS_ADDED` 저장
- 여행 생성 요청에 기존 사용자 동행자가 여러 명이면 outbox 행 1개에 여러 recipient 저장
- `TripParticipantService.linkTemporaryParticipant(...)` 성공 후 연결된 기존 사용자 대상 `TRIP_PARTICIPANTS_ADDED` 저장
- `TripInviteService.joinTrip(...)` 성공 후 기존 활성 회원 참여자 대상 `TRIP_PARTICIPANT_JOINED` 저장
- `TripParticipantService.removeParticipant(...)` 성공 후 제거된 회원 참여자 대상 `TRIP_PARTICIPANT_REMOVED` 저장
- 코드/링크 초대 생성 시 outbox 미생성 확인
- 초대/참여 테스트 작성

이 단위가 끝나면 `TRIP_PARTICIPANTS_ADDED`, `TRIP_PARTICIPANT_JOINED`, `TRIP_PARTICIPANT_REMOVED`가 실제 기능 흐름에서 저장된다.

### 구현 단위 4. 소비 게시글 통합 생성 API와 거래 생성 로직 공통화

목표는 소비 알림의 정확한 발행 시점을 만들기 위해 현재 앱의 2단계 호출 문제를 서버에서 먼저 해결하는 것이다.
이 단위는 기능 변경 폭이 가장 크므로 게시글 일반 알림과 분리해서 진행한다.

포함 작업은 다음과 같다.

- `TransactionService.createTransaction(...)` 내부의 거래 생성 로직을 공통 내부 서비스로 분리
- 기존 거래 생성 API가 분리된 내부 서비스를 사용하도록 변경
- 기존 거래 생성 API 응답은 `TransactionDetailResponse` 유지
- 소비 게시글 통합 생성 API 추가
- 예시 endpoint: `POST /api/trips/{tripId}/expense-posts`
- 요청 형식: `multipart/form-data`
- 요청 내용: 게시글 필드, 첨부 파일, 거래 생성 필드
- 거래 생성, 결제자/부담자 저장, 소비 게시글 생성, 첨부 저장, outbox 저장을 하나의 DB 트랜잭션으로 처리
- 통합 생성 API 응답은 게시글 상세 기준으로 하되 연결된 거래 요약 포함
- 통합 생성 API 성공 후에만 `EXPENSE_POST_CREATED` 저장
- 거래 단독 생성 API에서는 사용자 알림용 outbox 미생성 확인
- 통합 생성 API 테스트와 기존 거래 생성 API 회귀 테스트 작성

이 단위가 끝나면 소비 등록은 서버 관점에서 거래와 게시글이 한 트랜잭션으로 묶이고, `EXPENSE_POST_CREATED`를 안전하게 발행할 수 있다.
앱 전환은 client #25에서 별도로 진행한다.

### 구현 단위 5. 게시글/댓글 이벤트 연결

목표는 일반 기록과 댓글 알림을 연결하고, 소비 게시글은 통합 생성 API에서만 알림이 나가게 하는 것이다.

포함 작업은 다음과 같다.

- 일반 기록 `PostService.createPost(...)` 성공 후 `POST_CREATED` 저장
- 게시글 작성자 제외
- 여행 활성 회원 참여자 대상 처리
- 소비 게시글 알림은 통합 생성 API의 `EXPENSE_POST_CREATED`로만 처리
- `PostService.createComment(...)` 성공 후 `POST_COMMENT_CREATED` 저장
- 댓글 알림 대상은 게시글 작성자 1명으로 제한
- 댓글 작성자가 게시글 작성자와 같으면 outbox 미생성
- 게시글 작성자가 임시 참여자이거나 `userId`가 없으면 outbox 미생성
- 게시글/댓글 테스트 작성

이 단위가 끝나면 `POST_CREATED`, `EXPENSE_POST_CREATED`, `POST_COMMENT_CREATED`의 발행 경계가 분리된다.

### 구현 단위 6. 정산/송금 이벤트 연결

목표는 정산 확정과 송금 확인 상태 변화에 맞춰 필요한 알림 이벤트를 저장하는 것이다.
여기서는 중복 알림을 막기 위해 실제 상태 변경 여부를 서비스 밖으로 전달하는 작업이 함께 필요하다.

포함 작업은 다음과 같다.

- `SettlementService.confirmSettlement(...)` 성공 후 `SETTLEMENT_CONFIRMED` 저장
- 확정자 제외
- `SETTLEMENT_CONFIRMED` outbox 행 1개에 사용자별 송금 항목 요약 포함
- 별도 `SETTLEMENT_TRANSFER_CREATED` 미생성
- `SettlementTransferRow` projection에 `senderUserId`, `receiverUserId` 추가 또는 이벤트 발행 시 `SettlementTransfer` entity 재조회
- `SettlementTransferConfirmationProcessor`의 실제 변경 여부를 서비스 밖에서 알 수 있게 결과 DTO 또는 projection 보강
- `confirmAsSender(...)`에서 sender 확인이 이번 요청으로 새로 기록된 경우 `SETTLEMENT_TRANSFER_CONFIRMED_BY_SENDER` 저장
- sender 또는 receiver 확인 요청으로 상태가 새로 `COMPLETED`가 된 경우 `SETTLEMENT_TRANSFER_COMPLETED` 저장
- 탈퇴 사용자 자동 확인 시 사용자 알림 이벤트 미생성
- 정산/송금 테스트 작성

이 단위가 끝나면 `SETTLEMENT_CONFIRMED`, `SETTLEMENT_TRANSFER_CONFIRMED_BY_SENDER`, `SETTLEMENT_TRANSFER_COMPLETED`가 상태 변화 기준으로 저장된다.

### 구현 단위 7. SQS 후속 확장 경계 확인

목표는 이번 이슈 범위 안에서 SQS를 구현하지 않더라도, 이후 SQS 연동이 작게 붙을 수 있는 구조인지 마지막에 확인하는 것이다.

포함 작업은 다음과 같다.

- 도메인 서비스가 SQS, 알림 서버, 발행 작업자를 직접 참조하지 않는지 확인
- outbox payload가 SQS 메시지로 그대로 감쌀 수 있는 형태인지 확인
- outbox 행이 수신자별로 쪼개지지 않는지 확인
- 알림 서버 멱등 키 `(sourceService, sourceEventId, userId)` 계약이 문서에 남아 있는지 확인
- 후속 SQS 연동 시 추가할 `OutboxEventSender`, `SqsOutboxEventSender`, 발행 작업자 경계 재확인

이 단위가 끝나면 #37은 `PENDING` outbox 저장까지 완료되고, SQS 실제 전송은 후속 이슈로 안전하게 넘길 수 있다.

아래 세부 항목은 위 구현 단위에 포함되는 구체 작업 목록이다.

### 1. `OutboxEventPublisher` 추가

- `OutboxEventRepository`를 감싼 얇은 서비스로 둔다.
- `publish(aggregateType: OutboxAggregateType, aggregateId: Long, eventType: OutboxEventType, payload: OutboxNotificationPayload)` 형태를 기본으로 한다.
- 이벤트 데이터 직렬화는 Jackson `ObjectMapper`를 사용한다.
- `aggregateId`는 `OutboxEvent`에서 nullable이 아니므로 이벤트별 기준 원장 id를 반드시 정한다.
- 저장 직전 payload의 `recipients`를 `userId` 기준으로 중복 제거하되, 처음 등장한 순서를 유지한다.
- payload의 수신자 목록이 비어 있으면 outbox 행을 저장하지 않고 반환한다.
- outbox 저장은 도메인 트랜잭션 안에서 함께 수행한다.

### 2. 이벤트 타입과 원장 타입 enum 정의

- `OutboxEventType` enum을 추가해 문자열 오타를 줄인다.
- enum 값은 `TRIP_PARTICIPANTS_ADDED`, `TRIP_PARTICIPANT_JOINED`, `TRIP_PARTICIPANT_REMOVED`, `POST_CREATED`, `EXPENSE_POST_CREATED`, `POST_COMMENT_CREATED`, `SETTLEMENT_CONFIRMED`, `SETTLEMENT_TRANSFER_CONFIRMED_BY_SENDER`, `SETTLEMENT_TRANSFER_COMPLETED`로 시작한다.
- 도메인 서비스는 문자열 대신 `OutboxEventType`을 사용하고, 저장 시 enum의 `name`을 `OutboxEvent.eventType`에 넣는다.
- `OutboxAggregateType` enum을 추가하고, 값은 `TRIP`, `POST`, `SETTLEMENT`, `SETTLEMENT_TRANSFER`처럼 원장 기준으로 둔다.
- 도메인 서비스는 문자열 대신 `OutboxAggregateType`을 사용하고, 저장 시 enum의 `name`을 `OutboxEvent.aggregateType`에 넣는다.

### 3. 이벤트별 payload DTO 정의

- `Map<String, Any>`를 직접 조립하지 않고 이벤트별 data class를 둔다.
- DTO는 `global/outbox` 하위 패키지에 모아 아웃박스 이벤트 계약을 한 곳에서 관리한다.
- 알림용 payload DTO는 `OutboxNotificationPayload` 공통 인터페이스를 구현한다.
- `eventVersion`은 각 payload DTO의 기본값으로 둔다. 처음 버전은 `val eventVersion: Int = 1`로 시작한다.
- 공통 수신자 항목은 `OutboxRecipientPayload(userId)`처럼 작게 분리한다.
- `TRIP_PARTICIPANTS_ADDED`, `TRIP_PARTICIPANT_JOINED`, `TRIP_PARTICIPANT_REMOVED`는 여행/참여자 기준 payload DTO를 사용한다.
- `POST_CREATED`, `EXPENSE_POST_CREATED`, `POST_COMMENT_CREATED`는 게시글/댓글 기준 payload DTO를 사용한다.
- `SETTLEMENT_CONFIRMED`, `SETTLEMENT_TRANSFER_CONFIRMED_BY_SENDER`, `SETTLEMENT_TRANSFER_COMPLETED`는 정산/송금 기준 payload DTO를 사용한다.
- 정산 확정 payload의 `recipients`만 송금 항목 요약을 포함하는 별도 recipient DTO를 사용하되, 이 DTO도 `userId`를 노출해 publisher의 중복 제거 대상이 되게 한다.

### 4. 대상자 조회 기능 추가

- 여행 활성 회원 참여자 `userId` 목록 조회 기능을 둔다.
- 행위자는 대상에서 제외한다.
- nullable `userId`, 탈퇴/삭제 참여자는 제외한다.
- 현재 `TripParticipantRepository`에는 이 목적의 전용 조회가 없으므로 query method 또는 native projection을 추가한다.
- 댓글 알림처럼 게시글 작성자 1명만 대상인 경우에도 작성자 본인 여부와 `userId` 존재 여부를 확인한다.

### 5. 초대/참여 이벤트 연결

- `createTrip(...)` 성공 후 요청에 포함된 기존 사용자 동행자를 `TRIP_PARTICIPANTS_ADDED`로 기록한다.
- 여행 생성 요청에 기존 사용자 동행자가 여러 명이면 outbox 행은 1개만 만들고, `recipients`에 여러 사용자를 담는다.
- `linkTemporaryParticipant(...)` 성공 후 연결된 기존 사용자를 `TRIP_PARTICIPANTS_ADDED`로 기록한다.
- `joinTrip(...)` 성공 후 기존 참여자에게 `TRIP_PARTICIPANT_JOINED`를 기록한다.
- `removeParticipant(...)` 성공 후 제거된 참여자가 회원이면 `TRIP_PARTICIPANT_REMOVED`를 기록한다.
- 코드/링크 초대 생성은 대상 사용자가 없으므로 알림 이벤트를 만들지 않는다.

### 6. 소비 게시글 통합 생성 API 추가

- #37 안에서 소비 등록 통합 생성 API를 추가해 거래와 게시글을 한 트랜잭션으로 생성한다.
- 예시 endpoint는 `POST /api/trips/{tripId}/expense-posts`로 둔다.
- 요청 형식은 `multipart/form-data`로 둔다.
- 요청에는 게시글 필드, 첨부 파일, 거래 생성 필드를 함께 담는다.
- 거래 생성, 결제자/부담자 저장, 소비 게시글 생성, 첨부 저장, outbox 저장을 하나의 DB 트랜잭션으로 처리한다.
- 통합 생성 API 성공 후에만 `EXPENSE_POST_CREATED`를 저장한다.
- 거래 단독 생성 시점에는 사용자 알림용 outbox를 만들지 않는다.
- 통합 생성 API 응답은 게시글 상세를 기준으로 하고, 연결된 거래 요약을 함께 포함한다.

### 7. 거래 생성 로직 공통화

- 거래 생성 로직은 복제하지 않는다.
- 현재 `TransactionService.createTransaction(...)` 안에 있는 원장 생성, 결제자/부담자 저장, 환율 스냅샷, 거래 이벤트 기록 로직을 공통 내부 서비스로 분리한다.
- 예를 들어 `TransactionCreationService` 또는 `TransactionCommandService` 같은 내부 서비스를 둔다.
- 기존 거래 생성 API와 소비 게시글 통합 생성 API가 같은 내부 서비스를 사용하게 한다.
- 기존 거래 생성 API는 계속 `TransactionDetailResponse`를 반환한다.
- 소비 게시글 통합 생성 API는 생성된 `Transaction`과 저장된 payment/share를 받아 소비 게시글 생성과 outbox 저장을 이어서 처리한다.

### 8. 게시글/댓글 이벤트 연결

- 일반 기록 `createPost(...)` 성공 후 `POST_CREATED`를 저장한다.
- 일반 기록도 이번 범위에서는 알림을 보낸다.
- 게시글 알림 대상은 여행의 활성 회원 참여자이며 작성자는 제외한다.
- `createComment(...)` 성공 후 `POST_COMMENT_CREATED`를 저장한다.
- 댓글 알림은 게시글 작성자에게만 보낸다.
- 댓글 작성자가 게시글 작성자와 같거나 게시글 작성자가 임시 참여자인 경우 이벤트를 만들지 않는다.
- 기존 댓글 작성자나 여행 참여자 전체에게는 댓글 알림을 보내지 않는다.

### 9. 정산/송금 이벤트 연결

- `confirmSettlement(...)` 성공 후 `SETTLEMENT_CONFIRMED`를 저장한다.
- 확정자는 대상에서 제외한다.
- `SETTLEMENT_CONFIRMED` outbox 행은 하나만 만들고, `recipients`에 사용자별 송금 항목 요약을 함께 담는다.
- 별도 `SETTLEMENT_TRANSFER_CREATED` 이벤트는 만들지 않는다.
- `confirmAsSender(...)`에서 실제로 sender 확인이 새로 기록된 경우 `SETTLEMENT_TRANSFER_CONFIRMED_BY_SENDER`를 저장한다.
- sender 또는 receiver 확인 요청으로 상태가 새로 `COMPLETED`가 된 경우 `SETTLEMENT_TRANSFER_COMPLETED`를 저장한다.
- 이를 위해 확인 처리 결과 DTO를 추가하거나 projection에 변경 전/후 판단에 필요한 값을 포함한다.
- 송금 확인 알림 대상 `userId`를 얻기 위해 `SettlementTransferRow` projection에 `senderUserId`, `receiverUserId`를 추가하거나, 이벤트 발행 시 `SettlementTransfer` entity를 다시 조회한다.

### 10. SQS 후속 확장 경계 유지

- 이번 이슈에서는 `PENDING` outbox 행 생성까지만 완료 기준으로 둔다.
- 도메인 서비스는 SQS, 알림 서버, 발행 작업자를 직접 참조하지 않는다.
- SQS를 사용할 수 있으면 후속 이슈에서 `SqsOutboxEventSender`와 발행 작업자를 추가한다.
- 후속 SQS 연동은 `OutboxEventSender`와 발행 작업자를 추가하는 방식으로 분리한다.
- outbox 행은 논리 이벤트 기준으로 1개만 만들고, 수신자는 이벤트 데이터의 `recipients`에 담는다.
- 기본 `recipients` 항목은 `userId`만 포함하고, 정산처럼 수신자별 내용이 필요한 경우에만 요약 데이터를 함께 담는다.

## 테스트 계획

### 1. `OutboxEventPublisher` 단위 테스트

- 이벤트 데이터가 jsonb 문자열로 저장된다.
- payload DTO의 `eventVersion` 기본값이 저장된다.
- 기본 상태가 `PENDING`이다.
- 수신자가 여러 명이어도 outbox 행은 1개만 저장되고 `recipients`에 여러 사용자가 들어간다.
- 중복된 수신자가 들어오면 `userId` 기준으로 한 번만 저장되고, 처음 등장한 순서가 유지된다.
- 수신자가 0명이면 outbox 행이 저장되지 않는다.
- `OutboxEventType`과 `OutboxAggregateType` enum의 `name`이 각각 `eventType`, `aggregateType`에 저장된다.

### 2. 초대/참여 테스트

- 여행 생성 요청에 기존 사용자 동행자 `userId`가 포함되면 해당 사용자 대상 `TRIP_PARTICIPANTS_ADDED` outbox가 생성된다.
- 여행 생성 요청에 기존 사용자 동행자가 여러 명 포함되어도 `TRIP_PARTICIPANTS_ADDED` outbox 행은 1개만 생성되고, `recipients`에 여러 사용자가 들어간다.
- 여행 생성 요청의 임시 동행자에는 `TRIP_PARTICIPANTS_ADDED` outbox가 생성되지 않는다.
- 방장이 임시 참여자를 기존 사용자와 연결하면 연결된 사용자 대상 `TRIP_PARTICIPANTS_ADDED` outbox가 생성된다.
- 코드/링크 초대 생성만으로는 outbox가 생성되지 않는다.
- 참여 완료 시 기존 활성 회원 참여자 대상 `TRIP_PARTICIPANT_JOINED` outbox가 생성된다.
- 참여자 본인은 대상에서 제외된다.
- 임시 참여자 또는 `userId`가 없는 대상은 제외된다.
- 제거된 참여자가 회원이면 제거된 사용자에게만 `TRIP_PARTICIPANT_REMOVED` outbox가 생성된다.
- 제거된 참여자가 임시 참여자이면 `TRIP_PARTICIPANT_REMOVED` outbox가 생성되지 않는다.

### 3. 게시글/댓글/소비 테스트

- 일반 기록 생성 시 `POST_CREATED`가 저장된다.
- 일반 기록 작성자는 알림 대상에서 제외된다.
- 소비 게시글 통합 생성 API 성공 시 거래와 게시글이 함께 저장되고 `EXPENSE_POST_CREATED`가 저장된다.
- 소비 게시글 통합 생성 API 응답에는 게시글 상세와 연결된 거래 요약이 포함된다.
- 소비 게시글 통합 생성 중 게시글 저장이 실패하면 거래도 함께 롤백된다.
- 거래 단독 생성 API는 사용자 알림용 outbox를 만들지 않는다.
- 댓글 작성 시 게시글 작성자 대상 `POST_COMMENT_CREATED`가 저장된다.
- 본인 게시글에 본인이 댓글을 단 경우 알림 이벤트가 생성되지 않는다.
- 게시글 작성자가 임시 참여자이거나 `userId`가 없으면 댓글 알림 이벤트가 생성되지 않는다.

### 4. 정산/송금 테스트

- 정산 확정 시 `SETTLEMENT_CONFIRMED`가 저장된다.
- 정산 확정자는 `SETTLEMENT_CONFIRMED` 대상에서 제외된다.
- 정산 확정 알림에는 대상 사용자의 송금 항목 요약이 포함된다.
- 별도 `SETTLEMENT_TRANSFER_CREATED`는 저장되지 않는다.
- 송금자 확인 시 수금자 대상 `SETTLEMENT_TRANSFER_CONFIRMED_BY_SENDER`가 저장된다.
- 이미 확인된 송금을 다시 확인한 no-op 요청에서는 중복 알림 이벤트가 생성되지 않는다.
- 양쪽 확인 완료 시 `SETTLEMENT_TRANSFER_COMPLETED`가 저장된다.
- 송금 완료 이벤트는 이번 요청으로 상태가 새로 `COMPLETED`가 된 경우에만 저장된다.
- 탈퇴 사용자 자동 확인은 사용자 알림 이벤트를 만들지 않는다.

### 5. 회귀 테스트

- outbox 저장은 도메인 트랜잭션 안에서 함께 커밋된다.
- outbox 저장이 실패하면 도메인 변경도 함께 롤백된다.
- 도메인 검증 실패 시 outbox 행이 남지 않는다.
- 도메인 서비스가 SQS 관련 타입에 의존하지 않는다.
- 같은 outbox event가 재전송될 수 있으므로 알림 서버가 `(sourceService, sourceEventId, userId)` 기준으로 멱등 처리해야 한다는 계약을 문서에 남긴다.

## 위험과 확인 사항

- `TRIP_PARTICIPANTS_ADDED`는 코드/링크 초대가 아니라 여행 생성 요청에 포함된 기존 사용자 동행자와 `participant-connections` 기반 기존 사용자 연결에서 발행한다.
  현재 구현은 수락/거절이 없는 즉시 참여 상태이므로, 사용자에게 보이는 문구는 “초대받았다”보다 “여행에 추가됐다”로 맞춘다.
- 소비 알림은 현재 분리 호출 구조에서는 정확한 발행 시점을 잡기 어렵다.
  #37 안에서 소비 게시글 통합 생성 API를 먼저 만들고, 해당 API 성공 시에만 `EXPENSE_POST_CREATED`를 발행한다.
- 거래 생성 로직을 통합 API 안에 복제하면 기존 거래 API와 소비 게시글 통합 API의 검증/환율/이벤트 기록이 갈라질 수 있다.
  거래 생성 내부 서비스를 분리해 두 API가 같은 로직을 사용하게 한다.
- outbox 저장 실패는 도메인 트랜잭션 실패로 본다.
  알림 누락을 허용하지 않고 transactional outbox의 일관성을 우선한다.
- 이벤트 데이터에는 알림 문구 생성에 필요한 표시 정보를 발생 시점 스냅샷으로 넣는다.
  알림 서버가 main 서버를 다시 조회하지 않아도 문구를 만들 수 있게 하고, 과거 알림은 당시 정보로 유지한다.
- 정산 확정 시 transfer가 많아도 main outbox 행은 하나만 만든다.
  사용자별 송금 요약은 `recipients` 안에 담고, 알림 서버가 수신자별 알림 행으로 나눈다.
- 이벤트 타입과 이벤트 데이터 버전은 알림 서버와 공유하는 약속이 되므로 문서화 후 변경 시 버전을 올린다.
- SQS 연동을 붙일 때도 도메인 서비스는 수정하지 않는 방향을 유지한다.
  새 의존성은 발행 작업자와 `OutboxEventSender` 구현체에만 둔다.
- 수신자별 알림 행과 FCM 발송 이력 행은 알림 서버 책임이다.
  main 서버 outbox는 도메인 이벤트 원장으로만 유지한다.
