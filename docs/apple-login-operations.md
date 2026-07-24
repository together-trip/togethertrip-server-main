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

private key와 암호화 키는 저장소나 로그에 넣지 않고 secret manager로 주입한다. `APPLE_TOKEN_ENCRYPTION_KEY`를 교체할 때는 기존 ciphertext를 이전 키로 복호화해 새 키로 재암호화하는 마이그레이션이 필요하므로 즉시 덮어쓰지 않는다.

## 키 회전

1. Apple Developer에서 새 `.p8` 키를 생성한다.
2. 새 `APPLE_KEY_ID`와 `APPLE_PRIVATE_KEY`를 함께 배포한다.
3. `/api/auth/oauth/apple` authorization code 교환과 탈퇴 revoke를 확인한다.
4. 모든 인스턴스 전환 후 이전 Apple key를 폐기한다.

서버 client secret은 요청 시 ES256으로 생성하며 유효기간은 150일이다. Apple 공개키는 1시간 캐시하고, token의 `kid`를 찾지 못하면 즉시 한 번 갱신한다.

## 보안 및 장애 동작

- identity token의 RS256 서명, issuer, audience, 만료, 발급시각, nonce를 검증한다.
- 사용 완료한 token의 `jti`를 Redis에 기록해 같은 로그인 응답 재사용을 거부한다.
- Apple refresh token은 AES-256-GCM으로 암호화해 `oauth_accounts.encrypted_refresh_token`에 보관한다.
- 회원 탈퇴 트랜잭션 커밋 후 Apple revoke API를 호출한다. 외부 장애가 탈퇴 자체를 되돌리지는 않으며 사용자 ID만 포함한 경고 로그를 남긴다.
