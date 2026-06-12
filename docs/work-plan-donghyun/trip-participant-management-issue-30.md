# Work Plan

## 작업

이슈 #30 `feat: 여행 참여자와 임시 동행자 관리 구현`을 진행한다.

구현 대상은 여행 참여자 추가, 목록 조회, 상세 조회, 표시 정보 수정, 제거, 임시 참여자 회원 연결 API다.

- GitHub Issue: https://github.com/together-trip/togethertrip-server-main/issues/30
- 작업 브랜치: `feature/issue-30-trip-participant-management`

## 배경

TogetherTrip 정산 기준은 `User`가 아니라 `TripParticipant`다.

여행 생성 시 이미 임시 동행자는 `TripParticipant(user = null)` 형태로 저장되고 있었지만, 여행 생성 이후 참여자를 추가하거나 임시 참여자를 회원과 연결하는 API는 Controller와 DTO가 placeholder 상태였다.

거래와 정산은 participant id를 참조하므로, 임시 참여자를 회원과 연결할 때 기존 row를 유지해야 과거 참조가 깨지지 않는다. 참여자 제거도 hard delete 대신 상태와 삭제 시각을 남기는 방식이 필요하다.

## 범위

- `POST /api/trips/{tripId}/participants` 임시 참여자 추가를 구현한다.
- `GET /api/trips/{tripId}/participants` 참여자 목록 조회를 구현한다.
- `GET /api/trips/{tripId}/participants/{participantId}` 참여자 상세 조회를 구현한다.
- `PATCH /api/trips/{tripId}/participants/{participantId}` 참여자 표시 정보 수정을 구현한다.
- `DELETE /api/trips/{tripId}/participants/{participantId}` 참여자 제거를 구현한다.
- `POST /api/trips/{tripId}/participant-connections` 임시 참여자와 회원 연결을 구현한다.
- 참여자 요청 DTO를 실제 필드 기반으로 변경한다.
- 참여자 응답에 `participantType`을 추가한다.
- 참여자 관련 에러 코드를 추가한다.
- `TripParticipantServiceTest`로 주요 성공/실패 흐름을 검증한다.

## 제외 범위

- 초대 코드/링크 생성.
- 알림 서버 연동.
- 채팅방 멤버 동기화.
- 방장 위임 전체 플로우.
- 거래/정산 재계산.
- 참여자 표시명 중복 제한.
- 참여자 복구 API.

## 설계

### 권한 정책

- 참여자 추가, 수정, 제거, 회원 연결은 여행 owner만 가능하다.
- 참여자 목록과 상세 조회는 여행 owner 또는 해당 여행의 active 회원 참여자만 가능하다.
- 비활성 사용자 요청은 기존 사용자 정책에 맞춰 실패한다.

### 임시 참여자 추가

- 요청 값은 `displayName`, `profileImageUrl`이다.
- `displayName`은 trim 후 빈 값이면 실패한다.
- `profileImageUrl`은 trim 후 빈 값이면 null로 저장한다.
- 생성되는 참여자는 `user = null`, `MEMBER`, `ACTIVE` 상태다.
- 정산이 시작된 여행은 새 참여자를 받을 수 없도록 `TRIP_JOIN_CLOSED`로 실패한다.

### 참여자 조회

- 목록 조회는 `status`, `type` optional query parameter를 받는다.
- `status`는 `TripParticipantStatus` enum 이름을 대소문자 무관하게 받는다.
- `type`은 `USER`, `TEMPORARY`를 대소문자 무관하게 받는다.
- 잘못된 status/type 값은 각각 `INVALID_TRIP_PARTICIPANT_STATUS`, `INVALID_TRIP_PARTICIPANT_TYPE`으로 실패한다.
- 일반 목록 조회는 `deleted_at IS NULL` 참여자만 조회한다.
- `status`가 명시되면 native query로 삭제 시각이 기록된 참여자도 상태 기준 조회 대상에 포함한다.
- 응답은 기존 `TripParticipantSummaryResponse`를 재사용하고 `participantType`을 추가한다.

### 참여자 수정

- 현재 범위에서는 표시 정보만 수정한다.
- `displayName`과 `profileImageUrl`이 모두 null이면 `INVALID_INPUT`으로 실패한다.
- 회원 참여자는 표시 응답에서 `User` 프로필 값을 우선 사용하므로, participant row의 표시 정보 수정은 임시 참여자 중심으로 사용된다.

### 참여자 제거

- 방장 또는 `LEADER` 참여자는 제거할 수 없다.
- 제거는 hard delete가 아니라 `participantStatus = REMOVED`, `leftAt`, `deletedAt`을 함께 기록한다.
- `@SQLRestriction("deleted_at IS NULL")` 때문에 일반 조회에서는 제거된 참여자가 제외된다.
- 기존 거래/정산 row의 participant id 참조는 유지된다.

### 임시 참여자 회원 연결

- owner가 `participantId`, `userId`를 전달해 임시 참여자를 회원과 연결한다.
- 대상 사용자는 active 사용자여야 한다.
- 대상 사용자가 이미 같은 여행의 active 회원 참여자이면 `TRIP_ALREADY_JOINED`로 실패한다.
- 대상 participant가 active가 아니거나 같은 여행에 속하지 않으면 `TRIP_PARTICIPANT_NOT_FOUND`로 실패한다.
- 이미 회원과 연결된 participant는 `TRIP_PARTICIPANT_ALREADY_LINKED`로 실패한다.
- 연결 성공 시 기존 participant row에 `user`, 사용자 nickname/profile image, `joinedAt`을 기록한다.
- 연결 저장 시 `saveAndFlush`로 DB unique 제약 위반을 즉시 확인하고, 동시 요청으로 중복 active participant가 만들어질 수 있는 상황은 `TRIP_ALREADY_JOINED`로 변환한다.

## 구현 결과

- `TripParticipantController`가 모든 API를 `TripParticipantService`와 연결하고 `ApiResponse`를 반환한다.
- `TripParticipantApiSpec` 반환 타입을 실제 응답 타입으로 정리했다.
- `AddTripParticipantRequest`, `UpdateTripParticipantRequest`, `LinkTripParticipantRequest`를 실제 필드 기반 DTO로 변경했다.
- `TripParticipantType`을 추가하고 `TripParticipantSummaryResponse`에 포함했다.
- `TripParticipantService`에 참여자 추가/조회/상세/수정/제거/연결 로직을 구현했다.
- `TripParticipantRepository`에 trip 범위 participant 단건 조회 메서드를 추가했다.
- `TripParticipantRepository`에 상태 기준 삭제 포함 조회 메서드를 추가했다.
- `TripErrorCode`에 참여자 관리 전용 에러 코드를 추가했다.
- `TripParticipantServiceTest`를 추가해 핵심 정책을 검증했다.

## 변경 파일

- `src/main/kotlin/com/togethertrip/main/trip/controller/TripParticipantController.kt`
- `src/main/kotlin/com/togethertrip/main/trip/controller/spec/TripParticipantApiSpec.kt`
- `src/main/kotlin/com/togethertrip/main/trip/dto/request/AddTripParticipantRequest.kt`
- `src/main/kotlin/com/togethertrip/main/trip/dto/request/UpdateTripParticipantRequest.kt`
- `src/main/kotlin/com/togethertrip/main/trip/dto/request/LinkTripParticipantRequest.kt`
- `src/main/kotlin/com/togethertrip/main/trip/dto/response/TripParticipantSummaryResponse.kt`
- `src/main/kotlin/com/togethertrip/main/trip/dto/response/TripParticipantType.kt`
- `src/main/kotlin/com/togethertrip/main/trip/exception/TripErrorCode.kt`
- `src/main/kotlin/com/togethertrip/main/trip/repository/TripParticipantRepository.kt`
- `src/main/kotlin/com/togethertrip/main/trip/service/TripParticipantService.kt`
- `src/test/kotlin/com/togethertrip/main/trip/service/TripParticipantServiceTest.kt`

## 테스트 계획

- 방장의 임시 참여자 추가 성공을 검증한다.
- 방장이 아닌 사용자의 임시 참여자 추가 실패를 검증한다.
- 참여자 목록 status/type 필터를 검증한다.
- 참여자 표시 정보 수정을 검증한다.
- 참여자 제거 시 `REMOVED`, `leftAt`, `deletedAt` 변경을 검증한다.
- 방장 제거 제한을 검증한다.
- 임시 참여자와 회원 연결 성공을 검증한다.
- 이미 활성 참여자인 사용자의 연결 실패를 검증한다.
- 이미 회원과 연결된 참여자의 재연결 실패를 검증한다.
- 동시 연결 요청 등으로 DB 중복 제약이 발생할 때 도메인 에러로 변환되는지 검증한다.

## 검증 결과

```bash
./gradlew test --tests com.togethertrip.main.trip.service.TripParticipantServiceTest
./gradlew test
git diff --check
```

검증 결과:

- `./gradlew test --tests com.togethertrip.main.trip.service.TripParticipantServiceTest`: 통과
- `git diff --check`: 통과
- `./gradlew test`: 실패
  - 실패 테스트: `MainApplicationTests.contextLoads`
  - 원인: 로컬 `together_trip_test` DB의 Flyway migration version 7 checksum mismatch
  - 이번 변경에는 migration 추가가 없으며, 실패 원인은 기존 로컬 DB schema history 상태로 보인다.

## 위험과 확인 사항

- 참여자 제거는 `deleted_at`을 기록하므로 일반 JPA 조회에서는 제외된다. 제거된 참여자를 감사/관리 화면에서 다시 보여줘야 하면 별도 query가 필요하다.
- 회원 참여자는 응답 표시명과 프로필 이미지가 `User` 값을 우선 사용한다. participant row의 표시 정보 수정은 임시 참여자에 더 의미가 있다.
- 이번 연결 API는 owner가 특정 active user를 임시 참여자에 연결하는 정책이다. 사용자가 직접 임시 참여자를 claim하는 UX가 필요하면 별도 인증/검증 플로우가 필요하다.
- 방장 위임 기능은 제외했다. 방장 제거가 필요해지면 owner 이전 정책을 먼저 구현해야 한다.
