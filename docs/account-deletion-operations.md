# 계정 삭제 운영 계약

## API와 데이터 수명주기

- `DELETE /api/users/me`는 인증된 활성 사용자만 실행할 수 있다.
- 사용자 닉네임, 성별, 생년월일, 프로필 URL과 제재 사유는 익명화한다.
- 여행 참여자의 `display_name`, `profile_image_url`은 삭제된 참여자를 포함해 익명화한다.
- OAuth 연결과 약관 동의는 hard delete하고 Redis refresh token은 DB 커밋 후 제거한다.
- 로컬 프로필 파일과 Apple refresh token revoke는 DB 커밋 후 best effort로 실행한다.
- 정산·지출 원장은 기존 사용자·여행 참여자 참조를 유지한다.
- OAuth 연결이 제거되므로 동일 provider 계정의 다음 로그인은 새 사용자 가입으로 처리한다.

## 서비스 간 이벤트

계정 삭제 트랜잭션은 다음 outbox 이벤트를 함께 저장한다.

```json
{
  "id": 301,
  "aggregateType": "USER",
  "aggregateId": 7,
  "eventType": "USER_ACCOUNT_DELETED",
  "payload": {
    "eventVersion": 1,
    "userId": 7,
    "occurredAt": "2026-07-28T12:00:00Z"
  }
}
```

- `id`는 notification의 `source_event_id` 멱등 키다.
- payload에는 닉네임, OAuth provider ID, 프로필 URL 등 개인정보를 넣지 않는다.
- notification 처리가 지연돼도 main의 계정 삭제 트랜잭션은 되돌리지 않는다.

## 운영 확인

- SQS sender가 `USER_ACCOUNT_DELETED` 이벤트를 notification queue로 발행하는지 확인한다.
- Apple revoke 실패 로그에는 사용자 ID만 기록하고 token 원문을 기록하지 않는다.
- PostgreSQL 통합 테스트 `UserAccountDeletionIntegrationTest`로 hard delete와 삭제 참여자 익명화를 검증한다.
