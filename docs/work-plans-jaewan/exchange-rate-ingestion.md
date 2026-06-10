# 한국수출입은행 환율 데이터 수집 및 DB 구축 계획

## 배경

- GitHub Issue: #54 `feat: 한국수출입은행 환율 데이터 수집 및 DB 구축`
- 작업 브랜치: `feature/issue-54-exchange-rate-ingestion`
- 기준 브랜치: 최신 `origin/develop`
- 현재 거래 등록/수정 플로우는 외부 환율 API를 직접 호출하지 않고 `exchange_rates` 전역 테이블을 조회하는 정책으로 정리되어 있다.
- TogetherTrip은 한국 앱 기준으로 정산 기준 통화를 `KRW`로 고정한다.
- 거래 환율은 소비일 기준으로 조회하고, 해당 일자의 row가 없으면 `rate_date <= 기준일` 중 최신 환율을 사용한다.
- 운영 서버는 이중화될 예정이므로 스케줄러 중복 실행, 외부 API 중복 호출, DB 중복 저장을 함께 방어해야 한다.
- 운영 관리 최소화를 위해 정시 수집 실패분은 다음 스케줄 실행에서 자동으로 보정한다.

## 현재 기준 확인

- `exchange_rates` 테이블은 이미 존재한다.
- `(base_currency, target_currency, rate_date)` 기준 unique index가 있어 날짜/통화 중복 저장을 DB에서 방어할 수 있다.
- `TransactionExchangeRateResolver`는 `baseCurrency = KRW`, `targetCurrency = 요청 통화`, `rateDate <= 기준일` 조건의 최신 row를 조회한다.
- `KRW` 거래는 환율 DB 조회 없이 `1.000000`을 사용한다.
- 외부 API 연동, 수집 스케줄러, one-shot 백필, 분산락은 아직 없다.

## 추천 구현 조합

- 수집 실행: Spring `@Scheduled`
- 실패 보정: scheduler 실행 시 최근 `exchange-rate.scheduler.catch-up-days`일과 오늘을 포함한 기간에서 `exchange_rate_import_runs`가 `SUCCESS`가 아닌 날짜를 자동 수집
- 수집 정합성 원장: `exchange_rate_import_runs`에 provider/date별 `PENDING`, `RUNNING`, `SUCCESS`, `NO_DATA`, `FAILED`와 시도 횟수, row 수, upsert 수, 마지막 오류를 기록
- 서버 이중화 중복 실행 방지: Redisson `RLock` 기반 Redis 분산락
- 저장 idempotency: PostgreSQL native `INSERT ... ON CONFLICT ... DO UPDATE`
- 초기 데이터 확보: property-gated `CommandLineRunner` one-shot 백필
- 수집 핵심 로직: `ExchangeRateImportService`로 분리해 스케줄러와 백필이 같은 정책을 공유

이 조합을 선택하는 이유:

- 별도 서버, Lambda, Spring Batch, Quartz 없이 현재 Spring Boot API 서버 안에서 구현할 수 있다.
- 서버 이중화 환경에서도 한 인스턴스만 스케줄 작업을 수행하게 할 수 있다.
- Redisson은 Redis 분산락에 특화된 `RLock`, lease time, watchdog, 안전한 unlock API를 제공하므로 Jedis로 직접 `SET NX PX`/Lua unlock을 구현하는 것보다 구현 위험이 낮다.
- 락이 만료되거나 수동 백필이 겹쳐도 DB upsert가 최종 중복 방어선이 된다.
- 한 날짜의 여러 통화 row는 bulk `VALUES` upsert 1회로 저장해 DB round-trip을 줄인다.
- 기본 `catch-up-days = 7`이므로 매일 실행 시 오늘 포함 최대 8일 범위를 확인하고, `SUCCESS`가 아닌 날짜를 자동으로 다시 호출한다.
- 기본 검증 기준은 `minimum-row-count = 20`, `required-currencies = USD,JPY,EUR`이다. 기준 미달이면 저장하지 않고 `FAILED`로 남긴다.
- 추후 Lambda나 별도 worker로 이관하더라도 API client, normalizer, import service 정책을 재사용하기 쉽다.

## 제외 범위

- AWS Lambda 기반 환율 수집 파이프라인
- 별도 환율 수집 서버
- Spring Batch 도입
- Quartz clustered scheduler 도입
- 거래 등록/수정 요청 중 외부 환율 API 직접 호출
- Flyway migration을 통한 대량 환율 seed 데이터 삽입
- 관리자용 백필 HTTP API

## 브랜치 및 미병합 작업 충돌 판단

- #54 브랜치는 최신 `origin/develop` 기준으로 유지한다.
- 미병합 작업 중 직접 충돌 가능성이 있는 파일은 `build.gradle.kts`, `application-local.yml`, `application-prod.yml`이다.
- `build.gradle.kts`는 Redisson 의존성 추가 위치가 로깅 AOP 의존성과 가까울 수 있으므로 rebase 시 additive conflict를 확인한다.
- `application-*.yml`에는 `exchange-rate:` 최상위 설정 블록을 분리해 게시글 첨부 설정 등 다른 작업과 충돌 가능성을 낮춘다.
- 환율 수집 코드는 신규 패키지/파일 중심으로 작성해 정산, 게시글, 거래 응답 DTO 작업과 직접 충돌하지 않게 한다.
- migration 번호는 현재 최신 기준에서 `V6__...` 후보로 작성하되, 다른 migration이 먼저 병합되면 rebase 시 번호를 조정한다.

## TogetherTrip 에이전트 판단

### Planner

- 이번 작업은 #54 범위의 1차 MVP로 본다.
- 목표는 한국수출입은행 현재환율 API 데이터를 `exchange_rates`에 안정적으로 적재하는 것이다.
- 거래 쓰기 플로우는 이미 DB 조회 정책으로 분리되어 있으므로, 이번 작업은 환율 원천 DB를 채우는 수집 파이프라인에 집중한다.
- 운영 자동 적재와 초기 one-shot 백필은 같은 import service를 사용해 정책 차이를 만들지 않는다.
- 실패 원인, 누락일, 부분 실패를 로그와 테스트로 추적할 수 있게 한다.

### Architect

아키텍처 판단:
`exchange_rates`는 여행별 데이터가 아니라 거래 플로우가 공통으로 조회하는 전역 환율 원천 데이터다. 따라서 환율 수집과 조회 기반 코드는 `trip` 하위가 아니라 top-level `exchange` feature로 분리한다. 패키지는 가장 엄격한 feature 내부 책임 기준으로 `exchange/client`, `exchange/config`, `exchange/domain`, `exchange/repository`, `exchange/service`, `exchange/service/normalizer`, `exchange/scheduler`, `exchange/support`로 나눈다.

영향받는 영역:
`exchange` 환율 도메인, 외부 API client, 설정 properties, scheduler, transaction 환율 조회 검증 테스트.

의존성 규칙:
Controller는 추가하지 않는다. 외부 API 호출은 `client`, 설정은 `config`, 저장 모델은 `domain`, DB 접근은 `repository`, 수집 orchestration은 `service`, provider 응답 정규화는 `service.normalizer`, 자동 실행은 `scheduler`, Redisson 분산락 adapter는 `support`에 둔다. 거래 서비스는 계속 `ExchangeRateRepository` 조회만 사용한다.

절충점:
Redisson 의존성이 추가되지만 Quartz나 Spring Batch보다 작고, 이중화 스케줄러 문제를 현재 요구에 맞게 해결한다. Jedis로 직접 분산락을 구현할 수도 있으나 lock token, TTL, 안전한 unlock, 재시도 정책을 직접 유지해야 하므로 #54 범위에서는 Redisson이 더 타당하다. native upsert는 DB 종속성이 있지만 이미 PostgreSQL/Flyway 기반 운영과 잘 맞고 idempotency를 명확히 보장한다.

필요한 테스트:
API result code 처리, `deal_bas_r` 파싱, 100단위 통화 정규화, upsert 재실행, 백필 범위 반복, 스케줄러가 import service를 호출하는지 검증한다.

다음 추천 에이전트:
TDD Guide, Security Reviewer, Verify Agent.

### TDD Guide

- 먼저 순수 로직 테스트를 작성한다.
- `KoreaEximExchangeRateNormalizerTest`
  - comma 포함 `deal_bas_r`를 `BigDecimal`로 파싱한다.
  - `JPY(100)`, `IDR(100)`은 100으로 나눠 1단위당 KRW 환율로 저장한다.
  - 일반 통화는 그대로 저장한다.
  - 유효하지 않은 숫자, 0 이하 환율은 실패한다.
- `KoreaEximExchangeRateClientTest`
  - result code `1`, `2`, `3`, `4`, 빈 응답을 구분한다.
  - 인증 오류와 일일 제한 초과는 재시도해도 해결되지 않는 실패로 분류한다.
  - 데이터 없음은 해당 기준일 누락으로 로그 가능한 결과를 반환한다.
- `ExchangeRateImportServiceTest`
  - 정상 응답을 upsert repository에 전달한다.
  - 일부 통화 정규화 실패 시 전체 실패 또는 해당 row skip 정책을 테스트로 고정한다.
  - 동일 날짜 재실행이 중복 저장 없이 upsert 경로를 탄다.
- `ExchangeRateBackfillRunnerTest`
  - property가 꺼져 있으면 실행하지 않는다.
  - `from`, `to` 범위의 날짜를 순서대로 import한다.
- `ExchangeRateSchedulerTest`
  - enabled일 때만 import service를 호출한다.
  - scheduler 자체에는 복잡한 비즈니스 로직을 두지 않는다.

### Security Reviewer

- 환율 API `auth-key`는 `secret.yml` 또는 `KOREA_EXIM_EXCHANGE_RATE_AUTH_KEY` 환경변수로만 주입하고 코드, 테스트 fixture, 로그에 남기지 않는다.
- 외부 API 요청/응답 로그에는 `auth-key`를 포함하지 않는다.
- 실패 로그에는 날짜, provider, result code, 통화 정도만 남기고 민감한 설정값은 제외한다.
- 거래 금액 무결성을 위해 저장 환율은 항상 `외화 1단위당 KRW 금액`으로 정규화한다.
- `rate <= 0` 또는 파싱 실패 row는 저장하지 않는다.
- 거래 등록/수정 트랜잭션 안에서는 외부 네트워크 호출을 수행하지 않는 정책을 유지한다.
- 백필 runner는 property로 명시적으로 켰을 때만 실행한다. 운영에서 실수로 장기 백필이 매 기동마다 반복되지 않게 실행 조건을 엄격히 둔다.
- 서버 이중화 환경에서 Redisson lock과 DB upsert를 모두 사용해 중복 실행과 중복 저장을 이중 방어한다.

### Verify Agent

- 구현 후 `./gradlew test`를 실행한다.
- 검증 문서는 `docs/verifications-jaewan/exchange-rate-ingestion.md`에 작성한다.
- 검증 문서에는 실행 명령, 결과, 남은 위험, 외부 API 실제 호출 여부를 기록한다.
- 실제 한국수출입은행 API 호출 테스트는 `auth-key`와 네트워크 의존성이 있으므로 기본 단위 테스트에서는 mock client로 검증한다.
- 실제 API smoke test가 필요하면 별도 profile 또는 수동 실행 명령으로 분리하고, 실행 여부와 사유를 검증 문서에 남긴다.

## 구현 단계

1. 설정 구조 추가
   - `ExchangeRateProperties`를 추가한다.
   - `exchange-rate.korea-exim.base-url`, `auth-key`, `data-code`, timeout, scheduler/backfill enabled 설정을 둔다.
   - scheduler에는 `catch-up-days`를 둬 최근 누락 환율 자동 보정 범위를 숫자로 제어한다.
   - `application-local.yml`, `application-prod.yml`에는 환경변수 기반 placeholder만 추가한다.

2. Redisson 분산락 설정 추가
   - `build.gradle.kts`에 Redisson 의존성을 추가한다.
   - 기존 `spring.data.redis.host`, `spring.data.redis.port` 기반으로 `RedissonClient`를 구성한다.
   - scheduler/backfill 실행 전에 `RLock.tryLock(waitTime, leaseTime)`을 호출한다.
   - lock name은 `exchange-rate:import:korea-exim`을 기본값으로 둔다.
   - `leaseTime`은 외부 API timeout과 DB upsert 시간을 고려해 기본 10분으로 둔다.
   - 분산락 wrapper는 비즈니스 서비스와 분리해 `exchange.support.ExchangeRateDistributedLock`에 둔다.

3. 한국수출입은행 client 구현
   - WebClient 또는 기존 HTTP client 패턴을 따른다.
   - 요청 파라미터는 `authkey`, `searchdate`, `data`를 사용한다.
   - 응답 DTO는 provider 응답 형상 그대로 받고 내부 도메인 DTO로 변환한다.
   - result code별 예외/결과 타입을 분리한다.

4. 정규화 로직 구현
   - `cur_unit`에서 `(100)` 단위를 감지한다.
   - `JPY(100)`, `IDR(100)` 등은 `deal_bas_r / 100`으로 변환한다.
   - `deal_bas_r` comma를 제거한 뒤 `BigDecimal`로 파싱한다.
   - 저장 값은 `baseCurrency = KRW`, `targetCurrency = 정규화 통화`, `rate = 외화 1단위당 KRW`로 통일한다.
   - `source = KOREA_EXIM`으로 저장한다.
   - 정규화 구현은 provider별 변환 책임으로 보고 `exchange.service.normalizer`에 둔다.

5. upsert repository 구현
   - native query로 `exchange_rates`에 idempotent bulk upsert를 수행한다.
   - 한 날짜의 통화 row 목록은 `NamedParameterJdbcTemplate`과 다중 `VALUES` 구문으로 한 번에 저장한다.
   - conflict target은 `base_currency`, `target_currency`, `rate_date`를 사용한다.
   - 기존 row가 있으면 `rate`, `source`, `updated_at`, `deleted_at` 복구 여부를 정책화한다.
   - 부분 unique index와 `ON CONFLICT` 호환성을 확인하고 필요하면 unique constraint 또는 index 구조 보강 migration을 검토한다.

6. import run 원장 구현
   - `exchange_rate_import_runs` 테이블을 추가한다.
   - `(provider, rate_date)` partial unique index로 날짜별 수집 상태를 하나로 제한한다.
   - `attempt_count`, `row_count`, `upsert_count`, `last_result_code`, `last_error_message`, `started_at`, `finished_at`을 기록한다.
   - run 상태 전이는 별도 service에서 `REQUIRES_NEW` 트랜잭션으로 처리해 외부 API 호출/DB 저장 실패와 상태 기록을 분리한다.

7. import service 구현
   - `importByDate(rateDate)`를 중심 메서드로 둔다.
   - client 호출, result code 판단, 정규화, upsert, 로그 기록을 순서대로 수행한다.
   - 데이터 없음과 오류를 구분한다.
   - 날짜별 예외를 `ExchangeRateImportResult.Error`로 변환해 한 날짜 실패가 이후 날짜 수집을 막지 않게 한다.
   - provider 성공 응답이어도 정규화 row 수가 `minimum-row-count`보다 작거나 필수 통화가 빠지면 실패로 처리한다.
   - 일부 row 실패 정책은 초기에 보수적으로 전체 실패를 추천하되, API가 부분적으로 이상한 row를 줄 가능성을 고려해 실패 row 로그 후 skip 대안도 검토한다.

8. one-shot 백필 runner 구현
   - `exchange-rate.backfill.enabled=true`일 때만 실행한다.
   - `from`, `to`가 없으면 실행하지 않고 명확한 설정 오류로 실패한다.
   - 같은 날짜 범위를 재실행해도 upsert로 idempotent하게 동작한다.
   - `exchange_rate_import_runs.SUCCESS`인 날짜는 API 호출 없이 건너뛴다.
   - 기본 `max-days-per-run = 31`로 긴 백필 범위 중 `SUCCESS`가 아닌 날짜를 앞에서부터 최대 31일만 처리한다.
   - 기본 `pause-between-requests = 300ms`로 provider/API/DB에 가는 연속 부하를 낮춘다.
   - 운영 기동마다 반복되는 사고를 막기 위해 기본값은 항상 disabled로 둔다.

9. 운영 scheduler 구현
   - `exchange-rate.scheduler.enabled=true`일 때만 실행한다.
   - `Asia/Seoul` 기준 현재 날짜를 기준으로 최근 `catch-up-days`일과 오늘을 확인한다.
   - 기본값은 `catch-up-days = 7`이며, 오늘 포함 최대 8일 범위에서 `exchange_rate_import_runs`가 `SUCCESS`가 아닌 날짜만 수집한다.
   - 한국수출입은행 데이터 공개 지연을 고려해 운영 실행 시각은 오전 늦은 시간 또는 오후로 둔다.
   - Redisson `RLock`으로 이중화 서버 중 하나만 실행하게 한다.

10. 거래 플로우 회귀 확인
   - `TransactionExchangeRateResolver`가 외부 API client를 의존하지 않는지 확인한다.
   - `KRW` 거래는 DB 조회 없이 `1.000000`을 유지한다.
   - 외화 거래는 `rateDate <= 기준일` 최신 row 조회 정책을 유지한다.

11. 문서 및 검증
    - 구현 후 검증 문서를 작성한다.
    - `./gradlew test`를 실행한다.
    - 실패 시 로그 기준으로 최소 수정한다.

## 데이터 및 실패 정책

- 정상 저장:
  - `base_currency = KRW`
  - `target_currency = USD`, `JPY`, `IDR` 등
  - `rate = targetCurrency 1단위당 KRW`
  - `rate_date = API searchdate`
  - `source = KOREA_EXIM`

- 데이터 없음:
  - 해당 날짜는 저장하지 않는다.
  - scheduler 로그에 provider/date/result를 남긴다.
  - `exchange_rate_import_runs`에는 `NO_DATA`로 기록한다.
  - 다음 scheduler 실행에서도 `catch-up-days` 범위 안에 있고 `SUCCESS`가 아니므로 다시 자동 수집 대상이 된다.
  - 거래 조회는 기존 정책대로 이전 날짜 최신 환율을 사용한다.

- 인증 오류:
  - 즉시 실패 로그를 남긴다.
  - retry로 해결되지 않는 설정 오류로 본다.

- 일일 제한 초과:
  - 실패 로그를 남기고 다음 스케줄 실행에서 `catch-up-days` 범위 안의 누락일로 자동 재시도한다.
  - 중복 실행 방지를 통해 제한 초과 가능성을 낮춘다.

- 부분 실패:
  - 저장 대상 row 정규화 중 실패가 있으면 해당 날짜 실행을 실패시킨다.
  - 정규화 row 수가 기본 20개 미만이거나 `USD`, `JPY`, `EUR` 중 하나라도 없으면 저장하지 않고 실패로 기록한다.
  - 한 날짜 실패는 이후 날짜 수집을 막지 않는다.

## 수동 백필 실행 방법

백필은 HTTP API가 아니라 `CommandLineRunner`로 실행된다. 따라서 `exchange-rate.backfill.enabled=true`와 날짜 범위를 설정한 뒤 앱을 기동하면 시작 시 1회 실행된다.

로컬에서 2026년 6월 3일부터 2026년 6월 10일까지 8일치를 백필하려면 다음 값으로 실행한다.

```bash
EXCHANGE_RATE_BACKFILL_ENABLED=true \
EXCHANGE_RATE_BACKFILL_FROM=2026-06-03 \
EXCHANGE_RATE_BACKFILL_TO=2026-06-10 \
./gradlew bootRun --args='--spring.profiles.active=local'
```

현재 `secret.yml` 기본값으로 같은 범위를 켜둔 경우에는 아래 명령만으로 실행된다.

```bash
./gradlew bootRun --args='--spring.profiles.active=local'
```

실행 숫자 기준:

- `from=2026-06-03`, `to=2026-06-10`이면 총 8일을 순서대로 수집한다.
- 기본 `max-days-per-run = 31`이므로 전체 범위가 길어도 `SUCCESS`가 아닌 날짜 중 앞에서부터 최대 31일만 처리한다.
- 이미 `exchange_rate_import_runs.SUCCESS`인 날짜는 처리 개수에 포함하지 않고 API 호출 없이 건너뛴다.
- 예를 들어 `from=2015-01-01`, `to=2026-06-11`, `max-days-per-run=1000`이고 2019년 12월 31일까지 `SUCCESS`라면 2020년 1월 1일부터 `SUCCESS`가 아닌 날짜 최대 1000개를 처리한다.
- 날짜별 수집 사이에는 기본 300ms pause를 둔다.
- 날짜별 API timeout 기본값은 10초다.
- Redisson lock name은 `exchange-rate:import:korea-exim`이다.
- lock wait time은 0초이므로 다른 서버가 이미 수집 중이면 즉시 건너뛴다.
- lock lease time은 10분이다.
- 저장은 `(base_currency, target_currency, rate_date)` 기준 upsert라 같은 범위를 다시 실행해도 중복 row는 만들지 않는다.
- 수집 성공/실패 이력은 `exchange_rate_import_runs`에 provider/date별로 남는다.

백필 실행 후에는 운영 기동 때마다 같은 범위가 반복 호출되지 않도록 반드시 `EXCHANGE_RATE_BACKFILL_ENABLED=false`로 내리거나 `secret.yml` 기본값을 false로 되돌린다. 중복 저장은 upsert로 막히지만 외부 API 호출은 다시 발생한다.

## 테스트 계획

- 단위 테스트
  - 한국수출입은행 응답 DTO 변환
  - result code별 실패 처리
  - comma 제거 및 `BigDecimal` 파싱
  - 100단위 통화 정규화
  - `rate <= 0` 거부
  - import service orchestration
  - backfill runner enabled/disabled
  - scheduler enabled/disabled

- repository 테스트
  - 동일 날짜/통화 upsert 재실행 시 row가 중복되지 않는다.
  - 기존 row의 `rate`, `source`, `updated_at`이 갱신된다.
  - soft-deleted row 처리 정책이 의도대로 동작한다.

- 회귀 테스트
  - 소비일 기준 이하 최신 환율 조회가 유지된다.
  - 환율 데이터가 없으면 `EXCHANGE_RATE_NOT_READY`가 유지된다.
  - `KRW` 거래는 환율 DB를 조회하지 않는다.

## 남은 질문

- 한국수출입은행 API result code의 실제 의미를 공식 문서 기준으로 한 번 더 확인해야 한다.
- 운영 스케줄 시간은 몇 시로 둘지 결정이 필요하다. 기본 제안은 `Asia/Seoul` 기준 평일/매일 오후 시간대다.
- 주말/공휴일 데이터 없음을 정상 로그로 볼지, warning으로 볼지 정책 결정이 필요하다.
- 기존 partial unique index가 native `ON CONFLICT` target으로 충분한지 실제 PostgreSQL에서 확인해야 한다.
- `deleted_at`이 있는 기존 row를 upsert 시 복구할지, 새 insert를 허용할지 정책 결정이 필요하다.
- 백필 범위는 최초 운영 반영 시 어느 기간까지 확보할지 결정이 필요하다.
- Redis 단일 노드 장애/Redis failover 시 lock 안전성은 운영 Redis topology에 의존한다. 그래도 `exchange_rates` upsert를 최종 방어선으로 유지한다.

## 완료 기준

- 한국수출입은행 현재환율 API 응답을 정규화해 `exchange_rates`에 저장할 수 있다.
- 동일 날짜/통화 재실행 시 중복 row가 생기지 않는다.
- 서버 이중화 환경에서 스케줄러 중복 실행을 방지한다.
- 초기 백필을 property로 명시적으로 실행할 수 있고 재실행해도 idempotent하다.
- 거래 등록/수정 플로우는 외부 API가 아니라 DB 환율만 사용한다.
- API 오류, 인증 오류, 일일 제한 초과, 데이터 없음이 구분되어 처리된다.
- 관련 테스트와 검증 문서가 완료된다.
