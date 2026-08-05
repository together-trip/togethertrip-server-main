# Work Plan: Flyway 도입 및 DB 스키마 관리 일원화

> 상태 정정(2026-08-05): 이 문서의 전화번호 인증과 탈퇴 계정 재활성화 설계는 후속 이슈
> #122, #125에서 폐기됐다. 현재는 전화번호 인증을 사용하지 않고, 계정 삭제 시 OAuthAccount를
> 제거하므로 동일 provider의 다음 로그인은 새 사용자 가입이다. 삭제되지 않은 연결이 탈퇴 사용자를
> 가리키면 `INACTIVE_USER`로 거부한다. 아래 내용은 Flyway 도입 당시의 역사적 계획으로만 남긴다.

작성일: 2026-06-04
대상 모듈: `main` (Spring Boot 4.0.6 / Kotlin / PostgreSQL)

## 작업

DB 스키마를 Flyway 마이그레이션으로 관리하도록 전환한다. 첫 적용 사례로 현재 엔티티와
`schema.sql`의 드리프트를 정리하고, 탈퇴 후 동일 카카오 계정 재가입 시 기존 계정을 재활성화하는
흐름을 정상화한다.

## 배경

현재 스키마 관리는 손으로 유지하는 `schema.sql` + `spring.sql.init.mode: always`(local) + Hibernate
`ddl-auto`(local=update, prod=validate) 혼합이다. 한계가 명확하다.

- `CREATE TABLE IF NOT EXISTS` 기반이라 기존 테이블의 컬럼/제약 변경을 안정적으로 표현하지 못한다.
- `sql.init.mode: always`는 local에만 설정돼 있어 prod에는 `schema.sql`이 적용되지 않을 가능성이 높다.
- 변경 이력 추적과 재현성이 없다.
- `User.email`, `OAuthAccount.email`은 코드에서 제거됐지만 `schema.sql`에는 아직 남아 있다.

재가입 정책은 다음으로 확정한다.

> 탈퇴 사용자가 동일 OAuth 계정으로 재가입하면 기존 `users` row를 재활성화한다. 단, 전화번호 소유 여부는
> 다시 확인하기 위해 전화번호 인증을 재요구한다.

따라서 `oauth_accounts`를 soft delete하거나 partial unique index로 신규 가입을 유도하지 않는다.
동일 카카오 계정은 기존 `oauth_accounts(provider, provider_user_id)` row를 통해 기존 user로 돌아와야 한다.

## 범위

1. Flyway 의존성 추가 (`flyway-core`, `flyway-database-postgresql`).
2. 마이그레이션 디렉토리(`src/main/resources/db/migration`) 및 베이스라인 도입.
   - `V1__baseline.sql` — 최신 엔티티 기준 스키마.
   - `V2__drop_legacy_email_columns.sql` — 기존 개발 DB 대응용 email 컬럼 제거.
3. 설정 전환: `spring.flyway` 활성화, `spring.sql.init.mode: never`, `ddl-auto`를 local도 `validate`로.
4. 기존 `schema.sql` 제거(내용은 V1으로 이관).
5. 탈퇴 계정 재활성화 코드 구현.

## 제외 범위

- Flyway/마이그레이션 및 탈퇴 계정 재활성화와 무관한 다른 테이블 스키마 리팩터링.
- 앱(Flutter) 측 변경.
- 탈퇴 계정의 여행/정산/게시글 관계 복구 정책 변경. 기존 userId를 유지하므로 기존 관계는 그대로 남긴다.

## 설계

### 1. 의존성 (`build.gradle.kts`)

Spring Boot 4 dependency management가 버전을 관리하므로 버전 명시 불필요.

```kotlin
implementation("org.springframework.boot:spring-boot-flyway")
implementation("org.flywaydb:flyway-core")
runtimeOnly("org.flywaydb:flyway-database-postgresql")
```

> Spring Boot 4에서는 Flyway auto-configuration을 위해 `spring-boot-flyway` 모듈이 필요하다.
> Flyway 10+부터 PostgreSQL 지원이 별도 모듈(`flyway-database-postgresql`)로 분리되어 있어 함께 추가한다.

### 2. 설정 (yml)

공통(`application.yml`) 또는 프로파일별로 둔다. 핵심:

```yaml
spring:
  flyway:
    enabled: true
    locations: classpath:db/migration
    baseline-on-migrate: true
    baseline-version: 1
  sql:
    init:
      mode: never
  jpa:
    hibernate:
      ddl-auto: validate
```

- `baseline-on-migrate: true`: 기존(비어있지 않은) DB는 V1을 baseline으로 표시하고 V2부터 적용한다.
  신규(빈) DB는 V1부터 전부 적용된다.
- `ddl-auto: validate`: Hibernate가 스키마를 변경하지 않고 엔티티-스키마 정합만 검증한다.

### 3. 마이그레이션 파일

```
src/main/resources/db/migration/
  V1__baseline.sql
  V2__drop_legacy_email_columns.sql
```

`V1__baseline.sql`:

- 현재 최신 엔티티 기준으로 작성한다.
- `users.email` 컬럼을 포함하지 않는다.
- `oauth_accounts.email` 컬럼을 포함하지 않는다.
- `oauth_accounts(provider, provider_user_id)` full unique 제약은 유지한다.

`V2__drop_legacy_email_columns.sql`:

```sql
ALTER TABLE users
    DROP COLUMN IF EXISTS email;

ALTER TABLE oauth_accounts
    DROP COLUMN IF EXISTS email;
```

개발 단계라 데이터가 없다면 신규 DB는 V1만으로 충분하다. 다만 기존 local DB를 유지한 채 전환할 수 있도록
V2를 함께 둔다.

### 4. 재활성화 정책

동일 OAuth 계정 재가입 시 신규 user를 만들지 않고 기존 user를 복구한다.

1. 카카오 로그인으로 `provider`, `providerUserId`를 얻는다.
2. `oauth_accounts(provider, provider_user_id)`로 기존 OAuthAccount를 조회한다.
3. 연결 user가 `WITHDRAWN` 또는 `deleted_at != null`이면 재활성화한다.
4. 재활성화 시 `status = ACTIVE`, `deleted_at = null`, `updated_at = now`.
5. 전화번호는 재인증을 요구하기 위해 `phone_number = null`, `phone_verified_at = null`로 초기화한다.
6. 응답은 `PHONE_VERIFICATION_REQUIRED`와 임시 토큰을 반환한다.
7. 전화번호 인증 완료 후 기존 user에 새 전화번호를 저장한다.
8. 프로필 완료 여부에 따라 `PROFILE_REQUIRED` 또는 `AUTHENTICATED`를 반환한다.

### 5. 여러 기기 동시 회원가입 정책

동일 OAuth 계정으로 여러 기기에서 회원가입을 동시에 진행할 수 있다. 임시 토큰은 기기별로 발급될 수 있으나,
전화번호 인증 완료와 사용자 생성/갱신은 OAuth 계정 단위로 한 번만 성공해야 한다.

1. A/B 기기가 같은 카카오 계정으로 로그인하면 각각 임시 토큰을 받을 수 있다.
2. 인증번호 확인 단계에서 `provider + providerUserId` 기준으로 짧은 Redis lock을 획득한다.
3. lock을 잡은 세션만 전화번호 인증 완료 후 사용자 생성/전화번호 갱신을 진행한다.
4. 트랜잭션 커밋이 끝난 뒤 lock을 해제해 다른 세션이 미완료 상태를 잘못 읽지 않게 한다.
5. 한 세션이 먼저 완료한 뒤 다른 세션이 인증번호 확인을 시도하면 `SIGNUP_ALREADY_COMPLETED`를 반환한다.
6. 사용자는 “다른 기기에서 회원가입이 완료되었습니다. 다시 로그인해주세요.” 안내를 받고 다시 카카오 로그인한다.
7. 동시에 확인 요청이 들어와 lock 획득에 실패하면 `SIGNUP_CONFIRMATION_IN_PROGRESS`를 반환하고 잠시 후 재시도를 유도한다.

### 6. 재가입 화면 정책

동일 OAuth 계정 재가입은 신규 회원가입이 아니라 기존 계정 재활성화로 본다.

1. 탈퇴 사용자가 동일 카카오 계정으로 로그인하면 기존 프로필은 유지한다.
2. 전화번호 소유 여부만 다시 확인하기 위해 전화번호 인증을 재요구한다.
3. 전화번호 인증 완료 후 기존 프로필이 완료되어 있으면 `AUTHENTICATED`를 반환해 메인으로 이동한다.
4. 기존 프로필이 미완료 상태였던 사용자만 `PROFILE_REQUIRED`를 반환해 프로필 입력 화면을 거친다.

### 7. 코드 측 구현

마이그레이션만으로는 재활성화가 완성되지 않는다. 다음 코드 변경을 함께 진행한다.

- `User`:
  - `reactivateForSignup()` 도메인 메서드 추가.
  - `status = ACTIVE`, `deletedAt = null`, `phoneNumber = null`, `phoneVerifiedAt = null`, `updatedAt = now`.
- `User` 조회 정책:
  - 재활성화가 필요하므로 `@SQLRestriction("deleted_at IS NULL")` 제거를 검토한다.
  - 일반 사용자 조회는 repository 메서드에서 `status = ACTIVE`, `deletedAt IS NULL` 조건을 명시한다.
  - 탈퇴 user 조회가 필요한 OAuth 재활성화 경로는 별도 repository query로 명확히 둔다.
- `OAuthAccount`:
  - full unique 제약을 유지한다.
  - `@SQLRestriction("deleted_at IS NULL")`를 추가하지 않는다.
  - 탈퇴 시 OAuthAccount를 unlink/soft delete하지 않는다.
- `AuthService.loginWithKakao()`:
  - 기존 OAuthAccount가 있고 user가 탈퇴 상태면 `reactivateForSignup()` 후 `PHONE_VERIFICATION_REQUIRED` 반환.
  - 기존 OAuthAccount가 있고 user가 ACTIVE면 기존 로그인 플로우 유지.
- `AuthService.confirmPhoneVerification()`:
  - 재활성화된 기존 user에도 전화번호 인증 결과를 저장할 수 있어야 한다.
  - 동일 OAuth 계정의 여러 임시 세션 중 첫 인증 완료만 성공하도록 OAuth 가입 lock과 완료 상태 재확인을 적용한다.
- `UserService.deleteMe()`:
  - OAuthAccount unlink 호출 없이 user만 탈퇴 처리한다.

## 작업 순서

1. 현재 실 스키마 스냅샷 확보:
   - local이 `ddl-auto: update`로 운영돼 `schema.sql`과 드리프트가 있을 수 있다.
   - `pg_dump --schema-only`로 실제 스키마를 확인하고, 최신 엔티티 기준 V1을 확정한다.
2. Flyway 의존성/설정 추가.
3. `db/migration/V1__baseline.sql`, `V2__drop_legacy_email_columns.sql` 작성.
4. `schema.sql` 제거.
5. 재활성화 코드 구현.
6. 동시 회원가입 세션 방어 로직 구현.
7. 로컬 검증.

## 테스트 계획

```bash
./gradlew test
```

부팅/마이그레이션 확인(로컬):

- 앱 기동 시 Flyway가 마이그레이션 적용, `flyway_schema_history` 테이블에 V1/V2 기록 확인.
- `ddl-auto: validate` 부팅 성공.
- 신규 빈 DB로 기동 → V1부터 전부 적용되어 동일 스키마 생성 확인.
- 기존 개발 DB로 기동 → V2가 `users.email`, `oauth_accounts.email` 제거 확인.
- 회귀: 탈퇴 → 동일 카카오 로그인 → 기존 userId 재활성화 → 전화번호 인증 재요구.
- 회귀: 전화번호 인증 완료 후 기존 userId로 `PROFILE_REQUIRED` 또는 `AUTHENTICATED` 반환.
- 회귀: 동일 OAuth 계정으로 여러 임시 토큰을 받은 뒤 한 세션이 인증 완료하면 다른 세션의 인증 완료는
  `SIGNUP_ALREADY_COMPLETED`로 실패.

## 위험과 확인 사항

- 스키마 드리프트:
  - V1은 반드시 최신 엔티티와 실제 스키마를 비교해 확정한다.
  - `schema.sql`에는 email 컬럼이 남아 있으므로 그대로 복사하면 안 된다.
- 기존 DB baseline:
  - `baseline-on-migrate: true`로 기존 DB는 V1을 건너뛰므로, V2가 기존 DB 보정 역할을 한다.
- `@SQLRestriction`:
  - 탈퇴 user 재활성화에는 soft-deleted row 조회가 필요하다.
  - 전역 `@SQLRestriction`은 편하지만 재활성화 경로를 막을 수 있으므로 제거 또는 명시 query 전환을 검토한다.
- OAuthAccount unique:
  - 재활성화 정책에서는 full unique가 오히려 올바른 제약이다.
  - partial unique index로 바꾸면 동일 카카오 계정이 새 user로 가입될 수 있어 정책과 어긋난다.
- 전화번호:
  - 재활성화 시 기존 전화번호 인증 상태는 신뢰하지 않고 재인증한다.
  - 기존 `existsByPhoneNumberAndDeletedAtIsNull` 정책은 탈퇴 user를 제외하므로 재인증 저장과 충돌하지 않아야 한다.
- prod 적용 경로:
  - 현재 prod의 스키마 배포 방식을 먼저 확인한다.
  - Flyway 도입 후 prod도 동일 마이그레이션이 기동 시 적용되도록 설정/배포 파이프라인을 맞춘다.

## 산출물/컨벤션

- 브랜치: `feature/issue-<N>-flyway-migration`
- 커밋:
  - `chore: Flyway 도입 및 스키마 관리 일원화`
  - `fix: 탈퇴 계정 재활성화 플로우 구현`
- PR: `develop` 대상, `Closes #<N>` 연결
