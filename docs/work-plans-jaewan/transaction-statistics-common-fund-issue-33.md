# Work Plan: 공동경비 잔액과 거래 통계 API 구현

## 작업

GitHub Issue #33 `feat: 공동경비 잔액과 거래 통계 API 구현`을 진행한다.

구현 대상은 다음 두 API다.

- `GET /api/trips/{tripId}/common-fund-balance`
- `GET /api/trips/{tripId}/transaction-statistics`

현재 `TransactionController`와 `TransactionApiSpec`에는 두 API가 stub으로 존재하지만, 응답 타입이 `ApiResponse<Unit>`이고 실제 서비스/조회 로직은 없다. 이 작업에서는 거래 원장 데이터를 기준으로 공동경비 충전/사용/잔액과 거래 통계를 반환하도록 구현한다.

## 현재 브랜치 전략

- 작업 브랜치: `feature/issue-33-transaction-statistics`
- 기준 브랜치: `develop`
- PR 대상: `develop`
- 연결 이슈: #33

현재 열린 PR #62 `feature/issue-61-auth-user-stabilization`은 인증/회원 프로필 안정화 작업이며 `transaction` 패키지를 직접 수정하지 않는다. 따라서 #33은 PR #62 위에 stacked branch로 만들지 않고 `develop`에서 분리한다.

단, PR #62는 `src/test/resources/application-test.yml`, `src/main/resources/db/local/R__local_sample_data.sql`, migration 번호 `V10`을 수정하므로 #33에서 local sample data를 건드리면 머지 순서에 따른 충돌 가능성이 있다. `FUND_CHARGE`, `FUND_USE` 거래 유형은 현재 `develop`의 enum/DB check constraint에 없으므로, migration 번호 충돌을 피하기 위해 #33에서는 `V11__add_common_fund_transaction_types.sql`로 거래 유형 제약만 작게 확장한다.

## 배경

거래 모델은 `Transaction`, `TransactionPayment`, `TransactionShare`로 분리되어 있다.

- `Transaction`: 거래 유형, 원 금액, 통화, 기준 통화 환산 금액, 상태를 가진 원장
- `TransactionPayment`: 실제 결제자별 금액
- `TransactionShare`: 부담자별 금액
- `Post`: 거래 기반 기록의 카테고리와 발생 시각을 가진 화면 기록

Issue #33은 카테고리별/참여자별 통계와 기간 필터를 요구한다. 다만 현재 `transactions` 테이블과 `Transaction` 엔티티에는 `category`, `occurred_at` 필드가 없다. 해당 필드는 V3 migration에서 제거되었고, 현재는 거래와 연결된 `posts.category`, `posts.occurred_at`에 남아 있다.

따라서 이번 구현은 다음 정책을 기본값으로 둔다.

- 금액 집계 기준은 `transactions.base_amount`, `transaction_payments.base_amount`, `transaction_shares.base_share_amount`를 사용한다.
- 카테고리 집계 기준은 연결된 거래 기반 게시글 `posts.category`를 사용한다.
- 기간 필터 기준은 연결 게시글이 있으면 `posts.occurred_at`, 없으면 `transactions.created_at`을 사용한다.
- 연결 게시글이 없거나 카테고리가 비어 있으면 `UNCATEGORIZED`로 묶는다.

거래 자체에 `category`, `occurredAt`을 복원하는 정책이 별도로 확정되면 이 작업은 migration/엔티티 확장을 포함하도록 재계획한다.

## 범위

- 공동경비 잔액 응답 DTO를 추가한다.
- 거래 통계 응답 DTO를 추가한다.
- `TransactionApiSpec`와 `TransactionController`의 두 stub API 반환 타입을 실제 DTO로 교체한다.
- `TransactionService`에 공동경비 잔액과 거래 통계 읽기 메서드를 추가한다.
- 통계 전용 query repository를 추가한다.
- `FUND_CHARGE`, `FUND_USE`, `EXPENSE` 유형별 집계 규칙을 구현한다.
- `from`, `to`, `groupBy` 요청 파라미터를 검증하고 처리한다.
- `TransactionStatus.ACTIVE` 거래와 삭제되지 않은 데이터만 집계한다.
- 활성 사용자와 여행 접근 권한을 기존 `TransactionService` 읽기 API와 같은 수준으로 검증한다.
- `TransactionType`과 DB check constraint에 `FUND_CHARGE`, `FUND_USE`를 추가한다.
- 공동경비/통계 테스트를 추가한다.

## 제외 범위

- 차트 라이브러리에 맞춘 시각화 전용 포맷 최적화
- 외부 환율 API 재계산
- 기존 거래 환율 스냅샷 재계산
- 정산 확정 송금 계산
- 거래 엔티티에 category/occurredAt 필드 재추가
- local sample data 대규모 개편

## 정책

### 공동경비 잔액

대상 데이터:

- `transactions.deleted_at is null`
- `transactions.status = ACTIVE`
- `transactions.trip_id = :tripId`

집계 규칙:

- `FUND_CHARGE`: 공동경비 충전 합계에 더한다.
- `FUND_USE`: 공동경비 사용 합계에 더한다.
- `EXPENSE`: 공동경비 잔액 계산에는 포함하지 않는다.
- `balanceBaseAmount = chargedBaseAmount - usedBaseAmount`

응답 초안:

```kotlin
data class CommonFundBalanceResponse(
    val tripId: Long,
    val baseCurrency: String,
    val chargedBaseAmount: BigDecimal,
    val usedBaseAmount: BigDecimal,
    val balanceBaseAmount: BigDecimal,
)
```

`baseCurrency`는 집계 대상 거래의 `base_currency`가 있으면 그 값을 사용하고, 거래가 없으면 여행의 `defaultCurrency`를 사용한다.

### 거래 통계

지원 파라미터:

- `from`: `yyyy-MM-dd`, optional
- `to`: `yyyy-MM-dd`, optional
- `groupBy`: `category`, `participant`, `type`, optional

기본값:

- `groupBy`가 없으면 `type`으로 처리한다.
- `from`, `to`가 모두 없으면 전체 기간을 조회한다.
- `to`는 해당 날짜의 끝까지 포함한다.

검증:

- `from > to`이면 `CommonErrorCode.INVALID_INPUT`
- `groupBy`가 지원 값이 아니면 `CommonErrorCode.INVALID_INPUT`
- 날짜 파싱 실패 시 `CommonErrorCode.INVALID_INPUT`

기간 기준:

- `coalesce(posts.occurred_at, transactions.created_at)`

카테고리 기준:

- 연결된 `Post`가 있으면 `posts.category`
- `null` 또는 blank는 `UNCATEGORIZED`

참여자 기준:

- 참여자별 통계는 `transaction_shares` 기준으로 부담 금액을 집계한다.
- 앱에서 “누가 얼마를 썼는지”가 아니라 “누가 얼마를 부담했는지”를 보여주는 통계로 정의한다.
- 결제자 기준 지출 통계가 필요하면 추후 `participantBasis=payment|share` 같은 별도 파라미터를 추가한다.

거래 유형 기준:

- `transactions.transaction_type`별 `base_amount` 합계를 반환한다.

응답 초안:

```kotlin
data class TransactionStatisticsResponse(
    val tripId: Long,
    val groupBy: String,
    val from: LocalDate?,
    val to: LocalDate?,
    val totalBaseAmount: BigDecimal,
    val items: List<TransactionStatisticsItemResponse>,
)

data class TransactionStatisticsItemResponse(
    val key: String,
    val label: String,
    val transactionCount: Long,
    val totalBaseAmount: BigDecimal,
)
```

참여자 group item은 `key = participantId.toString()`, `label = TripParticipantDisplay.displayName(participant)` 형태로 반환한다.

## 설계

### 1. Query Repository

`TransactionStatisticsQueryRepository`를 추가한다.

후보 패키지:

- `src/main/kotlin/com/togethertrip/main/transaction/repository/TransactionStatisticsQueryRepository.kt`

역할:

- 공동경비 잔액 row 조회
- type 통계 row 조회
- category 통계 row 조회
- participant share 통계 row 조회

복잡한 조인과 `coalesce(posts.occurred_at, transactions.created_at)` 처리가 필요하므로 JPQL보다 native query가 적합하다.

### 2. Projection

query repository 내부 DTO 또는 별도 projection 패키지를 둔다.

후보:

- `CommonFundBalanceRow`
- `TransactionStatisticsRow`
- `ParticipantStatisticsRow`

응답 DTO와 repository row를 분리해 DB 조회 모양이 API 계약으로 새지 않게 한다.

### 3. Service

`TransactionService`에 다음 메서드를 추가한다.

```kotlin
@Transactional(readOnly = true)
fun getCommonFundBalance(
    userId: Long,
    tripId: Long,
): CommonFundBalanceResponse

@Transactional(readOnly = true)
fun getTransactionStatistics(
    userId: Long,
    tripId: Long,
    from: String?,
    to: String?,
    groupBy: String?,
): TransactionStatisticsResponse
```

권한 확인은 기존 읽기 API와 동일하게 처리한다.

1. `getActiveUser(userId)`
2. `getAccessibleTrip(userId, tripId)`
3. 요청 파라미터 파싱/검증
4. query repository 호출
5. 응답 DTO 변환

### 4. Controller와 Swagger Spec

현재 stub:

- `ApiResponse<Unit>`

변경:

- `ApiResponse<CommonFundBalanceResponse>`
- `ApiResponse<TransactionStatisticsResponse>`

`@RequireActiveTripParticipant`는 유지한다.

### 5. 에러 처리

새로운 도메인 에러 코드를 만들기보다 입력 파라미터 오류는 `CommonErrorCode.INVALID_INPUT`을 사용한다.

권한/사용자/여행 오류는 기존 흐름을 따른다.

- `UserErrorCode.USER_NOT_FOUND`
- `UserErrorCode.INACTIVE_USER`
- `TripErrorCode.TRIP_NOT_FOUND`
- `TripErrorCode.TRIP_ACCESS_DENIED`

## 테스트 계획

### Service 단위 테스트

`TransactionServiceTest` 또는 별도 `TransactionStatisticsServiceTest`에 추가한다.

- 공동경비 충전만 있으면 잔액이 충전 합계와 같다.
- 공동경비 충전과 사용이 있으면 잔액은 차감된다.
- `EXPENSE`는 공동경비 잔액에서 제외된다.
- `VOIDED` 거래는 공동경비 잔액과 통계에서 제외된다.
- 삭제된 거래, payment, share는 제외된다.
- `groupBy=type`은 거래 유형별 합계를 반환한다.
- `groupBy=category`는 연결 게시글 category별 합계를 반환한다.
- category가 없으면 `UNCATEGORIZED`로 묶는다.
- `groupBy=participant`는 share 기준 참여자별 부담 합계를 반환한다.
- `from`, `to` 기간 필터가 적용된다.
- 잘못된 날짜, 잘못된 `groupBy`, `from > to`는 `INVALID_INPUT`으로 실패한다.

### Repository 테스트

native query를 사용할 경우 통합 성격의 repository 테스트를 추가한다.

- 실제 H2/PostgreSQL 호환 쿼리 실행 여부
- `coalesce(posts.occurred_at, transactions.created_at)` 기간 필터
- `posts.deleted_at is null` 조건
- `transactions.status = ACTIVE` 조건

### 전체 검증

기본 검증:

```bash
./gradlew test
```

추가로 변경 후 다음을 확인한다.

```bash
git diff --check
```

## 구현 순서

1. 응답 DTO를 추가한다.
2. `TransactionStatisticsQueryRepository`와 projection row를 추가한다.
3. `TransactionService`에 공동경비 잔액 조회를 구현한다.
4. `TransactionService`에 통계 조회 파라미터 파싱과 groupBy 분기를 구현한다.
5. `TransactionController`와 `TransactionApiSpec` stub 반환 타입을 교체한다.
6. 공동경비 잔액 테스트를 추가한다.
7. 통계 groupBy/기간/무효 거래 제외 테스트를 추가한다.
8. `./gradlew test`와 `git diff --check`를 실행한다.
9. 필요하면 구현 결과를 기준으로 이 계획 문서를 갱신한다.

## 주요 리스크와 대응

- 카테고리/기간 기준이 거래 엔티티가 아니라 `Post`에 있다.
  - 이번 작업에서는 연결 게시글 기준으로 정의한다.
  - 제품 정책이 다르면 migration 포함 작업으로 재계획한다.
- participant 통계가 결제자 기준인지 부담자 기준인지 해석이 갈릴 수 있다.
  - 이번 작업에서는 정산/비용 부담 관점에 맞춰 `TransactionShare` 기준으로 정의한다.
  - 결제자 기준이 필요하면 별도 파라미터를 추가한다.
- native query가 DB dialect에 민감할 수 있다.
  - 기존 settlement query repository처럼 `EntityManager.createNativeQuery` 패턴을 따르고, repository 테스트로 보강한다.
- PR #62와 테스트 설정/local sample data가 충돌할 수 있다.
  - #33에서는 해당 파일 수정을 피한다.
  - 필요해지면 PR #62 머지 이후 최신 `develop`으로 rebase한다.
