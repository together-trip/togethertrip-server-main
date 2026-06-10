# 정산 흐름 리뷰 위험 대응 구현 계획

## 배경

- 대상 브랜치: `feature/issue-34-settlement-flow`
- 대상 PR: #51 `feat: 정산 미리보기와 확정 흐름 구현`
- 기준 이슈: #34 `feat: 정산 미리보기와 확정 흐름 구현`
- 작성일: 2026-06-08

정산 흐름 구현 리뷰에서 공개 공유 API의 정보 노출, 중도 퇴장/제거 참여자 정산 포함 정책 불일치, 정산 확정 동시성, 송금 확인 재호출, 서비스 테스트 부족이 병합 전 위험으로 확인되었다. 이 문서는 해당 위험을 TogetherTrip 에이전트 역할 기준으로 수정하기 위한 구현 계획이다.

## TogetherTrip 에이전트 판단

### Planner

- 이번 수정은 #34 정산 MVP의 병합 전 안정화 범위다.
- 신규 기능 확장보다 기존 PR의 보안, 무결성, 정책 일치성을 우선한다.
- 코드 변경은 공개 DTO 분리, 정산 참여자 조회 정책, 확정 동시성, 송금 상태 전이, 테스트 보강으로 제한한다.

### Security Reviewer

- 인증 없는 공유 API는 내부 ID와 개인정보 노출을 최소화해야 한다.
- 정산 금액은 과거 거래 스냅샷을 기준으로 유지되어야 하며, 참여자 상태 변경으로 채권/채무가 사라지면 안 된다.
- 정산 확정은 동시에 두 번 성공하거나 내부 예외가 그대로 노출되면 안 된다.
- 송금 확인 timestamp는 감사성 데이터이므로 최초 기록 후 덮어쓰지 않는다.

### Architect

- feature-based MVC 구조를 유지한다.
- 공개 공유 응답 DTO는 `settlement/dto/response`에 별도 클래스로 둔다.
- 정산 참여자 조회는 일반 활성 참여자 조회와 분리하고, 정산 전용 snapshot/projection 조회 책임을 `settlement/service/support` 또는 repository 전용 메서드로 격리한다.
- 도메인 상태 전이는 `SettlementTransfer` 내부 메서드에서 보장한다.
- Service는 method-level `@Transactional`만 사용한다.

### TDD Guide

- 현재 누락된 서비스 흐름 테스트를 우선 추가한다.
- 공개 응답 필드 최소화, 퇴장/제거 참여자 포함, 중복 확정, 송금 확인 재호출을 회귀 테스트로 잠근다.
- 계산 엔진 순수 단위 테스트는 유지하고, 변경 대상은 서비스/도메인 테스트 중심으로 보강한다.

### Verify Agent

- 구현 후 `./gradlew test`를 실행한다.
- 문서와 코드 정책이 서로 맞는지 확인한다.
- PR 본문 또는 검증 문서가 있다면 공개 공유 응답과 참여자 포함 정책을 실제 코드 기준으로 갱신한다.

## 수정 대상 위험

### 1. 공개 공유 API 응답 정보 노출

현재 `GET /api/settlement-shares?token=...`은 인증 없이 접근 가능한데 내부 정산 조회용 `SettlementResponse`를 그대로 반환한다. 이 응답에는 다음 값이 포함된다.

- `confirmedByUserId`
- `participantId`
- `userId`
- `profileImageUrl`
- transfer 내부 `id`
- 내부 계산 버전

공개 공유 응답은 외부 전달용 링크로 소비될 수 있으므로 내부 식별자와 개인정보를 제거해야 한다.

#### 구현 방안

1. `SettlementShareResponse`를 추가한다.
2. 공개용 balance/transfer DTO를 추가하거나 `SettlementShareResponse` 내부 중첩 DTO로 둔다.
3. 공개 응답 필드는 다음으로 제한한다.
   - `settlementId` 공개 여부는 재검토한다. 기본은 제거한다.
   - `status`
   - `baseCurrency`
   - `totalExpenseAmount`
   - `totalShareAmount`
   - `confirmedAt`
   - 참여자 표시명
   - 참여자 정산 금액
   - 송금자/수금자 표시명
   - 송금 금액과 상태
4. `SettlementShareController`, `SettlementShareApiSpec`, `SettlementShareService` 반환 타입을 `SettlementShareResponse`로 변경한다.
5. 내부 인증 API의 `SettlementResponse`는 유지한다.

## 2. 중도 퇴장/제거/탈퇴 참여자 정산 포함 정책 불일치

정책은 과거 거래의 결제자/부담자로 저장된 참여자를 정산에 포함하는 것이다. 현재 구현은 `TripParticipant`의 `@SQLRestriction("deleted_at IS NULL")`와 `findByTripIdAndDeletedAtIsNullOrderByCreatedAtAsc`에 의존하므로 soft-deleted 참여자가 과거 거래에 연결되어 있으면 정산 실패 또는 누락 위험이 있다.

#### 구현 방안

1. 정산 계산에 필요한 참여자 조회를 일반 여행 참여자 조회와 분리한다.
2. 거래 payment/share에 실제 등장한 participant id 목록을 기준으로 정산 참여자 snapshot을 만든다.
3. soft-deleted 참여자를 포함해야 하면 다음 중 하나를 선택한다.
   - native query/projection으로 `trip_participants`를 직접 조회한다.
   - `@SQLRestriction` 영향을 받지 않는 정산 전용 조회 모델을 둔다.
4. 응답에는 참여자 상태를 표시하되, 탈퇴 사용자 개인정보는 마스킹한다.
5. 거래에 등장하지 않은 0원 참여자를 미리보기/확정 스냅샷에 포함할지는 별도 정책으로 분리한다. 이번 수정 기본값은 “거래에 등장한 참여자 중심”이다.

## 3. 정산 확정 동시성 보강

현재 흐름은 `settlementStatus`와 기존 confirmed settlement를 확인한 뒤 저장한다. 동시에 두 요청이 들어오면 둘 다 검증을 통과할 수 있고, DB unique index 충돌이 flush/commit 시점에 발생하면 비즈니스 예외 변환이 보장되지 않는다.

#### 구현 방안

1. `Trip.version` 기반 optimistic lock을 우선 사용한다.
2. `Trip` 엔티티에 `@Version` 매핑이 실제로 있는지 먼저 확인한다. 컬럼만 있고 엔티티 매핑이 없으면 낙관적 락은 동작하지 않는다.
3. 확정 흐름에서는 `trip.markSettled(...)`를 반드시 수행해 같은 여행을 동시에 확정하는 요청이 같은 aggregate version을 갱신하게 한다.
4. `tripRepository.saveAndFlush(trip)` 또는 `settlementRepository.saveAndFlush(settlement)`로 서비스 메서드 내부에 flush 지점을 만든다.
5. 동시 확정 충돌은 다음 예외를 `SETTLEMENT_ALREADY_CONFIRMED`로 변환한다.
   - `ObjectOptimisticLockingFailureException`
   - `OptimisticLockException`
   - `DataIntegrityViolationException`
6. confirmed settlement unique index는 최종 방어선으로 유지한다.
7. `Settlement`, `SettlementTransfer`, `Trip.markSettled(...)` 변경은 기존처럼 단일 트랜잭션에서 처리한다.

#### 대안

- `Trip.version` 매핑이 없거나 확정 흐름에서 version 갱신을 안정적으로 보장하기 어렵다면 `Trip` row pessimistic lock을 적용한다.
- pessimistic lock은 확정 요청을 trip 단위로 직렬화하므로 단순하지만, 락 대기 비용이 있다. #34 범위에서는 optimistic lock을 우선 추천한다.

## 4. 송금 확인 재호출 정책

현재 `SettlementTransfer.confirmAsSender(...)`, `confirmAsReceiver(...)`는 이미 확인된 송금도 timestamp를 다시 쓸 수 있다. 송금 확인 시각은 감사성 데이터이므로 최초 확인 이후 덮어쓰면 안 된다.

#### 구현 방안

1. sender 재확인은 no-op으로 처리한다.
2. receiver 재확인도 no-op으로 처리한다.
3. 양쪽 확인이 완료되어 `COMPLETED`가 된 이후에는 `completedAt`을 덮어쓰지 않는다.
4. 다른 참여자의 확인 시도는 기존처럼 access denied를 유지한다.
5. 상태 전이는 `SettlementTransfer` 도메인 메서드 내부에서 보장한다.

## 5. 테스트 보강

### SettlementShareServiceTest

- 공유 토큰 조회 성공 시 공개 DTO만 반환한다.
- 공개 응답에 `userId`, `participantId`, `profileImageUrl`, `confirmedByUserId`, transfer `id`가 없다.
- 토큰이 없으면 실패한다.
- 확정되지 않은 정산이면 실패한다.

### SettlementServiceTest

- 방장만 정산을 확정할 수 있다.
- 이미 확정된 정산이 있으면 실패한다.
- unique index 충돌은 `SETTLEMENT_ALREADY_CONFIRMED`로 변환한다.
- 과거 거래에 포함된 `LEFT`, `REMOVED`, soft-deleted 참여자가 정산 결과에 포함된다.
- 확정 시 `Trip.settlementStatus`가 `SETTLED`로 변경된다.

### SettlementTransferServiceTest

- sender만 송금자 확인 가능하다.
- receiver만 수금자 확인 가능하다.
- sender 재확인은 기존 `senderConfirmedAt`을 덮어쓰지 않는다.
- receiver 재확인은 기존 `receiverConfirmedAt`을 덮어쓰지 않는다.
- 양쪽 확인 완료 후 `completedAt`은 최초 완료 시각을 유지한다.

## 구현 순서

1. 공개 공유 응답 DTO를 분리한다.
2. `SettlementShareService`와 spec/controller 반환 타입을 변경한다.
3. `SettlementTransfer` 상태 전이 메서드를 idempotent하게 수정한다.
4. 정산 참여자 snapshot 조회 정책을 구현한다.
5. 정산 확정용 optimistic lock 예외 변환과 `saveAndFlush`를 적용한다.
6. 서비스 테스트를 추가한다.
7. 문서와 Swagger spec을 실제 응답 정책에 맞게 갱신한다.
8. `./gradlew test`를 실행한다.

## 완료 기준

- 공개 공유 API에서 내부 ID와 개인정보가 노출되지 않는다.
- 과거 거래에 포함된 퇴장/제거/탈퇴 참여자의 정산 금액이 사라지지 않는다.
- 동시 정산 확정 시 하나만 성공하고 나머지는 비즈니스 예외로 변환된다.
- 송금 확인 재호출이 timestamp를 덮어쓰지 않는다.
- `./gradlew test`가 통과한다.
- PR 설명, 작업 계획, Swagger spec이 실제 코드와 충돌하지 않는다.

## 2차 코드 리뷰 반영 계획

### 배경

1차 구현 후 재리뷰에서 다음 추가 위험이 확인되었다.

- 정산 확정 응답의 `transfers.id`가 저장된 `SettlementTransfer.id`가 아니라 `null`로 반환될 수 있다.
- 정산 결과 조회는 native projection으로 soft-deleted 참여자를 처리하지만, 송금 목록/확인 API는 여전히 entity 기반 조회를 사용한다.
- 정산 확정의 optimistic lock 및 unique index 예외 변환이 테스트로 검증되지 않았다.
- `transaction` repository가 `settlement` domain projection에 의존해 feature 간 결합 방향이 어긋난다.
- 공개 공유 응답 테스트가 필드 이름만 검증해 실제 mapper의 민감정보 제거를 충분히 보장하지 못한다.
- `SettlementTransfer` 포매팅이 Kotlin style과 맞지 않는 부분이 있다.

### TogetherTrip 에이전트 판단

#### Planner

- 이번 범위는 1차 위험 대응 구현의 마무리 안정화다.
- API 스펙 확장 없이 응답 정합성, soft-delete 정산 정책, 테스트 보강, 패키지 결합 정리를 수행한다.
- 송금 확인 가능 주체 정책은 이번 구현 전 명확히 기록한다.
- 탈퇴 사용자는 직접 확인할 수 없으므로 송금자/수금자 어느 쪽이든 자동 동의로 처리한다.

#### Security Reviewer

- 공개 공유 API는 실제 mapper 결과 기준으로 내부 식별자와 개인정보가 제거되는지 검증해야 한다.
- soft-deleted 참여자가 송금 sender/receiver인 경우에도 정산 결과가 누락되지 않아야 한다.
- 제거/퇴장 사용자의 직접 송금 확인은 별도 정책이다. 현재 인증 API는 `ACTIVE` 참여자만 허용한다.
- 탈퇴 사용자는 인증 API를 호출할 수 없으므로 송금자/수금자 역할과 무관하게 자동 동의 처리한다.

#### Architect

- `TransactionPaymentRepository`, `TransactionShareRepository`가 settlement projection을 import하지 않도록 결합 방향을 정리한다.
- 선택지는 둘 중 하나다.
  - projection interface를 `transaction/repository` 쪽으로 이동한다.
  - settlement 전용 query repository를 만들어 settlement feature 내부에서 native query를 소유한다.
- 추천은 후자다. 정산 입력 조회는 transaction feature의 일반 책임이 아니라 settlement use case 전용 조회이기 때문이다.
- `SettlementTransferService`는 entity 조회와 projection 조회가 섞이지 않도록 송금 목록 조회용 row mapper를 분리한다.

#### TDD Guide

- 확정 응답의 transfer id, soft-deleted transfer 조회, optimistic lock 예외 변환, 공개 공유 mapper를 테스트로 잠근다.
- 단순 reflection 테스트보다 실제 DTO 변환 결과를 검증한다.

#### Verify Agent

- `./gradlew test`를 재실행한다.
- 문서의 soft-deleted 참여자 정책과 실제 송금 API 정책이 서로 충돌하지 않는지 확인한다.

### 수정 항목

#### 1. 정산 확정 응답 transfer id 보정

현재 확정 응답은 저장 전 송금 계획 DTO를 사용하므로 `SettlementTransferResponse.id`가 `null`일 수 있다.

구현 방안:

1. `saveConfirmedSettlementSnapshot(...)`이 저장된 `Settlement`만 반환하는 구조를 유지한다.
2. `confirmSettlement(...)`은 저장 이후 `settlementTransferRepository.findTransferRowsBySettlementId(settlement.id)`로 실제 저장된 송금 목록을 다시 조회한다.
3. 응답에는 저장된 transfer `id`, 확인 시각, 상태를 포함한다.
4. 미리보기 응답은 저장 전 계획이므로 `id = null`을 유지한다.

테스트:

- `confirmSettlement` 성공 응답의 `transfers.first().id`가 저장된 transfer id와 일치한다.
- 미리보기 응답의 transfer id는 계속 `null`이다.

#### 2. 송금 목록/확인 API의 soft-deleted 참여자 정책 정리

현재 `SettlementTransferService.getTransfers(...)`는 entity fetch query를 사용한다. soft-deleted 참여자가 sender/receiver인 송금은 누락될 수 있다.

구현 방안:

1. 송금 목록 조회는 native projection 기반으로 변경한다.
2. `participantId`, `settlementId`, `status`, `direction` 필터를 projection query 또는 service filter로 동일하게 적용한다.
3. display name은 탈퇴 사용자 마스킹 정책을 적용한다.
4. `confirmAsSender(...)`, `confirmAsReceiver(...)`는 현재처럼 `ACTIVE` 참여자만 허용한다.
5. 탈퇴 사용자가 sender면 `senderConfirmedAt`을 자동 기록하고 `autoConfirmed = true`, `autoConfirmReason`에 탈퇴 사용자 자동 동의 사유를 남긴다.
6. 탈퇴 사용자가 receiver면 `receiverConfirmedAt`을 자동 기록하고 `autoConfirmed = true`, `autoConfirmReason`에 탈퇴 사용자 자동 동의 사유를 남긴다.
7. sender와 receiver가 모두 탈퇴 사용자면 양쪽 확인을 자동 기록하고 즉시 `COMPLETED`로 둔다.
8. 제거/퇴장 참여자의 송금 확인은 #34 범위 밖 정책으로 남긴다. 필요하면 방장 대리 확인 또는 수동 완료 API를 후속 이슈로 분리한다.

테스트:

- soft-deleted sender/receiver가 포함된 송금도 목록 조회에 포함된다.
- `direction=SENT/RECEIVED` 필터가 현재 활성 사용자 기준으로 정상 동작한다.
- 탈퇴 사용자 표시명은 `탈퇴한 사용자`로 마스킹된다.
- 탈퇴 사용자가 sender인 송금은 송금자 확인이 자동 완료된다.
- 탈퇴 사용자가 receiver인 송금은 수금자 확인이 자동 완료된다.
- 양쪽 모두 탈퇴 사용자면 송금 상태가 즉시 `COMPLETED`가 된다.

#### 3. 정산 확정 동시성 테스트 보강

구현 방안:

1. `settlementRepository.saveAndFlush(...)`가 `DataIntegrityViolationException`을 던지면 `SETTLEMENT_ALREADY_CONFIRMED`로 변환되는지 테스트한다.
2. `tripRepository.saveAndFlush(...)`가 `ObjectOptimisticLockingFailureException`을 던지면 `SETTLEMENT_ALREADY_CONFIRMED`로 변환되는지 테스트한다.
3. 가능하면 `OptimisticLockException`도 별도 단위 테스트로 검증한다.

테스트:

- 중복 confirmed unique index 충돌 변환
- `Trip.version` optimistic lock 충돌 변환
- 실패 시 응답/예외가 내부 JPA 예외를 노출하지 않음

#### 4. feature 간 projection 결합 정리

현재 `transaction/repository`가 `settlement/domain/calculation`의 `SettlementPaymentRow`, `SettlementShareRow`에 의존한다.

구현 방안:

1. settlement 전용 입력 조회 repository를 추가한다.
   - 예: `settlement/repository/SettlementTransactionQueryRepository`
2. `findSettlementPaymentRows(...)`, `findSettlementShareRows(...)`를 해당 repository로 이동한다.
3. projection interface도 `settlement/repository/projection` 또는 `settlement/domain/calculation` 중 하나로 정리한다.
4. `TransactionPaymentRepository`, `TransactionShareRepository`에서는 settlement 전용 query를 제거한다.

추천 위치:

- `settlement/repository/SettlementTransactionQueryRepository.kt`
- `settlement/repository/projection/SettlementPaymentRow.kt`
- `settlement/repository/projection/SettlementShareRow.kt`

테스트:

- `SettlementCalculationService`가 새 query repository를 통해 payment/share row를 읽는다.
- transaction repository는 settlement package import가 없다.

#### 5. 공개 공유 mapper 테스트 강화

구현 방안:

1. `SettlementShareResponse.from(...)`에 내부 `SettlementParticipantBalanceResponse`, `SettlementTransferResponse`를 넣는다.
2. 내부 DTO에 `participantId`, `userId`, `profileImageUrl`, transfer `id`가 있어도 공개 DTO에는 값이 존재하지 않는지 검증한다.
3. reflection 테스트는 보조로 유지하거나 실제 mapper 테스트로 대체한다.

테스트:

- 공개 balance에는 displayName과 금액만 존재한다.
- 공개 transfer에는 sender/receiver 표시명과 금액/상태만 존재한다.
- 공개 response에는 `tripId`, `settlementId`, `confirmedByUserId`가 없다.

#### 6. Kotlin style 정리

구현 방안:

1. `SettlementTransfer.kt`의 `) : BaseEntity()` indent를 IntelliJ Kotlin style에 맞춘다.
2. 변경 후 `./gradlew test`로 컴파일과 테스트를 확인한다.

### 2차 구현 순서

1. `SettlementTransfer.kt` 포매팅을 정리한다.
2. 확정 응답을 저장 후 transfer row 재조회 방식으로 변경한다.
3. 확정 시 탈퇴 사용자 sender/receiver 자동 동의 정책을 적용한다.
4. 송금 목록 조회를 projection 기반으로 변경한다.
5. settlement 전용 transaction query repository로 projection 결합을 정리한다.
6. 공개 공유 mapper 테스트를 강화한다.
7. 정산 확정 동시성 예외 변환 테스트를 추가한다.
8. `./gradlew test`를 실행한다.

### 2차 완료 기준

- 정산 확정 응답의 transfer id가 실제 저장된 id로 반환된다.
- soft-deleted 참여자가 포함된 송금도 목록 조회에서 누락되지 않는다.
- 송금 확인 API의 `ACTIVE` 참여자 제한 정책이 문서와 코드에 명확히 남는다.
- 탈퇴 사용자가 sender 또는 receiver인 송금은 자동 동의 처리된다.
- transaction feature가 settlement projection에 의존하지 않는다.
- optimistic lock 및 unique index 충돌이 비즈니스 예외로 변환되는 테스트가 있다.
- 공개 공유 응답 mapper가 실제 값 기준으로 내부 ID/개인정보를 제거함을 테스트한다.
- `./gradlew test`가 통과한다.
