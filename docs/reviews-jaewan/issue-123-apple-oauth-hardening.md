# Review Report: 이슈 #123 Apple OAuth 보안 강화

## 요약

Apple 전용 HTTP timeout, identity token 수명 제한, replay 방지 key 보존 기간을
보안 경계와 장애 격리 관점에서 검토했다. 공용 WebClient나 API DTO를 변경하지 않고 Apple
integration 내부에만 정책을 적용했다. 출시를 차단하는 미해결 코드 결함은 발견하지 못했다.

## 발견 사항

| 심각도 | 영역 | 내용 | 조치 |
| --- | --- | --- | --- |
| 높음(해결) | Apple HTTP 호출 | JWKS, token, revoke 응답이 끝나지 않으면 요청 처리도 무기한 대기할 수 있었다. | Apple 전용 Reactor Netty client에 연결/응답 timeout을 두고 reactive timeout도 적용했다. |
| 높음(해결) | identity token 검증 | `exp`가 미래인지만 확인해 비정상적으로 긴 수명의 정상 서명 token을 허용했다. | `exp - iat`가 양수이며 10분 이하인지 검증한다. |
| 중간(해결) | replay 방지 | replay key TTL을 현재 시각부터 10분으로 제한해 발급시각 clock skew가 포함된 token은 `exp` 전에 key가 사라질 수 있었다. | TTL 상한을 제거하고 검증 시점부터 실제 `exp`까지 보존한다. |

## 구조 및 장애 계약 리뷰

- `AppleOAuthWebClient`가 공용 builder를 복제해 Apple 호출에만 connect/response timeout을 적용한다.
- connect와 response timeout은 1ms 이상 30초 이하만 허용해 오설정을 시작 시점에 거부한다.
- token 교환, revoke, JWKS 조회에 reactive timeout을 함께 적용해 body 완료까지 경계를 둔다.
- HTTP 또는 timeout 장애는 기존 `APPLE_AUTHORIZATION_FAILED` 계약으로 변환하고, 무관한
  프로그래밍 예외는 삼키지 않는다.
- Controller, 요청/응답 DTO, Redis key 형식, 데이터베이스 schema는 변경하지 않았다.

## 보안 리뷰

- 정상 서명 여부와 별개로 `exp - iat` 최대 10분을 강제한다.
- 허용된 미래 `iat` clock skew를 포함해 replay key를 token의 실제 만료 시각까지 유지한다.
- timeout 환경변수와 기본값만 문서화했으며 private key, refresh token 등 secret은 추가하지 않았다.
- 회원 탈퇴 revoke는 기존 영속 cleanup 작업과 재시도 계약을 유지한다.

## 확인한 명령

- Red: Apple timeout 설정이 구현되기 전 대상 테스트가 `compileTestKotlin`에서 실패
- 대상 Apple 테스트 통과
- `./gradlew check`
- `git diff --check`

## 남은 위험

- 실제 Apple sandbox의 지연, 연결 실패, revoke 응답은 운영 자격증명이 없어 검증하지 못했다.
- 운영 네트워크에서 기본 3초/5초가 너무 짧거나 길 수 있으므로 배포 후 인증 실패율과 외부 호출
  지연을 관찰해 허용 범위 안에서 조정해야 한다.
