# 정산 송금 확인 API 구현 계획

## 배경

- GitHub Issue: #35 `feat: 정산 송금 확인 API 구현`
- 현재 브랜치: `feature/issue-35-transfer-confirmation`
- 작성일: 2026-06-08

정산 확정 후 생성된 `SettlementTransfer` 목록을 조회하고, 송금자/수금자가 각각 확인하여 송금 상태를 전이하는 API를 구현한다. #34에서 정산 미리보기/확정 흐름과 일부 송금 확인 기반이 구현되었으므로, 이번 작업은 이슈 #35 범위에 맞춰 송금 확인 정책과 테스트를 정리한다.

## 현재 코드 상태

- `SettlementTransferController`는 다음 API를 가진다.
  - `GET /api/trips/{tripId}/settlement-transfers`
  - `PATCH /api/trips/{tripId}/settlement-transfers/{transferId}/sender-confirmation`
  - `PATCH /api/trips/{tripId}/settlement-transfers/{transferId}/receiver-confirmation`
- `SettlementTransferService`는 송금 목록 조회와 sender/receiver 확인 메서드를 가진다.
- `SettlementTransfer`는 sender/receiver 확인 시각과 `PENDING`, `SENDER_CONFIRMED`, `RECEIVER_CONFIRMED`, `COMPLETED` 상태 전이를 가진다.
- #34 후속 구현에서 soft-deleted 참여자 조회를 위해 송금 목록은 native projection 기반으로 정리되었다.
- 탈퇴 사용자는 정산 확정 시 sender/receiver 역할과 무관하게 자동 동의 처리한다.

## 확정 정책

1. 방장 대리 확인은 허용하지 않는다.
   - 송금자 확인은 sender 본인만 가능하다.
   - 수금자 확인은 receiver 본인만 가능하다.
   - 방장은 송금 목록 조회는 가능하지만 대리 확인은 할 수 없다.
2. 모든 transfer가 `COMPLETED` 되어도 별도 `Trip`/`Settlement` 상태는 변경하지 않는다.
   - #34에서 정산 확정 시 `Trip.settlementStatus = SETTLED`로 변경한다.
   - 송금 완료 여부는 `SettlementTransfer` 목록의 aggregate 상태로 판단한다.
3. 중복 확인은 idempotent no-op으로 처리한다.
   - 이미 sender 확인이 끝난 송금자 확인 재요청은 성공 응답을 반환하되 `senderConfirmedAt`을 덮어쓰지 않는다.
   - 이미 receiver 확인이 끝난 수금자 확인 재요청도 `receiverConfirmedAt`을 덮어쓰지 않는다.
   - 이미 `COMPLETED`인 송금 재확인도 완료 시각을 덮어쓰지 않는다.
4. 탈퇴 사용자는 직접 확인할 수 없으므로 자동 동의한다.
   - sender가 탈퇴 사용자면 `senderConfirmedAt`을 자동 기록한다.
   - receiver가 탈퇴 사용자면 `receiverConfirmedAt`을 자동 기록한다.
   - 양쪽 모두 탈퇴 사용자면 즉시 `COMPLETED`로 둔다.
5. 제거/퇴장 사용자 대리 확인 정책은 #35 범위 밖이다.
   - 현재 인증 API는 `ACTIVE` 참여자만 호출 가능하다.
   - 제거/퇴장 사용자가 확인해야 하는 미응답 송금은 후속 운영/분쟁 정책으로 분리한다.

## TogetherTrip 에이전트 판단

### Planner

- 이슈 #35는 MVP 범위의 정산 후속 기능이다.
- 핵심은 송금 상태 전이, 권한 차단, 조회 필터, idempotency 검증이다.
- 실제 계좌 이체, 분쟁 처리, 알림은 제외한다.

### Security Reviewer

- sender/receiver 본인이 아닌 사용자의 확인 요청은 차단한다.
- 방장이라도 대리 확인은 허용하지 않는다.
- 송금 목록 조회는 활성 여행 참여자에게 허용하되, 공개 공유 API와 달리 인증 API이므로 내부 transfer id를 반환할 수 있다.
- 탈퇴 사용자는 개인정보를 마스킹하고 자동 동의 사유를 기록한다.

### Architect

- Controller는 인증 주체와 요청 파라미터 전달, `ApiResponse` 포장만 담당한다.
- Service는 권한 확인과 상태 전이를 조합한다.
- `SettlementTransfer` 도메인은 상태 전이와 idempotency를 보장한다.
- soft-deleted 참여자가 송금 sender/receiver인 경우에도 조회가 누락되지 않도록 projection 기반 조회를 유지한다.
- 전체 송금 완료 판단은 별도 상태 변경 없이 조회/응답에서 aggregate로 판단한다.

### TDD Guide

- 도메인 상태 전이 테스트를 먼저 둔다.
- Service 테스트로 권한, idempotency, projection 조회, 탈퇴 사용자 자동 동의를 검증한다.
- Controller 테스트는 기존 패턴이 없으면 service 단위 테스트를 우선한다.

### Verify Agent

- `./gradlew test`를 실행한다.
- 문서의 정책과 실제 코드가 일치하는지 확인한다.
- 이슈 #35 완료 기준과 PR 설명이 충돌하지 않는지 확인한다.

## 구현 범위

### 1. 송금 목록 조회

- `GET /api/trips/{tripId}/settlement-transfers`
- 활성 여행 참여자만 조회 가능하다.
- 필터:
  - `settlementId`
  - `participantId`
  - `status`
  - `direction`
- `direction`은 현재 로그인 사용자의 active participant 기준으로 적용한다.
  - `SENT`, `SEND`, `SENDER`: 내가 sender인 송금
  - `RECEIVED`, `RECEIVE`, `RECEIVER`: 내가 receiver인 송금
- soft-deleted sender/receiver도 목록에서 누락되지 않아야 한다.
- 탈퇴 사용자 display name은 `탈퇴한 사용자`로 마스킹한다.

### 2. 송금자 확인

- `PATCH /api/trips/{tripId}/settlement-transfers/{transferId}/sender-confirmation`
- 현재 로그인 사용자가 해당 transfer의 sender active participant여야 한다.
- 방장 대리 확인은 허용하지 않는다.
- 이미 sender 확인이 끝난 경우 no-op으로 응답한다.
- receiver 확인이 이미 끝난 상태라면 `COMPLETED`로 전이한다.

### 3. 수금자 확인

- `PATCH /api/trips/{tripId}/settlement-transfers/{transferId}/receiver-confirmation`
- 현재 로그인 사용자가 해당 transfer의 receiver active participant여야 한다.
- 방장 대리 확인은 허용하지 않는다.
- 이미 receiver 확인이 끝난 경우 no-op으로 응답한다.
- sender 확인이 이미 끝난 상태라면 `COMPLETED`로 전이한다.

### 4. 상태 전이 규칙

- `PENDING` + sender 확인 -> `SENDER_CONFIRMED`
- `PENDING` + receiver 확인 -> `RECEIVER_CONFIRMED`
- `SENDER_CONFIRMED` + receiver 확인 -> `COMPLETED`
- `RECEIVER_CONFIRMED` + sender 확인 -> `COMPLETED`
- `COMPLETED` + 재확인 -> no-op
- 동일 역할 재확인 -> no-op
- no-op은 기존 확인 시각과 완료 시각을 덮어쓰지 않는다.

### 5. 탈퇴 사용자 자동 동의

- 정산 확정 시 transfer 생성 과정에서 적용한다.
- sender가 탈퇴 사용자면 `autoConfirmSender(...)`를 호출한다.
- receiver가 탈퇴 사용자면 `autoConfirmReceiver(...)`를 호출한다.
- 자동 동의 시:
  - `autoConfirmed = true`
  - `autoConfirmReason = WITHDRAWN_USER_AUTO_CONFIRMED`
  - 역할별 confirmedAt 기록
  - 양쪽 확인이 완료되면 `COMPLETED`

### 6. 전체 완료 판단

- 이슈 #35에서는 모든 transfer 완료 시 `Trip` 또는 `Settlement` 상태를 추가 변경하지 않는다.
- 전체 완료 여부가 필요하면 다음 기준으로 계산한다.
  - 해당 settlement의 모든 transfer가 `COMPLETED`
- 별도 상태 컬럼 또는 enum 추가는 후속 이슈로 분리한다.

## 패키지/클래스 계획

- `settlement/domain/SettlementTransfer.kt`
  - 상태 전이와 idempotency 책임 유지
  - 자동 동의 메서드 유지
- `settlement/service/SettlementTransferService.kt`
  - 목록 조회, sender 확인, receiver 확인
  - 본인 권한 검증
- `settlement/repository/SettlementTransferRepository.kt`
  - projection 기반 목록/단건 조회
- `settlement/domain/SettlementTransferRow.kt`
  - soft-deleted 참여자 포함 조회 projection
- `settlement/dto/response/SettlementTransferResponse.kt`
  - 인증 API 응답 DTO

## 테스트 계획

### SettlementTransferTest

- sender 확인 시 `SENDER_CONFIRMED`로 전이
- receiver 확인 시 `RECEIVER_CONFIRMED`로 전이
- 양쪽 확인 완료 시 `COMPLETED`
- sender 재확인은 `senderConfirmedAt`을 덮어쓰지 않음
- receiver 재확인은 `receiverConfirmedAt`을 덮어쓰지 않음
- completed 재확인은 `completedAt`을 덮어쓰지 않음
- 탈퇴 사용자 자동 동의 시 `autoConfirmed`, `autoConfirmReason` 기록

### SettlementTransferServiceTest

- sender만 송금자 확인 가능
- receiver만 수금자 확인 가능
- 방장은 sender/receiver가 아니면 대리 확인 불가
- transfer가 trip에 속하지 않으면 실패
- `direction=SENT`는 현재 사용자 sender 송금만 반환
- `direction=RECEIVED`는 현재 사용자 receiver 송금만 반환
- invalid status/direction은 비즈니스 예외
- 중복 확인은 no-op 응답
- soft-deleted sender/receiver가 포함된 송금도 목록 조회에 포함

### SettlementServiceTest

- 정산 확정 시 탈퇴 sender는 자동 sender 확인
- 정산 확정 시 탈퇴 receiver는 자동 receiver 확인
- 양쪽 탈퇴 사용자인 transfer는 즉시 `COMPLETED`
- 자동 동의된 transfer도 확정 응답에 실제 transfer id와 상태가 반환됨

## 구현 순서

1. 현재 #34 후속 구현 상태를 점검한다.
2. `SettlementTransfer` 상태 전이 테스트를 보강한다.
3. `SettlementTransferServiceTest`를 추가한다.
4. 송금 목록 projection 필터와 direction 필터를 검증한다.
5. sender/receiver 본인 확인 권한 테스트를 추가한다.
6. 탈퇴 사용자 자동 동의 테스트를 보강한다.
7. 필요 시 service/repository 구현을 테스트 기준에 맞게 수정한다.
8. `./gradlew test`를 실행한다.

## 완료 기준

- 송금 목록 API가 필터와 direction을 적용해 송금 목록을 반환한다.
- sender 본인만 송금자 확인 가능하다.
- receiver 본인만 수금자 확인 가능하다.
- 방장 대리 확인은 차단된다.
- 중복 확인은 idempotent no-op으로 처리된다.
- 모든 transfer 완료 시 별도 여행/정산 상태 변경은 없다.
- 탈퇴 사용자는 자동 동의 처리된다.
- `./gradlew test`가 통과한다.
