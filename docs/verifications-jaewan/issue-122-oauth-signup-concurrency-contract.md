# Verification Report: 이슈 #122 OAuth 가입 동시성 계약

## 검증 범위

- 실제 Spring `AuthService.loginWithKakao` 동시 호출
- Redis `SET NX` signup lock 경합과 transaction 종료 후 해제
- 신규 User/OAuthAccount 단일 생성
- `PROFILE_REQUIRED`, access/refresh token과 token userId
- lock 충돌 응답의 해제 대기 후 재조회·재시도
- 재시도 뒤 User/OAuthAccount row count 유지

## 테스트 경계

- 외부 Kakao HTTP는 프로덕션 `KakaoOAuthClient`의 local-test stub 경로로 격리한다.
- AuthService, transaction interceptor, JWT, refresh token 저장은 실제 Spring bean을 사용한다.
- PostgreSQL과 Redis는 Testcontainers의 실제 인프라를 사용한다.
- 테스트 전용 PostgreSQL trigger는 해당 providerUserId의 신규 OAuthAccount insert만 0.5초 지연해
  Redis lock 보유 구간을 결정적으로 겹치게 하며 `finally`에서 제거한다.

## 실행 명령

```bash
./gradlew integrationTest \
  --tests 'com.togethertrip.main.auth.service.AuthServiceOAuthSignupConcurrencyIntegrationTest'
```

```bash
./gradlew check
```

```bash
git diff --check
```

## 결과

- 동일 Kakao 신규 로그인 12건의 첫 경합에서 성공과
  `SIGNUP_CONFIRMATION_IN_PROGRESS`만 반환했다.
- 성공 응답은 `PROFILE_REQUIRED`와 검증 가능한 access/refresh token을 포함했다.
- 모든 성공 token의 userId가 단일 OAuthAccount에 연결된 userId와 같았다.
- PostgreSQL의 대상 OAuthAccount와 연결 User가 각각 1행이었다.
- 실제 Redis lock key 해제를 기다린 뒤 충돌 건을 재시도했다.
- 재시도는 기존 OAuthAccount를 재조회해 같은 userId의 `PROFILE_REQUIRED` token을 반환했다.
- 모든 재시도 뒤에도 User/OAuthAccount는 각각 1행이고 마지막 refresh token이 Redis에 저장됐다.

## 계약 정정

- 현재 인증 provider는 `KAKAO`, `APPLE`뿐이며 Google OAuth는 구현되어 있지 않다.
- Google Play User Data 문서 링크는 스토어 정책 참고 자료이지 지원 provider 표기가 아니다.
- 계정 삭제는 OAuthAccount를 제거하므로 동일 provider 재로그인은 기존 user 재활성화가 아니라
  새 가입이다.
- 삭제되지 않은 OAuthAccount가 탈퇴 user를 가리키는 레거시 상태는 `INACTIVE_USER`다.
