# Work Plan: 이슈 #125 계정 삭제와 데이터 수명주기

## 작업

GitHub 이슈 #125의 계정 삭제를 단순 soft delete에서 개인정보 익명화, 인증 연결 제거,
연관 서비스 정리 이벤트를 포함하는 수명주기 흐름으로 전환한다.

## 배경

현재 `DELETE /api/users/me`는 `users.status`와 `deleted_at`만 변경한다. 사용자 프로필,
OAuth 식별자, 약관 동의, refresh token, 프로필 파일과 notification 서비스 데이터가 남고,
동일 OAuth 로그인 시 기존 사용자를 재활성화한다.

## 범위

- 사용자 닉네임·성별·생년월일·프로필 URL·제재 사유를 익명화한다.
- 모든 여행 참여자 스냅샷의 표시명과 프로필 URL을 익명화한다.
- OAuthAccount와 약관 동의 연결을 삭제하고 refresh token을 폐기한다.
- 로컬 프로필 이미지 파일은 트랜잭션 커밋 후 삭제한다.
- Apple refresh token은 기존 revoker를 통해 커밋 후 폐기한다.
- 동일 OAuth 재로그인은 기존 탈퇴 사용자를 복구하지 않고 새 가입으로 처리한다.
- `USER_ACCOUNT_DELETED` v1 outbox 이벤트를 개인정보 없이 발행한다.
- 정산·지출 원장은 익명화된 사용자/참여자 참조로 유지한다.
- 도메인, 서비스, outbox, DB 통합 테스트와 API 문서를 갱신한다.

## 제외 범위

- 법률상 보관 기간을 코드 상수로 임의 확정하지 않는다.
- notification DB 정리는 notification 이슈 #31에서 처리한다.
- Flutter 안내 UI와 Gateway 검증은 각각 이슈 #55, #23에서 처리한다.
- 게시글·댓글 본문을 일괄 삭제하지 않는다. 공유 여행 기록의 원장은 유지하되 작성자 식별정보를 익명화한다.

## 설계

- `User.anonymizeAndWithdraw()`가 사용자 직접 식별정보와 상태 전이를 책임진다.
- `TripParticipantRepository`의 고정 native bulk update가 삭제된 참여자를 포함해 스냅샷 PII를 제거한다.
- `UserService.deleteMe()`가 OAuth/약관 삭제, Apple revoke 예약, refresh token·파일 후처리,
  outbox 저장을 하나의 트랜잭션 경계에서 조율한다.
- 이벤트 계약은 `aggregateType=USER`, `eventType=USER_ACCOUNT_DELETED`,
  payload `{eventVersion, userId, occurredAt}`로 고정한다.
- 외부 파일·Redis 정리는 커밋 이후 실행하고, DB rollback 시 실행하지 않는다.

## 테스트 계획

- 사용자 도메인 익명화와 탈퇴 상태를 검증한다.
- UserService가 참여자 익명화, OAuth/약관 삭제, Apple revoke, outbox 발행을 호출하는지 검증한다.
- 커밋 후 refresh token과 로컬 프로필 파일이 삭제되고 rollback에서는 삭제되지 않는지 검증한다.
- 탈퇴 뒤 동일 OAuth 로그인은 새 사용자 가입으로 처리되는 계약을 검증한다.
- 실제 PostgreSQL에서 삭제된 참여자를 포함한 익명화 bulk update를 검증한다.
- `./gradlew test`, `./gradlew integrationTest`, `./gradlew check`를 실행한다.

## 위험과 확인 사항

- 외부 Apple revoke 실패는 삭제 트랜잭션을 되돌리지 않으므로 운영 경고 로그를 유지한다.
- 사용자 ID는 정산 원장 참조와 notification 정리 키로만 유지하며 외부 응답에서 노출하지 않는다.
- outbox 전송 지연 동안 notification이 기존 이벤트를 받을 수 있으므로 notification tombstone이 최종 차단한다.

## 구현 상태

- 사용자·여행 참여자 개인정보 익명화, OAuth/약관 hard delete를 구현했다.
- refresh token·프로필 파일·Apple revoke를 커밋 후 후처리로 분리했다.
- `USER_ACCOUNT_DELETED` v1 outbox payload와 재가입 새 계정 정책을 반영했다.
- 전체 단위 테스트와 `verifyQueryConventions`는 통과했다.
- Docker daemon 부재로 PostgreSQL 통합 테스트는 로컬에서 시작하지 못했으며 테스트 코드는 추가했다.
