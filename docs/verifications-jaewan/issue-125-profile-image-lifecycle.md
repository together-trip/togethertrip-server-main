# Verification Report: 이슈 #125 프로필 이미지 수명주기

## 검증 대상

- 프로필 교체 commit과 이전 managed 파일 cleanup task 저장의 원자성
- rollback 시 기존 URL/task 보존과 새 업로드 파일만 제거하는 경계
- 동일 URL과 외부 URL의 불필요한 cleanup 방지
- 파일 삭제 실패의 backoff 재시도와 완료 뒤 미실행
- 반복 파일 삭제의 멱등성
- 과거 managed URL 재참조 시 현재 프로필 파일 삭제 방지
- 다른 managed URL 직접 지정 거부와 동일 URL/외부 URL/multipart 허용
- 기존 사용자/계정 삭제 기능과 전체 품질 gate 회귀

## 실행한 명령

```bash
./gradlew test \
  --tests 'com.togethertrip.main.user.service.UserServiceTest' \
  --tests 'com.togethertrip.main.user.service.UserAccountDeletionCleanupEnqueueServiceTest' \
  --tests 'com.togethertrip.main.user.service.storage.LocalUserProfileImageStorageTest'
```

```bash
./gradlew integrationTest \
  --tests 'com.togethertrip.main.user.service.UserProfileImageLifecycleIntegrationTest' \
  --tests 'com.togethertrip.main.user.repository.UserAccountDeletionCleanupTaskRepositoryIntegrationTest'
```

```bash
./gradlew check
```

```bash
git diff --check
```

## 결과

- Red 단계에서 `enqueueProfileImage`가 없어 `compileTestKotlin`이 실패했다.
- 실제 PostgreSQL에서 성공한 교체는 새 URL과 이전 URL cleanup task를 함께 커밋했다.
- 강제 rollback은 기존 URL을 유지하고 cleanup task를 남기지 않았다.
- multipart rollback callback은 새 파일을 삭제하고 이전 파일을 직접 삭제하지 않았다.
- 같은 URL은 task를 만들지 않고 외부 Kakao URL은 payload로 저장하지 않았다.
- 파일 삭제 첫 실패 뒤 30초 동안 claim하지 않고, due 시 재시도해 완료했다.
- 완료 후 task를 다시 실행하지 않고 payload를 제거했다.
- 같은 managed 파일 URL을 두 번 삭제해도 성공했다.
- `A→B` cleanup 대기 중 `B→A`로 현재 참조가 되돌아간 상황에서 A 파일 삭제를 건너뛰고
  cleanup task는 완료했다.
- 탈퇴 또는 사용자 부재처럼 현재 활성 참조가 없으면 기존과 같이 파일 삭제를 실행했다.
- 실제 Spring storage 판별로 다른 managed URL 직접 지정이 `INVALID_INPUT`인지 검증했다.
- 동일 managed URL 유지, 신뢰한 외부 OAuth URL 전환, multipart 신규 업로드가 통과했다.
- 전체 `check`의 단위/통합 테스트, JaCoCo, query convention, PIT gate가 통과했다.

## 검증 중 조정

- 최초 재시도 통합 테스트는 수동 생성한 lifecycle service 호출을 transaction으로 감싸지 않아
  상태 flush가 다음 claim에 반영되지 않았다. 제품 코드가 사용하는 transaction 경계를 테스트에도
  적용한 뒤 신규/기존 repository 통합 테스트가 모두 통과했다.

## 미검증 및 범위 밖

- 운영 파일시스템의 실제 권한 장애와 프로세스 강제 종료는 재현하지 않았다.
- 법적 보관 정책과 신규 운영 알람 설계는 범위 밖이다.
- 이 검증 결과만으로 이슈 #125를 닫지 않는다.
