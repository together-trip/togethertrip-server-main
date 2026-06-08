# Work Fix: 여행 목록 cursor 없는 조회 오류

작성일: 2026-06-08
브랜치: `fix/trip-list-cursor-query`

## 문제

Flutter 앱에서 로그인 후 여행 탭이 `GET /api/trips`를 호출하면 server-main 로그에 PostgreSQL 파라미터 타입 추론 오류가 발생했다.

```text
ERROR: could not determine data type of parameter $5
SQLState: 42P18
```

실제 Hibernate SQL은 cursor 없는 첫 페이지 조회에서도 다음 조건을 포함했다.

```sql
and (
  ? is null
  or t1_0.created_at < ?
  or (t1_0.created_at = ? and t1_0.id < ?)
)
```

첫 페이지 조회에서는 `cursorCreatedAt = null`이므로 PostgreSQL이 `? is null` 위치의 파라미터 타입을 추론하지 못했다.

## 원인

`TripRepository.findAccessibleTrips`가 cursor 없는 조회와 cursor 있는 조회를 하나의 JPQL로 처리하고 있었다.

```kotlin
and (
  :cursorCreatedAt is null
  or t.createdAt < :cursorCreatedAt
  or (t.createdAt = :cursorCreatedAt and t.id < :cursorId)
)
```

JPQL/Kotlin 시그니처에서는 `Instant?`로 타입이 보이지만, PostgreSQL은 SQL 실행 시점의 null 파라미터만 보고 타입을 결정해야 해서 `42P18` 오류가 발생했다.

## 수정

- cursor 없는 첫 페이지 조회와 cursor 있는 다음 페이지 조회를 별도 repository 메서드로 분리했다.
- `TripService.getTrips()`에서 cursor 유무에 따라 호출할 repository 메서드를 분기했다.
- `TripServiceTest` mock 기대값을 분리된 repository 메서드에 맞춰 수정했다.

## 변경 파일

- `src/main/kotlin/com/togethertrip/main/trip/repository/TripRepository.kt`
  - `findAccessibleTrips(...)`: cursor 없는 조회 전용
  - `findAccessibleTripsAfterCursor(...)`: cursor 조건 조회 전용
- `src/main/kotlin/com/togethertrip/main/trip/service/TripService.kt`
  - `tripCursor == null`이면 cursor 없는 쿼리 호출
  - `tripCursor != null`이면 cursor 조건 쿼리 호출
- `src/test/kotlin/com/togethertrip/main/trip/service/TripServiceTest.kt`
  - cursor 없는 목록 조회 테스트 mock 수정
  - cursor 기반 목록 조회 테스트 mock 수정

## 테스트

```bash
./gradlew test --tests com.togethertrip.main.trip.service.TripServiceTest
git diff --check
```

검증 결과:

- `TripServiceTest`: 통과
- `git diff --check`: 통과

## 재현/확인 방법

수정 전:

1. server-main을 실행한다.
2. Flutter 앱에서 로그인 후 여행 탭 진입 또는 직접 `GET /api/trips`를 호출한다.
3. 서버 로그에서 `could not determine data type of parameter` 오류가 발생한다.

수정 후:

1. server-main을 재시작한다.
2. 같은 `GET /api/trips` 첫 페이지 요청을 보낸다.
3. cursor 없는 전용 쿼리가 실행되어 null cursor 파라미터 타입 오류가 발생하지 않아야 한다.

## 위험과 확인 사항

- 목록 정렬 정책(`createdAt desc, id desc`)과 cursor 포맷은 변경하지 않았다.
- status 필터의 `:status is null` 조건은 enum 파라미터라 이번 오류의 직접 원인이 아니며 기존 동작을 유지했다.
- 전체 테스트는 로컬 PostgreSQL/Flyway 환경에 따라 `MainApplicationTests.contextLoads()`가 환경 의존적으로 실패할 수 있어, 이번 fix의 직접 범위인 `TripServiceTest`를 우선 검증했다.

---

# PR

- base: `develop` ← head: `fix/trip-list-cursor-query`
- 제목: `fix: 여행 목록 커서 없는 조회 오류 수정`

```text
# 작업 내용

- 여행 목록 조회 쿼리를 cursor 없는 첫 페이지 조회와 cursor 있는 다음 페이지 조회로 분리했습니다.
- PostgreSQL이 `:cursorCreatedAt is null`의 null 파라미터 타입을 추론하지 못해 발생하던 `42P18` 오류를 방지했습니다.
- `TripService.getTrips()`에서 cursor 유무에 따라 repository 메서드를 분기하도록 수정했습니다.
- 관련 `TripServiceTest` mock 기대값을 수정했습니다.
- 수정 내용을 `docs/work-fix-donghyun/trip-list-cursor-null-query.md`에 기록했습니다.

# 변경 유형

- [ ] 기능 추가
- [x] 버그 수정
- [ ] 리팩토링
- [ ] 설정 변경
- [x] 문서 수정
- [x] 테스트 추가/수정

# 확인 사항

- [ ] 로컬에서 빌드가 성공했습니다.
- [x] 테스트가 성공했습니다.
- [x] 불필요한 로그/주석을 제거했습니다.
- [x] 민감 정보가 포함되지 않았습니다.
- [ ] API 변경 사항이 있다면 문서 또는 요청 예시를 함께 수정했습니다.

# 테스트 방법

```bash
./gradlew test --tests com.togethertrip.main.trip.service.TripServiceTest
git diff --check
```

# 관련 이슈

- 앱 연동 중 발견한 런타임 오류 수정으로 별도 이슈는 연결하지 않았습니다.

# 참고 사항

- 전체 테스트는 로컬 PostgreSQL/Flyway 테스트 DB 준비 상태에 영향을 받을 수 있어 직접 관련 테스트를 우선 실행했습니다.
- API 응답 형식과 cursor 포맷은 변경하지 않았습니다.
```
