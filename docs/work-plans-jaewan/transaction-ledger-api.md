# Work Plan: 거래 원장 API 구현

## 작업

이슈 #32 `feat: 거래 원장 API 구현`을 진행한다.

구현 대상은 여행 환율표 자동 초기화/재조회/수동 수정, 여행 거래 등록/목록/상세/수정/무효 처리, 결제자/부담자 금액 무결성 검증, 거래 변경 이벤트 기록, `Trip.expenseVersion` 증가, 정산 상태에 따른 거래 쓰기 제한이다.

외화 거래는 기준 통화 환산 금액이 있어야 정산에 사용할 수 있다. 사용자가 소비 등록 때마다 환율을 선택하지 않도록, 여행 생성/국가 설정 시점에 외부 환율 API로 여행별 환율표(`TripExchangeRate`)를 만들고 거래 등록/수정 시 자동 적용한다.

## 확정된 제품 정책

- 환율 기준일은 `exchangeRateBaseDate` -> `startDate` -> 여행 생성일 순서로 결정한다.
- `TripExchangeRate.rate`는 `targetCurrency` 1 단위당 `baseCurrency` 금액을 의미한다.
- 거래 환산 공식은 `baseAmount = amount * rate`다.
- 여러 국가 여행이면 각 국가 통화를 모두 수집해 환율을 저장한다.
- 기본 통화도 `TripExchangeRate`에 `rate = 1.000000`으로 저장한다.
- 여행 생성/국가 설정 시 필요한 환율 조회에 실패하면 전체 요청을 실패시킨다.
- 여행 환율 재조회/갱신 기능은 이번 이슈에 포함한다.
- 거래 등록/수정 시점에는 외부 환율 API를 호출하지 않고 `TripExchangeRate`만 사용한다.
- 외부 환율 API provider는 아직 미정이다. `ExchangeRateClient` 인터페이스 중심으로 교체 가능하게 설계한다.
- 자동 API로 가져온 환율을 사용자가 여행 설정에서 수동 수정할 수 있다.
- 여행 환율이 재조회/수정되어도 이미 저장된 `Transaction.exchangeRate` 스냅샷은 재계산하지 않는다.

## 배경

`main` 서버에는 `transaction` 패키지의 Entity, Repository, Controller skeleton, 요청 DTO가 있으나 `TransactionService` 구현과 응답 DTO가 비어 있다.

`docs/modeling.md`는 거래를 `Transaction` 원장, `TransactionPayment` 결제자, `TransactionShare` 부담자로 분리한다. 정산 계산은 거래의 기준 통화 금액 스냅샷을 입력으로 사용하므로, 거래 저장 시 `exchangeRate`, `baseCurrency`, `baseAmount`를 확정해야 한다.

현재 `TripExchangeRate` 엔티티와 테이블은 있지만 실서비스 저장 로직은 없다. 따라서 이번 작업에는 거래 API뿐 아니라 여행별 환율표 자동 초기화/재조회/수동 수정 최소 구현을 포함한다.

## 범위

- 외부 환율 API 연동용 `ExchangeRateClient` 인터페이스를 추가한다.
- provider 미정 상태를 고려해 환율 client 구현체를 교체 가능하게 둔다.
- 여행 생성/국가 설정 시 국가 통화 전체에 대한 `TripExchangeRate` 초기화를 구현한다.
- 기준 통화도 `TripExchangeRate`에 `rate = 1.000000`으로 저장한다.
- 환율 기준일 결정 규칙을 구현한다: `exchangeRateBaseDate` -> `startDate` -> 여행 생성일.
- 환율 조회 실패 시 여행 생성/국가 설정 전체를 실패시킨다.
- 여행 환율 재조회/갱신 API를 구현한다.
- 여행 환율 수동 수정 API를 구현한다.
- 거래 등록/수정 시 여행 환율표를 조회해 `exchangeRate`, `baseCurrency`, `baseAmount`를 스냅샷 저장한다.
- 거래 등록/수정 시 외부 환율 API를 호출하지 않는다.
- 필요한 여행 환율이 없으면 거래 등록/수정에 실패한다.
- `POST /api/trips/{tripId}/transactions` 거래 등록을 구현한다.
- `GET /api/trips/{tripId}/transactions` 거래 목록 조회를 구현한다.
- `GET /api/trips/{tripId}/transactions/{transactionId}` 거래 상세 조회를 구현한다.
- `PATCH /api/trips/{tripId}/transactions/{transactionId}` 거래 수정을 구현한다.
- `DELETE /api/trips/{tripId}/transactions/{transactionId}` 거래 무효 처리를 구현한다.
- 결제자 금액 합계와 부담자 금액 합계가 거래 금액과 일치하는지 검증한다.
- 거래 변경 시 `Trip.expenseVersion`을 증가시키고 `TransactionEvent`를 기록한다.
- `TripSettlementStatus.NOT_STARTED`가 아니면 거래 등록/수정/삭제를 차단한다.
- Transaction API는 Post API와 같은 `@RequireActiveTripParticipant` 어노테이션으로 활성 여행 참여자 접근을 먼저 보장한다.
- Transaction API용 request/response DTO를 실제 계약으로 확정한다.
- 환율/거래 API 성공/실패 경로 테스트를 추가한다.

## 제외 범위

- 영수증 OCR.
- 알림 발송.
- 실제 송금 API 연동.
- 정산 계산 엔진 구현.
- `GET /api/trips/{tripId}/common-fund-balance` 구현.
- `GET /api/trips/{tripId}/transaction-statistics` 구현.
- 독립 `PUT /transactions/{transactionId}/payments`, `PUT /transactions/{transactionId}/shares` 고도화. 이번 작업은 등록/수정 요청 안에서 결제자/부담자 목록을 함께 처리한다.

## 설계

### 1. ExchangeRateClient

외부 provider가 미정이므로 domain/service 코드는 인터페이스에만 의존한다.

```kotlin
interface ExchangeRateClient {
    fun fetchRates(
        baseCurrency: String,
        targetCurrencies: Set<String>,
        rateDate: LocalDate,
    ): List<ExchangeRateQuote>
}

data class ExchangeRateQuote(
    val baseCurrency: String,
    val targetCurrency: String,
    val rate: BigDecimal,
    val rateDate: LocalDate,
    val source: String,
)
```

`rate`는 `targetCurrency` 1 단위당 `baseCurrency` 금액이다. 예를 들어 `baseCurrency = KRW`, `targetCurrency = JPY`, `rate = 9.15`이면 `1 JPY = 9.15 KRW`다.

테스트와 로컬 개발에서는 고정 fixture 기반 client를 둘 수 있다. 실제 provider 구현은 같은 인터페이스를 구현하게 한다.

### 2. 국가 통화 수집

여행 생성/국가 설정 시 방문 국가 코드에서 통화 코드를 수집한다.

국가 코드 -> 통화 코드 매핑은 API provider와 별개 정책이다. 간단한 MVP 구현은 정적 매핑 컴포넌트를 두고, 미지원 국가가 있으면 여행 생성/국가 설정을 실패시킨다.

기본 통화도 target currency 집합에 포함해 `TripExchangeRate` row로 저장한다.

예:

```text
defaultCurrency = KRW
countries = KR, JP, VN

TripExchangeRate:
KRW -> KRW = 1.000000
JPY -> KRW = 9.15
VND -> KRW = 0.054
```

### 3. TripExchangeRate 초기화

`TripService.createTrip()`과 `TripService.updateTripCountries()`에서 국가 저장과 환율 저장을 같은 트랜잭션으로 처리한다.

흐름:

1. 여행 기본 정보와 국가를 준비한다.
2. 환율 기준일을 결정한다.
3. 국가 통화와 기본 통화를 수집한다.
4. 기본 통화 quote는 `rate = 1.000000`, `source = BASE_CURRENCY`로 만든다.
5. 외화 quote는 `ExchangeRateClient.fetchRates(...)`로 조회한다.
6. 필요한 quote가 하나라도 없거나 조회 실패하면 예외를 던져 전체 요청을 실패시킨다.
7. 모든 quote가 준비되면 `TripExchangeRate`에 저장한다.

환율 조회 실패 시 불완전한 여행/국가/참여자 데이터가 남지 않도록 트랜잭션 롤백을 보장한다.

### 4. 환율 재조회/갱신

여행 설정에서 환율을 다시 가져올 수 있는 API를 추가한다.

예상 API:

```http
POST /api/trips/{tripId}/exchange-rates/refresh
```

동작:

- 여행의 현재 국가 통화 목록을 다시 계산한다.
- 기존 환율 기준일 정책으로 `rateDate`를 결정한다.
- 외부 API에서 quote를 다시 가져온다.
- 기본 통화 row는 `1.000000`으로 유지한다.
- 기존 `TripExchangeRate` row를 갱신하거나 새 기준일 row를 저장한다.
- 기존 `Transaction`의 환율 스냅샷은 재계산하지 않는다.

환율 재조회 실패 시 기존 환율표는 유지하고 요청은 실패시킨다.

### 5. 환율 수동 수정

사용자가 여행 설정에서 환율을 직접 수정할 수 있는 API를 추가한다.

예상 API:

```http
PATCH /api/trips/{tripId}/exchange-rates/{exchangeRateId}
```

동작:

- 여행 owner 또는 정책상 허용된 참가자만 수정한다.
- `rate > 0`을 검증한다.
- 수정된 row의 `rate`와 `source = MANUAL`을 저장한다.
- 기존 `Transaction`의 환율 스냅샷은 재계산하지 않는다.
- 이후 새 거래는 수정된 `TripExchangeRate`를 사용한다.

### 6. 거래 환율 적용

거래 저장 시 외부 API를 호출하지 않는다. 항상 여행 DB 스냅샷인 `TripExchangeRate`를 조회한다.

조회 조건은 `tripId`, `baseCurrency = trip.defaultCurrency`, `targetCurrency = request.currency`, `rateDate = resolvedRateDate` 기준으로 한다. 필요한 row가 없으면 거래 저장에 실패한다.

현재 repository는 `JpaRepository`만 상속하므로 다음 조회 메서드 추가를 검토한다.

```kotlin
fun findFirstByTripIdAndBaseCurrencyAndTargetCurrencyAndRateDateAndDeletedAtIsNull(
    tripId: Long,
    baseCurrency: String,
    targetCurrency: String,
    rateDate: LocalDate,
): TripExchangeRate?
```

거래에는 조회한 환율의 `rate`, `baseCurrency`, 계산된 `baseAmount`를 복사 저장한다.

### 7. 금액 계산

거래 원본 금액은 요청의 `amount`, `currency`를 사용한다. `baseAmount`는 서버가 `amount * exchangeRate`로 계산하고 소수점 2자리 기준으로 반올림한다.

결제자 금액과 부담자 금액도 요청에는 원본 통화 금액만 받는다. 서버가 거래와 동일한 환율을 적용해 `baseAmount`, `baseShareAmount`를 계산한다.

합계 검증은 원본 통화 금액 기준으로 먼저 수행한다.

- `payments.sum(amount) == transaction.amount`
- `shares.sum(shareAmount) == transaction.amount`

정산 계산은 기준 통화 금액을 사용한다.

### 8. 거래 변경 이벤트

거래 생성 시 `TransactionEventType.CREATED`, 수정 시 `UPDATED`, 무효 처리 시 `VOIDED` 이벤트를 기록한다.

`aggregateVersion`은 변경 후 `Trip.expenseVersion` 또는 `Transaction.version` 중 하나로 통일해야 한다. 정산 스냅샷 기준이 `Trip.expenseVersion`이므로, 이번 작업에서는 `Trip.expenseVersion` 증가 후 그 값을 이벤트 버전으로 사용하는 방향을 우선 검토한다.

이벤트 payload는 최소한 변경 후 거래 요약, 결제자, 부담자, 적용 환율 정보를 JSON 문자열로 저장한다.

### 9. 정산 상태 제한

거래 등록/수정/무효 처리는 `Trip.settlementStatus == NOT_STARTED`일 때만 허용한다.

`IN_PROGRESS`, `SETTLED` 상태에서는 실패한다. 와이어프레임의 “정산이 시작되면 더 이상 소비를 등록하거나 수정할 수 없어요” 문구에 맞춰 삭제도 함께 차단한다.

### 10. 응답 DTO 초안

- `TripExchangeRateResponse`: `id`, `baseCurrency`, `targetCurrency`, `rate`, `rateDate`, `source`, `createdAt`, `updatedAt`
- `TransactionSummaryResponse`: `id`, `tripId`, `transactionType`, `amount`, `currency`, `exchangeRate`, `baseCurrency`, `baseAmount`, `status`, `createdByUserId`, `createdAt`, `updatedAt`
- `TransactionDetailResponse`: summary 필드 + `payments`, `shares`, `events`
- `TransactionPaymentResponse`: `participantId`, `displayName`, `amount`, `currency`, `exchangeRate`, `baseCurrency`, `baseAmount`
- `TransactionShareResponse`: `participantId`, `displayName`, `shareAmount`, `currency`, `exchangeRate`, `baseCurrency`, `baseShareAmount`, `shareRatio`
- `TransactionEventResponse`: `id`, `eventType`, `aggregateVersion`, `payload`, `createdByUserId`, `createdAt`

## 계획

1. 기존 엔티티/마이그레이션과 DTO 차이를 점검한다.
2. `TripExchangeRate`에 수동 수정 출처 표현이 충분한지 확인하고 필요한 마이그레이션을 추가한다.
3. 국가 코드 -> 통화 코드 매핑 컴포넌트를 추가한다.
4. `ExchangeRateClient`, `ExchangeRateQuote`, 테스트/로컬 fixture client를 추가한다.
5. `TripExchangeRateRepository` 조회/저장 메서드와 환율 기준일 결정 로직을 추가한다.
6. `TripService.createTrip()`과 `updateTripCountries()`에 동기 환율 초기화와 실패 시 롤백을 구현한다.
7. 환율 목록 조회, 재조회/갱신, 수동 수정 API를 구현한다.
8. Transaction 요청/응답 DTO와 에러 코드를 추가한다.
9. 거래 등록 서비스에서 여행/참여자/환율 조회, 금액 계산, 합계 검증, 원장 저장, 이벤트 기록, `expenseVersion` 증가를 구현한다.
10. 거래 목록/상세 조회를 구현한다.
11. 거래 수정 서비스에서 기존 결제자/부담자 soft delete 또는 교체 저장 정책을 구현한다.
12. 거래 삭제는 원장 보존을 위해 물리 삭제나 `deletedAt` 처리 없이 `status = VOIDED` 상태 변경으로 구현한다.
13. 컨트롤러와 Swagger spec 반환 타입을 `ApiResponse<T>` 형태로 맞춘다.
14. Transaction 컨트롤러에 `@RequireActiveTripParticipant`를 적용해 Post API와 동일한 활성 여행 참여자 접근 제어를 적용한다.
15. 단위/통합 테스트를 추가하고 `./gradlew test`로 검증한다.

## 테스트 계획

- 여행 생성 시 외부 환율 API client가 호출되는지 검증한다.
- 국가 설정 변경 시 외부 환율 API client가 호출되는지 검증한다.
- 다중 국가 여행에서 각 국가 통화 환율이 모두 저장되는지 검증한다.
- 기준 통화도 `rate = 1.000000`으로 저장되는지 검증한다.
- `TripExchangeRate.rate`가 `targetCurrency` 1 단위당 `baseCurrency` 금액으로 저장되는지 검증한다.
- `exchangeRateBaseDate`가 있으면 해당 날짜가 `rateDate`로 저장되는지 검증한다.
- `exchangeRateBaseDate`가 없고 `startDate`가 있으면 `startDate`가 `rateDate`로 저장되는지 검증한다.
- 두 날짜가 모두 없으면 여행 생성일이 `rateDate`로 저장되는지 검증한다.
- 환율 API 실패 시 여행 생성/국가 설정 전체가 실패하고 데이터가 롤백되는지 검증한다.
- 여행 환율 재조회/갱신 성공 시 `TripExchangeRate`가 갱신되는지 검증한다.
- 여행 환율 재조회/갱신 실패 시 기존 환율표가 유지되는지 검증한다.
- 여행 환율 수동 수정 시 `rate`와 `source`가 갱신되는지 검증한다.
- 기준 통화 거래는 저장된 기본 통화 row를 사용하는지 검증한다.
- 외화 거래는 여행 환율표를 자동 적용해 `baseAmount`를 저장하는지 검증한다.
- 거래 등록/수정 시 외부 환율 API를 호출하지 않는지 검증한다.
- 환율이 없으면 거래 등록/수정이 실패하는지 검증한다.
- 환율 갱신/수동 수정 후 기존 거래 스냅샷이 재계산되지 않는지 검증한다.
- 결제 금액 합계와 거래 금액이 다르면 실패하는지 검증한다.
- 부담 금액 합계와 거래 금액이 다르면 실패하는지 검증한다.
- 거래 생성 시 `Trip.expenseVersion`이 증가하고 `CREATED` 이벤트가 기록되는지 검증한다.
- 거래 수정 시 `Trip.expenseVersion`이 증가하고 `UPDATED` 이벤트가 기록되는지 검증한다.
- 거래 무효 처리 시 `Trip.expenseVersion`이 증가하고 `VOIDED` 이벤트가 기록되는지 검증한다.
- 정산 상태가 `IN_PROGRESS` 또는 `SETTLED`이면 등록/수정/삭제가 실패하는지 검증한다.
- 목록 조회가 해당 여행의 활성 거래만 반환하는지 검증한다.
- 상세 조회가 결제자/부담자를 함께 반환하는지 검증한다.
- 최종 검증 명령은 `./gradlew test`다.

## 위험과 확인 사항

- 외부 환율 API provider가 아직 미정이다. #32에서는 `ExchangeRateClient` 인터페이스와 테스트/로컬 구현을 먼저 만들고, 실제 provider 선택 시 구현체를 교체할 수 있게 둔다.
- 국가 코드에서 통화 코드를 자동 추론하는 정책이 필요하다. 정적 매핑으로 시작하되 미지원 국가 처리 정책을 명확히 해야 한다.
- 기존 `TripExchangeRate` 샘플 데이터는 `baseCurrency = KRW`, `targetCurrency = JPY`, `rate = 0.109300`처럼 저장되어 있어 확정 정책과 방향이 다르다. 샘플 데이터와 마이그레이션을 `1 JPY = N KRW` 방향으로 정정해야 한다.
- `TripExchangeRate.source`는 현재 문자열 컬럼이다. `API`, provider name, `MANUAL`, `BASE_CURRENCY` 값을 어떻게 표준화할지 구현 전에 정해야 한다.
- `docs/modeling.md`의 `created_by_user_id` 설명은 `TripParticipant` 기준과 `User` 기준이 섞여 있다. 현재 엔티티는 `User`를 참조하므로 이번 구현은 코드 기준을 따른다.
- `Transaction` 엔티티에는 문서의 `category`, `description`, `occurredAt`, `location`, `placeName` 필드가 아직 없다. API 계약에 포함하려면 엔티티/마이그레이션 추가가 필요하다.
- `TransactionPayment`와 `TransactionShare`에는 soft delete 조건 조회 메서드가 없다. 수정 시 기존 row 처리 정책을 명확히 해야 한다.
- 정산 상태 enum 주석에는 `OPEN`이 언급되지만 실제 enum은 `NOT_STARTED`, `IN_PROGRESS`, `SETTLED`다. 구현은 실제 enum 기준으로 진행한다.
