# Review Report: 이슈 #125 계정 삭제 외부 정리 신뢰성

## 요약

계정 삭제 외부 정리를 `user` 기능 내부의 영속 작업으로 분리한 구현을 구조, API 계약,
개인정보와 동시성 관점에서 검토했다. API 요청/응답과 익명화/outbox 계약 변경은 없으며,
초기 구현의 장기 DB 트랜잭션 문제를 lease claim 구조로 수정했다. 출시를 차단하는 미해결
코드 결함은 발견하지 못했다.

## 발견 사항

| 심각도 | 파일 | 내용 | 조치 |
| --- | --- | --- | --- |
| 중간(해결) | `UserAccountDeletionCleanupDispatchService.kt` | 외부 HTTP/파일/Redis I/O 동안 batch transaction과 row lock을 유지했다. | 짧은 claim transaction, transaction 밖 외부 I/O, 작업별 완료/실패 transaction으로 분리했다. |
| 중간(해결) | `UserAccountDeletionCleanupTask.kt` | lease 경계에서 늦은 worker가 새 claim을 덮어쓸 수 있었다. | claim ID 확인과 `@Version` 낙관적 잠금을 함께 적용했다. |
| 중간(해결) | `UserAccountDeletionCleanupEnqueueService.kt` | 삭제할 수 없는 외부 프로필 URL이 작업 payload로 복제될 수 있었다. | 서버가 관리하는 로컬 파일 URL만 enqueue하도록 제한했다. |
| 낮음(수용) | `user_account_deletion_cleanup_tasks` | 영구 Apple 장애 시 암호문이 성공할 때까지 남는다. | revoke 전 제거 시 영구 정리가 불가능하므로 암호문 보존을 선택하고, 24시간 실패 경보와 암호화 키 보존 절차를 문서화했다. |

## 구조 리뷰

- Controller와 API DTO는 변경하지 않았다.
- `UserService`는 삭제 트랜잭션 조율과 enqueue만 담당한다.
- 상태 전이는 `user/domain`, claim/실행 흐름은 `user/service`, due/lock 조회는
  `user/repository`, 주기 실행은 `user/scheduler`에 위치한다.
- 알림 SQS 계약을 가진 `global.outbox`를 로컬 외부 정리 실행에 재사용하지 않았다.
- 다른 저장소와 서비스는 수정하지 않았다.

## API 계약 리뷰

- `DELETE /api/users/me` method, path, 입력, 성공 응답을 변경하지 않았다.
- 사용자 익명화, OAuth/약관 hard delete, lifecycle outbox payload도 기존 계약을 유지한다.
- 내부 DB migration과 worker 설정만 추가되어 app/gateway DTO 동기화는 필요하지 않다.

## 보안 리뷰

- Apple refresh token은 기존 AES-GCM 암호문만 저장하며 실행 순간에만 복호화한다.
- 완료 시 Apple 암호문과 로컬 프로필 URL payload를 `NULL`로 제거한다.
- Redis 작업에는 payload가 없고 외부 Kakao URL은 저장하지 않는다.
- 실패 로그는 작업 ID, 사용자 ID, 작업 유형, 고정 오류 코드, 예외 클래스명만 기록한다.
  token, 암호문, URL, 예외 메시지와 stack trace는 기록하지 않는다.
- Apple의 이미 폐기된 token 재요청 `200` 계약, Redis delete, `Files.deleteIfExists`를 근거로
  lease 복구 시 중복 실행을 멱등 처리한다.

## 테스트 리뷰

- 도메인 완료/실패/lease 만료 상태 전이를 검증한다.
- 세 외부 작업의 성공, 개별 실패 격리, 비민감 오류 코드를 단위 테스트한다.
- 실제 PostgreSQL에서 migration, due/backoff, lease 회수, 다중 worker `SKIP LOCKED`,
  완료 payload 제거를 검증한다.
- 기존 계정 삭제 통합 테스트가 작업 enqueue와 익명화/outbox 계약을 함께 검증한다.

## 확인한 명령

- `./gradlew test`
- `./gradlew integrationTest --tests '*UserAccountDeletionCleanupTaskRepositoryIntegrationTest' --tests '*UserAccountDeletionIntegrationTest' --tests '*TestcontainersInfrastructureTest'`
- `./gradlew check`
- `rg -n -i "solapi|sms_solapi|solapi_" src/main/resources/.env.example src/main src/test`
- `git diff --check`

## 남은 위험

- 실제 Apple sandbox revoke는 운영 자격증명 없이 실행하지 못했다.
- 5분 lease보다 외부 호출이 길면 중복 실행될 수 있으나 세 작업은 멱등 계약을 가진다.
- 전용 Micrometer 지표는 없으며 현재는 DB 상태 집계와 구조화 로그로 운영 감시한다.
