# 거래 작성 시점 환율 스냅샷 전환 작업 계획

## 배경

- 기존 브랜치 작업에서는 여행 생성/수정 시 `TripService`에서 여행 환율표를 미리 초기화하고, 거래 등록 시 그 환율표를 조회하는 구조였다.
- 현재 정책은 거래를 적는 시점의 환율을 조회해 거래 스냅샷에 저장하는 방향이 더 적합하다.
- TogetherTrip은 한국 사용자를 기본 대상으로 하므로 정산 기준 통화는 항상 `KRW`로 고정한다.
- 거래 쓰기 경로에서는 외부 환율 API를 직접 호출하지 않는다. 외부 API 연동은 크론/배치가 담당하고, 거래 서비스는 배치가 저장한 환율 DB만 조회한다.
- `TripService`는 다른 작업자가 진행 중인 영역이므로 이 브랜치에서 건드린 환율 초기화 변경은 되돌리고, 거래 영역 중심으로 구현한다.

## GitHub Issue

- 생성하지 못했다.
- 사유: `gh auth status` 확인 결과 `red-sprout` 계정의 GitHub token이 invalid 상태라 Issue 생성 권한을 사용할 수 없다.

## 작업 범위

- `TripService`에 추가되었던 `TripExchangeRateService` 의존성과 `initializeExchangeRates(...)` 호출 제거
- `TripServiceTest`에 추가되었던 환율 초기화 검증 제거
- `TransactionService`가 미리 저장된 `TripExchangeRate`를 조회하지 않도록 변경
- 거래 생성/수정 시 배치가 적재한 환율 DB를 조회해 `TransactionCurrencySnapshot` 생성
- 기준 통화는 `Trip.defaultCurrency`가 아니라 `KRW`로 고정
- 거래 통화가 `KRW`이면 외부 조회와 DB 조회 없이 `1.000000` 환율 적용
- 조회한 환율을 `Transaction`, `TransactionPayment`, `TransactionShare`의 기존 환율/기준통화/기준금액 필드에 저장
- `TripExchangeRateController`와 `TripExchangeRateApiSpec`는 여행별 수동 환율표 정책과 결합되어 있으므로 제거한다.
- 환율 배치/크론과 환율 원천 테이블 설계는 후속 작업으로 분리한다.

## 환율 방향 정책

- 저장되는 환율은 `targetCurrency` 1단위당 `KRW` 금액이다.
- 예: `targetCurrency = JPY`, `rate = 9.150000`이면 `1 JPY = 9.15 KRW`를 의미한다.
- 기준 금액은 항상 `amount * exchangeRate`로 계산하고 소수점 2자리 기준으로 반올림한다.
- `Transaction.baseCurrency`, `TransactionPayment.baseCurrency`, `TransactionShare.baseCurrency`에는 항상 `KRW`를 저장한다.

## 기준일 정책

- 기본 기준일은 소비일이다.
- 거래 저장 요청에는 아직 소비일 필드가 없으므로 저장 시점에는 `Asia/Seoul` 기준 현재 날짜를 기준일로 사용한다.
- 환율 미리보기 API는 `spendingDate` 요청 파라미터가 있으면 해당 소비일을 기준일로 사용하고, 없으면 `Asia/Seoul` 기준 현재 날짜를 기준일로 사용한다.
- 미래 소비일처럼 기준일의 환율 row가 아직 없으면 기준일 이하 최신 적재 환율을 적용한다.
- 기준일 이하 환율 row가 전혀 없으면 거래 환율 오류로 실패시킨다. 이 경우 환율 카테고리/통화 선택 차단 UI는 프론트에서 처리한다.
- 거래 요청/엔티티에 `transactionDate`, `spentAt`, `occurredAt` 같은 소비일 필드가 확정되면 저장 API도 같은 기준일 정책에 해당 필드를 넘긴다.

## 수정 시점 스냅샷 정책

- 거래 수정 시에도 수정 시점의 환율을 다시 조회해 새 스냅샷을 저장한다.
- `updateTransactionPayments(...)`, `updateTransactionShares(...)`처럼 결제자/부담자만 수정하는 API도 현재 전체 거래 수정 흐름으로 위임하므로 동일하게 수정 시점 환율을 다시 적용한다.
- 기존 이벤트 payload는 수정 후 거래 요약을 저장하므로, 환율 변경 이력은 이벤트 payload의 변경 전후 비교로 확인한다.

## 구현 계획

1. `TransactionService` 생성자에서 `TripExchangeRateRepository`, `TripExchangeRateService` 의존성을 제거한다.
2. 거래 환율 스냅샷 생성을 담당하는 작은 컴포넌트 또는 private 로직을 추가한다.
3. `KRW` 고정 기준 통화 상수를 거래 환율 스냅샷 로직에 둔다.
4. 거래 통화가 `KRW`이면 `TransactionCurrencySnapshot(currency = "KRW", baseCurrency = "KRW", exchangeRate = 1.000000)`을 만든다.
5. 외화 거래이면 배치가 적재한 환율 DB에서 `baseCurrency = KRW`, `targetCurrency = request.currency`, `rateDate <= 기준일` 조건으로 최신 환율을 조회한다.
6. 환율 row가 없거나 rate가 0 이하이면 거래 환율 오류로 실패시킨다.
7. 기존 거래 생성/수정 흐름은 유지하고 `resolveCurrencySnapshot(...)`만 새 정책으로 교체한다.
8. `TransactionServiceTest`를 새 정책에 맞춰 수정한다.
9. `KRW` 거래는 환율 DB를 조회하지 않는지 검증하는 테스트를 추가한다.
10. 외화 거래는 환율 DB를 조회하고 `baseAmount`를 `amount * rate`로 저장하는지 검증하는 테스트를 추가한다.
11. 거래 수정과 결제자/부담자 수정 시 수정 시점 환율을 다시 적용하는지 검증하는 테스트를 추가한다.
12. 거래 등록/수정 화면에서 적용 예정 환율을 볼 수 있도록 환율 미리보기 API를 추가한다.
13. 미래 소비일 환율 미리보기는 소비일 이하 최신 적재 환율을 반환하는지 검증하는 테스트를 추가한다.
14. 여행 환율 컨트롤러/spec 파일과 관련 API 문서를 삭제한다.
15. `TripExchangeRateService.initializeExchangeRates(...)`와 여행 생성/수정의 환율 초기화 연동을 제거한다.
16. `TransactionErrorCode.EXCHANGE_RATE_NOT_READY` 메시지를 여행 환율표 기준 표현에서 거래 환율 조회 실패 표현으로 정리한다.

## 제외 범위

- 배치/크론 구현
- 실제 외부 환율 API provider 연동
- 전역 환율 원천 테이블 신규 설계와 Flyway 마이그레이션
- 기존 `TripExchangeRate` 테이블, 엔티티, repository 삭제
- `Trip.exchangeRateBaseDate` 필드 삭제
- 여행 생성/수정 API DTO에서 환율 기준일 필드 제거
- 실제 소비일 필드 추가

## 검증

- `./gradlew test`
- `TransactionServiceTest`
- `TripExchangeRateServiceTest`는 여행별 환율 API 제거 범위에 맞춰 삭제 또는 수정

## 보안/무결성 검토

- 거래 생성/수정 시점에 저장된 환율 스냅샷은 이후 환율 DB 변경과 무관하게 유지되어야 한다.
- 정산 시작 이후 거래 수정 제한은 기존 `validateWritableTrip(...)` 정책을 유지한다.
- 환율 조회 실패 시 부분 저장이 발생하지 않도록 저장 전에 예외로 실패시킨다.
- 거래 쓰기 트랜잭션 안에서는 외부 네트워크 호출을 수행하지 않는다.
- 기준 통화는 사용자 입력이나 여행 설정에 의존하지 않고 `KRW`로 고정한다.
