# Issue #105 장소 검색 프록시 검증

## 결과

- Google Places 자동완성, 상세 조회, Geocoding 역지오코딩 프록시를 `/api/trips/{tripId}/places`에 구현했다.
- 모든 endpoint는 `@RequireActiveTripParticipant`를 적용한다.
- 사용자별 Redis 분당 제한은 자동완성 60회, 상세/역지오코딩 각각 30회다.
- API 키, 검색어, session token, 정확한 좌표는 service debug 로그에 노출하지 않는다.
- 게시글/소비 생성·수정 요청은 장소명 100자, 좌표 쌍, 위경도 범위를 검증한다.

## 실행 명령

```bash
./gradlew check
./gradlew -I /private/tmp/togethertrip-jacoco.init.gradle check jacocoTestReport
```

두 명령 모두 성공했다.

## 커버리지

- 전체 LINE: 7,199 / 9,144 = 78.73%
- 전체 BRANCH: 1,323 / 2,112 = 62.64%
- 신규 `place` 패키지 LINE: 249 / 249 = 100%
- 신규 `place` 패키지 BRANCH: 114 / 124 = 91.94%
- `post/validation` LINE: 14 / 14 = 100%
- `post/validation` BRANCH: 32 / 34 = 94.12%

현재 `develop`의 Gradle 설정에는 문서와 달리 JaCoCo task가 없어서, 저장소 파일을 변경하지 않는 임시 init script로 측정했다. 전체 LINE 78%, BRANCH 60% 기준은 통과한다.

## 환경 설정

Places API (New)와 Geocoding API를 허용한 서버용 키를 다음 환경변수로 주입한다.

```text
GOOGLE_MAPS_WEB_SERVICE_API_KEY
```

## 외부 API 검증

- 로컬 서버 키를 노출하지 않고 Places Autocomplete를 호출해 HTTP 200과 후보 5건을 확인했다.
- 동일 키로 Geocoding을 호출해 HTTP 200, `OK`, 결과 10건을 확인했다.
- HTTP 응답 body 없음, 필수 필드 누락, provider 오류, Place Details 실패 fallback은 가짜 `WebClient` 응답으로 자동 검증했다.
