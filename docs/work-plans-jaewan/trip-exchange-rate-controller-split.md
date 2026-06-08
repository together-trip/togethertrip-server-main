# 여행 환율 컨트롤러 분리 작업 계획

## 배경

- `TripController`와 `TripApiSpec`에 여행 기본 API와 여행 환율 API가 함께 있어 기존 `Trip` 작업과 충돌 가능성이 있다.
- 환율 비즈니스 로직은 이미 `TripExchangeRateService`로 분리되어 있으므로 컨트롤러와 Swagger spec도 같은 책임 단위로 분리한다.

## GitHub Issue

- 생성하지 못했다.
- 사유: `gh auth status` 확인 결과 `red-sprout` 계정의 GitHub token이 invalid 상태라 Issue 생성 권한을 사용할 수 없다.

## 작업 범위

- `TripController`에서 환율 API 엔드포인트 제거
- `TripApiSpec`에서 환율 API 문서 메서드 제거
- `TripExchangeRateController` 추가
- `TripExchangeRateApiSpec` 추가
- 기존 URL 경로와 응답 형상은 유지

## 검증

- `./gradlew test`
