# Work Plan

## 작업

이슈 #61 `인증 및 회원 프로필 흐름 안정화`를 진행한다.

- GitHub Issue: https://github.com/together-trip/togethertrip-server-main/issues/61
- 작업 브랜치: `feature/issue-61-auth-user-stabilization`

## 배경

회원가입, 인증, 회원 프로필 흐름에서 동시 가입 정합성, 전화번호 보관 정책, 토큰 갱신, 입력 검증을 보강한다.

전화번호는 가입 본인 확인, 중복 가입 방지, 재인증 확인, 알림톡/서비스성 SMS 발송에 사용한다.

## 확정 결정

- 전화번호 원문은 DB에 저장하지 않는다.
- 알림톡/서비스성 SMS 발송을 위해 정규화 전화번호를 `AES-256-GCM` 암호문으로 저장한다.
- 동일성 비교, 중복 방지, 검색, rate limit은 정규화 전화번호를 `HMAC-SHA256(PHONE_HASH_KEY, normalizedPhone)`으로 계산한 `phone_number_hash`를 사용한다.
- 화면/운영 확인에 원문 복호화를 남용하지 않도록 `phone_number_masked`를 함께 저장한다.
- `BCrypt`/`Argon2`는 사용하지 않는다. salt 때문에 같은 전화번호의 DB 동등성 비교와 unique index를 만들 수 없기 때문이다.
- 서버는 원문 전화번호를 API 응답, 로그, 알림 발송 이력에 저장하지 않는다.
- 저장된 전화번호 복호화는 알림톡/서비스성 SMS 발송처럼 허용된 내부 유스케이스로 제한한다.
- 재인증/복구가 필요하면 사용자가 전화번호를 다시 입력하고 서버는 hash 비교로 확인한다.
- 최종 API 응답에서 원문 `phoneNumber`는 제공하지 않는다. 필요 시 `phoneNumberMasked`, `phoneVerified` 또는 `phoneVerifiedAt` 중심으로 제공한다.
- Redis key/value에도 전화번호 원문을 저장하지 않는다. SMS 발송은 요청값으로 즉시 수행하고, 인증 상태에는 `phoneNumberHash`만 저장한다.
- 기존 데이터에 전화번호 중복은 없다는 전제로 진행한다.
- HMAC key rotation은 이번 이슈에서 구현하지 않는다. 키 유출/교체가 필요하면 별도 이슈에서 병행 hash 전환을 설계한다.
- 전화번호 암호화 key rotation은 이번 이슈에서 구현하지 않는다. `phone_number_encryption_version`으로 추후 병행 복호화/재암호화를 준비한다.
- refresh token 저장은 가입 DB 트랜잭션 commit 이후에 수행한다.
- SMS 인증 성공 후 DB 가입 저장이 실패하면 인증 코드/세션은 소비된 것으로 보고 삭제한다.
- 신규 사용자의 기본 프로필 이미지는 카카오 OAuth에서 받은 본인 프로필 이미지 URL을 사용한다.
- 가입 프로필 입력 및 내 정보 수정에서는 `multipart/form-data`의 `profileImage` 파일 업로드를 허용하고, 저장된 public URL을 `users.profile_image_url`에 반영한다.
- 사용자/동행자 프로필 이미지 URL은 공통 allowlist 정책을 적용한다. 내부 업로드 URL은 단일 파일명 형식의 `/uploads/user-profile-images/*.{jpg,jpeg,png}`만 허용하고, 외부 URL은 신뢰한 Kakao CDN `https`만 허용한다.
- 프로필 이미지 파일 저장 후 DB 트랜잭션이 rollback되면 저장 파일을 삭제해 orphan 파일을 줄인다.

## 범위

- `users.phone_number_encrypted`, `users.phone_number_encryption_version`, `users.phone_number_masked`, `users.phone_number_hash`, `users.phone_number_hash_version` 추가.
- 전화번호 저장은 암호문+hash+masked 병행 저장으로 전환한다.
- 전화번호 조회/중복 체크/rate limit은 hash 기준으로 전환.
- 인증 완료 활성 사용자 전화번호 hash 부분 유니크 인덱스 추가.
- 전화번호 인증 Redis 상태를 `phoneNumberHash`와 원자적 attempt count 구조로 변경.
- refresh token rotation 적용.
- JWT claim 파싱 실패를 인증 실패로 처리.
- 프로필 이미지 URL 검증 보강.
- 프로필 이미지 파일 업로드 저장 경로와 정적 리소스 공개 경로 추가.
- `UserResponse.phoneNumber` 원문 제거 또는 미제공. 필요 시 `phoneNumberMasked`만 제공.
- local sample data와 local-test 인증을 암호문+hash+masked 저장 경로로 전환.
- 관련 테스트 추가.

## 제외 범위

- 알림톡 provider 연동 구현.
- 알림톡 템플릿/발송 이력/재시도 정책 설계.
- 전화번호 기반 사용자 검색 API 변경 또는 신규 활용 설계.
- 닉네임 기반 초대 후보 검색 설계/구현.
- 여행 생성/초대 API 계약 변경.
- 카카오 OAuth 외 provider 추가.
- 기기별 다중 세션 관리.
- refresh token family/reuse detection.
- HMAC key rotation 구현.
- 전화번호 암호화 key rotation 구현.
- 기존 `phone_number` 컬럼 drop. drop은 전환 완료 후 별도 이슈로 처리한다.

## 동시 가입 정합성

최종 불변식:

- 하나의 OAuth 계정은 최대 하나의 활성 사용자에만 연결된다.
- 하나의 인증 완료 `phone_number_hash`는 최대 하나의 활성 사용자에만 연결된다.
- 탈퇴 사용자는 재가입할 수 있지만 같은 전화번호 hash를 가진 다른 활성 사용자와 공존할 수 없다.
- 한 임시 세션의 전화번호 인증 확인은 최대 한 번만 성공한다.
- 가입 트랜잭션이 실패하면 refresh token은 Redis에 저장되지 않는다.

케이스별 기대 동작:

| 케이스 | 기대 동작 | 방어선 |
| --- | --- | --- |
| 같은 OAuth 계정 + 같은 임시 토큰 confirm 중복 | 하나만 성공 | OAuth signup lock, 인증 상태 삭제 |
| 같은 OAuth 계정 + 서로 다른 임시 토큰 confirm | 하나만 성공, 나머지는 이미 완료 오류 | OAuth signup lock, OAuth unique |
| 가입 완료 후 stale 임시 토큰 confirm | 실패 후 재로그인 유도 | `rejectIfSignupAlreadyCompleted` |
| 다른 OAuth 계정 + 같은 전화번호 confirm | 하나만 성공 | `phone_number_hash` partial unique index |
| 탈퇴 계정 재가입 + 신규 계정 같은 전화번호 | 하나만 성공 | `phone_number_hash` partial unique index |
| 같은 탈퇴 OAuth 계정 두 기기 재가입 | 하나만 성공 | OAuth signup lock, user row lock |
| 기존 미인증 OAuth 사용자 + 신규 OAuth 사용자 같은 전화번호 | 하나만 성공 | user row lock, phone hash unique |
| DB unique 위반 | 토큰 저장 없음, 인증 코드/세션 삭제 | afterCommit token 저장, 예외 변환 |

구현 원칙:

- OAuth 계정 단위 충돌은 `OAuthSignupLock`과 `oauth_accounts(provider, provider_user_id)` unique 제약으로 막는다.
- 기존 사용자 재가입/미인증 완료는 user row pessimistic lock을 사용한다.
- 전화번호 단위 충돌은 애플리케이션 `exists` 조회가 아니라 DB unique index를 최종 방어선으로 둔다.
- DB unique 위반은 `PHONE_NUMBER_ALREADY_USED` 또는 `SIGNUP_ALREADY_COMPLETED` 같은 비즈니스 오류로 변환한다.

## 마이그레이션

V10은 전화번호 암호문, 마스킹 값, hash 컬럼과 인증 완료 전화번호 hash unique index를 함께 추가한다. 기존 데이터의 전화번호 중복은 없다는 결정에 따라 이번 이슈에서 unique index까지 적용한다.

```sql
ALTER TABLE users
    ADD COLUMN IF NOT EXISTS phone_number_encrypted TEXT,
    ADD COLUMN IF NOT EXISTS phone_number_encryption_version VARCHAR(30),
    ADD COLUMN IF NOT EXISTS phone_number_masked VARCHAR(30),
    ADD COLUMN IF NOT EXISTS phone_number_hash VARCHAR(64),
    ADD COLUMN IF NOT EXISTS phone_number_hash_version VARCHAR(30);

CREATE UNIQUE INDEX IF NOT EXISTS uk_users_verified_phone_hash
    ON users (phone_number_hash)
    WHERE phone_number_hash IS NOT NULL
      AND phone_verified_at IS NOT NULL
      AND deleted_at IS NULL;
```

구현 상태:

1. 신규 코드는 legacy `phone_number`를 dual write하지 않는다. 인증 완료 시 `phone_number`는 `null`로 유지하고 `phone_number_encrypted`, `phone_number_encryption_version`, `phone_number_masked`, `phone_number_hash`, `phone_number_hash_version`를 저장한다.
2. repository 조회/중복 체크/rate limit은 `phone_number_hash` 기준으로 전환한다.
3. `UserResponse.phoneNumber`와 인증번호 요청 응답의 원문 `phoneNumber`는 제공하지 않는다. 표시가 필요하면 `phoneNumberMasked`만 제공한다.
4. local sample data와 local-test 인증은 암호문+hash+masked 저장 경로를 사용한다.
5. 기존 `phone_number` 컬럼 drop은 별도 이슈로 처리한다.

## 전화번호 암호화와 알림톡 사용

- 암호화 대상은 정규화된 E.164 전화번호다. 예: `+821012345678`.
- 암호화 알고리즘은 `AES-256-GCM`을 사용한다. 랜덤 IV를 매번 생성하고, 저장값에는 key version, IV, ciphertext, auth tag를 복호화 가능한 형태로 포함한다.
- 동일 번호 비교는 암호문으로 하지 않는다. 같은 번호도 IV 때문에 암호문이 달라질 수 있으므로 `phone_number_hash`를 사용한다.
- `phone_number_masked`는 `010-****-1234` 형식으로 저장한다. 응답/운영 화면에는 원문 대신 마스킹 값을 사용한다.
- 알림톡 발송 대상은 `userId`, 여행 참여자, 정산 참여자 같은 도메인 관계로 결정한다. `phone_number_hash`는 대상 선정이 아니라 중복/검색/제한에만 사용한다.
- 알림톡 발송 직전에만 `phone_number_encrypted`를 복호화해 provider 요청에 전달한다.
- 알림 발송 이력에는 원문 전화번호를 저장하지 않고 `userId`, `phone_number_hash`, `phone_number_masked`, provider message id, 발송 상태만 저장한다.
- 마케팅성 카카오톡 메시지는 마케팅 수신 동의와 채널별 수신 동의가 있을 때만 발송한다. 여행 초대, 정산 요청, 계정/보안 안내 같은 서비스성 알림과 분리한다.

## Redis 인증 상태

Redis에는 전화번호 원문을 저장하지 않는다.

- 인증번호 요청:
  - request의 전화번호를 정규화한다.
  - SMS 발송은 request 값으로 즉시 수행한다.
  - Redis에는 `phoneNumberHash`, `code`, `expiresAt`만 저장한다.
- 인증번호 확인:
  - request의 전화번호를 정규화 후 hash 한다.
  - Redis의 `phoneNumberHash`와 비교한다.
- attempt count:
  - JSON read-modify-write 대신 Redis `INCR` 기반 별도 key로 관리한다.
  - 성공 또는 초과 시 인증 상태 key와 attempt key를 모두 삭제한다.
- Redis key에는 전화번호 원문을 포함하지 않는다.
- 전화번호 기준 rate limit key도 HMAC hash 기반으로 생성한다.

## Refresh Token

- refresh 성공 시 access token과 refresh token을 모두 새로 발급한다.
- 새 refresh token은 기존 Redis 값을 교체한다.
- 기존 refresh token 재사용은 실패한다.
- 가입 완료 시 refresh token 저장은 DB transaction commit 이후 수행한다.
- 사용자당 refresh token 1개 정책은 유지한다.

## JWT와 입력 검증

- `JwtTokenProvider.validateToken()`은 claim 구조까지 검증한다.
- `getClaims()` 변환 실패는 인증 실패로 정리한다.
- access token 필터 경로와 refresh API 경로 모두 500으로 번지지 않게 한다.
- `profileImageUrl`은 서버 업로드 경로 또는 신뢰한 카카오 CDN `https` URL만 허용한다.
- `profileImageUrl` 빈 문자열, host 없는 URL, `javascript:`, `data:`, 신뢰하지 않는 외부 도메인은 `INVALID_INPUT`으로 처리한다.
- 여행 생성 시 입력되는 동행자 `profileImageUrl`도 같은 공통 URL 정책을 적용한다.
- `PATCH /api/users/me`는 기존 `application/json` 요청을 유지하고, `multipart/form-data` 요청에서는 `profileImage` 파일을 저장한 뒤 `profileImageUrl`로 반영한다.
- 프로필 이미지 파일은 요청 `Content-Type`이나 원본 확장자를 신뢰하지 않고, 공통 파일 시그니처 검증으로 JPEG/PNG만 허용한다.
- 게시글 첨부도 같은 공통 파일 시그니처 검증을 사용해 JPEG/PNG/MP4만 허용한다.
- 사용자 입력 `profileImageUrl`은 서버 업로드 경로 또는 신뢰한 카카오 CDN `https` URL만 허용한다. `javascript:`, `data:`, 신뢰하지 않는 외부 도메인은 저장하지 않는다.

## 테스트 계획

- 동시 가입
  - 같은 OAuth 계정의 같은 임시 토큰 confirm 중복은 하나만 성공.
  - 같은 OAuth 계정의 서로 다른 임시 토큰 confirm은 하나만 성공.
  - stale 임시 토큰 confirm은 실패.
  - 다른 OAuth 계정 두 개가 같은 전화번호로 완료하려 할 때 하나만 성공.
  - 탈퇴 재가입과 신규 가입이 같은 전화번호로 완료하려 할 때 하나만 성공.
  - DB unique 위반 시 refresh token이 Redis에 저장되지 않음.
  - DB unique 위반 시 인증 코드/세션은 삭제됨.
- 전화번호 hash
  - 같은 전화번호의 HMAC hash는 같다.
  - 서로 다른 전화번호의 HMAC hash는 다르다.
  - 단순 SHA-256, BCrypt, Argon2를 사용하지 않는다.
  - hash version이 저장된다.
- 전화번호 암호화
  - 같은 전화번호를 두 번 암호화해도 암호문은 다르다.
  - 암호문을 복호화하면 정규화 전화번호가 복원된다.
  - encryption version과 masked 값이 저장된다.
  - 복호화 실패는 원문 노출 없이 비즈니스 오류 또는 발송 실패로 처리한다.
- Redis 인증
  - Redis value에 `phoneNumberHash`만 저장되고 원문은 없다.
  - Redis key에 전화번호 원문이 없다.
  - attempt count는 동시 실패 요청에서도 유실되지 않는다.
- 응답
  - `UserResponse`에서 원문 `phoneNumber`를 제공하지 않는다.
  - 표시가 필요한 응답은 `phoneNumberMasked`만 제공한다.
  - 인증 상태는 `phoneVerified` 또는 `phoneVerifiedAt`로 확인한다.
- 프로필 이미지
  - 신규 사용자는 카카오 OAuth 프로필 이미지 URL을 기본값으로 가진다.
  - multipart 프로필 이미지 업로드 시 저장소가 반환한 URL이 사용자 `profileImageUrl`에 반영된다.
  - 프로필 이미지는 JPEG/PNG 파일 시그니처만 허용하고, SVG나 위장 파일은 실패한다.
  - 게시글 첨부는 JPEG/PNG/MP4 파일 시그니처만 허용하고, MIME/확장자 위장 파일은 실패한다.
  - `profileImageUrl`은 내부 업로드 경로 또는 카카오 CDN allowlist만 허용한다.
  - 내부 업로드 `profileImageUrl`은 단일 파일명만 허용하고 경로 traversal, query string, SVG 확장자는 거부한다.
  - 여행 생성 동행자 `profileImageUrl`도 allowlist 밖이면 실패한다.
- refresh token
  - refresh 성공 시 refresh token이 rotation된다.
  - 이전 refresh token 재사용은 실패한다.
  - 같은 refresh token 동시 갱신은 하나만 성공한다.
- JWT
  - subject, role, tokenType 비정상 claim은 인증 실패로 처리된다.
- 마이그레이션
  - V10 암호문/hash/masked 컬럼과 unique index 추가 확인.
  - 같은 전화번호 hash 중복을 unique index가 거부하는지 확인.
  - local sample data와 local-test 인증이 암호문+hash+masked 저장 경로를 탄다.

## 남은 주의점

- 테스트 DB는 장기적으로 매 실행마다 clean 후 migration하는 구조로 전환했다. clean 전략은 테스트 소스에만 두어 운영 artifact에는 포함하지 않는다.
- 구버전/신버전 앱이 섞이는 배포 구간에서는 구버전이 `phone_number` 원문을 다시 쓰지 않도록 배포 순서를 관리한다.
- HMAC 키 유출 시 기존 hash를 새 키로 재계산하는 별도 작업이 필요하다. 이번 이슈에서는 rotation을 구현하지 않는다.
- 전화번호 암호화 키 유출 시 기존 암호문 재암호화, provider 발송 중단, 접근 로그 감사가 필요하다. 이번 이슈에서는 rotation을 구현하지 않는다.
- `PHONE_HASH_KEY`는 로그에 출력하지 않고 접근 권한을 최소화한다.
- `PHONE_ENCRYPTION_KEY`는 로그에 출력하지 않고 접근 권한을 최소화한다.
- 서버는 저장된 전화번호 원문을 복호화할 수 있으므로 복호화 사용처를 발송/운영 승인 유스케이스로 제한하고 감사 로그를 남긴다.
- 운영 DB, Redis, 로그, sample data 어디에도 전화번호 원문이 평문으로 남지 않는지 확인한다.
