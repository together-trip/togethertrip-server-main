# 계정 삭제 운영 계약

## API와 데이터 수명주기

- `DELETE /api/users/me`는 인증된 활성 사용자만 실행할 수 있다.
- 사용자 닉네임, 성별, 생년월일, 프로필 URL과 제재 사유는 익명화한다.
- 여행 참여자의 `display_name`, `profile_image_url`은 삭제된 참여자를 포함해 익명화한다.
- OAuth 연결과 약관 동의는 hard delete하고 Redis refresh token 정리 작업은 같은 DB 트랜잭션에 저장한다.
- 서버가 관리하는 로컬 프로필 파일과 Apple refresh token revoke 대상도 같은 트랜잭션에 정리 작업으로 저장한다.
- 정리 worker는 DB 커밋 후 Apple revoke, 로컬 파일 삭제, Redis 삭제를 실행하고 성공할 때까지 재시도한다.
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
- 정리 작업은 `PENDING -> PROCESSING -> COMPLETED`로 전이하며 실패하면 `FAILED`와
  `retry_count`, `next_attempt_at`, `last_error_code`를 기록한다.
- worker는 짧은 claim 트랜잭션에서 5분 lease를 잡은 뒤 DB 트랜잭션 밖에서 외부 I/O를 수행한다.
  프로세스가 종료되거나 lease가 만료되면 다른 worker가 작업을 다시 claim한다.
- 완료/실패 저장은 claim ID 일치와 JPA `version` 낙관적 잠금을 함께 사용해 늦은 worker가
  새 worker의 claim 상태를 덮어쓰지 못하게 한다.
- Redis key 삭제와 로컬 파일 `deleteIfExists`는 멱등이다. Apple revoke도 이미 폐기된 token의
  반복 요청을 `200`으로 취급하는
  [Apple Token revocation 계약](https://developer.apple.com/documentation/signinwithapplerestapi/revoke-tokens)을 따른다.
- 성공한 작업의 `payload`는 즉시 `NULL`로 제거한다. Redis 작업에는 payload를 저장하지 않고,
  외부 Kakao 이미지 URL은 서버 소유 파일이 아니므로 작업 테이블에 복제하지 않는다.
- Apple refresh token은 기존 AES-GCM 암호문만 보관한다. revoke 전 payload를 제거하면 영구 정리가
  불가능하므로 `COMPLETED` 전까지 보존하며, 해당 암호문이 남은 동안 기존 암호화 키를 유지한다.
- 로그에는 `taskId`, `userId`, `taskType`, 고정 오류 코드와 예외 클래스명만 기록하고 token, 암호문,
  프로필 URL, 예외 메시지와 stack trace는 기록하지 않는다.
- PostgreSQL 통합 테스트 `UserAccountDeletionIntegrationTest`로 hard delete와 삭제 참여자 익명화를 검증한다.

### 실패 작업 점검

payload를 조회하지 않고 다음 집계만 운영 지표로 사용한다.

```sql
select task_type, status, last_error_code, count(*)
from user_account_deletion_cleanup_tasks
where status in ('FAILED', 'PROCESSING')
group by task_type, status, last_error_code;
```

```sql
select count(*)
from user_account_deletion_cleanup_tasks
where status = 'FAILED'
  and created_at < now() - interval '24 hours';
```

- 24시간 이상 `FAILED`인 작업이 있으면 Apple 운영 설정, Redis 연결, 파일 권한을 확인한다.
- `PROCESSING`이 `lease_expires_at`을 넘으면 다음 scheduler 주기에 자동 회수된다.
- 암호화 키를 교체할 때 미완료 `APPLE_REFRESH_TOKEN` 작업이 0건인지 먼저 확인한다.

### 설정

- `USER_ACCOUNT_DELETION_CLEANUP_ENABLED` (기본 `true`)
- `USER_ACCOUNT_DELETION_CLEANUP_FIXED_DELAY` (기본 `PT5S`)
- `USER_ACCOUNT_DELETION_CLEANUP_BATCH_SIZE` (기본 `50`, 코드에서 최대 `500`)
- `USER_ACCOUNT_DELETION_CLEANUP_LEASE_DURATION` (기본 `PT5M`)
