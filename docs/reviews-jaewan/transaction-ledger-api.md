# 거래 원장 API 코드 리뷰

## 검토 대상

- 이슈 #32 거래 원장 API 구현
- 여행 환율표 초기화/조회/재조회/수동 수정
- 거래 등록/목록/상세/수정/무효 처리
- 금액 합계 검증, 정산 상태 잠금, 거래 이벤트 기록

## Code Reviewer 판단

발견 사항 없음.

검토 중 다음 위험은 구현 중 수정했다.

- 거래 목록이 page 기반으로 구현되던 부분을 Post API와 같은 cursor 기반으로 변경했다.
- 거래 무효 처리가 `deletedAt`을 기록해 이벤트 조회와 원장 보존을 방해하던 부분을 `status = VOIDED`만 변경하도록 수정했다.
- 거래 읽기 API에서 사용자 활성 상태를 확인하지 않던 부분을 보강했다.
- `Trip.updateTrip`에서 기본 통화, 환율 기준일, 시작일이 바뀌어도 환율표가 갱신되지 않던 부분을 보강했다.
- 계획 문서의 `ADJUSTED` 이벤트 표기를 실제 enum/DB 제약인 `UPDATED`로 수정했다.
- Transaction 컨트롤러에 Post API와 같은 `@RequireActiveTripParticipant` 적용 가능성을 확인하고 전체 거래 하위 API에 적용했다.

## Security Reviewer 판단

- 거래 등록/수정/삭제는 `TripSettlementStatus.NOT_STARTED`일 때만 허용한다.
- 거래 읽기/쓰기 모두 활성 사용자와 여행 접근 권한을 확인한다.
- 거래 하위 API는 `@RequireActiveTripParticipant`로 활성 여행 참여자 접근을 선검증한다.
- 거래 결제자/부담자는 해당 여행의 ACTIVE 참가자만 허용한다.
- 환율 수동 수정과 재조회는 여행 owner만 수행할 수 있다.
- 거래 등록/수정 시 외부 환율 API를 호출하지 않고 DB에 저장된 `TripExchangeRate`만 사용한다.
- 기존 거래의 `Transaction.exchangeRate` 스냅샷은 여행 환율 변경으로 재계산하지 않는다.

## 남은 위험

- 실제 외부 환율 provider는 아직 미정이다. 현재 구현은 `ExchangeRateClient` 인터페이스와 `StaticExchangeRateClient` fixture 구현으로 provider 교체 지점을 만든 상태다.
- 국가 코드에서 통화 코드로 변환하는 정적 매핑은 MVP 범위의 주요 국가만 포함한다. 미지원 국가 정책은 `UNSUPPORTED_TRIP_COUNTRY_CURRENCY` 실패로 처리한다.
- `Transaction` 엔티티에 category, description, occurredAt, placeName이 아직 없어 거래 화면의 부가 입력값은 이번 구현에 포함하지 않았다.
