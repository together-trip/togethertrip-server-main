# Issue #105 게시글 장소 검색 프록시와 좌표 검증

## 배경

`Post`는 `placeName`, `latitude`, `longitude`를 이미 저장하지만 요청 좌표의 쌍/범위를 검증하지 않는다. 모바일 앱에서 글로벌 장소 검색을 제공하되 Places Web Service 키는 서버에서 보호해야 한다.

## 범위

- 인증된 사용자가 호출하는 장소 자동완성, 상세 조회, 좌표 역지오코딩 API
- Google Places API 호출을 담당하는 명시적 client/service 구성
- 앱에 필요한 이름, 주소, 좌표만 반환하는 provider-neutral 응답 DTO
- 게시글/소비 생성·수정 요청의 장소명 길이, 좌표 쌍, 위경도 범위 검증
- 외부 API 실패를 내부 오류 형상으로 변환하고 키/원문 응답을 로그에 노출하지 않음

## 제외 범위

- 장소 검색 결과 영구 캐시
- 이동 경로, 백그라운드 위치 추적
- `Post` 스키마 및 기존 API 응답 필드 변경

## 아키텍처 판단

- 장소 검색은 `place` feature의 controller/service/client/dto로 둔다.
- client는 Google 응답을 내부 모델로 변환하며 Controller는 외부 응답을 직접 노출하지 않는다.
- gateway의 기존 `/api/**` route를 사용하므로 gateway 변경은 하지 않는다.
- 선택 완료 후에는 provider id 대신 기존 장소명과 좌표 snapshot만 `Post`에 저장한다.

## TDD 시나리오

1. 자동완성은 query와 session token을 Google 요청으로 변환하고 필요한 필드만 반환한다.
2. 상세 조회는 이름, 주소, 위도, 경도를 반환한다.
3. 역지오코딩은 핀 좌표를 표시 가능한 장소명/주소로 변환한다.
4. API 키 미설정, upstream 4xx/5xx, 빈 결과를 정의된 오류로 변환한다.
5. 게시글 생성·수정에서 좌표 한쪽만 전달하거나 범위를 벗어나면 400으로 거절한다.
6. 좌표가 모두 없거나 유효한 쌍이면 기존 흐름이 유지된다.

## 검증

- 관련 단위/Controller 테스트
- `./gradlew check`
- JaCoCo 신규 코드 커버리지 확인

## 보안

- Places Web Service 키는 환경변수로만 주입한다.
- 장소 API는 인증 필터가 적용되는 `/api/**` 아래에 둔다.
- query/session token/API key를 애플리케이션 로그에 직접 남기지 않는다.
