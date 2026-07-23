# Work Plan

## 작업

`trip` 목록 커서 처리 책임을 서비스에서 분리하고, `TripCursor`를 별도 `pagination` 패키지로 이동한다.

- GitHub Issue: 이번 작업에서는 생성/연결하지 못함. 사용자 요청 우선으로 로컬 코드 수정과 검증을 먼저 진행했다.

## 배경

현재 `TripService`에는 여행 목록 조회용 cursor 파싱 로직과 내부 `TripCursor` `data class`가 함께 들어 있어, 커서 포맷 해석 책임이 서비스 계층에 섞여 있다.

커서 포맷 인코딩/디코딩 책임은 커서 타입 자체가 가지는 편이 응집도 측면에서 낫다.

한편 `TripListResponse`를 전역 공통 `CursorResponse<T>`로 올리는 방향은 타당하지만, 동일 패턴이 실제로 반복되는 시점에 묶어서 정리하는 편이 변경 범위 관리 측면에서 더 낫다. 이번 작업에서는 응답 공통화는 보류하고 커서 책임 분리만 우선 반영한다.

## 범위

- `TripService` 내부 `TripCursor` 선언을 제거한다.
- `trip/pagination/TripCursor`를 추가한다.
- `TripCursor`에 `decode(cursor: String)`와 `encode()` 책임을 둔다.
- `TripService`는 cursor 예외를 `BusinessException(CommonErrorCode.INVALID_INPUT)`으로 변환만 하도록 정리한다.
- 기존 `TripListResponse`는 유지한다.
- `TripService.getTrips()` 반환 타입은 `TripListResponse`를 유지한다.
- `TripController`, `TripApiSpec`의 목록 조회 반환 타입도 그대로 유지한다.
- 관련 테스트가 기존 동작을 유지하는지 검증한다.

## 제외 범위

- `global/response/CursorResponse<T>` 추가.
- `post`, `transaction` 등 다른 도메인의 cursor 응답 전면 교체.
- cursor 문자열 포맷을 URL-safe Base64 같은 다른 규격으로 변경.
- 여행 목록 조회 쿼리 정렬 정책 변경.
- Swagger 설명 문구 세부 보강.
- DB 스키마 변경.

## 설계

- `TripCursor`는 [`trip/pagination`](/Users/leedonghyun/Desktop/Side-Project/together-trip/togethertrip-server-main/src/main/kotlin/com/togethertrip/main/trip/pagination) 패키지에 둔다.
- `TripCursor.decode()`는 문자열 포맷 검증과 `createdAt`, `id` 파싱을 담당한다.
- `TripCursor.encode()`는 다음 페이지 조회용 커서를 생성한다.
- `TripService.parseTripCursor()`는 `TripCursor.decode()` 호출 후 런타임 예외를 `INVALID_INPUT`으로 매핑한다.
- 목록 응답은 기존 [`TripListResponse.kt`](/Users/leedonghyun/Desktop/Side-Project/together-trip/togethertrip-server-main/src/main/kotlin/com/togethertrip/main/trip/dto/response/TripListResponse.kt)를 유지한다.
- 이번 단계에서는 응답 구조 공통화보다 커서 해석 책임 이동에 집중한다.

## 테스트 계획

- 여행 목록 상태 필터 조회가 기존과 동일하게 동작하는지 검증한다.
- cursor 없이 목록 조회 시 `hasNext = false`, `nextCursor = null`이 유지되는지 검증한다.
- cursor 기반 목록 조회 시 `size + 1` 조회 결과로 `hasNext`와 `nextCursor`가 계산되는지 검증한다.
- 잘못된 cursor 문자열이면 `INVALID_INPUT`으로 실패하는지 검증한다.
- `TripServiceTest`를 중심으로 변경 후 컴파일 및 동작을 확인한다.
- 최종 검증 명령은 `./gradlew test`를 기준으로 하되, 환경 제약이 있으면 범위를 좁힌 테스트 실행 결과를 함께 남긴다.

## 위험과 확인 사항

- 로컬 전체 테스트는 `MainApplicationTests > contextLoads()`에서 PostgreSQL/Flyway 연결이 필요해 환경 의존적으로 실패할 수 있다.
- `CursorResponse<T>` 공통화는 후속 작업으로 남긴다. 그 시점에는 `trip`, `post`, `transaction` 목록 응답을 함께 보는 편이 안전하다.
- 현재 `TripCursor` 포맷은 `createdAt_id` 문자열이다. 이번 작업 범위에서는 포맷 호환성을 유지하고 구조만 이동한다.
