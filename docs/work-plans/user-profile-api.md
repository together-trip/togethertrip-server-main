# Work Plan

## 작업

이슈 #28 `feat: 사용자 프로필 API 구현`을 진행한다.

구현 대상은 사용자 내 정보 조회/수정/탈퇴, 여행 내 내 참여자 정보 조회, 가입 단계 전화번호 인증, 전화번호 기반 사용자 검색 API다.

## 배경

`main` 서버에는 사용자 엔티티, 인증 흐름, 사용자 Controller skeleton이 있으나 `UserService`와 사용자 API의 실제 비즈니스 구현이 비어 있다.

도메인 데이터 삭제는 soft delete 정책을 따른다. 회원 탈퇴는 레코드 물리 삭제가 아니라 사용자 상태 변경과 `deleted_at` 기록으로 처리한다.

## 범위

- `GET /api/users/me` 내 정보 조회를 구현한다.
- `PATCH /api/users/me` 닉네임과 프로필 이미지 수정을 구현한다.
- `DELETE /api/users/me` 회원 탈퇴를 soft delete로 구현한다.
- `GET /api/users/me/trip-participants?tripId=` 현재 사용자의 여행 참여자 정보 조회를 구현한다.
- 카카오 로그인 성공 후 전화번호 인증 전 단계에서는 서비스 회원 생성/토큰 발급 대신 임시 토큰을 발급한다.
- 전화번호 인증번호 발송/확인 API를 구현한다.
- `POST /api/users/search/phone` 전화번호 기반 가입 사용자 검색 API를 구현한다.
- 사용자 API용 `dto/response`를 추가하고 기존 `ApiResponse` 형식에 맞춘다.
- 비활성 사용자 접근 제한과 필요한 `ErrorCode`를 정리한다.
- 사용자 API 성공/실패 경로 테스트를 추가한다.

## 제외 범위

- OAuth 제공자 연결/해제 관리.
- 푸시 토큰 관리.
- 알림 서버 연동.
- 여행 참여자 전체 관리 API 구현.
- 회원 탈퇴 시 외부 OAuth 계정 해제.
- 연락처 동기화.
- 전화번호 인증 이력 DB 저장.
- 전화번호 원문 암호화/해시 저장.
- 탈퇴한 사용자의 전화번호 재사용 정책 확정.

## 설계

- Controller는 route mapping, 인증 주체 전달, `ApiResponse` 포장만 담당한다.
- Swagger/OpenAPI 문서 책임은 현재 구조처럼 `controller/spec`에 둔다.
- Request DTO는 `user/dto/request`, Response DTO는 `user/dto/response`에 둔다.
- `UpdateUserRequest`는 기존 placeholder를 제거하고 `nickname`, `profileImageUrl`만 받는다.
- `UserService`가 트랜잭션 경계와 비즈니스 규칙을 담당한다.
- `User`, `TripParticipant`에는 `@SQLRestriction("deleted_at IS NULL")`을 적용해 일반 조회에서 soft deleted row를 제외한다.
- 모든 사용자 API는 `userRepository.findByIdAndDeletedAtIsNull(...)`로 사용자를 조회한다.
- 사용자가 없으면 `USER_NOT_FOUND`, `status != ACTIVE`면 `INACTIVE_USER`를 반환한다.
- 회원 탈퇴는 `status = WITHDRAWN`, `deletedAt = now`, `updatedAt = now`로 처리한다.
- 여행 참여자 조회는 `tripId + userId + deletedAt is null` 조건으로 조회한다.
- 여행 참여자가 없으면 새 에러 코드 `TRIP_PARTICIPANT_NOT_FOUND`를 사용한다.
- `AuthService.loginExistingUser()`의 비활성 사용자 예외도 `BusinessException(ErrorCode.INACTIVE_USER)`로 정리한다.
- 전화번호 인증은 가입 필수 단계다. 카카오 로그인 성공 후 신규 사용자 또는 `phoneVerifiedAt == null` 기존 사용자는 `PHONE_VERIFICATION_REQUIRED` 응답과 임시 토큰을 받는다.
- 임시 토큰은 opaque token으로 발급하고, OAuth 사용자 정보는 Redis에 10분 TTL로 저장한다.
- 인증번호는 SMS로 발송하며 6자리 숫자, 3분 만료, 5회 실패 시 재발송 필요 정책을 적용한다.
- 같은 전화번호 인증번호 요청은 1분에 1회, 하루 5회로 제한한다.
- 전화번호는 한국 휴대폰 번호만 허용하고 `010...` 또는 `+8210...` 입력을 `+8210...` E.164 형태로 정규화한다.
- 인증 완료 시 `users.phone_number`, `users.phone_verified_at`을 저장한다.
- 인증 완료 시 신규 사용자는 사용자 생성과 OAuth 계정 연결 후 토큰을 발급하고, 기존 미인증 사용자는 전화번호를 갱신한 뒤 토큰을 발급한다.
- SMS 발송은 SOLAPI/CoolSMS REST API를 기준으로 `SmsSender` 인터페이스 뒤에 둔다.
- 전화번호 검색 API는 로그인과 전화번호 인증이 완료된 사용자만 호출할 수 있다.
- 전화번호 검색은 정확히 일치하는 `ACTIVE` + `phoneVerifiedAt != null` 사용자 1명만 대상으로 한다.
- 검색 결과 없음은 `200 OK`와 `found = false`, `user = null`로 응답한다.

응답 DTO 초안은 다음 기준으로 둔다.

- `UserResponse`: `id`, `email`, `nickname`, `gender`, `birthDate`, `profileImageUrl`, `phoneNumber`, `phoneVerifiedAt`, `role`, `status`
- `MyTripParticipantResponse`: `id`, `tripId`, `userId`, `displayName`, `profileImageUrl`, `participantRole`, `participantStatus`, `joinedAt`, `leftAt`
- `AuthResponse`: `status`, `temporaryToken`, `accessToken`, `refreshToken`
- `PhoneUserSearchResponse`: `found`, `user`

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
- 신규 카카오 로그인 시 `PHONE_VERIFICATION_REQUIRED`와 임시 토큰 발급을 검증한다.
- 임시 토큰 기반 인증번호 발송/확인 성공을 검증한다.
- 인증번호 만료, 불일치, 시도 횟수 초과, 재요청 제한, 일일 요청 제한을 검증한다.
- 전화번호 중복 가입 차단을 검증한다.
- 전화번호 검색 성공과 결과 없음 응답을 검증한다.
- 최종 검증 명령은 `./gradlew test`다.

## 위험과 확인 사항

- 현재 테스트 설정은 PostgreSQL과 Redis 로컬 인프라를 요구한다. 실행 환경에 따라 `./gradlew test`가 외부 의존성 때문에 실패할 수 있다.
- JWT 필터는 access token의 사용자 상태를 조회하지 않는다. 이슈 #28 범위에서는 사용자 API Service에서 활성 상태를 검증한다.
- soft delete 정책상 `findById()` 직접 사용은 삭제된 사용자까지 포함할 수 있으므로 사용자 API에서는 사용하지 않는다.
- Controller 반환 타입 변경 시 `UserApiSpec` 시그니처도 함께 맞춰야 한다.
- SOLAPI/CoolSMS 실발송은 운영 설정값이 필요하다. 설정값이 비어 있으면 SMS 발송 시 명확한 서버 설정 오류로 실패시킨다.
- 전화번호 중복 정책은 “하나의 전화번호는 하나의 사용자 계정에만 연결 가능”으로 적용한다. 탈퇴 사용자 번호 재사용 여부는 후속 확인 필요 항목으로 남긴다.
