# Work Plan

## 작업

이슈 #45 `test: 동시 회원가입 요청 k6 부하 테스트 추가`를 진행한다.

목표는 성능 수치 개선이 아니라 `POST /api/auth/phone/confirm`의 동시성 정합성을 재현 가능하게 검증하는 것이다. 동일 OAuth 세션, 서로 다른 OAuth 세션의 동일 전화번호, 프로세스/Redis/DB 경계 장애 상황에서 중복 사용자·중복 OAuth 계정·중복 전화번호가 생성되지 않는지 확인한다.

## 배경

`AuthService.confirmPhoneVerification`은 Redis 임시 세션을 읽고 `OAuthSignupLock`으로 OAuth provider/providerUserId 단위 잠금을 잡은 뒤, 전화번호 인증 상태 삭제, 사용자 저장, OAuth 계정 저장, `flush`, 임시 세션 삭제를 수행한다.

정합성을 지키는 주요 장치는 다음과 같다.

- `RedisOAuthSignupLock`: `auth:oauth-signup-lock:{provider}:{providerUserId}` key에 `SET NX EX 10s` 방식으로 동일 OAuth 가입 진행을 제한한다.
- `oauth_accounts(provider, provider_user_id)` unique 제약: 동일 OAuth 계정 중복 저장을 막는다.
- `users(phone_number_hash)` partial unique index: 동일 전화번호 hash 중복 저장을 막는다.
- `AuthService.flushSignupState`: DB unique 충돌을 `PHONE_NUMBER_ALREADY_USED`로 변환하고 임시 세션을 삭제한다.

## 산출물 위치

이슈 본문은 `docs/verifications/k6/`를 지정하지만, 재사용 가능한 부하 테스트 자산은 repo의 기존 성능 테스트 구조와 맞춰 `performance/` 아래에 둔다.

따라서 산출물은 다음 위치에 둔다.

- k6 스크립트: `performance/k6/concurrent-signup.js`
- 실행 스크립트: `performance/k6/run-concurrent-signup.sh`
- DB 검증 SQL: `performance/seed/validate-concurrent-signup.sql`
- 실행 로그 원문: root `docs/k6-results/concurrent-signup/`

## 범위

- 로컬 테스트 프로파일에서 카카오 실 API와 SMS 실발송 없이 가입 전제 데이터를 만들 수 있는 방법을 확정한다.
- 동일 `temporaryToken`으로 `POST /api/auth/phone/confirm` 동시 요청을 보내는 k6 시나리오를 작성한다.
- 서로 다른 `temporaryToken`이 같은 전화번호를 confirm하는 k6 시나리오를 작성한다.
- 각 시나리오 실행 후 API 응답뿐 아니라 DB와 Redis 사후 상태를 검증한다.
- 수동 실행 절차, 사전 데이터 생성 절차, 기대 응답, 사후 확인 SQL/Redis 명령을 리포트에 남긴다.

## 제외 범위

- k6 결과의 성능 목표 설정.
- CI 자동화.
- 카카오 실 API 호출.
- SMS 실발송 검증.
- 운영 Redis/DB 대상 테스트.
- 정합성 결함 발견 시의 본격 수정. 단, 테스트 작성 중 드러난 명백한 테스트 지원 결함은 별도 이슈 또는 후속 작업으로 분리한다.

## 사전 확인

1. `auth.local-test.enabled=true` 환경에서 `POST /api/auth/oauth/kakao`에 `accessToken = "local-test:{id}"`를 보내 임시 세션을 만들 수 있는지 확인한다.
2. 전화번호 인증 코드 준비 방법을 확정한다.
   - 우선 순위 1: 로컬 테스트용 SMS sender가 코드 값을 로그 또는 테스트 저장소에서 확인 가능하면 그 값을 사용한다.
   - 우선 순위 2: 수동 부하 테스트 전용 프로파일에서 인증 코드를 고정하는 test-only 구성을 추가한다.
   - 우선 순위 3: 운영 코드에 영향 없는 test-only endpoint를 `local-test` 활성 시에만 노출해 인증 상태를 seed한다.
3. PostgreSQL unique index와 `oauth_accounts` unique constraint가 현재 마이그레이션으로 실제 생성되는지 확인한다.
4. Redis lock TTL 10초가 confirm 트랜잭션 시간보다 충분한지 관찰한다. 이 작업에서는 성능 목표가 아니라 TTL 만료로 lock이 풀리는 정합성 위험만 본다.

## k6 시나리오

### 시나리오 1: 동일 OAuth 세션 동시 confirm

- 준비: `POST /api/auth/oauth/kakao`로 단일 `temporaryToken`을 생성하고, 같은 token에 대한 전화번호 인증 상태를 준비한다.
- 실행: VU 20, 짧은 duration 또는 shared-iterations 20으로 동시에 `POST /api/auth/phone/confirm`.
- 기대:
  - 성공은 정확히 1건이다.
  - 실패는 `SIGNUP_CONFIRMATION_IN_PROGRESS`, `SIGNUP_ALREADY_COMPLETED`, `PHONE_VERIFICATION_TOKEN_EXPIRED`, 또는 인증 상태 선삭제로 인한 전화번호 인증 관련 오류 중 사전에 허용한 코드만 발생한다.
  - `oauth_accounts(provider, provider_user_id)`는 1건이다.
  - 해당 providerUserId에 연결된 `users`는 1건이다.
  - Redis 임시 세션과 전화번호 인증 key는 최종적으로 삭제되어야 한다.

### 시나리오 2: 서로 다른 OAuth 세션의 동일 전화번호 동시 confirm

- 준비: 서로 다른 `local-test:{id}`로 N개의 `temporaryToken`을 생성하고, 모두 같은 전화번호 인증 상태를 준비한다.
- 실행: VU 10, shared-iterations 10으로 동시 confirm.
- 기대:
  - 성공은 정확히 1건이다.
  - 나머지는 `PHONE_NUMBER_ALREADY_USED`가 기본 기대값이다.
  - `users.phone_number_hash` 동일 hash는 1건이다.
  - 실패한 OAuth 세션의 사용자/OAuth 계정이 반쯤 생성되어 남지 않아야 한다.
  - 실패한 임시 세션과 인증 상태의 삭제 정책은 코드 정책과 리포트에 명확히 기록한다.

## 장애 재현 계획

k6만으로는 “갑자기 꺼짐”을 결정적인 지점에 맞춰 재현하기 어렵다. 재현성을 확보하려면 요청 부하와 장애 주입을 분리한다.

### A. 프로세스 kill 기반 수동 재현

- k6 실행 중 Spring Boot 프로세스를 `SIGTERM` 또는 `SIGKILL`로 종료한다.
- 재시작 후 같은 시나리오를 다시 실행하거나 남은 임시 토큰으로 재시도한다.
- 확인:
  - DB에 중복 사용자/OAuth 계정/전화번호 hash가 없다.
  - Redis lock은 TTL 이후 사라진다.
  - DB commit 전 kill이면 사용자/OAuth 계정이 rollback되어야 한다.
  - DB commit 후 Redis 임시 세션 삭제 전 kill이면 재시도 시 `SIGNUP_ALREADY_COMPLETED` 또는 unique 충돌 기반 실패로 수렴해야 한다.

한계: kill 시점이 랜덤이라 커밋 직전/직후를 정확히 맞추기 어렵다. 리포트에는 반복 횟수와 관찰된 상태를 남긴다.

### B. 테스트 훅 기반 결정적 장애 주입

정합성 경계를 정확히 재현하려면 `local-test` 또는 테스트 프로파일에서만 동작하는 장애 주입 지점이 필요하다.

- `afterPhoneVerificationConfirmed`: 인증 상태 삭제 직후 예외 또는 대기.
- `afterUserAndOAuthSavedBeforeFlush`: 사용자/OAuth 저장 후 flush 전 예외 또는 대기.
- `afterFlushBeforeTemporarySessionDelete`: DB 반영 후 임시 세션 삭제 전 예외 또는 대기.
- `afterTemporarySessionDeleteBeforeResponse`: 임시 세션 삭제 후 응답 전 예외 또는 대기.

구현 방식은 운영 코드 오염을 줄이기 위해 `AuthSignupTestFaultInjector` 같은 no-op bean을 두고, `test` 또는 `local-test` profile에서만 활성화하는 방향을 우선 검토한다. 장애 주입 자체는 이슈 #45 범위를 넘을 수 있으므로, 최소한 계획과 한계는 리포트에 남긴다.

### C. Redis 장애 재현

- confirm 요청 중 Redis를 일시 중지하거나 재시작한다.
- 확인:
  - lock 획득 실패 또는 임시 세션 조회 실패가 중복 가입으로 이어지지 않는다.
  - Redis lock이 orphan 상태로 남아도 TTL 이후 재시도 가능해야 한다.

### D. DB unique 충돌 결정적 재현

- 시나리오 2에서 DB unique index를 최종 방어선으로 본다.
- 두 요청이 `validatePhoneNumberAvailable`에서 모두 false를 보고 진입해도, `flush` 시 하나만 성공하고 나머지는 `PHONE_NUMBER_ALREADY_USED`로 변환되어야 한다.
- 이 케이스는 k6 동시성만으로도 재현 가능성이 높지만, 실패 재현이 불안정하면 테스트 훅으로 flush 직전 대기를 걸어 동시에 통과시킨다.

## 리포트 기준

리포트에는 다음을 기록한다.

- 실행 일시, branch, commit SHA.
- 실행 환경: Spring profile, DB/Redis 위치, k6 버전.
- 시나리오별 VU/iteration/duration.
- 응답 코드와 비즈니스 에러 코드별 count.
- 성공 응답 count가 정확히 1인지.
- 사후 DB 조회 결과.
- 사후 Redis key 조회 결과.
- kill/Redis 재시작 등 장애 주입 시도와 관찰 결과.
- 재현하지 못한 장애와 이유.

## 검증 명령

- `./gradlew test`
- 로컬 서버 실행 후 k6:
  - `k6 run performance/k6/concurrent-signup.js -e SCENARIO=same-session`
  - `k6 run performance/k6/concurrent-signup.js -e SCENARIO=same-phone`
- DB 사후 검증 SQL:
  - 동일 OAuth provider/providerUserId의 `oauth_accounts` count.
  - 동일 `phone_number_hash`의 active `users` count.
- Redis 사후 검증:
  - `auth:oauth-temporary:*`
  - `auth:phone-verification:*`
  - `auth:oauth-signup-lock:*`

## 위험과 확인 사항

- 현재 `PhoneVerificationService.confirmCode`는 인증 성공 시 전화번호 인증 상태를 먼저 삭제한다. 동일 token 동시 confirm에서는 한 요청만 인증 상태를 가져가고 나머지는 인증 상태 없음/만료류 오류로 떨어질 수 있다. 이는 허용 실패 코드로 명시해야 한다.
- `RedisOAuthSignupLock`은 lock TTL이 10초다. confirm 처리 시간이 TTL을 넘으면 동일 OAuth 세션 중복 진입 위험이 생긴다. 성능 목표는 아니지만 TTL 만료 경계는 장애 주입으로 확인할 가치가 있다.
- `flushSignupState`는 `DataIntegrityViolationException`을 모두 `PHONE_NUMBER_ALREADY_USED`로 변환한다. 동일 OAuth unique 충돌이 발생하면 에러 코드가 전화번호 중복으로 보일 수 있어, 리포트에서 실제 DB 제약명까지 확인하는 것이 좋다.
- k6는 장애 시점을 세밀하게 제어하지 못한다. 갑작스러운 종료 재현은 가능하지만, 커밋 전후 같은 경계별 재현은 테스트 훅 또는 별도 통합 테스트가 필요하다.
- 이슈의 완료 기준은 k6 스크립트와 결과 리포트 커밋이지만, 정합성 관점에서는 DB/Redis 사후 상태 검증이 빠지면 불충분하다.
