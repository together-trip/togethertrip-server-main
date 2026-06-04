# Work Plan

## 작업

이슈 #39 `feat: 가입 프로필 API 보강`을 진행한다.

- GitHub Issue: https://github.com/together-trip/togethertrip-server-main/issues/39

## 배경

클라이언트 최초 가입 흐름은 `카카오 로그인 -> 프로필 설정 -> 메인 페이지`다.

현재 `main` 서버에는 전화번호 인증 기반 가입 플로우와 `PATCH /api/users/me` 프로필 수정 API가 구현되어 있지만, 가입 프로필 화면에서 필요한 API 계약에 공백이 있다.

1. 닉네임 중복 체크 API가 없다.
2. `users.gender`, `users.birth_date`, `UserResponse.gender`, `UserResponse.birthDate`는 존재하지만 `UpdateUserRequest`, `User.updateProfile()`, `UserService.updateMe()`에서 성별과 생년월일을 저장하지 않는다.

## 범위

- 닉네임 중복 체크 API를 추가한다.
- `PATCH /api/users/me`에서 성별과 생년월일 저장을 지원한다.
- 전화번호 인증 후 필수 프로필 입력을 완료하지 않은 사용자는 `PROFILE_REQUIRED` 상태로 프로필 입력 화면에 재진입할 수 있게 한다.
- `UpdateUserRequest`에 `gender`, `birthDate` 필드를 추가한다.
- `User.updateProfile()`이 성별과 생년월일을 갱신하도록 확장한다.
- `UserService.updateMe()` 검증 로직을 보강한다.
- `UserApiSpec` Swagger/OpenAPI 설명을 갱신한다.
- 전역 API 명세 `docs/api-spec.md`의 User API 설명을 갱신한다.
- 관련 테스트를 추가한다.

## 제외 범위

- 아이디/비밀번호 기반 회원가입.
- 프로필 이미지 업로드 파일 API.
- 생년월일 기반 나이 제한 정책.
- 성별 enum 테이블 또는 별도 코드 관리.
- 닉네임 예약어/금칙어 정책.
- 탈퇴 사용자의 닉네임 재사용 정책 변경.

## API 설계 초안

### 닉네임 중복 체크

닉네임 입력 중 클라이언트가 중복 여부를 확인할 수 있도록 별도 API를 둔다.

```http
GET /api/users/nickname-availability?nickname={nickname}
Authorization: Bearer {accessToken}
```

Query Parameters:

```text
nickname: String, required, 2~20자 또는 서버 정책 확정값
```

Response 200:

```json
{
  "success": true,
  "data": {
    "available": true
  },
  "message": null
}
```

Response DTO:

```kotlin
data class NicknameAvailabilityResponse(
    val available: Boolean,
)
```

### 내 프로필 수정

기존 API를 확장한다.

```http
PATCH /api/users/me
Authorization: Bearer {accessToken}
```

Request Body:

```json
{
  "nickname": "여행자",
  "gender": "MALE",
  "birthDate": "1990-01-01",
  "profileImageUrl": null
}
```

Request DTO:

```kotlin
data class UpdateUserRequest(
    val nickname: String? = null,
    val gender: String? = null,
    val birthDate: LocalDate? = null,
    val profileImageUrl: String? = null,
)
```

Response는 기존 `UserResponse`를 유지한다.

```json
{
  "success": true,
  "data": {
    "id": 1,
    "nickname": "여행자",
    "gender": "MALE",
    "birthDate": "1990-01-01",
    "profileImageUrl": null,
    "phoneNumber": "01012345678",
    "phoneVerifiedAt": "2026-06-04T00:00:00Z",
    "role": "USER",
    "status": "ACTIVE"
  },
  "message": null
}
```

## 정책 결정안

- 닉네임은 2~20자로 통일한다.
- 닉네임은 공백 문자열을 허용하지 않는다.
- 닉네임 중복은 `deleted_at IS NULL` 사용자 기준으로 검사한다.
- 필수 가입 프로필은 `nickname`, `gender`, `birthDate` 기준으로 완료 여부를 판단한다.
- 전화번호 인증 완료 후 앱을 종료하고 다시 카카오 로그인해도 필수 가입 프로필이 비어 있으면 `PROFILE_REQUIRED`를 반환한다.
- 본인의 기존 닉네임은 수정 요청과 중복 체크에서 사용 가능으로 본다.
- 성별은 우선 문자열로 저장하되 허용값을 `MALE`, `FEMALE`로 제한한다.
- 생년월일은 ISO date 형식 `yyyy-MM-dd`로 받는다.
- `birthDate`는 미래 날짜를 허용하지 않는다.
- `profileImageUrl`은 기존처럼 optional, max 500 정책을 유지한다.

## 구현 설계

- `UserRepository`에 닉네임 조회/존재 여부 메서드를 추가한다.
  - `existsByNicknameAndDeletedAtIsNull(nickname: String): Boolean`
  - 본인 제외가 필요한 경우 `existsByNicknameAndIdNotAndDeletedAtIsNull(...)`를 추가한다.
- `UserService`에 `checkNicknameAvailability(userId, nickname)`를 추가한다.
- `UserService.updateMe()`에서 닉네임 변경 시 중복을 검사한다.
- 중복 닉네임이면 새 에러 코드 `DUPLICATE_NICKNAME` 또는 `NICKNAME_ALREADY_USED`를 반환한다.
- `UserErrorCode`에 닉네임 중복 오류를 추가한다.
- `User.updateProfile()` 시그니처를 `nickname`, `gender`, `birthDate`, `profileImageUrl`로 확장한다.
- `UserApiSpec`에 닉네임 중복 체크 operation을 추가한다.
- Controller는 route mapping, 인증 주체 전달, `ApiResponse` 포장만 담당한다.

## 테스트 계획

- `UserService` 단위 테스트를 추가/갱신한다.
- 닉네임 중복 체크 성공: 사용 가능한 닉네임은 `available = true`.
- 닉네임 중복 체크 성공: 이미 사용 중인 닉네임은 `available = false`.
- 본인의 현재 닉네임은 사용 가능으로 처리한다.
- 프로필 수정 성공: 닉네임, 성별, 생년월일이 저장된다.
- 프로필 수정 성공: `GET /api/users/me` 응답에 성별, 생년월일이 반영된다.
- 프로필 수정 실패: 중복 닉네임이면 비즈니스 예외를 반환한다.
- 프로필 수정 실패: blank 닉네임이면 `INVALID_INPUT`.
- 프로필 수정 실패: 허용되지 않은 성별이면 `INVALID_INPUT`.
- 프로필 수정 실패: 미래 생년월일이면 `INVALID_INPUT`.
- 최종 검증 명령은 `./gradlew test`다.

## 문서 갱신

- `main/src/main/kotlin/com/togethertrip/main/user/controller/spec/UserApiSpec.kt`
- 루트 `docs/api-spec.md`
- 필요 시 `docs/kakao-oauth-flow.md`의 최초 가입 흐름 설명

## 위험과 확인 사항

- 현재 클라이언트 와이어프레임은 성별을 `남자/여자`로 표현한다. API에는 `MALE/FEMALE`로 보낼지, 서버가 한국어 입력을 받아 정규화할지 결정이 필요하다.
- 기존 `UpdateUserRequest.nickname` max 50과 와이어프레임/요구사항의 2~20자 정책이 다르다. 이번 작업에서 2~20자로 통일할지 확인한다.
- 닉네임 중복 체크 API가 인증 필요 API인지, 가입 완료 전 임시 토큰 단계에서도 호출되어야 하는지 확인이 필요하다.
- 현재 가입 플로우는 전화번호 인증 후 토큰을 발급하고 `PATCH /api/users/me`를 호출하는 구조다. 닉네임 중복 체크가 전화번호 인증 전에도 필요하면 임시 토큰 기반 endpoint를 별도로 검토한다.
