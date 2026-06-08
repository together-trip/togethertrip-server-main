# 거래 원장 API 검증 리포트

## 검증 대상

- `src/main/kotlin/com/togethertrip/main/trip/**`
- `src/main/kotlin/com/togethertrip/main/transaction/**`
- `src/test/kotlin/com/togethertrip/main/transaction/service/TransactionExchangeRateResolverTest.kt`
- `src/test/kotlin/com/togethertrip/main/transaction/service/TransactionServiceTest.kt`

## 실행한 명령

```bash
./gradlew test
git diff --check
```

## 결과

- `./gradlew test` 성공.
- `git diff --check` 성공.
- 거래 등록/수정은 거래 시점 환율 스냅샷을 저장한다.
- 환율 기준 통화는 항상 `KRW`다.
- 거래 쓰기 경로는 외부 API를 직접 호출하지 않고, 배치/크론이 적재한 전역 `exchange_rates` DB를 조회한다.
- 외화 거래는 `baseCurrency = KRW`, `targetCurrency = 거래 통화`, `rateDate <= 기준일` 조건의 최신 환율을 사용한다.
- `KRW` 거래는 환율 DB 조회 없이 `1.000000` 환율을 사용한다.
- 거래 등록/수정 화면용 환율 미리보기 API는 거래 쓰기와 같은 `TransactionExchangeRateResolver` 정책을 사용한다.
- 거래 목록은 Post API와 같은 cursor 기반 응답을 사용한다.
- 거래 API는 Post API와 같은 `@RequireActiveTripParticipant` 접근 제어를 적용한다.
- 거래 무효 처리는 원장 보존을 위해 `status = VOIDED`만 기록한다.

## 남은 확인

- 실제 환율 provider 연동과 배치/크론 적재 정책 검증은 후속 작업이다.
- Swagger UI에서 신규 거래 환율 미리보기/거래 API 노출 형태는 브라우저로 확인하지 않았다.
