# 가입 프로필 API 보강 검증 리포트

## 작업

이슈 #39 `feat: 가입 프로필 API 보강`.

## 검증 범위

- 닉네임 중복 체크 API 추가
- 성별, 생년월일 프로필 수정 저장
- OAuth/user email 의존 제거
- 중복 닉네임 차단
- 닉네임, 성별, 생년월일 검증
- Swagger/User API spec 갱신
- 전역 API 명세 갱신

## 실행 결과

```text
./gradlew test
BUILD SUCCESSFUL in 16s
```

## 확인 내용

- `GET /api/users/nicknames/availability?nickname=` endpoint가 추가됐다.
- 닉네임 중복 체크 응답은 `NicknameAvailabilityResponse(available)`로 반환된다.
- `PATCH /api/users/me`가 `nickname`, `gender`, `birthDate`, `profileImageUrl`을 받도록 확장됐다.
- `User.updateProfile()`이 `gender`, `birthDate`를 저장한다.
- 중복 닉네임은 `NICKNAME_ALREADY_USED`로 실패한다.
- `gender`는 `MALE`, `FEMALE`만 허용한다.
- `birthDate`는 미래 날짜를 허용하지 않는다.
- OAuth 사용자 식별은 email이 아니라 `provider + providerUserId`만 사용한다.
- `User`, `UserResponse`, `OAuthUserInfo`, `OAuthTemporarySession`, `OAuthAccount`에서 email 필드 의존을 제거했다.

## 남은 확인 사항

- 클라이언트는 성별을 `남자/여자`로 표시하므로 API 요청 시 `MALE/FEMALE` 매핑이 필요하다.
- 클라이언트 `PATCH /api/users/me` 요청에 `gender`, `birthDate`를 포함하도록 별도 반영이 필요하다.
