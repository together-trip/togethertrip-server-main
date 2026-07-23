# 정산 흐름 구조/로직 리뷰

## 리뷰 범위

- 브랜치: `feature/issue-34-settlement-flow`
- PR: #51
- 관련 이슈: #34, #35
- 기준 문서:
  - `docs/agents/repo-context.md`
  - `docs/agents/quality-gates.md`
  - `docs/agents/roles/code-reviewer.md`
  - `docs/agents/roles/security-reviewer.md`
  - `docs/agents/roles/architect.md`
  - `docs/agents/roles/verify-agent.md`

## TogetherTrip 에이전트 판단

### Architect

- feature-based MVC 구조는 대체로 지켜졌다.
- Controller는 얇고, Service가 권한/흐름 조합을 담당한다.
- 계산 모델을 `settlement/domain/calculation`, snapshot 모델을 `settlement/domain/snapshot`으로 분리한 판단은 적절하다.
- 송금 확인 조건부 update와 no-op 판정은 `SettlementTransferConfirmationProcessor`로 분리해 `SettlementTransferService`가 세부 상태 전이 SQL 정책을 직접 알지 않도록 정리했다.
- 공유 토큰 조건부 발급은 `SettlementShareTokenIssuer`로 분리해 `SettlementService`가 idempotent 발급 세부사항을 직접 알지 않도록 정리했다.
- 다만 정산용 query가 `EntityManager` 수동 native mapping으로 들어가면서 Repository 컨벤션과 타입 안정성이 약해졌다.

### Code Reviewer

#### Resolved: 송금자/수금자 동시 확인 시 확인 상태 유실 가능

- 위치:
  - `src/main/kotlin/com/togethertrip/main/settlement/repository/SettlementTransferRepository.kt`
  - `src/main/kotlin/com/togethertrip/main/settlement/service/support/SettlementTransferConfirmationProcessor.kt`
- 기존 문제:
  - entity를 읽은 뒤 JPA dirty checking으로 상태를 저장해 sender/receiver 동시 확인 시 마지막 커밋이 한쪽 확인값을 덮을 수 있었다.
- 반영:
  - sender 확인은 `confirmAsSenderIfNeeded(...)` 조건부 update로 변경했다.
  - receiver 확인은 `confirmAsReceiverIfNeeded(...)` 조건부 update로 변경했다.
  - update SQL은 현재 DB row의 상대 역할 confirmed column을 기준으로 `COMPLETED` 여부를 계산한다.
  - 같은 역할 중복 확인은 update count `0` 이후 row 재조회로 no-op 응답을 반환한다.
  - 참여자 권한은 update where 조건과 service row 재조회 검증으로 유지한다.
- 남은 검증 공백:
  - 현재 프로젝트 테스트 패턴상 mock 기반 단위 테스트만 보강했다.
  - 실제 DB 동시 update 재현 테스트는 없다.

#### Resolved: 공유 토큰 동시 생성 시 응답 토큰이 무효가 될 수 있음

- 위치:
  - `src/main/kotlin/com/togethertrip/main/settlement/service/support/SettlementShareTokenIssuer.kt`
  - `src/main/kotlin/com/togethertrip/main/settlement/repository/SettlementRepository.kt`
- 기존 문제:
  - `shareToken == null`이면 entity 필드에 토큰을 대입하고 반환해 동시 요청 시 먼저 응답한 토큰이 나중 요청에 의해 무효화될 수 있었다.
- 반영:
  - `updateShareTokenIfAbsent(...)` 조건부 update를 추가했다.
  - update count가 `1`이면 생성한 토큰을 반환한다.
  - update count가 `0`이면 다른 요청이 먼저 저장한 것으로 보고 settlement를 재조회해 최종 토큰을 반환한다.
  - 조건부 update 성공/실패 재조회 단위 테스트를 추가했다.
- 남은 검증 공백:
  - 실제 DB 동시 요청 테스트는 없다.

#### Resolved: 취소/삭제된 정산의 transfer 확인 방어가 약함

- 위치:
  - `src/main/kotlin/com/togethertrip/main/settlement/service/SettlementTransferService.kt`
- 기존 문제:
  - transfer 존재 여부와 trip mismatch만 검증하고 settlement status를 직접 확인하지 않았다.
- 반영:
  - 송금 확인 전 `validateTransferInConfirmedTrip(...)`에서 `SettlementStatus.CONFIRMED`를 검증한다.
  - 조건부 update 쿼리에도 `settlement.status = 'CONFIRMED'`, `settlement.deleted_at is null` 조건을 포함했다.

#### Medium: 정산용 native query 수동 mapping은 유지보수 위험이 큼

- 위치:
  - `src/main/kotlin/com/togethertrip/main/settlement/repository/SettlementTransactionQueryRepository.kt`
  - `src/main/kotlin/com/togethertrip/main/transaction/repository/TransactionPaymentRepository.kt`
  - `src/main/kotlin/com/togethertrip/main/transaction/repository/TransactionShareRepository.kt`
- 문제:
  - 정산 계산은 `EntityManager.createNativeQuery(...)`와 `Array<*>` 캐스팅으로 구현되어 있다.
  - 반면 `TransactionPaymentRepository.findSettlementPayments(...)`, `TransactionShareRepository.findSettlementShares(...)`는 같은 목적처럼 보이는 미사용 메서드로 남아 있다.
- 영향:
  - 컬럼 순서 변경이나 DB 드라이버 반환 타입 변화에 취약하다.
  - 정산 계산 입력 경로가 두 개처럼 보여 이후 수정자가 잘못된 메서드를 고칠 가능성이 있다.
- 추천:
  - 미사용 JPQL 메서드를 제거하거나 정산 계산 조회를 Spring Data projection 기반 repository로 통일한다.
  - soft-deleted participant 포함 정책 때문에 native query가 필요하다면, 그 이유를 repository 메서드명과 주석에 명확히 남긴다.

### Security Reviewer

- 인증 정산 API는 Controller의 `@RequireActiveTripParticipant`를 통해 접근 제어가 적용되어 있다.
- 방장 전용 API는 Controller의 `@RequireTripOwner`를 통해 접근 제어가 적용되어 있다.
- Service 내부에서 실제 `Trip`, `User`, `TripParticipant` entity가 필요한 흐름은 `SettlementAccessResolver`로 조회 책임을 분리했다.
- 공개 공유 응답은 내부 participant id/user id/profile image를 제거해 정보 노출을 줄였다.
- 탈퇴 사용자 표시명과 profile/user id 마스킹은 적용되어 있다.
- 공유 토큰 생성은 조건부 update와 재조회 방식으로 idempotency를 보강했다.

### Verify Agent

- 실행 명령:

```bash
./gradlew test
```

- 결과: 성공
- 남은 검증 공백:
  - 실제 DB 기반 송금 확인 동시성 테스트 없음
  - 실제 DB 기반 공유 토큰 동시 생성 테스트 없음
  - native query가 실제 DB에서 soft-deleted participant를 포함하는지 확인하는 통합 테스트 없음

## 종합 판단

- 구조는 큰 방향에서 TogetherTrip 컨벤션에 맞다.
- 송금 확인과 공유 토큰의 주요 동시성 리스크는 조건부 update로 보강했다.
- native query와 미사용 repository 메서드는 병합 차단급은 아니지만, 지금 정리하지 않으면 정산 도메인 유지보수 비용이 커진다.
