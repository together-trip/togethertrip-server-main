# Issue 69. 전역 환율 날짜 기간 검색 API 구현 계획

## 목적

앱 환율 화면에서 여행방과 무관하게 KRW 기준 전역 환율을 조회할 수 있는 API를 추가한다.

현재 사용자 앱에서 사용할 수 있는 환율 API는 거래 입력 보조용
`GET /api/trips/{tripId}/transactions/exchange-rate`뿐이다. 이 API는 여행 참여자 권한과 거래 적용 환율 미리보기 흐름에 묶여 있어, 순수 환율 화면의 날짜별/기간별 리스트 조회에는 맞지 않는다.

## 범위

- `exchange_rates` 기존 테이블 재사용
- 사용자용 전역 환율 조회 API 추가
- 기준 통화는 우선 `KRW`만 허용
- 단일 날짜 조회와 날짜 기간 조회 지원
- 선택 통화 필터 지원
- soft delete 된 row 제외
- API 문서 갱신

## 제외 범위

- 외부 한국수출입은행 API 직접 호출
- 실시간 수집 트리거
- 환율 수집 batch/backfill 변경
- 여행별 `trip_exchange_rates` 변경
- 거래/정산 계산 정책 변경

## API 후보

### 단일 날짜 조회

```http
GET /api/exchange-rates?baseCurrency=KRW&targetCurrencies=USD,JPY,EUR&date=2026-06-17
```

의미:

- `date` 이하 최신값이 아니라, 해당 날짜의 저장된 전역 환율 목록을 조회한다.
- 해당 날짜가 휴일 또는 미수집일이면 빈 목록을 반환한다.
- "날짜 검색"의 의미가 최신 fallback이어야 한다면 별도 파라미터가 필요하다.

### 날짜 기간 조회

```http
GET /api/exchange-rates?baseCurrency=KRW&targetCurrencies=USD,JPY,EUR&from=2026-06-01&to=2026-06-17
```

의미:

- `from <= rateDate <= to` 범위의 저장된 환율 row를 반환한다.
- 앱 화면에서 최신 1일 목록만 필요하면 `date`를 사용한다.
- 차트/히스토리 화면이면 `from/to`를 사용한다.

## 입력

| 이름 | 위치 | 필수 | 기본값 | 설명 |
| --- | --- | --- | --- | --- |
| `baseCurrency` | query | false | `KRW` | 기준 통화. 1차 구현은 `KRW`만 허용 |
| `targetCurrencies` | query | false | 주요 통화 목록 | comma-separated ISO 4217 코드 |
| `date` | query | false | 없음 | 단일 날짜 조회. `yyyy-MM-dd` |
| `from` | query | false | 없음 | 기간 시작. `yyyy-MM-dd` |
| `to` | query | false | 없음 | 기간 종료. `yyyy-MM-dd` |

검증 규칙:

- `date`와 `from/to`는 동시에 받지 않는다.
- `from`과 `to`는 함께 받는다.
- `from > to`는 400.
- 기간 최대 길이는 1차 구현에서 31일 또는 90일 중 선택한다.
- `baseCurrency != KRW`는 400.
- 통화 코드는 trim + uppercase 처리한다.
- `targetCurrencies`가 비어 있으면 앱 주요 통화 기본 목록을 사용한다.

## 응답 후보

```json
{
  "baseCurrency": "KRW",
  "date": "2026-06-17",
  "from": null,
  "to": null,
  "rates": [
    {
      "targetCurrency": "USD",
      "rate": 1380.120000,
      "rateDate": "2026-06-17",
      "source": "KOREA_EXIM"
    }
  ]
}
```

응답 정렬:

- 단일 날짜: `targetCurrency ASC`
- 기간 조회: `targetCurrency ASC`, `rateDate DESC`

## 패키지/파일 계획

생성 후보:

- `src/main/kotlin/com/togethertrip/main/exchange/controller/ExchangeRateController.kt`
- `src/main/kotlin/com/togethertrip/main/exchange/controller/spec/ExchangeRateApiSpec.kt`
- `src/main/kotlin/com/togethertrip/main/exchange/dto/response/ExchangeRateResponse.kt`
- `src/main/kotlin/com/togethertrip/main/exchange/dto/response/ExchangeRateSearchResponse.kt`
- `src/main/kotlin/com/togethertrip/main/exchange/service/ExchangeRateQueryService.kt`
- `src/test/kotlin/com/togethertrip/main/exchange/service/ExchangeRateQueryServiceTest.kt`
- 필요 시 `src/test/kotlin/com/togethertrip/main/exchange/controller/ExchangeRateControllerTest.kt`

수정 후보:

- `src/main/kotlin/com/togethertrip/main/exchange/repository/ExchangeRateRepository.kt`
- `src/main/resources/db/migration/V13__add_exchange_rate_query_indexes.sql`
- `docs/api-spec.md`
- 필요 시 `docs/modeling.md`

## Repository 쿼리 후보

단일 날짜:

```kotlin
fun findByBaseCurrencyAndTargetCurrencyInAndRateDateAndDeletedAtIsNullOrderByTargetCurrencyAsc(
    baseCurrency: String,
    targetCurrencies: Collection<String>,
    rateDate: LocalDate,
): List<ExchangeRate>
```

기간 조회:

```kotlin
fun findByBaseCurrencyAndTargetCurrencyInAndRateDateBetweenAndDeletedAtIsNullOrderByTargetCurrencyAscRateDateDesc(
    baseCurrency: String,
    targetCurrencies: Collection<String>,
    from: LocalDate,
    to: LocalDate,
): List<ExchangeRate>
```

메서드명이 길어지면 `@Query`로 명시한다.

## 인덱스 후보

### 현재 존재

```sql
CREATE UNIQUE INDEX IF NOT EXISTS uk_exchange_rates_currency_date
    ON exchange_rates (base_currency, target_currency, rate_date)
    WHERE deleted_at IS NULL;
```

장점:

- 단일 통화의 날짜 조회에 적합하다.
- 기간 조회에서도 `base_currency`, `target_currency`, `rate_date` 조건을 탄다.

한계:

- 앱의 "특정 날짜 주요 통화 전체 목록"은 `target_currency IN (...)` 또는 전체 통화 목록 조회가 많다.
- `base_currency + rate_date`로 특정 날짜 목록을 먼저 좁히는 패턴에는 컬럼 순서가 최적은 아니다.

### 후보 A. 날짜 목록 조회 최적화

```sql
CREATE INDEX IF NOT EXISTS idx_exchange_rates_base_date_currency
    ON exchange_rates (base_currency, rate_date DESC, target_currency)
    WHERE deleted_at IS NULL;
```

적합한 쿼리:

- `base_currency = 'KRW' AND rate_date = :date`
- `base_currency = 'KRW' AND rate_date BETWEEN :from AND :to`
- 특정 날짜/기간의 여러 통화 목록 조회

평가:

- 앱 환율 화면 B안의 메인 조회 패턴에 가장 직접적으로 맞는다.
- 단일 날짜 주요 통화 리스트가 주 사용처라면 1순위 후보.

### 후보 B. 통화별 최신/기간 조회 정렬 최적화

```sql
CREATE INDEX IF NOT EXISTS idx_exchange_rates_base_target_date_desc
    ON exchange_rates (base_currency, target_currency, rate_date DESC)
    WHERE deleted_at IS NULL;
```

적합한 쿼리:

- `base_currency = 'KRW' AND target_currency = 'JPY' AND rate_date <= :date ORDER BY rate_date DESC LIMIT 1`
- 통화별 최신 fallback 조회

평가:

- 기존 unique index와 컬럼 순서는 같지만 정렬 방향이 최신 조회 패턴에 맞다.
- 거래 환율 미리보기의 `<= date ORDER BY rate_date DESC LIMIT 1`에도 유리할 수 있다.
- 단, 단순 기간 목록에는 후보 A보다 덜 직접적이다.

### 후보 C. 신규 인덱스 없음

적합한 상황:

- 초기 데이터 규모가 작다.
- 조회 트래픽이 낮다.
- 기존 unique index로 충분히 빠르다.

평가:

- 마이그레이션이 가장 작다.
- 하지만 앱 환율 화면이 자주 열리는 기능이면 추후 후보 A를 다시 추가할 가능성이 높다.

## 인덱스 1차 선택 제안

1차 구현은 후보 A를 우선 검토한다.

이유:

- 요구사항이 "날짜 기간별 검색"이다.
- 앱 B안은 특정 날짜 또는 기간의 통화 목록을 보여주는 구조다.
- 기존 unique index는 통화별 조회에는 좋지만, 날짜 중심 목록 조회에는 후보 A가 더 자연스럽다.

보류:

- 후보 B는 "선택 날짜 이하 최신 fallback" 정책을 API에 넣는 경우 함께 검토한다.
- 정확히 해당 날짜 데이터만 조회한다면 후보 B의 이점은 작다.

## 서비스 정책 결정 필요

구현 전 확정할 것:

- `date=2026-06-17` 요청이 해당 날짜 row만 반환할지, 해당 날짜 이하 최신 row를 반환할지
- 기본 주요 통화 목록
- 기간 최대 길이
- 인증 필요 여부

추천:

- 인증 필요: 기존 앱 API 정책과 맞추기 위해 bearer 인증 유지
- 날짜 의미: `date`는 해당 날짜 row 조회
- 최신 fallback이 필요하면 `mode=LATEST_BEFORE_OR_ON` 같은 별도 파라미터 추가
- 기본 주요 통화: `USD,JPY,EUR,CNY,TWD,HKD,VND,THB,SGD`

## 구현 순서

1. Issue 69 기준 API 계약 확정
2. `ExchangeRateQueryService` 추가
3. `ExchangeRateRepository` 날짜/기간 조회 메서드 추가
4. Controller/Spec/Response DTO 추가
5. 입력 검증 및 에러 처리 추가
6. 인덱스 마이그레이션 추가 여부 결정
7. 테스트 추가
8. `docs/api-spec.md` 갱신
9. `./gradlew test --tests '*ExchangeRate*'`
10. `./gradlew compileKotlin`

## 테스트 계획

- 단일 날짜 조회가 해당 날짜 환율만 반환한다.
- 기간 조회가 `from <= rateDate <= to` 범위를 반환한다.
- `targetCurrencies`가 있으면 해당 통화만 반환한다.
- `targetCurrencies`가 없으면 기본 주요 통화 목록을 사용한다.
- `date`와 `from/to`를 동시에 넘기면 실패한다.
- `from > to`면 실패한다.
- `baseCurrency != KRW`면 실패한다.
- soft delete 된 row는 제외한다.

## 문서 갱신

- `docs/api-spec.md`: 신규 API 계약 추가
- `docs/modeling.md`: 인덱스 추가 시 조회 패턴과 인덱스 목적 기록

## 현재 상태

- 이슈: https://github.com/together-trip/togethertrip-server-main/issues/69
- 브랜치: `feature/issue-69-exchange-rate-query`
- 구현 완료
- 선택 인덱스: `idx_exchange_rates_base_date_currency`
- 검증: `./gradlew test --tests '*ExchangeRate*'` 통과
