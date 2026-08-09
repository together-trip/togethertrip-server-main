# Verification Report: 이슈 #123 Apple OAuth 보안 강화

## 검증 대상

- Apple JWKS, token 교환, revoke의 연결/응답 시간 제한
- timeout의 `APPLE_AUTHORIZATION_FAILED` 변환
- identity token의 최대 10분 수명 제한
- 미래 발급시각 clock skew가 있어도 실제 `exp`까지 유지되는 replay 방지 TTL
- 기존 Apple 로그인, refresh token revoke, 전체 품질 gate 회귀

## 실행한 명령

```bash
./gradlew test \
  --tests 'com.togethertrip.main.auth.client.AppleIdentityTokenVerifierTest' \
  --tests 'com.togethertrip.main.auth.client.AppleOAuthClientTest'
```

```bash
./gradlew test \
  --tests 'com.togethertrip.main.auth.client.AppleIdentityTokenVerifierTest' \
  --tests 'com.togethertrip.main.auth.client.AppleOAuthClientTest' \
  --tests 'com.togethertrip.main.auth.client.AppleOAuthPropertiesTest' \
  --tests 'com.togethertrip.main.auth.service.apple.AppleOAuthAccountRevocationServiceTest'
```

```bash
./gradlew check
```

```bash
git diff --check
```

## 결과

- Red 단계에서 timeout 설정 property가 없어 대상 테스트가 `compileTestKotlin`에서 실패했다.
- JWKS와 token endpoint의 무응답을 인증 실패로 변환하는 테스트가 통과했다.
- 10분 1초 수명의 정상 서명 token 거부 테스트가 통과했다.
- 발급시각이 현재보다 30초 미래이고 수명이 10분인 token의 replay key TTL이 10분 30초로
  기록되는 테스트가 통과했다.
- 0 이하, 30초 초과 connect/response timeout 설정 거부 테스트가 통과했다.
- 기존 token 교환, revoke, 서명/claim 검증과 동일 token 재사용 거부 테스트가 통과했다.
- 전체 `check`의 단위/통합 테스트, JaCoCo, query convention, PIT gate가 통과했다.

## 미검증 항목과 다음 조치

- Apple sandbox 실제 네트워크 호출은 운영 자격증명이 없어 실행하지 않았다.
- 배포 후 Apple 인증 실패율, timeout 비율, 외부 호출 지연을 관찰한다.
- 계정 삭제 cleanup의 장기 `FAILED` 작업 경보와 함께 revoke timeout 반복 여부를 확인한다.
