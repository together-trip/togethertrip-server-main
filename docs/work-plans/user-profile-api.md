# Work Plan

## 작업

이슈 #28 `feat: 사용자 프로필 API 구현`을 진행한다.

구현 대상은 사용자 내 정보 조회/수정/탈퇴와 여행 내 내 참여자 정보 조회 API다.

## 배경

`main` 서버에는 사용자 엔티티, 인증 흐름, 사용자 Controller skeleton이 있으나 `UserService`와 사용자 API의 실제 비즈니스 구현이 비어 있다.

도메인 데이터 삭제는 soft delete 정책을 따른다. 회원 탈퇴는 레코드 물리 삭제가 아니라 사용자 상태 변경과 `deleted_at` 기록으로 처리한다.

## 범위

- `GET /api/users/me` 내 정보 조회를 구현한다.
- `PATCH /api/users/me` 닉네임과 프로필 이미지 수정을 구현한다.
- `DELETE /api/users/me` 회원 탈퇴를 soft delete로 구현한다.
- `GET /api/users/me/trip-participants?tripId=` 현재 사용자의 여행 참여자 정보 조회를 구현한다.
- 사용자 API용 `dto/response`를 추가하고 기존 `ApiResponse` 형식에 맞춘다.
- 비활성 사용자 접근 제한과 필요한 `ErrorCode`를 정리한다.
- 사용자 API 성공/실패 경로 테스트를 추가한다.

## 제외 범위

- OAuth 제공자 연결/해제 관리.
- 푸시 토큰 관리.
- 알림 서버 연동.
- 여행 참여자 전체 관리 API 구현.
- 회원 탈퇴 시 외부 OAuth 계정 해제.

## 설계

- Controller는 route mapping, 인증 주체 전달, `ApiResponse` 포장만 담당한다.
- Swagger/OpenAPI 문서 책임은 현재 구조처럼 `controller/spec`에 둔다.
- Request DTO는 `user/dto/request`, Response DTO는 `user/dto/response`에 둔다.
- `UpdateUserRequest`는 기존 placeholder를 제거하고 `nickname`, `profileImageUrl`만 받는다.
- `UserService`가 트랜잭션 경계와 비즈니스 규칙을 담당한다.
- 모든 사용자 API는 `userRepository.findByIdAndDeletedAtIsNull(...)`로 사용자를 조회한다.
- 사용자가 없으면 `USER_NOT_FOUND`, `status != ACTIVE`면 `INACTIVE_USER`를 반환한다.
- 회원 탈퇴는 `status = WITHDRAWN`, `deletedAt = now`, `updatedAt = now`로 처리한다.
- 여행 참여자 조회는 `tripId + userId + deletedAt is null` 조건으로 조회한다.
- 여행 참여자가 없으면 새 에러 코드 `TRIP_PARTICIPANT_NOT_FOUND`를 사용한다.
- `AuthService.loginExistingUser()`의 비활성 사용자 예외도 `BusinessException(ErrorCode.INACTIVE_USER)`로 정리한다.

응답 DTO 초안은 다음 기준으로 둔다.

- `UserResponse`: `id`, `email`, `nickname`, `gender`, `birthDate`, `profileImageUrl`, `phoneNumber`, `phoneVerifiedAt`, `role`, `status`
- `MyTripParticipantResponse`: `id`, `tripId`, `userId`, `displayName`, `profileImageUrl`, `participantRole`, `participantStatus`, `joinedAt`, `leftAt`

## 테스트 계획

- `UserService` 테스트를 추가한다.
- 내 정보 조회 성공을 검증한다.
- 없는 사용자 조회 시 `USER_NOT_FOUND`를 검증한다.
- `WITHDRAWN` 또는 `SUSPENDED` 사용자의 API 접근 시 `INACTIVE_USER`를 검증한다.
- 닉네임과 프로필 이미지 수정 성공을 검증한다.
- blank 닉네임 수정 요청 실패를 검증한다.
- 회원 탈퇴 시 `status = WITHDRAWN`, `deletedAt != null`을 검증한다.
- 내 여행 참여자 조회 성공을 검증한다.
- 다른 여행 또는 참여자 없음 상황에서 `TRIP_PARTICIPANT_NOT_FOUND`를 검증한다.
- 최종 검증 명령은 `./gradlew test`다.

## 위험과 확인 사항

- 현재 테스트 설정은 PostgreSQL과 Redis 로컬 인프라를 요구한다. 실행 환경에 따라 `./gradlew test`가 외부 의존성 때문에 실패할 수 있다.
- JWT 필터는 access token의 사용자 상태를 조회하지 않는다. 이슈 #28 범위에서는 사용자 API Service에서 활성 상태를 검증한다.
- soft delete 정책상 `findById()` 직접 사용은 삭제된 사용자까지 포함할 수 있으므로 사용자 API에서는 사용하지 않는다.
- Controller 반환 타입 변경 시 `UserApiSpec` 시그니처도 함께 맞춰야 한다.
