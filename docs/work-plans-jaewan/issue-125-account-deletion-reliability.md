# Work Plan: 이슈 #125 계정 삭제 외부 정리 신뢰성

## 작업

계정 삭제 DB 커밋 이후 수행하는 Apple refresh token 폐기, 프로필 이미지 삭제,
Redis refresh token 삭제를 내구성 있는 작업으로 저장하고 실패 상태와 재시도를 추적한다.

## 배경

기존 구현은 트랜잭션 `afterCommit` 콜백에서 외부 정리를 한 번만 시도한다. 프로세스 종료나
외부 시스템 장애가 발생하면 재시도할 레코드가 없고, OAuth 계정과 사용자 프로필 URL도 이미
삭제되어 운영자가 정리 대상을 복원할 수 없다.

## 범위

- 삭제 트랜잭션에서 외부 정리 대상을 작업 단위로 영속화한다.
- Apple token, 프로필 이미지, Redis refresh token을 서로 독립적으로 처리한다.
- 실패한 작업은 다음 시도 시각까지 대기한 뒤 지수 백오프로 다시 처리한다.
- `PENDING`, `FAILED`, `COMPLETED` 상태, 재시도 횟수, 다음 시도 시각,
  완료 시각, 비민감 오류 분류를 DB에서 추적한다.
- 여러 서버 인스턴스가 같은 작업을 중복 claim하지 않도록 `FOR UPDATE SKIP LOCKED`를 사용한다.
- 완료 시 민감 payload를 즉시 제거하고 로그에는 작업 ID, 사용자 ID, 작업 유형만 남긴다.
- 사용자의 삭제 응답, DB 익명화, lifecycle outbox 계약은 유지한다.
- 사용하지 않는 `SolapiSmsProperties`를 제거한다.

## 제외 범위

- 운영자용 정리 작업 조회 API는 추가하지 않는다. DB 상태와 구조화 로그로 추적한다.
- notification 서비스의 사용자 데이터 삭제 정책은 변경하지 않는다.
- Apple revoke API와 Redis, 파일 저장소 자체의 구현을 교체하지 않는다.

## 설계

### Planner 판단

- 삭제 성공 응답은 외부 정리 완료를 기다리지 않고 기존처럼 DB 삭제 커밋을 의미한다.
- 외부 정리의 일시 실패가 개인정보 익명화 트랜잭션을 롤백하지 않는다.
- 외부 정리 작업은 성공할 때까지 재시도하며 최대 시도 횟수로 폐기하지 않는다.

### Architect 판단

- `user/domain`, `user/repository`, `user/service`, `user/scheduler` 안에 전용 작업 모델을 둔다.
- 알림 전송용 `global.outbox`는 외부 SQS 계약을 가지므로 로컬 개인정보 정리 실행에 재사용하지 않는다.
- `UserService.deleteMe()`는 OAuth 레코드 삭제 전에 정리 작업을 enqueue한다.
- Apple refresh token은 기존 암호문 그대로 저장하고 실행 직전에만 복호화한다.
- 프로필 URL은 파일 삭제에 필요한 기간만 저장하며 완료 즉시 payload를 `NULL`로 지운다.
- repository claim 쿼리는 due 상태만 선택하고 행 잠금으로 다중 인스턴스 경쟁을 제어한다.
- 짧은 claim 트랜잭션이 5분 lease와 claim ID를 저장한 뒤 외부 I/O는 트랜잭션 밖에서 수행한다.
- 완료와 실패는 작업별 짧은 트랜잭션에서 현재 claim ID가 일치할 때만 저장한다.
- 작업 `version` 낙관적 잠금으로 lease 경계에서 늦은 worker가 새 claim 상태를 덮어쓰지 못하게 한다.
- lease 만료 작업은 다른 worker가 회수하며 `WORKER_LEASE_EXPIRED`로 재시도를 추적한다.

### TDD 시나리오

1. Apple 계정 수만큼 Apple revoke 작업이 생성되고 프로필/Redis 작업이 함께 생성된다.
2. 외부 호출 성공 시 작업이 `COMPLETED`가 되고 payload가 제거된다.
3. 외부 호출 실패 시 작업이 `FAILED`가 되고 재시도 횟수와 다음 시도 시각이 기록된다.
4. 실패한 작업은 due 전에는 claim되지 않고 due 이후 다시 성공할 수 있다.
5. 서로 다른 작업의 실패가 같은 batch의 나머지 작업 실행을 막지 않는다.
6. 삭제 트랜잭션에는 정리 작업이 남고 기존 익명화/outbox 계약은 유지된다.
7. 로그와 저장된 오류에는 토큰 평문, 암호문, 프로필 URL이 포함되지 않는다.

## 테스트 계획

- 도메인 상태 전이 단위 테스트
- enqueue 및 dispatcher 서비스 단위 테스트
- PostgreSQL repository due/lock 및 실패 복구 통합 테스트
- 기존 계정 삭제 통합 테스트에 정리 작업 저장 검증 추가
- `./gradlew test`
- `./gradlew check`

## 위험과 확인 사항

- 외부 호출 성공과 완료 상태 저장 사이의 프로세스 종료는 중복 실행을 만들 수 있다. Redis 삭제,
  로컬 파일 삭제, Apple revoke의 멱등 계약을 전제로 하며 claim ID로 늦은 worker의 상태 덮어쓰기를 막는다.
- 작업 payload는 삭제 완료 전까지 DB에 남는다. Apple token은 암호문만 저장하고 완료 즉시 제거한다.
- 영구 장애는 `FAILED` 상태로 계속 추적되며 백오프 상한 이후 일정 간격으로 재시도한다. revoke 전
  Apple 암호문을 제거하면 정리가 불가능하므로 운영 경보와 암호화 키 보존으로 장기 실패를 관리한다.
- 기존 이슈 #125의 미완료 범위이므로 새 GitHub 이슈는 만들지 않는다.

## 구현 상태

- 삭제 트랜잭션에서 Apple 암호문, 관리 대상 프로필 파일, Redis 정리 작업을 영속화한다.
- 5분 lease와 `FOR UPDATE SKIP LOCKED`로 다중 worker claim을 조정한다.
- 외부 I/O는 DB 트랜잭션 밖에서 실행하고 결과만 작업별 트랜잭션으로 저장한다.
- 실패는 30초부터 6시간 상한의 지수 백오프로 재시도하며 lease 만료 작업도 회수한다.
- 완료 payload 제거, 비민감 오류 코드, claim/version 경합 방어를 반영했다.
- API 응답, 사용자 익명화, `USER_ACCOUNT_DELETED` outbox 계약은 변경하지 않았다.
