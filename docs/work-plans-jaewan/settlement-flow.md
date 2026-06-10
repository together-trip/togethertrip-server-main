# 정산 미리보기와 확정 흐름 구현 계획

## 배경

- GitHub Issue: #34 `feat: 정산 미리보기와 확정 흐름 구현`
- 현재 브랜치: `feature/issue-34-settlement-flow`
- 정산 API controller/spec, service, repository, domain 골격은 존재하지만 실제 계산과 저장 로직은 비어 있다.
- 거래 원장에는 정산에 사용할 `baseAmount`, `baseShareAmount`, `baseCurrency` 스냅샷이 저장된다.
- 정산 기준 통화는 거래 환율 정책과 동일하게 `KRW`로 고정한다.
- Issue #34의 참고 문서인 `docs/modeling.md`는 현재 리포에 존재하지 않는다. 계획은 현재 엔티티와 마이그레이션 기준으로 작성한다.

## TogetherTrip 에이전트 판단

### Planner

- MVP 범위에 해당한다.
- 영향 API는 정산 미리보기, 잔액 요약, 정산 확정, 정산 결과 조회, 공유 토큰 생성/조회, 송금 목록 조회/확인이다.
- 금액 계산과 확정 저장이 핵심 위험이므로 작은 단위의 계산 엔진 테스트를 먼저 둔다.

### Architect

- feature-based MVC 구조를 유지한다.
- `settlement/domain`에 계산 입력/결과 값 객체와 계산 엔진을 두고, `settlement/service`가 권한 확인, 거래 조회, 저장 흐름을 조합한다.
- Repository는 Entity 조회/저장 책임만 갖는다.
- Controller는 인증 주체 전달, request/response 매핑, `ApiResponse` 포장만 담당한다.
- 정산 계산은 외부 네트워크 호출 없이 DB 스냅샷만 사용한다.

### TDD Guide

- 계산 엔진은 Spring 없이 순수 단위 테스트로 시작한다.
- 서비스 테스트는 권한, 중복 확정 차단, 스냅샷 저장, 송금 목록 생성, 공유 토큰 생성 정책을 검증한다.
- 통합 테스트가 없으면 최소한 repository 쿼리와 서비스 흐름은 Mockito 기반 기존 패턴으로 검증한다.

### Security Reviewer

- 여행 접근 권한이 없는 사용자는 모든 인증 정산 API에 접근할 수 없어야 한다.
- 공유 토큰 조회는 인증 없이 가능하므로 토큰 예측 가능성, settlement 존재 여부 노출, 삭제/취소 정산 노출을 막아야 한다.
- 금액은 `BigDecimal`과 기준 통화 스냅샷만 사용한다.
- 정산 확정 이후 거래 생성/수정/삭제 제한과 `Trip.settlementStatus` 변경이 일관되어야 한다.
- 정산 확정과 공유 토큰 생성은 방장 권한이 필요하다.

## 현재 코드 상태

- `SettlementController`, `SettlementTransferController`, `SettlementShareController`는 route만 있고 구현이 비어 있다.
- `SettlementService`, `SettlementTransferService`, `SettlementShareService`는 repository 의존성만 있다.
- `Settlement`, `SettlementTransfer`, `TripParticipantBalanceSummary` 엔티티와 기본 테이블/인덱스는 존재한다.
- `settlements`는 `status = CONFIRMED AND deleted_at IS NULL` 조건의 여행별 부분 유니크 인덱스를 가진다.
- `settlement_transfers`는 같은 정산 안에서 `sender, receiver` 쌍 중복을 막는 인덱스를 가진다.
- settlement 전용 exception/dto 패키지는 아직 없다.
- settlement 테스트 패키지는 아직 없다.
- 기존 AOP는 `@RequireActiveTripParticipant`만 있으며 방장 전용 AOP는 아직 없다.
- 구현 진행 중 `@RequireTripOwner` 방장 전용 AOP를 추가했다.

## 구현 범위

1. 정산 계산 엔진 구현
   - 활성 거래의 결제 금액 합계와 부담 금액 합계를 참여자별로 집계한다.
   - `netBaseAmount = paidBaseAmount - shareBaseAmount`로 순잔액을 계산한다.
   - 양수 참여자는 받을 사람, 음수 참여자는 보낼 사람으로 분류한다.
   - 투 포인터 방식으로 최소 송금 목록을 만든다.
   - 0원 또는 반올림 오차는 `0.01 KRW` 이하 잔여 처리 정책을 명시한다.

2. 정산 입력 조회 구현
   - `TransactionRepository` 또는 전용 조회 repository에 정산용 활성 거래 조회를 추가한다.
   - 입력은 `TransactionStatus.ACTIVE` 거래와 삭제되지 않은 payment/share만 포함한다.
   - 정산 계산은 원화 기준 필드만 사용한다.

3. 응답 DTO 추가
   - `SettlementPreviewResponse`
   - `BalanceSummaryResponse`
   - `SettlementResponse`
   - `SettlementTransferResponse`
   - `SettlementShareTokenResponse`
   - 필요하면 내부 계산 결과용 DTO와 API 응답 DTO를 분리한다.

4. 정산 미리보기 구현
   - `POST /api/trips/{tripId}/settlement-preview`
   - 접근 가능한 활성 여행 참여자만 호출 가능하게 한다.
   - 저장하지 않고 현재 거래 버전 기준 계산 결과를 반환한다.
   - `tripExpenseVersion = trip.expenseVersion`을 응답에 포함한다.

5. 참여자별 잔액 요약 조회 구현
   - `GET /api/trips/{tripId}/balance-summary`
   - 기본 방향은 현재 거래 원장을 즉시 재계산해 반환한다.
   - `TripParticipantBalanceSummary`는 Issue #34 범위에서 확정 시 스냅샷 저장 또는 후속 증분 집계 중 하나로 결정한다.
   - MVP에서는 stale projection 위험을 피하려고 실시간 재계산을 우선한다.

6. 정산 확정 구현
   - `POST /api/trips/{tripId}/settlements`
   - 이미 `CONFIRMED` 정산이 있으면 실패한다.
   - 여행의 `settlementStatus`가 `NOT_STARTED`가 아니면 실패한다.
   - 현재 거래 버전으로 계산한 결과를 `Settlement(CONFIRMED)`에 저장한다.
   - `SettlementTransfer` 목록을 생성한다.
   - `Trip.settlementStatus = SETTLED`, `Trip.settledAt = now`로 변경한다.
   - DB 유니크 인덱스 충돌은 비즈니스 예외로 변환한다.

7. 정산 결과 조회 구현
   - `GET /api/trips/{tripId}/settlements/{settlementId}`
   - 요청자가 여행 접근 권한을 가져야 한다.
   - settlement가 trip에 속하지 않으면 실패한다.
   - 저장된 snapshot payload와 transfer 목록을 기준으로 반환한다.

8. 공유 토큰 생성 및 조회 구현
   - `POST /api/trips/{tripId}/settlements/{settlementId}/share-tokens`
   - 확정 정산에 대해서만 생성한다.
   - 토큰은 충분히 긴 난수 URL-safe 문자열로 만든다.
   - 이미 토큰이 있으면 기존 토큰을 반환할지 새로 발급할지 정책을 정한다. MVP 추천은 idempotent하게 기존 토큰 반환이다.
   - `GET /api/settlement-shares?token=...`은 인증 없이 확정 정산 결과를 조회한다.
   - 공유 응답에는 사용자 민감 정보와 내부 ID 노출을 최소화한다.

9. 송금 목록 조회/확인 구현
   - Issue #34 완료 기준에는 직접 포함되지 않지만 existing controller가 있으므로 최소 조회 API는 같이 연결한다.
   - 송금자 확인은 sender 참여자 본인만 가능하다.
   - 수금자 확인은 receiver 참여자 본인만 가능하다.
   - 양쪽 확인이 완료되면 `COMPLETED`로 전환한다.

## 제외 범위

- 실제 송금 API 연동
- 정산 취소/재정산
- 공개 공유 페이지 UI
- 알림 발송
- 정산 증분 projection의 이벤트 기반 실시간 갱신
- 여행/거래 모델의 신규 소비일 정책 변경

## 도메인 정책

- 정산 계산 기준 통화는 항상 `KRW`다.
- 정산 계산은 거래 원장의 저장된 기준 금액 스냅샷을 사용한다.
- 거래 등록/수정 시점에 이미 결제자와 부담자 스냅샷이 확정되므로, 정산 엔진은 정산 시점에 인원수로 다시 나누지 않는다.
- 기본 N분의 1은 거래 작성 UI/요청 생성 단계의 편의 기능으로 보고, 서버 정산 계산은 저장된 `TransactionShare`만 신뢰한다.
- `shareRatio`는 표시/감사용 보조 정보이며, 정산 금액 계산의 기준은 `baseShareAmount`다.
- `shareAmount`와 `shareRatio`가 함께 있더라도 정산 엔진은 `shareAmount` 스냅샷을 우선한다.
- `totalExpenseAmount`는 활성 거래의 `baseAmount` 합계로 둔다.
- `totalShareAmount`는 활성 share의 `baseShareAmount` 합계로 둔다.
- 정상 데이터에서는 `totalExpenseAmount == totalShareAmount`여야 한다.
- 불일치가 발생하면 정산 확정은 실패시키고, 미리보기는 오류 또는 진단 필드를 반환한다. MVP 추천은 실패다.
- 확정 정산은 여행당 하나만 허용한다.
- 확정 시 `tripExpenseVersion`을 저장해 이후 거래 변경 여부를 판별할 수 있게 한다.
- 정산 확정 후 거래 쓰기 제한은 기존 transaction 정책을 따른다.
- 확정은 방장만 수행할 수 있다.
- 정산 미리보기, 잔액 요약, 확정 결과 조회, 송금 목록 조회는 활성 참여자에게 허용한다.
- 공유 토큰 생성은 방장만 수행할 수 있다.
- 공유 토큰 조회는 인증 없이 가능하지만, 확정 정산의 공유용 응답만 반환한다.

## 퇴장/제거/탈퇴 사용자 정책

- 중간에 나간 사용자(`LEFT`)나 제거된 사용자(`REMOVED`)도 과거 거래의 결제자/부담자로 저장되어 있으면 정산 계산에 포함한다.
- 새 거래 등록/수정에는 기존 정책처럼 `ACTIVE` 참여자만 결제자/부담자로 지정할 수 있다.
- 정산 결과 응답에는 퇴장/제거 상태를 표시할 수 있어야 한다.
- 탈퇴한 사용자는 정산 금액 계산에는 계속 포함하되, 응답에서는 탈퇴 사용자 표시 정책을 적용하고 개인정보를 노출하지 않는다.
- 방장이 참여자를 내보내더라도 이미 발생한 채권/채무는 제거하지 않는다.
- 미정산 채무가 있는 사용자의 여행 이탈을 차단할지는 #34 범위 밖으로 둔다.

## AOP 권한 적용 판단

- 기존 `@RequireActiveTripParticipant`는 활성 참여자 여부만 검증한다.
- 정산 미리보기, 잔액 요약, 정산 결과 조회, 송금 목록 조회에는 기존 `@RequireActiveTripParticipant`를 적용할 수 있다.
- 정산 확정과 공유 토큰 생성에는 방장 권한이 필요하므로 신규 `@RequireTripOwner` 또는 `@RequireTripLeader` AOP 추가를 검토한다.
- 현재 코드에서 방장 판정은 `Trip.ownerUser.id == userId` 중심이다. `TripParticipantRole.LEADER`와의 관계를 정리해야 한다.
- MVP 추천은 `Trip.ownerUser.id == authUser.userId` 기준의 `@RequireTripOwner`를 추가하고, 나중에 방장 위임 정책이 구현되면 AOP 내부 판정을 확장하는 것이다.
- 단, AOP는 Controller 진입 권한을 빠르게 차단하는 장치이고, Service에는 같은 권한 검증을 핵심 비즈니스 보호 로직으로 남긴다.
- 공유 토큰 조회는 인증 없는 공개 API라 AOP를 적용하지 않고 token 기반 조회 정책으로 보호한다.

## 패키지/클래스 계획

- `settlement/domain/SettlementCalculation.kt`
  - 초기 계획이었으나 루트 `domain`이 과도하게 커지지 않도록 하위 패키지로 분리한다.
- `settlement/domain/calculation/*`
  - 계산 입력/결과 값 객체와 순수 계산 엔진
- `settlement/domain/snapshot/*`
  - 확정 정산 snapshot payload 저장 모델
- `settlement/dto/response/*`
  - API 응답 DTO
- `settlement/exception/SettlementErrorCode.kt`
  - 정산 없음, 권한 없음, 중복 확정, 정산 상태 불일치, 금액 불일치, 토큰 없음
- `settlement/service/support/*`
  - 계산 입력 조회/응답 조립, 스냅샷 직렬화, 공유 토큰 생성, 정산 권한 확인
- `settlement/service/SettlementService.kt`
  - 미리보기, 잔액 요약, 확정, 결과 조회, 공유 토큰 생성
- `settlement/service/SettlementTransferService.kt`
  - 송금 목록 조회, 송금자/수금자 확인
- `settlement/service/SettlementShareService.kt`
  - 공유 토큰 조회
- `transaction/repository/TransactionRepository.kt`
  - 정산 계산용 활성 거래 조회 추가

## 단계별 작업 계획

1. settlement exception과 response DTO를 추가한다.
2. `SettlementCalculator` 순수 단위 테스트를 먼저 작성한다.
3. 2인 정산 계산을 구현한다.
4. 3인 이상 최소 송금 목록 생성을 구현한다.
5. 공동경비/다중 결제자/다중 부담자 케이스를 계산 테스트에 추가한다.
6. 정산용 거래 조회 repository 메서드를 추가한다.
7. `SettlementService.previewSettlement(...)`를 구현한다.
8. `SettlementController.previewSettlement(...)`를 `ApiResponse`로 연결한다.
9. 잔액 요약 조회를 구현한다.
10. 확정 정산 중복 차단과 `Settlement` 저장을 구현한다.
11. `SettlementTransfer` 저장을 구현한다.
12. `Trip.settlementStatus`, `Trip.settledAt` 변경을 구현한다.
13. 정산 결과 조회를 구현한다.
14. 공유 토큰 생성/조회 정책을 구현한다.
15. 송금 목록 조회와 송금자/수금자 확인을 구현한다.
16. Swagger spec 반환 타입과 설명을 실제 DTO에 맞게 수정한다.
17. `TripSettlementStatus` 주석의 `OPEN` 표현을 실제 enum 기준으로 정리한다.
18. 방장 전용 권한 AOP와 서비스 검증 helper를 추가한다.
19. `./gradlew test`를 실행한다.

## 테스트 계획

- `SettlementCalculatorTest`
  - 2인 정산: A가 10000원 결제, A/B가 5000원씩 부담하면 B -> A 5000원
  - 3인 정산: 받을 사람/보낼 사람 다수 케이스의 최소 송금 목록
  - 다중 결제자, 다중 부담자 집계
  - 순잔액 합계가 0인지 검증
  - 0원 송금은 생성하지 않음
  - 금액 불일치나 음수 입력 방어

- `SettlementServiceTest`
  - 접근 권한 없는 사용자는 미리보기/확정/조회 실패
  - 활성 참여자는 미리보기와 결과 조회 가능
  - 방장이 아니면 정산 확정 실패
  - 방장이 아니면 공유 토큰 생성 실패
  - 미리보기는 저장하지 않음
  - 확정은 `Settlement`와 `SettlementTransfer`를 저장함
  - 이미 확정된 정산이 있으면 중복 생성 실패
  - 확정 시 `tripExpenseVersion`을 저장함
  - 확정 시 여행 정산 상태를 `SETTLED`로 변경함
  - settlementId가 tripId와 맞지 않으면 조회 실패
  - 공유 토큰 생성은 확정 정산에만 허용
  - 공유 토큰 조회는 토큰이 없거나 취소/삭제 정산이면 실패
  - 퇴장/제거된 참여자가 과거 거래에 포함되어 있으면 정산 결과에 포함

- `SettlementTransferServiceTest`
  - sender만 송금자 확인 가능
  - receiver만 수금자 확인 가능
  - 양쪽 확인 완료 시 transfer가 완료 처리됨

- 전체 검증
  - `./gradlew test` 통과

## 보안/무결성 체크리스트

- 인증 API는 `@RequireActiveTripParticipant` 또는 서비스 권한 검증을 적용한다.
- 방장 전용 API는 신규 AOP 또는 서비스 검증으로 `Trip.ownerUser.id`를 확인한다.
- 공유 토큰 API는 인증이 없으므로 토큰 난수성과 응답 데이터 최소화를 검토한다.
- 정산 결과에는 전화번호, 인증 정보, 내부 사용자 상태 같은 개인정보를 포함하지 않는다.
- 정산 확정은 단일 트랜잭션에서 settlement, transfers, trip 상태를 함께 저장한다.
- DB 유니크 제약 충돌을 사용자 친화적인 정산 중복 오류로 변환한다.
- 금액 계산은 `Double`을 사용하지 않는다.
- 정산 계산에는 삭제/무효 거래, 삭제 payment/share를 포함하지 않는다.

## 확정한 MVP 정책

- 정산 엔진은 정산 시점에 다시 N분의 1을 계산하지 않고, 거래 저장 시 확정된 `TransactionShare` 스냅샷을 사용한다.
- 중도 퇴장/제거/탈퇴 사용자가 과거 거래의 결제자/부담자이면 정산에 포함한다.
- 새 거래의 결제자/부담자는 `ACTIVE` 참여자만 허용한다.
- 확정은 방장만 가능하다.
- 공유 토큰 생성은 방장만 가능하다.
- 공유 토큰 조회는 인증 없이 가능하지만 확정 정산의 공유용 데이터만 반환한다.
- 정산 취소/재정산은 #34에서 제외한다.

## 남은 질문

- `balance-summary`는 기존 `TripParticipantBalanceSummary` projection을 저장/조회해야 하는가, 아니면 MVP에서 실시간 재계산 응답으로 충분한가?
- 확정 시 여행 상태를 `IN_PROGRESS`를 거쳐 `SETTLED`로 둘지, 즉시 `SETTLED`로 둘지 결정이 필요하다. MVP 추천은 확정 완료 API이므로 즉시 `SETTLED`다.
- 공유 토큰 재발급 요청 시 기존 토큰 반환과 재발급 중 어떤 정책을 사용할지 결정이 필요하다. MVP 추천은 기존 토큰 반환이다.
- 송금자/수금자 확인 API까지 #34에 포함할지, 별도 이슈로 분리할지 결정이 필요하다.
- 방장 판정의 최종 기준을 `Trip.ownerUser`로 둘지 `TripParticipantRole.LEADER`로 통합할지 결정이 필요하다. MVP 추천은 현재 서비스 코드와 같은 `Trip.ownerUser` 기준이다.
