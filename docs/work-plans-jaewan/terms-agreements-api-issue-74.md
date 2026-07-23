# 이슈 74 약관 동의 API 구현 기록

## 요구사항

- 약관 항목 목록을 조회한다.
- 현재 사용자의 약관별 동의 상태를 조회한다.
- 가입 또는 프로필 완료 전후에 필수 약관 동의를 저장한다.
- 마이페이지에서 선택 약관인 광고성 정보 수신 동의를 저장하거나 철회한다.

## API

### GET `/api/terms`

- 인증 없이 현재 약관 목록을 조회한다.
- 응답 항목:
  - `code`
  - `title`
  - `required`
  - `version`
  - `content`

### GET `/api/terms/agreements/me`

- bearer access token 필요.
- 현재 사용자의 약관별 동의 상태를 조회한다.
- 응답 항목:
  - `code`
  - `title`
  - `required`
  - `currentVersion`
  - `agreed`
  - `agreedVersion`
  - `agreedAt`
  - `revokedAt`

### PUT `/api/terms/agreements`

- bearer access token 필요.
- 가입 또는 프로필 완료 전후 약관 동의를 저장한다.
- 요청 예시:

```json
{
  "agreements": [
    { "code": "SERVICE_TERMS", "version": "2026-06-18", "agreed": true },
    { "code": "PRIVACY_POLICY", "version": "2026-06-18", "agreed": true },
    { "code": "LOCATION_INFO_TERMS", "version": "2026-06-18", "agreed": true },
    { "code": "MARKETING_CONSENT", "version": "2026-06-18", "agreed": false }
  ]
}
```

### PATCH `/api/terms/agreements/{code}`

- bearer access token 필요.
- 마이페이지에서 약관 동의 상태를 변경한다.
- 필수 약관은 `agreed=false`로 변경할 수 없다.
- 선택 약관 `MARKETING_CONSENT`는 언제든 동의 또는 철회할 수 있다.

## 약관 코드

- `SERVICE_TERMS`: 필수, 서비스 이용약관
- `PRIVACY_POLICY`: 필수, 개인정보 처리방침
- `LOCATION_INFO_TERMS`: 필수, 위치기반서비스 이용약관
- `MARKETING_CONSENT`: 선택, 광고성 정보 수신 동의

## 도메인 규칙

- 필수 약관 3개는 모두 현재 버전으로 `agreed=true`여야 저장이 성공한다.
- 선택 약관은 누락되거나 `agreed=false`여도 가입 흐름을 막지 않는다.
- 요청 버전이 서버의 현재 버전과 다르면 `TERM_VERSION_MISMATCH`로 실패한다.
- 필수 약관 철회 요청은 `REQUIRED_TERM_WITHDRAW_NOT_ALLOWED`로 실패한다.
- `PHONE_VERIFICATION_REQUIRED` 단계에서는 아직 확정 사용자 ID가 없을 수 있으므로, 약관 저장은 전화번호 인증 후 발급된 access token 기준으로 처리한다.

## DB 변경

- 기존 `user_agreements`에 `term_version` 컬럼을 추가한다.
- 기존 코드 `LOCATION_TERMS`는 `LOCATION_INFO_TERMS`로, `MARKETING`은 `MARKETING_CONSENT`로 변환한다.
- 현재 약관 범위 밖의 기존 동의 row는 soft delete 처리한다.
- 활성 row 기준 `(user_id, agreement_type)` unique index를 추가한다.

## 검증

- `./gradlew test --tests com.togethertrip.main.terms.service.TermsServiceTest`
- `./gradlew test`
