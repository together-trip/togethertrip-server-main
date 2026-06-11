# Work Plan

## 작업

이슈 #59 `feat: 사용자 초대 API 구현`을 진행한다.

구현 대상은 여행 초대 코드 생성, 초대 링크 생성, 초대 정보 조회, 초대 참여 API다.

- GitHub Issue: https://github.com/together-trip/togethertrip-server-main/issues/59
- 작업 브랜치: `feature/issue-59-user-invite-api`

## 배경

`trip` 패키지에는 `TripInviteController`, `TripInviteApiSpec`, `TripInviteService`, `TripInvitation` 엔티티 뼈대가 있었지만 실제 서비스 로직은 비어 있었다.

앱에서 초대 코드나 링크로 여행 참여 화면을 구성하고, 참여 버튼을 눌렀을 때 실제 `TripParticipant`가 생성되는 흐름이 필요하다.

초대는 사용자 입력과 공유 링크를 통해 외부로 노출되는 값이므로 코드/토큰 중복, 만료 상태, 사용 완료 상태, 이미 참여한 사용자 중복 참여, 동시 참여 요청을 함께 고려해야 한다.

## 범위

- `POST /api/trips/{tripId}/invite-codes` 초대 코드 생성을 구현한다.
- `POST /api/trips/{tripId}/invite-links` 초대 링크 생성을 구현한다.
- `GET /api/trip-invites` 초대 코드 또는 초대 토큰 기반 초대 정보 조회를 구현한다.
- `POST /api/trip-invite-joins` 초대 참여를 구현한다.
- `TripInviteController`와 `TripInviteApiSpec` 반환 타입을 실제 `ApiResponse<T>` 응답으로 정리한다.
- 초대 생성, 조회, 참여 응답 DTO를 추가한다.
- `JoinTripRequest`에 `code`, `token` 입력을 추가한다.
- `TripInvitation`에 `code`, `invitationType`을 추가한다.
- 초대 코드/토큰 조회 repository 메서드와 참여 처리용 pessimistic lock 조회를 추가한다.
- 초대 관련 에러 코드를 추가한다.
- Flyway migration으로 초대 코드/타입 컬럼과 정합성 제약을 추가한다.
- active 참여자 중복 생성을 막기 위한 partial unique index를 추가한다.
- `TripInviteServiceTest`로 주요 성공/실패 흐름을 검증한다.

## 제외 범위

- 앱 딥링크 처리.
- 알림 서버 연동.
- 초대 URL 운영 도메인 최종 확정.
- 다회용 초대 링크 사용 이력 테이블 분리.
- 임시 동행자 관리 API 전체 구현.
- 초대 취소 API.
- 초대 만료 기간을 요청별로 조정하는 기능.

## 설계

### 초대 정책

이번 구현은 1회용 초대 정책을 따른다.

현재 `TripInvitation` 엔티티는 `usedBy`, `usedAt`, `USED` 상태를 이미 가지고 있다. 이 모델에 맞춰 초대 참여가 성공하면 해당 초대를 `USED`로 전환한다.

다회용 링크가 필요해지면 `TripInvitation` 자체를 계속 활성 상태로 두고, 별도 `TripInvitationUse` 같은 사용 이력 테이블을 분리하는 편이 맞다. 이번 이슈에서는 범위를 키우지 않고 기존 모델과 가장 잘 맞는 1회용 정책으로 구현한다.

### 초대 생성

- 초대 생성은 여행 owner만 허용한다.
- 생성자 사용자는 `ACTIVE` 상태여야 한다.
- 초대 코드는 `ABCDEFGHJKLMNPQRSTUVWXYZ23456789` 문자셋에서 8자리로 생성한다.
- 초대 토큰은 `SecureRandom` 32 bytes를 URL-safe Base64 문자열로 인코딩한다.
- 초대 코드 생성 API도 내부 추적을 위해 token을 함께 생성한다.
- 기본 만료 시간은 생성 시점 기준 7일이다.
- 코드/토큰 충돌은 사전 exists 조회와 DB unique index로 이중 방어하고, 충돌 시 최대 5회 재시도한다.

### 초대 조회

- `code` 또는 `token` 중 정확히 하나만 허용한다.
- `code`는 trim 후 uppercase로 정규화한다.
- 초대가 없으면 `TRIP_INVITATION_NOT_FOUND`로 실패한다.
- 초대 상태가 `ACTIVE`가 아니면 `TRIP_INVITATION_NOT_ACTIVE`로 실패한다.
- 만료 시간이 지난 초대는 `TRIP_INVITATION_EXPIRED`로 실패한다.
- 응답에는 초대 정보, 여행 요약, 초대 생성자 요약, 현재 사용자의 이미 참여 여부를 포함한다.
- 조회 응답에는 링크 전용 비밀값인 token을 노출하지 않는다.

### 초대 참여

- 참여 요청도 `code` 또는 `token` 중 정확히 하나만 허용한다.
- 초대 row를 pessimistic write lock으로 조회한다.
- 초대 상태와 만료 시간을 검증한다.
- 만료된 초대는 별도 트랜잭션에서 `EXPIRED` 상태로 전환한 뒤 실패 응답을 반환한다.
- 정산이 시작된 여행은 새 참여자를 받지 않는다.
- 이미 해당 여행에 active participant로 참여 중이면 `TRIP_ALREADY_JOINED`로 실패한다.
- 참여 가능하면 현재 사용자 기준으로 `TripParticipant`를 생성한다.
- 참여자 role은 `MEMBER`, status는 `ACTIVE`, `joinedAt`은 현재 시각으로 저장한다.
- 저장 성공 후 초대는 `USED`로 전환하고 `usedBy`, `usedAt`을 기록한다.
- DB unique 충돌이 발생하면 중복 참여로 보고 `TRIP_ALREADY_JOINED`로 변환한다.

### DB 정합성

- `trip_invitations.code`를 추가한다.
- `trip_invitations.invitation_type`을 추가한다.
- `token`은 기존 unique index를 유지한다.
- `code`는 null이 아닐 때 unique index를 둔다.
- `invitation_type`은 `CODE`, `LINK`만 허용한다.
- `CODE` 타입은 `code IS NOT NULL`, `LINK` 타입은 `code IS NULL`이어야 한다.
- `trip_participants(trip_id, user_id)`는 active 상태에서 partial unique index를 둔다.

## 구현 결과

- `TripInviteController`가 네 API를 실제 서비스와 연결하고 `ApiResponse`로 반환한다.
- `TripInviteService`가 초대 생성, 조회, 참여 트랜잭션을 담당한다.
- `TripInvitationType` enum을 추가했다.
- `TripInviteResponse`, `TripInviteInfoResponse`, `JoinTripResponse`를 추가했다.
- `JoinTripRequest`를 `code`, `token` 기반 요청으로 변경했다.
- `TripInvitationRepository`에 code/token 조회, exists 조회, lock 조회를 추가했다.
- 만료 상태 전환을 롤백과 분리하기 위해 `TripInvitationExpirationService`를 추가했다.
- `TripParticipantRepository`에 active 참여자 존재 여부 조회를 추가했다.
- `TripErrorCode`에 초대 전용 에러 코드를 추가했다.
- `V8__add_trip_invitation_code_and_constraints.sql`로 컬럼과 index를 추가했다.
- `V9__add_trip_invitation_type_code_check.sql`로 타입-code 정합성 check constraint를 추가했다.
- `application-local.yml`, `application-prod.yml`에 `trip.invite.base-url` 설정을 추가했다.
- `TripInviteServiceTest`를 추가해 성공/실패 흐름을 검증했다.

## 변경 파일

- `src/main/kotlin/com/togethertrip/main/trip/controller/TripInviteController.kt`
- `src/main/kotlin/com/togethertrip/main/trip/controller/spec/TripInviteApiSpec.kt`
- `src/main/kotlin/com/togethertrip/main/trip/domain/TripInvitation.kt`
- `src/main/kotlin/com/togethertrip/main/trip/domain/TripInvitationType.kt`
- `src/main/kotlin/com/togethertrip/main/trip/dto/request/JoinTripRequest.kt`
- `src/main/kotlin/com/togethertrip/main/trip/dto/response/TripInviteResponse.kt`
- `src/main/kotlin/com/togethertrip/main/trip/dto/response/TripInviteInfoResponse.kt`
- `src/main/kotlin/com/togethertrip/main/trip/dto/response/JoinTripResponse.kt`
- `src/main/kotlin/com/togethertrip/main/trip/exception/TripErrorCode.kt`
- `src/main/kotlin/com/togethertrip/main/trip/repository/TripInvitationRepository.kt`
- `src/main/kotlin/com/togethertrip/main/trip/repository/TripParticipantRepository.kt`
- `src/main/kotlin/com/togethertrip/main/trip/service/TripInviteService.kt`
- `src/main/resources/db/migration/V8__add_trip_invitation_code_and_constraints.sql`
- `src/main/resources/db/migration/V9__add_trip_invitation_type_code_check.sql`
- `src/main/resources/application-local.yml`
- `src/main/resources/application-prod.yml`
- `src/test/kotlin/com/togethertrip/main/trip/service/TripInviteServiceTest.kt`

## 테스트 계획

- 초대 코드 생성 성공을 검증한다.
- 초대 링크 생성 권한 실패를 검증한다.
- 초대 정보 조회 시 이미 참여 여부가 포함되는지 검증한다.
- `code`, `token`이 모두 없거나 둘 다 있으면 실패하는지 검증한다.
- 초대 토큰으로 여행 참여가 성공하는지 검증한다.
- 이미 참여 중인 사용자의 중복 참여가 실패하는지 검증한다.
- 만료된 초대 참여가 실패하고 상태가 `EXPIRED`로 전환되는지 검증한다.
- 정산이 시작된 여행은 초대 참여에 실패하는지 검증한다.
- Spring context 로딩 시 Flyway migration과 JPA validate가 통과하는지 검증한다.

## 검증 결과

```bash
./gradlew clean test
./gradlew test
```

검증 결과:

- `./gradlew clean test`: 통과
- `./gradlew test`: 통과

## 위험과 확인 사항

- 이번 구현은 1회용 초대다. 앱에서 같은 링크로 여러 명이 들어와야 하는 정책이면 별도 사용 이력 테이블이 필요하다.
- 초대 생성 권한은 여행 owner 기준이다. 추후 `TripParticipantRole.LEADER`와 owner의 의미를 분리하면 권한 정책을 다시 봐야 한다.
- 초대 만료 시간은 현재 7일 고정이다. 운영 정책에 따라 설정값으로 분리할 수 있다.
- `trip.invite.base-url`은 환경변수 `TRIP_INVITE_BASE_URL`로 바꿀 수 있게 했다. 실제 앱 딥링크 도메인이 정해지면 운영 값만 교체하면 된다.
- 초대 정보 조회는 참여 전 화면 구성을 위해 여행 참여자가 아니어도 가능하다. 노출 필드는 여행 요약 수준으로 제한했다.
