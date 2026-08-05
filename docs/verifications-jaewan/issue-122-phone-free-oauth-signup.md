# Issue 122 전화번호 없는 OAuth 가입 검증

## 검증 결과

| 항목 | 결과 |
|---|---|
| 프로덕션 및 테스트 코드 컴파일 | 통과 |
| 단위 테스트 `./gradlew test` | 통과 |
| 통합 테스트 `./gradlew integrationTest` | 통과 (68개, 1개 기존 조건부 테스트 skip) |
| 전체 품질 게이트 `./gradlew check` | 통과 |
| 변경 대상 mutation score / line coverage | 83% / 92% |
| Redis 동일 OAuth 가입 잠금 동시성 | 통과 |
| 실제 Kakao 로그인 동시 User/OAuthAccount 생성 | 각 1행, lock 해제 후 재시도 통과 |
| 빈 PostGIS 전체 Flyway migration | V22까지 통과 |
| users 전화번호 컬럼 7개 부재 확인 | 통과 |
| 전화번호 unique 인덱스 부재 확인 | 통과 |

## 계약 검증

- 신규 카카오 사용자: User/OAuthAccount 즉시 생성, access/refresh token 발급, `PROFILE_REQUIRED`
- 프로필 완료 기존 사용자: `AUTHENTICATED`
- 프로필 미완료 기존 사용자: `PROFILE_REQUIRED`
- 계정 삭제 후 동일 OAuth 로그인: OAuthAccount 연결이 제거되어 새 User/OAuthAccount로 가입
- 삭제되지 않은 OAuth 연결이 탈퇴 사용자를 가리키는 레거시 상태: `INACTIVE_USER`
- 정지 사용자: `INACTIVE_USER`
- 허용되지 않은 OAuth 프로필 이미지 URL: 저장하지 않음
- 현재 OAuth provider enum: `KAKAO`, `APPLE`. Google OAuth는 구현되지 않음

## 잔존 처리

- 전화번호 API·DTO·서비스·저장 컬럼·환경변수는 제거했다.
- `SensitiveDataMasker`의 전화번호 패턴은 사용자 작성 데이터가 로그에 유입될 때를 대비한 방어 로직으로 유지했다.

## 최종 품질 게이트

- `./gradlew check`: 통과
- PIT 결과: 115개 mutation 중 95개 kill, mutation score 83%, test strength 91%

## 동시성 자동 검증 보강

- 프로덕션 Kakao client의 local-test stub 경로로 외부 HTTP만 격리했다.
- Spring `AuthService` transaction, PostgreSQL, Redis signup lock은 실제 Testcontainers 인프라를 사용했다.
- 동일 Kakao 신규 로그인 12건을 동시에 시작해 성공 응답은 `PROFILE_REQUIRED`와 유효한
  access/refresh token을 반환하고, 경합 요청은 `SIGNUP_CONFIRMATION_IN_PROGRESS`임을 확인했다.
- lock 해제를 기다린 뒤 경합 요청을 재시도하면 기존 OAuthAccount를 재조회해 같은 userId token을
  반환하며 User/OAuthAccount row count가 계속 1임을 확인했다.
