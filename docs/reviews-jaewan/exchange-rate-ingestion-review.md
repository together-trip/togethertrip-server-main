# 한국수출입은행 환율 수집 구현 자체 리뷰

## 리뷰 범위

- 브랜치: `feature/issue-54-exchange-rate-ingestion`
- 이슈: #54 `feat: 한국수출입은행 환율 데이터 수집 및 DB 구축`
- 구현 방향: Redisson 기반 Redis 분산락 + 한국수출입은행 API client + 정규화 + native upsert + scheduler/backfill

## 1차 리뷰: 아키텍처

- Controller를 추가하지 않고 scheduler/backfill 중심으로 구현해 거래 쓰기 API와 외부 API 호출을 분리했다.
- 환율 수집 책임은 top-level `exchange` feature로 분리했다. `exchange_rates`는 여행별 데이터가 아니라 거래 플로우가 공통으로 조회하는 전역 환율 원천 데이터이므로 `trip` 하위에 두지 않는 편이 장기 이관과 provider 확장에 적합하다.
- feature 내부도 `batch/client/config/controller/domain/dto/repository/service/service.normalizer/scheduler/support`로 엄격히 나눴다. Batch 실행, provider 통신, 설정, 운영자 API, 저장 모델, DTO, DB 접근, orchestration, 응답 정규화, 자동 실행, 분산락 adapter가 한 패키지에 섞이지 않는다.
- `ExchangeRateImportService`를 중심으로 scheduler와 backfill이 같은 수집 정책을 공유한다.
- Admin API는 기존 수집 service와 run 원장을 재사용해 상태 조회와 Spring Batch 기반 비동기 백필 실행을 제공한다.
- `exchange_rate_backfill_jobs`는 운영자가 요청한 실행 단위와 진행률을 기록하고, 날짜별 상세 결과는 `exchange_rate_import_runs`가 계속 담당한다.
- Spring Batch Job/Step/Tasklet/Listener 설정은 `exchange.batch` 패키지에 모아 일반 설정 properties와 분리했다.
- Batch metadata는 별도 schema 없이 기본 schema의 `BATCH_*` 테이블로 둔다. 현재 프로젝트의 Flyway/JPA 기본 schema 운영 방식과 맞고, 별도 schema 설정 부담을 만들지 않는다.
- 환율 백필 원장과 Spring Batch metadata는 `V7__add_exchange_rate_backfill_jobs.sql`에 함께 두었다. 사용자 요청대로 DB를 비울 수 있는 상황이므로 별도 V8을 만들지 않아 migration 흐름을 단순하게 유지했다.
- 로컬 sample data는 `V9001__...` versioned migration에서 `R__local_sample_data.sql` repeatable migration으로 전환했다. 로컬 seed는 자주 바뀌기 쉬워 versioned checksum 충돌보다 repeatable 재적용 모델이 맞다.
- 외부 API 호출 후 DB 저장만 repository transaction으로 수행하도록 보완했다. 외부 네트워크 호출이 불필요하게 DB transaction 안에 머물지 않는다.

판단: 적합.

## 2차 리뷰: 분산락과 동시성

- 서버 이중화 중복 실행 방지는 Redisson `RLock.tryLock(waitTime, leaseTime)`으로 구현했다.
- lock name은 `exchange-rate:import:korea-exim`을 기본값으로 두고 환경변수로 교체 가능하게 했다.
- scheduler와 backfill이 같은 lock을 사용하므로 운영 수집과 수동 백필이 동시에 같은 provider 데이터를 적재하는 상황을 피한다.
- Admin 백필 Batch step도 같은 lock을 사용하므로 운영 수집과 화면 기반 수동 실행이 겹치는 상황을 줄인다.
- lock이 만료되거나 Redis 장애로 중복 실행이 발생해도 `exchange_rates` native upsert가 최종 중복 방어선으로 동작한다.
- upsert는 row별 query가 아니라 날짜별 통화 목록을 bulk `VALUES` query 1회로 저장해 백필 시 DB round-trip을 줄인다.
- conflict가 발생해도 `rate`, `source`가 동일하면 update하지 않으므로 재백필 시 불필요한 write를 줄인다.
- Redisson client는 기존 Redis host/port를 사용하고, 운영 Redis password가 있는 경우도 반영한다.
- scheduler는 기본 `catch-up-days = 7`로 오늘 포함 최대 8일 범위의 미완료 날짜를 재확인하므로, 11시 30분 정시 수집 실패분도 다음 스케줄에서 자동 보정된다.
- 토요일/일요일은 기본 `skip-weekends = true` 정책으로 API 호출 없이 `NON_BUSINESS_DAY`로 기록해 반복 호출을 피한다.
- run 상태 전이는 별도 service의 `REQUIRES_NEW` 트랜잭션으로 처리해 self-invocation으로 트랜잭션이 빠지는 위험을 피했다.

남은 위험:

- Redis failover 시 lock 안전성은 운영 Redis topology에 의존한다.
- 환율 저장은 idempotent하므로 최악의 경우에도 데이터 중복보다 외부 API 중복 호출/로그 중복이 주된 영향이다.

판단: 적합.

## 3차 리뷰: 환율 정규화와 금액 무결성

- `deal_bas_r`의 comma를 제거하고 `BigDecimal`로 파싱한다.
- `JPY(100)`, `IDR(100)` 같은 100단위 통화는 100으로 나눠 외화 1단위당 KRW 금액으로 저장한다.
- 저장 row는 `baseCurrency = KRW`, `targetCurrency = 정규화 통화`, `source = KOREA_EXIM`으로 통일했다.
- `rate <= 0`, 숫자 파싱 실패, 비어 있는 통화/환율은 저장하지 않고 실패한다.
- provider 성공 응답이어도 정규화 row 수가 기본 20개 미만이거나 `USD`, `JPY`, `EUR` 필수 통화가 빠지면 저장하지 않고 실패한다.
- 주말은 고시 데이터가 없는 정상 케이스로 보고 원천 환율 row를 만들지 않으며, 거래 조회는 직전 고시 환율 fallback 정책을 사용한다.

판단: 적합.

## 4차 리뷰: API 실패 처리와 운영 설정

- result code `2`, `3`, `4`를 enum으로 분리해 data code 오류, auth key 오류, 일일 제한 초과를 로그에서 구분할 수 있게 했다.
- `auth-key`는 `KOREA_EXIM_EXCHANGE_RATE_AUTH_KEY` 환경변수/`secret.yml`로만 주입되며 기본값은 빈 문자열이다.
- scheduler/backfill이 disabled인 상태에서는 `auth-key`가 없어도 앱이 뜰 수 있다.
- 실제 수집 실행 시 `auth-key`가 없으면 client가 명확한 예외를 던진다.
- scheduler와 backfill은 기본 disabled로 두어 운영 기동 시 실수로 장기 백필이 반복되지 않게 했다.

판단: 적합.

## 5차 리뷰: 테스트와 회귀 위험

- 정규화 테스트로 comma 파싱, 100단위 통화 변환, 0 이하/비숫자 실패를 검증했다.
- import service 테스트로 정상 저장, 데이터 없음, 실패 result code를 검증했다.
- scheduler/backfill 테스트로 enabled 조건과 날짜 범위 호출을 검증했다.
- backfill 테스트로 긴 범위를 그대로 받아 완료되지 않은 날짜 전체를 수집 service에 위임하는지 검증했다.
- scheduler catch-up 테스트로 최근 기간 중 완료되지 않은 날짜만 자동 수집하는 경로를 검증했다.
- import run 테스트로 `RUNNING`, `SUCCESS`, `FAILED` 상태 기록과 attempt count 증가를 검증했다.
- 비영업일 테스트로 주말에는 API/client/upsert 호출 없이 `NON_BUSINESS_DAY`로 기록되는지 검증했다.
- bulk upsert repository 테스트로 빈 row는 DB를 호출하지 않고, 다중 row는 한 번의 `INSERT ... VALUES ... ON CONFLICT` query로 전달되는지 검증했다.
- bulk upsert repository 테스트로 동일 값 conflict에서는 update 조건이 붙는지 검증했다.
- 예외 격리 테스트로 한 날짜 수집 실패 후 다음 날짜 수집이 계속되는지 검증했다.
- Redisson lock 테스트로 lock 획득 성공/실패와 unlock 동작을 검증했다.
- `./gradlew test`를 반복 실행해 전체 테스트 통과를 확인했다.

남은 검증 공백:

- 실제 한국수출입은행 API smoke test는 `auth-key`와 네트워크 의존성이 있어 수행하지 않았다.
- native upsert는 단위 테스트로 SQL 실행까지 검증하지 않았다. 전체 테스트는 통과했고, SQL은 기존 partial unique index의 `ON CONFLICT ... WHERE deleted_at IS NULL` 패턴과 동일한 형태를 사용한다.

판단: 병합 전 실제 운영 `auth-key`로 수동 smoke test를 추가하면 더 좋지만, 현재 구현 범위에서는 수용 가능.
