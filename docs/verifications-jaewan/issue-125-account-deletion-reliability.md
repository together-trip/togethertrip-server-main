# Verification Report: 이슈 #125 계정 삭제 외부 정리 신뢰성

## 검증 대상

- 계정 삭제 트랜잭션과 외부 정리 작업 저장의 원자성
- Apple revoke, 관리 대상 프로필 파일, Redis refresh token의 내구적 재시도
- 상태/횟수/다음 시도/완료/오류 추적
- 다중 worker claim과 lease 만료 복구
- 기존 사용자 익명화, 삭제 응답, lifecycle outbox 계약 유지
- 민감 token/URL 로그 비노출과 완료 payload 제거
- 미사용 Solapi/전화번호 환경 설정 제거

## 실행한 명령

```bash
./gradlew test
```

```bash
./gradlew integrationTest \
  --tests '*UserAccountDeletionCleanupTaskRepositoryIntegrationTest' \
  --tests '*UserAccountDeletionIntegrationTest' \
  --tests '*TestcontainersInfrastructureTest'
```

```bash
./gradlew check
```

```bash
rg -n -i "solapi|sms_solapi|solapi_" \
  src/main/resources/.env.example src/main src/test
```

```bash
git diff --check
```

## 결과

- 단위 테스트 전체 통과
- PostgreSQL/Testcontainers migration과 계정 삭제/정리 작업 통합 테스트 통과
- `FOR UPDATE SKIP LOCKED` 동시 claim ID 비중복 검증 통과
- 실패 후 30초 backoff 재시도와 완료 payload 제거 검증 통과
- 만료된 `PROCESSING` lease 회수와 `WORKER_LEASE_EXPIRED` 추적 검증 통과
- 전체 `check`의 통합 테스트, JaCoCo, query convention, PIT gate 통과
- 운영 코드와 `.env.example`에서 Solapi/전화번호 보안 환경변수 잔재 제거 확인
- API Controller/DTO 변경 없음 확인

## 실패 또는 미검증 항목

- 최초 전체 `check`에서 migration 현재 버전을 25로 고정한 인프라 테스트가 실패했다.
  V26 추가에 맞춰 기대값을 26으로 갱신한 뒤 전체 gate가 통과했다.
- Apple sandbox 실제 revoke는 Apple 운영 자격증명이 없어 실행하지 않았다.
- 운영 환경의 장기 실패 경보 연결은 배포 인프라 범위라 DB 점검 쿼리까지만 제공했다.

## 다음 조치

- 배포 후 `FAILED` 24시간 초과 건수와 만료된 `PROCESSING` 건수를 경보에 연결한다.
- Apple 암호화 키 교체 전에 미완료 `APPLE_REFRESH_TOKEN` 작업이 없는지 확인한다.
- 실제 Apple sandbox 계정으로 최초 revoke와 동일 token 반복 revoke가 모두 성공하는지 확인한다.
