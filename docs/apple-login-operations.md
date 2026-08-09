# Sign in with Apple 서버 운영 계약

## 환경변수

| 변수 | 설명 |
|---|---|
| `APPLE_AUTH_ENABLED` | Apple 로그인 활성화 여부. 운영에서는 `true` |
| `APPLE_CLIENT_ID` | iOS bundle identifier 또는 Services ID. identity token의 audience와 일치해야 함 |
| `APPLE_TEAM_ID` | Apple Developer Team ID |
| `APPLE_KEY_ID` | Sign in with Apple private key ID |
| `APPLE_PRIVATE_KEY` | `.p8` PKCS#8 private key PEM. 개행은 실제 개행 또는 `\\n` 허용 |
| `APPLE_TOKEN_ENCRYPTION_KEY` | Apple refresh token 암호화용 Base64 인코딩 32-byte AES 키 |
| `APPLE_JWKS_URL` | 기본값 `https://appleid.apple.com/auth/keys` |
| `APPLE_TOKEN_URL` | 기본값 `https://appleid.apple.com/auth/token` |
| `APPLE_REVOKE_URL` | 기본값 `https://appleid.apple.com/auth/revoke` |
| `APPLE_CONNECT_TIMEOUT` | Apple 전용 HTTP 연결 제한. ISO-8601 duration, 기본값 `PT3S`, 허용 범위 1ms~30초 |
| `APPLE_RESPONSE_TIMEOUT` | JWKS 조회, code 교환, revoke 응답 제한. ISO-8601 duration, 기본값 `PT5S`, 허용 범위 1ms~30초 |

private key와 암호화 키는 저장소나 로그에 넣지 않고 secret manager로 주입한다. `APPLE_TOKEN_ENCRYPTION_KEY`를 교체할 때는 기존 ciphertext를 이전 키로 복호화해 새 키로 재암호화하는 마이그레이션이 필요하므로 즉시 덮어쓰지 않는다.

## 키 회전

1. Apple Developer에서 새 `.p8` 키를 생성한다.
2. 새 `APPLE_KEY_ID`와 `APPLE_PRIVATE_KEY`를 함께 배포한다.
3. `/api/auth/oauth/apple` authorization code 교환과 탈퇴 revoke를 확인한다.
4. 모든 인스턴스 전환 후 이전 Apple key를 폐기한다.

서버 client secret은 요청 시 ES256으로 생성하며 유효기간은 150일이다. Apple 공개키는 1시간 캐시하고, token의 `kid`를 찾지 못하면 즉시 한 번 갱신한다.

## 보안 및 장애 동작

- identity token의 RS256 서명, issuer, audience, 만료, 발급시각, nonce를 검증하고 `exp - iat`가 10분을 초과하면 거부한다.
- 사용 완료한 token의 `jti`를 Redis에 실제 `exp`까지 기록한다. 허용된 발급시각 clock skew 때문에 현재 시각 기준 남은 수명이 10분을 넘더라도 만료 전 replay window가 다시 열리지 않는다.
- Apple refresh token은 AES-256-GCM으로 암호화해 `oauth_accounts.encrypted_refresh_token`에 보관한다.
- Apple 외부 호출은 공용 HTTP client와 분리된 연결 3초, 응답 5초 제한을 사용한다. JWKS, token, revoke timeout은 `APPLE_AUTHORIZATION_FAILED`로 변환되어 요청이 무기한 점유되지 않는다.
- 회원 탈퇴 트랜잭션은 Apple revoke 작업을 DB에 함께 저장한다. worker가 커밋 후 실행하고 성공할 때까지 backoff 재시도하며, 완료하면 암호화 token payload를 제거한다. 장기 실패는 `user_account_deletion_cleanup_tasks` 상태와 운영 경보로 감시한다.
