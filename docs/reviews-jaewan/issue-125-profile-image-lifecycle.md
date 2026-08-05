# Review Report: 이슈 #125 프로필 이미지 수명주기

## 요약

프로필 이미지 교체 시 이전 서버 관리 파일이 남는 누락을 트랜잭션, 장애 복구, 멱등성 관점에서
검토했다. 기존 durable cleanup 모델을 재사용해 API나 schema 변경 없이 보완했으며, 출시를
차단하는 미해결 코드 결함은 발견하지 못했다.

## 발견 사항

| 심각도 | 영역 | 내용 | 조치 |
| --- | --- | --- | --- |
| 중간(해결) | `UserService.updateMe` | 프로필 교체 성공 뒤 이전 managed 파일을 정리하지 않아 orphan 파일이 누적됐다. | 변경 전 URL을 캡처하고 실제 변경일 때 동일 트랜잭션에 `PROFILE_IMAGE` cleanup 작업을 저장한다. |
| 중간(해결) | 트랜잭션 경계 | 새 업로드 rollback 정리와 이전 파일 commit 정리의 소유권이 구분되지 않았다. | rollback callback은 새 파일만 삭제하고 이전 파일은 커밋된 durable task만 삭제하도록 분리했다. |
| 높음(해결) | 재참조 경계 | `A→B` 작업이 대기 중일 때 API로 `B→A`를 요청하면 과거 A cleanup이 현재 프로필 A를 삭제할 수 있었다. | dispatch 직전 활성 사용자의 현재 URL과 payload가 같으면 물리 삭제를 생략하고 task만 완료한다. |
| 높음(해결) | TOCTOU | worker의 현재 참조 확인과 파일 삭제 사이에 API가 과거 managed URL을 다시 지정할 수 있었다. | multipart가 아닌 요청은 현재 값과 다른 managed URL을 거부해 cleanup 대상이 다시 현재 참조가 되는 전이를 차단한다. |
| 낮음(해결) | 재실행 | 파일 삭제 성공 후 완료 상태 저장 전에 worker가 중단되면 같은 삭제가 반복될 수 있다. | 기존 `deleteIfExists` 멱등성을 테스트로 고정하고 claim/lease 재시도 모델을 유지했다. |

## 구조 리뷰

- 신규 테이블, task type, scheduler를 추가하지 않았다.
- 기존 `UserAccountDeletionCleanupTask.profileImage`, lifecycle service, dispatch worker를 재사용한다.
- `UserService`는 이전/다음 URL 비교와 enqueue 조율만 담당하고 managed 파일 판별은
  `UserProfileImageStorage`에 위임한다.
- 외부 URL과 동일 URL은 task를 만들지 않는다.
- 새 managed URL은 multipart 업로드 결과로만 반영하고, JSON의 managed URL은 현재 값 유지에만
  사용할 수 있다. 외부 OAuth URL 전환은 허용한다.
- Controller, DTO, migration, 다른 저장소는 변경하지 않았다.

## 트랜잭션 리뷰

- 사용자 profile URL 변경과 이전 파일 task 저장은 하나의 Spring transaction에 참여한다.
- commit 이후에만 다른 worker가 task를 claim할 수 있다.
- rollback 시 사용자 URL과 task insert가 함께 취소된다.
- multipart 저장 직후 등록한 기존 synchronization은 rollback 시 새 `StoredUserProfileImage`만
  삭제하며 이전 URL을 직접 삭제하지 않는다.
- 탈퇴 흐름은 사용자 URL을 `NULL`로 만든 뒤 commit하므로 현재 참조 보호가 기존 파일 삭제를
  방해하지 않는다.

## 테스트 리뷰

- 단위 테스트로 실제 변경, 동일 URL, managed/외부 URL 필터와 새 파일 rollback 삭제를 검증했다.
- PostgreSQL 통합 테스트로 profile URL과 cleanup task의 commit/rollback 원자성을 검증했다.
- 파일 삭제 실패 후 30초 backoff 재시도, 완료 payload 제거, 완료 뒤 미실행을 검증했다.
- 로컬 저장소의 동일 URL 반복 삭제가 성공하는지 검증했다.
- A cleanup 시점에 활성 사용자가 A를 다시 참조하는 경우 저장소 삭제를 호출하지 않고 task를
  완료하는지 검증했다.
- 다른 managed URL 직접 지정은 거부하고 동일 URL, 외부 URL, multipart 업로드는 허용하는지
  검증했다.

## 앱 계약 확인

- Flutter 앱은 새 이미지 선택 시 multipart `profileImage`를 전송한다.
- 이미지를 선택하지 않으면 JSON에 현재 `profileImageUrl`을 그대로 전송한다.
- 과거 managed URL을 선택하는 UI/API 흐름은 없어 서버 불변식과 호환된다.

## 범위 밖

- 법적 파일 보관 정책과 신규 운영 알람은 변경하지 않았다.
- 이 구현만으로 이슈 #125를 닫지 않는다.
