# 한국수출입은행 환율 수집 구현 검증

## 검증 대상

- 브랜치: `feature/issue-54-exchange-rate-ingestion`
- 이슈: #54
- 범위:
  - Redisson 기반 Redis 분산락
  - 한국수출입은행 API client
  - 환율 응답 정규화
  - `exchange_rates` native upsert
  - scheduler/backfill 실행 경로
  - scheduler 실패 보정 catch-up 실행 경로
  - backfill `SUCCESS` skip, 요청 간 pause 설정
  - `exchange_rate_import_runs` 상태 원장
  - row 수/필수 통화 검증
  - 날짜별 bulk upsert
  - 주말 API 호출 skip
  - Admin 상태 조회 API
  - Spring Batch 기반 Admin 비동기 백필 실행 API
  - `exchange_rate_backfill_jobs` 실행 요청 원장
  - Spring Batch `BATCH_*` metadata 테이블
  - top-level `exchange` feature의 엄격 패키지 분리
  - 로컬 sample data repeatable migration 전환
  - 관련 단위 테스트

## 실행 명령

```bash
./gradlew test
```

## 결과

- 성공
- 마지막 실행 결과: `BUILD SUCCESSFUL`

## 확인 내용

- scheduler/backfill 기본값은 disabled다.
- 환율 API `auth-key`는 설정으로만 주입된다.
- Redisson lock을 획득한 서버만 scheduler/backfill 수집을 수행한다.
- lock 획득 실패 시 수집을 건너뛴다.
- 동일 날짜/통화 저장은 native upsert로 처리된다.
- 동일 날짜/통화가 재수집되더라도 `rate`, `source`가 같으면 실제 update는 수행하지 않는다.
- 한 날짜의 여러 통화 row는 bulk upsert query 1회로 저장된다.
- `/api/admin/**`는 `ROLE_ADMIN`만 접근 가능하다.
- Admin API로 날짜별 `exchange_rate_import_runs` 상태를 기간/상태 기준으로 조회할 수 있다.
- Admin API로 백필을 실행하면 `exchange_rate_backfill_jobs`에 요청자, 기간, Batch execution id, 실행 결과 집계가 기록된다.
- Spring Batch 6 기준 deprecated 된 `JobLauncher`, `TaskExecutorJobLauncher` 대신 `JobOperator`, `TaskExecutorJobOperator`를 사용한다.
- Batch metadata는 별도 schema 없이 기본 schema의 `BATCH_*` 테이블에 둔다.
- 환율 백필 원장과 Spring Batch metadata는 `V7__add_exchange_rate_backfill_jobs.sql`에 함께 둔다.
- 로컬 sample data는 `R__local_sample_data.sql` repeatable migration으로 전환해 로컬 seed 변경 시 versioned migration checksum 충돌 부담을 낮췄다.
- 거래 등록/수정 플로우는 외부 API client에 의존하지 않는다.
- 환율 수집 코드는 `exchange/batch`, `exchange/client`, `exchange/config`, `exchange/controller`, `exchange/domain`, `exchange/dto`, `exchange/repository`, `exchange/service`, `exchange/service/normalizer`, `exchange/scheduler`, `exchange/support`로 분리되어 있다.
- scheduler 기본 `catch-up-days = 7` 기준으로 오늘 포함 최대 8일 범위에서 완료되지 않은 날짜만 자동 수집한다.
- 수집 run은 `PENDING`, `RUNNING`, `SUCCESS`, `NO_DATA`, `FAILED`, `NON_BUSINESS_DAY` 상태와 시도 횟수, 저장 row 수, 마지막 오류를 기록한다.
- 기본 검증 기준은 `minimum-row-count = 20`, `required-currencies = USD,JPY,EUR`이다.
- 기본 `skip-weekends = true` 기준으로 토요일/일요일은 API 호출 없이 `NON_BUSINESS_DAY`로 기록한다.
- backfill은 지정 범위 중 완료되지 않은 날짜 전체를 처리하며, 날짜별 요청 사이에는 기본 `pause-between-requests = 300ms`를 적용한다.

## 미실행 검증

- 실제 한국수출입은행 API 호출 smoke test
  - 사유: `auth-key`와 외부 네트워크 의존성이 있는 수동 검증 영역이다.
- 실제 PostgreSQL repository 통합 테스트
  - 사유: 현재 프로젝트 테스트 패턴은 mock/단위 테스트 중심이며, 이번 구현에서는 SQL 형태를 기존 local seed의 partial unique `ON CONFLICT` 패턴과 맞췄다.

## 남은 위험

- 운영 Redis topology에 따라 lock failover 안전성이 달라질 수 있다.
- 그래도 `exchange_rates` upsert가 최종 데이터 중복 방어선으로 남아 있다.
- 운영 반영 전 `KOREA_EXIM_EXCHANGE_RATE_AUTH_KEY` 설정과 scheduler enable 시점을 확인해야 한다.
