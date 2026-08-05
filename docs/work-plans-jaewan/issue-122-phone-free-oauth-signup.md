# Issue 122 전화번호 없는 OAuth 가입 구현 계획

## 목표

- 카카오 OAuth만으로 신규 사용자를 원자적으로 생성하고 JWT를 발급한다.
- 프로필 닉네임 입력 전에는 `PROFILE_REQUIRED`, 입력 후에는 `AUTHENTICATED`를 반환한다.
- 전화번호 수집·인증·검색·저장과 Solapi 의존을 운영 코드와 데이터베이스에서 제거한다.

> 현재 지원 OAuth provider는 `KAKAO`, `APPLE` 두 가지다. 이슈 #122 구현 당시 범위는 카카오였고
> Apple은 후속 구현으로 추가됐다. Google OAuth는 구현되어 있지 않으며 Google Play 링크는 스토어
> 개인정보 정책 참고 자료일 뿐 인증 provider 계약이 아니다.

## 구현 범위

1. 인증 계약
   - 카카오 로그인 시 OAuth 계정 조회와 신규 User/OAuthAccount 생성을 하나의 가입 잠금 안에서 처리한다.
   - 신규 사용자는 빈 닉네임으로 생성해 프로필 입력 단계를 명시한다.
   - 기존 활성 사용자와 탈퇴 후 재가입 사용자의 상태를 새 응답 계약에 맞춘다.
   - 계정 삭제는 OAuthAccount 연결을 제거하므로 같은 provider의 다음 로그인은 새 사용자 가입이다.
     삭제되지 않은 연결이 탈퇴 사용자를 가리키는 레거시 상태는 `INACTIVE_USER`로 거부한다.
2. 전화번호 기능 제거
   - 인증 API, DTO, Redis 임시 세션, 인증번호 제한, SMS 클라이언트를 제거한다.
   - 사용자 전화번호 검색 API와 응답 필드를 제거한다.
   - 전화번호 암호화·해시·정규화 구현과 환경설정을 제거한다.
3. 데이터 파기
   - V22 migration에서 전화번호 인덱스와 일곱 개 개인정보 컬럼을 삭제한다.
   - 로컬·부하 테스트 시드와 개인정보처리방침을 새 구조로 변경한다.
4. 검증
   - 신규/기존/프로필 미완료/탈퇴/정지 OAuth 사용자를 단위 테스트한다.
   - 실제 `AuthService.loginWithKakao` 경합에서 Redis 가입 잠금, User/OAuthAccount 단일 생성,
     lock 해제 후 재조회·재시도를 PostgreSQL/Redis 통합 테스트로 검증한다.
   - Flyway 최종 스키마를 통합 테스트한다.
   - 전체 품질 게이트를 실행한다.

## 의사결정

- 운영 사용자가 아직 없으므로 호환 기간 없이 전화번호 기능과 컬럼을 즉시 제거한다.
- OAuth 제공 닉네임은 OAuthAccount 감사 정보로만 보존하고, User 닉네임은 빈 값으로 생성해 사용자가 직접 프로필을 완성하도록 한다.
- 사용자 작성 콘텐츠나 로그에 전화번호가 포함될 가능성은 남으므로 민감정보 로그 마스커는 유지한다.
- 과거 Flyway migration은 재현성을 위해 수정하지 않고 V22에서 파기한다.

## 검증 명령

```bash
./gradlew test
./gradlew integrationTest
./gradlew check
```
