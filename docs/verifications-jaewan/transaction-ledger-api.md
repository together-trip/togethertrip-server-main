# 거래 원장 API 검증 리포트

## 검증 대상

- `src/main/kotlin/com/togethertrip/main/trip/**`
- `src/main/kotlin/com/togethertrip/main/transaction/**`
- `src/test/kotlin/com/togethertrip/main/trip/service/TripExchangeRateServiceTest.kt`
- `src/test/kotlin/com/togethertrip/main/transaction/service/TransactionServiceTest.kt`

## 실행한 명령

```bash
./gradlew test
git diff --check
```

## 결과

- `./gradlew test` 성공.
- `git diff --check` 성공.
- 여행 생성/국가 변경/여행 기본 정보 변경 시 환율 초기화 경로가 추가되었다.
- 거래 등록/수정은 `TripExchangeRate`를 조회해 환율 스냅샷을 저장한다.
- 거래 목록은 Post API와 같은 cursor 기반 응답을 사용한다.
- 거래 API는 Post API와 같은 `@RequireActiveTripParticipant` 접근 제어를 적용한다.
- 거래 무효 처리는 원장 보존을 위해 `status = VOIDED`만 기록한다.

## 남은 확인

- 실제 환율 provider 선정 후 `ExchangeRateClient` 구현체 교체와 provider별 실패/재시도 정책 검증이 필요하다.
- Swagger UI에서 신규 환율/거래 API 노출 형태는 브라우저로 확인하지 않았다.
