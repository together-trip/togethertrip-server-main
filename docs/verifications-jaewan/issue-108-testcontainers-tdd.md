# 이슈 #108 Testcontainers 기반 TDD 검증 현황

## 문서 목적

이 문서는 이슈 #108 구현 중 각 TDD 루프의 기준선, 실패 원인, 코드 수준 변경, 수치 변화와 남은 위험을 누적한다. 완료 결과만 사후 작성하지 않고 실행 직후 갱신한다.

## 목표와 현재 상태

| 항목 | 시작 상태 | 목표 | 현재 상태 |
|---|---:|---:|---:|
| 테스트 메서드 | 392개 | 약 650~800개 예상 | 569개 |
| LINE coverage | 78.7292% | 90% 지향 | 90.0065% |
| BRANCH coverage | 63.1155% | 80% 지향 | 80.0752% |
| Testcontainers 통합 테스트 | 0개 | 위험 경로 80~130개 예상 | 70개 |
| 고정 PostgreSQL/Redis 포트 의존 | 있음 | 없음 | 테스트 코드·설정에서 제거 |
| 반복 실행 flaky test | 미측정 | 0개 | 핵심 동시성 suite 5/5 통과, 실패·timeout 0 |
| Mutation score | 미측정 | 기준선 후 ratchet | 83%(115개 중 PIT 판정 killed 95, core gate 83%) |

테스트 개수와 coverage 목표는 방향을 잡기 위한 예상치다. 의미 없는 DTO·getter 테스트로 숫자를 채우지 않고 실제 결함과 경계 조건을 우선한다.

## 현재 구조 감사

- 운영 Kotlin 파일: 417개
- 테스트 파일: 68개
- 테스트 메서드: 약 392개
- Flyway migration: 21개
- Redis 기반 구현: 8개
- native SQL/JDBC 기반 구현: 6개
- `@SpringBootTest` 클래스: 5개
- CI는 `postgis/postgis:17-3.5`, `redis:7` service를 고정 포트로 실행한다.
- `application-test.yml`도 `127.0.0.1:5432`, `127.0.0.1:6379`를 사용한다.

## 검증 매트릭스

| 영역 | 검증할 실제 동작 | 상태 |
|---|---|---|
| Flyway | 빈 PostGIS DB에 V1부터 최신 migration 적용 후 JPA validation | 통과 |
| PostgreSQL | `ON CONFLICT`, partial index, `jsonb`, native aggregate query | `jsonb`·aggregate 회귀 및 upsert·partial index 계약 통과, 정산 native query 진행 중 |
| Transaction | rollback, unique constraint, 동시 요청 최종 상태 | trip optimistic version 20라운드, 생성·수정·삭제 경합 각 10라운드와 allocation/event ledger 계약 통과 |
| Redis | Lua compare-and-swap, TTL, rate limit, key 삭제 | refresh·signup lock·전화번호/place rate limit·인증 실패 횟수 통과 |
| Redisson | 단일 lock 획득, 소유자 해제, 경합 | 16-way 단일 실행·예외 해제·thread 소유권 통과 |
| 정산 | 금액 불변식, 중복 확정 방지, 상태 수렴 | 송금 query·멱등 update 및 서비스 확정 10라운드 상태 수렴 통과 |
| 인증·권한 | refresh rotation, signup lock, 접근 거부 | refresh/logout·signup lock 및 HTTP 401/403/404, 정산 접근 분기 8개 통과 |
| Outbox | payload `jsonb`, 중복 생성, claim·복구 | `jsonb`, 2-worker skip-locked claim, publish 실패 후 복구와 최대 5회 제한 통과 |
| HTTP | security filter, validation, exception-to-status | 실제 filter/AOP/validation 7개 통과 |

## 루프 기록

### 루프 0 — 기준선 재측정

- 상태: 완료
- 목적: 캐시되지 않은 clean 실행으로 테스트 수, LINE/BRANCH coverage, 실행 시간을 고정한다.
- 실행:
  - `./gradlew clean test jacocoTestReport jacocoTestCoverageVerification --rerun-tasks`
- 결과:
  - BUILD SUCCESSFUL
  - 테스트 392개, failures 0, errors 0, skipped 0
  - LINE 78.7292% (`covered=7199`, `missed=1945`)
  - BRANCH 63.1155% (`covered=1333`, `missed=779`)
  - METHOD 71.3610%, CLASS 90.7368%
  - clean 실행 시간 2분 13초
  - 기존 공식 기준선 LINE 78.09%, BRANCH 60.18%보다 각각 0.6392%p, 2.9355%p 높지만 아직 ratchet은 변경하지 않는다.
  - 현재 성공은 로컬 `127.0.0.1:5432`, `127.0.0.1:6379` 서비스가 실행 중인 환경에서 얻은 결과다.

### 루프 1 — Testcontainers 기반

- 상태: 진행 중
- acceptance test:
  - 빈 PostGIS container에서 Flyway current version이 21인지 확인
  - PostGIS extension query가 실행되는지 확인
  - Redis container에 실제 write/read/delete가 가능한지 확인
- 최초 실패:
  - 명령: `./gradlew test --tests 'com.togethertrip.main.global.config.TestcontainersInfrastructureTest' --rerun-tasks`
  - 단계: `compileTestKotlin`
  - 오류: `Unresolved reference 'withUsername'`
  - 원인: Testcontainers의 self-referential generic과 Kotlin `PostgreSQLContainer<Nothing>`을 fluent chain으로 연결하면서 반환 타입이 소실됐다.
- 최소 수정:
  - `PostgreSQLContainer` 설정을 `apply` 블록으로 변경해 fluent 반환 타입에 의존하지 않는다.
- 재검증:
  - infrastructure acceptance test 2개 통과
  - 전체 단위 테스트 382개 통과
  - 전체 Testcontainers 통합 테스트 12개 통과
  - failures 0, errors 0, skipped 0
  - JDBC metadata URL이 PostGIS container JDBC URL과 일치함을 assertion으로 확인
  - Spring Redis connection의 host·port가 Redis container mapped host·port와 일치함을 assertion으로 확인
  - Flyway current version 21과 `postgis_version()` query 확인
- task 분리 후 실패:
  - 명령: `./gradlew clean test integrationTest jacocoTestReport jacocoTestCoverageVerification --rerun-tasks`
  - `test`, `integrationTest` 자체는 통과했다.
  - JaCoCo가 `build/test-results/test/binary`를 execution data로 읽으려 해 report와 verification이 실패했다.
  - 원인: Gradle 9.4에서 `executionData(Test task)`가 Test task의 JaCoCo 파일 외 output까지 포함했다.
- 최소 수정:
  - JaCoCo 입력을 `build/jacoco/*.exec`로 제한해 `test.exec`, `integrationTest.exec`만 집계한다.
- 최종 실행:
  - `./gradlew clean test integrationTest jacocoTestReport jacocoTestCoverageVerification --rerun-tasks`
  - BUILD SUCCESSFUL, 39초
  - LINE 78.7292%, BRANCH 63.1155%로 기준선 유지
  - 단위·통합 실행 데이터가 각각 `build/jacoco/test.exec`, `build/jacoco/integrationTest.exec`에 생성되고 통합 report에 반영됨
  - CI의 고정 PostgreSQL·Redis service 정의를 제거하고 `./gradlew check jacocoTestReport`로 통일
- 상태: 완료

### 루프 2 — 환율 upsert와 partial unique index 계약

- 상태: 완료
- 목적: mock이 SQL 문자열만 확인하던 `ExchangeRateUpsertRepository`를 실제 PostgreSQL 17/PostGIS에서 검증한다.
- 사전 공백:
  - 기존 `ExchangeRateUpsertRepositoryTest`는 `NamedParameterJdbcTemplate`을 mock 처리해 PostgreSQL의 `ON CONFLICT ... WHERE deleted_at IS NULL` 실행 결과를 증명하지 못했다.
  - 특히 동일 키 재수집, 값 변경, soft delete 이후 재삽입이 partial unique index와 함께 동작하는지는 미검증 상태였다.
- 추가한 계약 테스트 4개:
  - 서로 다른 환율 2건을 bulk insert하면 affected row와 active row가 모두 2인지 확인
  - 동일 값을 재수집하면 affected row가 0이고 기존 active row 1건이 유지되는지 확인
  - 환율 또는 출처가 바뀌면 같은 row id를 유지하며 실제 값이 갱신되는지 확인
  - 기존 row를 soft delete한 뒤 같은 business key를 넣으면 새 active row가 생성되고 전체 2건·active 1건인지 확인
- 실행:
  - `./gradlew integrationTest --tests 'com.togethertrip.main.exchange.repository.ExchangeRateUpsertRepositoryIntegrationTest' --rerun-tasks`
- 결과:
  - BUILD SUCCESSFUL, 24초
  - 테스트 4개, failures 0, errors 0, skipped 0
  - `ON CONFLICT`와 partial unique index 조합이 예상 계약을 충족해 운영 코드 수정은 발생하지 않았다.
  - 이는 Red product defect가 아니라 기존 mock-only 검증 공백을 실제 DB characterization test로 닫은 결과다.

### 루프 3 — 정산 송금 native query와 동시 상태 수렴

- 상태: 완료
- 목적: mock service test만 존재하던 `SettlementTransferRepository`의 projection, 필터, soft delete, 멱등 update와 실제 PostgreSQL row-lock 수렴을 검증한다.
- Red 1 — fixture 결함 분리:
  - 최초 실행은 6개 중 3개 실패였다.
  - 2개는 `(settlement_id, sender_participant_id, receiver_participant_id)` unique constraint에 동일 pair를 중복 생성한 테스트 fixture 결함이었다.
  - sender/receiver 조합을 실제 schema가 허용하는 서로 다른 pair로 바꿔 테스트 전제 오류를 제거했다.
- Red 2 — 운영 결함 확정:
  - fixture 수정 후 6개 중 1개만 실패했다.
  - `findTransferRowById`가 삭제된 settlement에 속한 transfer projection을 반환했다.
  - 동일 원인으로 `findTransferRowsBySettlementId`도 부모 settlement의 soft delete를 확인하지 않았다.
- 최소 운영 수정:
  - 두 native query의 `where transfer.deleted_at is null` 다음에 `and settlement.deleted_at is null`을 추가했다.
  - API 계약·DB schema·정산 계산 정책은 변경하지 않았다.
- 계약 테스트 6개:
  - 확정 settlement의 non-deleted transfer만 완료 요약에 집계
  - participant display name과 linked user 상태를 native projection으로 반환
  - settlement·participant·status 필터 동시 적용
  - deleted settlement의 단건·목록 projection 차단
  - 동일 side 확인의 순차 멱등성 및 잘못된 participant 갱신 차단
  - 양측 순차 확인의 `COMPLETED` 수렴과 최초 `completed_at` 보존
- 동시성 테스트 2개:
  - 동일 sender 확인 16개 transaction을 latch로 동시에 시작: affected-row 합계 1, 최종 `SENDER_CONFIRMED`, `completed_at` null
  - sender/receiver 별도 transaction을 동시에 시작하는 라운드 20회: 각 side affected-row 1, 20/20 모두 `COMPLETED`, 양측 timestamp 보존, `completed_at`은 마지막으로 상태를 완성한 transaction 시각 중 하나
- 실행:
  - `./gradlew integrationTest --tests 'com.togethertrip.main.settlement.repository.SettlementTransferRepositoryIntegrationTest' --tests 'com.togethertrip.main.settlement.repository.SettlementTransferRepositoryConcurrencyTest' --rerun-tasks`
- 결과:
  - BUILD SUCCESSFUL, 27초
  - 테스트 8개, failures 0, errors 0, skipped 0
  - 동시 update 호출 56회: same-side 16회 + opposite-side 40회
  - 동시 수렴 검증 라운드 21회: same-side 1회 + opposite-side 20회

### 루프 4 — 정산 공유 토큰 원자적 최초 발급

- 상태: 완료
- 목적: `update settlements ... where share_token is null`이 별도 transaction 경합에서도 단 하나의 토큰만 채택하는지 검증한다.
- 동시성 구성:
  - 서로 다른 token 16개와 timestamp 16개를 준비한다.
  - 16-thread executor와 start latch로 각 `updateShareTokenIfAbsent` transaction을 동시에 시작한다.
  - affected-row 합계, 최종 저장 token, 후보 token 집합 포함 여부를 확인한다.
- 실행:
  - `./gradlew integrationTest --tests 'com.togethertrip.main.settlement.repository.SettlementTransferRepositoryConcurrencyTest' --rerun-tasks`
- 결과:
  - BUILD SUCCESSFUL, 26초
  - 해당 클래스 테스트 3개 모두 통과
  - 공유 토큰 update 16회 중 affected-row 합계 1
  - 최종 `share_token`은 경쟁에 참여한 16개 후보 중 정확히 하나
  - 운영 코드는 기존 conditional update로 계약을 충족해 수정하지 않았다.

### 루프 5 — refresh token Redis Lua·TTL·동시 rotation

- 상태: 완료
- 목적: `RefreshTokenService`의 Redis 값·TTL과 compare-and-swap Lua script를 실제 Redis 7에서 검증한다.
- 계약 테스트 4개:
  - 저장 값 일치·불일치와 delete, JWT refresh expiration과 Redis TTL 오차 0~2초
  - 기존 TTL 5초인 key를 정상 rotation하면 JWT refresh TTL로 재설정
  - 잘못된 current token은 새 값을 저장하지 않고 기존 값과 TTL을 보존
  - 동일 current token으로 새 token 16개가 동시에 rotation할 때 성공 1회
- 동시성 구성:
  - 16-thread executor와 start latch로 Lua script 호출을 동시에 시작한다.
  - 성공 future 수 1, Redis 최종 값이 16개 후보 중 하나, TTL이 refresh expiration과 0~2초 범위인지 확인한다.
- 실행:
  - `./gradlew integrationTest --tests 'com.togethertrip.main.auth.service.RefreshTokenServiceIntegrationTest' --rerun-tasks`
- 결과:
  - BUILD SUCCESSFUL, 25초
  - 테스트 4개, failures 0, errors 0, skipped 0
  - 동시 rotation 16회 중 성공 1회
  - 정상·실패 Lua 경로 모두 기존 운영 코드로 계약을 충족해 수정하지 않았다.

### 루프 6 — OAuth signup Redis lock 소유권·transaction·경합

- 상태: 완료
- 목적: `RedisOAuthSignupLock`의 `SET NX`와 token 비교 release Lua가 실제 Redis 경합 및 Spring transaction lifecycle에서 안전한지 검증한다.
- 계약 테스트 3개:
  - 16개 thread 동시 획득에서 block 진입 1개, `SIGNUP_CONFIRMATION_IN_PROGRESS` 15개
  - lock 획득 후 Redis token을 다른 소유자 값으로 교체하면 원래 소유자의 finally release가 key를 삭제하지 않음
  - transaction synchronization이 활성화된 경우 block 반환 직후에도 key가 유지되고 commit의 `afterCompletion` 이후 삭제됨
- 실행:
  - `./gradlew integrationTest --tests 'com.togethertrip.main.auth.service.oauth.RedisOAuthSignupLockIntegrationTest' --rerun-tasks`
- 결과:
  - BUILD SUCCESSFUL, 24초
  - 테스트 3개, failures 0, errors 0, skipped 0
  - 동시 획득 16회 결과 success 1, conflict 15
  - 다른 소유자 token 보존 및 commit 이후 release 확인
  - 기존 `SET NX` + owner-token Lua 구현이 계약을 충족해 운영 코드 수정은 없었다.

### 루프 7 — 전화번호 인증 rate limit 원자성

- 상태: 완료
- 목적: `PhoneVerificationRateLimiter`의 재요청 간격과 일일 횟수가 동시 요청에서도 순차 정책과 동일하게 적용되는지 검증한다.
- Red:
  - 16개 thread를 latch로 동시에 시작한 결과 기대 허용 1건과 달리 5건이 성공했다.
  - 성공 5건은 `DAILY_REQUEST_LIMIT=5`와 일치하며, 나머지는 `DAILY_LIMIT_EXCEEDED`로 진행됐다.
  - 원인은 `hasKey(rate)`, `INCR daily`, `SET rate EX`가 별도 Redis 명령이어서 여러 thread가 rate key 생성 전에 검사를 모두 통과한 것이다.
- 최소 운영 수정:
  - 세 단계와 TTL 설정을 `DefaultRedisScript<Long>` 한 번으로 묶었다.
  - script return을 `ALLOWED=0`, `RATE_LIMITED=1`, `DAILY_LIMITED=2`로 명시해 기존 두 BusinessException 정책을 보존했다.
  - 첫 daily increment에서만 2일 TTL을 설정하고, 허용된 요청에서만 60초 rate key를 생성한다.
- 계약 테스트 3개:
  - 첫 요청의 rate TTL 58~60초, daily TTL 172,798~172,800초와 즉시 재요청 차단
  - 16-way 동시 요청의 허용 1건·`REQUEST_TOO_SOON` 15건·daily count 1
  - rate key를 요청 사이 제거해 5회 허용 후 6번째 `DAILY_LIMIT_EXCEEDED`, daily count 6과 TTL 유지
- 실행:
  - Red: 동일 targeted integration test에서 2개 중 1개 실패, 동시 성공 actual 5
  - Green: `./gradlew integrationTest --tests 'com.togethertrip.main.auth.service.phone.PhoneVerificationRateLimiterIntegrationTest' --rerun-tasks`
- 결과:
  - BUILD SUCCESSFUL, 27초
  - 테스트 3개, failures 0, errors 0, skipped 0
  - 동시 허용 5/16에서 1/16으로 개선, 동일 요청 간격 우회 4건 제거

### 루프 8 — 장소 API rate limit 동시 count

- 상태: 완료
- 목적: Redis `INCR` 기반 `RedisPlaceRequestRateLimiter`가 operation별 key를 격리하고 동시 호출에서도 정확한 허용 수를 유지하는지 검증한다.
- 계약 테스트 2개:
  - 동일 user의 `AUTOCOMPLETE`와 `DETAIL` key·count가 각각 1이며 TTL 118~120초
  - `AUTOCOMPLETE` 64개 thread 동시 시작 시 limit 60개 성공·4개 `PLACE_RATE_LIMIT_EXCEEDED`, Redis 최종 count 64와 양수 TTL
- 실행:
  - `./gradlew integrationTest --tests 'com.togethertrip.main.place.service.RedisPlaceRequestRateLimiterIntegrationTest' --rerun-tasks`
- 결과:
  - BUILD SUCCESSFUL, 27초
  - 테스트 2개, failures 0, errors 0, skipped 0
  - 동시 64회에서 허용 60/64, 거부 4/64로 설정값과 일치
  - Redis `INCR` 기반 운영 구현이 계약을 충족해 수정하지 않았다.

### 루프 9 — Redisson 환율 수집 분산 lock

- 상태: 완료
- 목적: mock으로만 검증되던 `ExchangeRateDistributedLock`을 실제 Redis/Redisson에서 검증한다.
- 계약 테스트 3개:
  - wait time 0인 lock에 16개 thread 동시 경합: block 실행 1개, 획득 실패 null 15개
  - 획득한 block이 예외를 던져도 finally unlock되어 다음 호출이 즉시 획득
  - 한 thread의 수동 acquire 중 다른 thread는 false, 소유 thread release 이후 다른 thread가 true로 재획득
- 실행:
  - `./gradlew integrationTest --tests 'com.togethertrip.main.exchange.support.ExchangeRateDistributedLockIntegrationTest' --rerun-tasks`
- 결과:
  - BUILD SUCCESSFUL, 26초
  - 테스트 3개, failures 0, errors 0, skipped 0
  - 16-way block execution 1, skip 15
  - 테스트 코드의 nullable Java generic compile warning 1건은 즉시 non-null `Boolean` assertion으로 정리했으며 다음 전체 gate에서 재검증한다.
  - 운영 lock 구현 변경은 없었다.

### 루프 10 — 누적 clean 품질 게이트

- 상태: 완료
- 목적: PostgreSQL·Redis·Redisson 루프가 전체 단위 테스트와 함께 실행될 때 context/container 간섭, compile warning, JaCoCo 누락이 없는지 검증한다.
- 실행:
  - `./gradlew clean check jacocoTestReport --rerun-tasks`
  - JUnit XML의 suite별 test 수 직접 합산
  - JaCoCo XML의 report-level LINE/BRANCH counter 직접 확인
  - `git diff --check`
  - 내부 task URI 패턴이 남아 있는지 전체 범위 검색
- 결과:
  - BUILD SUCCESSFUL, 39초
  - 단위 테스트 382개 + Testcontainers 통합 테스트 40개 = 총 422개
  - failures 0, errors 0, skipped 0
  - LINE 79.6501% (`covered=7284`, `missed=1861`)
  - BRANCH 64.0832% (`covered=1356`, `missed=760`)
  - 기준선 대비 LINE +0.9209%p, BRANCH +0.9677%p
  - line covered +85, missed -84; branch covered +23, missed -19
  - `git diff --check` 통과
  - 내부 task URI 참조 0건
  - 직전 Redisson nullable generic warning은 재발하지 않았다.

### 루프 11 — 전화번호 인증 실패 임계 상태 수렴

- 상태: 완료
- 목적: `PhoneVerificationStore`의 실패 횟수와 verification 삭제가 5회 임계점의 동시 요청에서 한 번만 전이되도록 한다.
- Red:
  - 16-way 동시 실패에서 기대 `ATTEMPT_EXCEEDED` 1건과 달리 actual 12건이 발생했다.
  - 기존 `INCR`, 최초 `EXPIRE`, 임계 `DELETE`가 분리되어 여러 thread가 모두 임계 이상 count를 보고 중복 삭제했다.
- 최소 운영 수정:
  - verification key 존재 확인, attempt `INCR`, 최초 `PEXPIRE`, 임계 도달 시 두 key `DEL`을 단일 Lua script로 원자화했다.
  - script 결과를 `ATTEMPT_RECORDED=0`, `ATTEMPT_EXCEEDED=1`, `VERIFICATION_EXPIRED=2`로 분리했다.
  - 임계 삭제 이후 도착한 요청은 attempt key를 재생성하지 않고 기존 순차 흐름과 같은 `PHONE_VERIFICATION_CODE_EXPIRED`로 처리한다.
  - TTL은 초 단위 절삭을 피하기 위해 남은 Duration을 millisecond로 전달하고 최소 1ms를 보장한다.
- 계약 테스트 3개:
  - JSON state round-trip, TTL 58~60초, state·attempt 동시 delete
  - 순차 5번째 실패에서 `ATTEMPT_EXCEEDED`와 두 key 삭제
  - 16-way 동시 실패에서 recorded 4, exceeded 1, expired 11, 최종 key 0개
- 실행:
  - Red: 3개 중 동시성 1개 실패, exceeded actual 12
  - Green: `./gradlew integrationTest --tests 'com.togethertrip.main.auth.service.phone.PhoneVerificationStoreIntegrationTest' --rerun-tasks`
- 결과:
  - BUILD SUCCESSFUL, 25초
  - 테스트 3개, failures 0, errors 0, skipped 0
  - 임계 예외 12/16 → 1/16, 이후 만료 11/16, 최종 Redis key 0개

### 루프 12 — Outbox worker 중복 claim 차단

- 상태: 완료
- 목적: 두 dispatcher transaction이 같은 `PENDING` 이벤트를 동시에 읽어 중복 전송하지 않도록 DB claim 경계를 검증한다.
- Red:
  - 이벤트 10개를 만든 뒤 2개 transaction을 latch로 동시에 조회했다.
  - 두 worker의 ID 교집합이 10/10으로 동일 이벤트 전부를 중복 claim했다.
  - 기존 `findByStatusOrderByCreatedAtAsc`는 상태 조회만 하고 row lock이나 claim 상태 전이가 없었다.
- Green 검증 보강:
  - 최초 `PESSIMISTIC_WRITE` + lock-timeout hint Green은 실행 시간이 길어, 실제 skip이 아니라 잠금 대기 후 상태 재평가로 교집합이 사라졌을 가능성이 있었다.
  - 두 조회가 2초 안에 모두 완료되어야 한다는 assertion을 추가해 non-blocking 조건을 명시했다.
- 최소 운영 수정:
  - `findPendingForDispatch(limit)` native query에 `FOR UPDATE SKIP LOCKED`를 명시했다.
  - dispatcher가 이 claim query를 사용하도록 변경하고 기존 단위 테스트 3개의 mock 경계도 같은 method로 맞췄다.
  - Outbox schema, status enum, public dispatcher API는 변경하지 않았다.
- 동시성 구성:
  - committed PENDING 이벤트 10개
  - 2-thread, 각각 독립 Spring transaction
  - 두 조회가 완료될 때까지 transaction을 열어 lock 보유 구간을 겹치게 함
  - claim ID 교집합 0, 합집합 10을 assertion
- 실행:
  - Red: 교집합 actual 10
  - Green: `./gradlew integrationTest --tests 'com.togethertrip.main.global.outbox.repository.OutboxEventRepositoryConcurrencyTest' --rerun-tasks`
- 결과:
  - repository 동시성 BUILD SUCCESSFUL, 24초
  - dispatcher 단위 테스트 3개 BUILD SUCCESSFUL, 8초
  - 테스트 1개, failures 0, errors 0, skipped 0
  - 중복 claim 10/10 → 0/10, 누락 0/10
  - 두 claim query가 2초 barrier 안에 함께 완료되어 잠금 대기가 아닌 skip 동작임을 확인

### 루프 13 — 정산 확정 서비스 동시 요청 수렴

- 상태: 완료
- 목적: repository 단위가 아니라 실제 `SettlementService.confirmSettlement` transaction 전체가 동시 확정에서 snapshot·transfer·Outbox 중복 없이 수렴하는지 검증한다.
- 실제 fixture:
  - active owner/member 2명과 여행 1개
  - owner payment 10,000원
  - owner/member share 각 5,000원
  - 실제 native payment/share 집계, 계산기, participant projection, JSON snapshot, transfer 생성, trip optimistic version, Outbox publisher를 모두 사용
- 동시성 구성:
  - 각 라운드에서 독립 thread 2개를 latch로 동시에 시작
  - 총 10라운드, service transaction 20회
  - 성공/BusinessException뿐 아니라 라운드별 최종 DB row를 native query로 확인
- 실행:
  - `./gradlew integrationTest --tests 'com.togethertrip.main.settlement.service.SettlementServiceConcurrencyTest' --rerun-tasks`
- 결과:
  - BUILD SUCCESSFUL, 26초
  - 테스트 1개, failures 0, errors 0, skipped 0
  - 10/10 라운드에서 성공 1건 + `SETTLEMENT_ALREADY_CONFIRMED` 1건
  - 여행별 CONFIRMED settlement 1건, transfer 1건, `SETTLEMENT_CONFIRMED` Outbox 1건
  - 20개 요청에서 중복 snapshot·transfer·event 0건
  - 기존 partial unique index, trip optimistic version, 예외 변환이 계약을 충족해 운영 수정은 없었다.

### 루프 14 — 인증 서비스 refresh/logout 경쟁

- 상태: 완료
- 목적: Redis service 단위 Lua 검증을 넘어 실제 JWT 검증·user 조회·transaction proxy를 포함한 `AuthService` 경합을 검증한다.
- 계약 테스트 2개:
  - 동일한 유효 refresh JWT로 16개 `refreshToken` 요청을 동시에 시작
  - 동일 refresh JWT를 다시 저장한 뒤 `refreshToken`과 `logout`을 동시에 시작하는 라운드 20회
- 시간 경계:
  - JWT가 초 단위 `issuedAt`을 사용하므로 current token 발급 후 1.1초를 두어 새 refresh token이 current와 다른 서명인지도 assertion했다.
- 실행:
  - `./gradlew integrationTest --tests 'com.togethertrip.main.auth.service.AuthServiceConcurrencyTest' --rerun-tasks`
- 결과:
  - BUILD SUCCESSFUL, 28초
  - 테스트 2개, failures 0, errors 0, skipped 0
  - 동일 token refresh 16회: 성공 1, `INVALID_REFRESH_TOKEN` 15, 최종 Redis 값은 성공 응답 token
  - refresh/logout 20라운드: refresh 결과는 성공 또는 invalid의 허용 집합 안에 있고 20/20 최종 Redis key 없음
  - service transaction 56회(16 refresh + 20 refresh + 20 logout)에서 token 재사용 성공·logout 후 잔존 0건

### 루프 15 — 송금 확인 서비스 상태·Outbox 멱등성

- 상태: 완료
- 목적: repository update의 affected-row 멱등성이 `SettlementTransferService`의 Outbox 발행과 여행 정산 완료 상태까지 보장하는지 검증한다.
- 실제 fixture:
  - active sender/receiver user와 participant
  - `IN_PROGRESS` 여행, CONFIRMED settlement, PENDING transfer 1건
- 동시성 구성:
  - sender user의 `confirmAsSender` 16개를 latch로 동시 시작
  - 이어서 receiver user의 `confirmAsReceiver` 16개를 latch로 동시 시작
  - 총 service transaction 32회
- 실행:
  - `./gradlew integrationTest --tests 'com.togethertrip.main.settlement.service.SettlementTransferServiceConcurrencyTest' --rerun-tasks`
- 결과:
  - BUILD SUCCESSFUL, 25초
  - 테스트 1개, failures 0, errors 0, skipped 0
  - sender 응답 16/16 `SENDER_CONFIRMED`, sender-confirmed Outbox 1건
  - receiver 응답 16/16 `COMPLETED`, completed Outbox 1건
  - 최종 trip `SETTLED`
  - 중복 확인 32회에서 중복 Outbox 0건, 상태 역행 0건

### 루프 16 — 실제 HTTP security·권한·validation 경계

- 상태: 완료
- 목적: controller 단위 mock을 넘어서 실제 `SecurityFilterChain`, JWT filter, trip 권한 AOP, validation, global exception handler의 HTTP status/body 계약을 검증한다.
- 구성:
  - `@MainIntegrationTest` + `@AutoConfigureMockMvc`
  - 실제 PostGIS user/trip/participant fixture와 실제 access JWT
  - controller/service/security bean을 mock하지 않음
- 계약 테스트 6개:
  - 무인증 정산 확정 → 401 + `AUTHENTICATION_REQUIRED`
  - invalid Bearer token 송금 확인 → 401 + `AUTHENTICATION_REQUIRED`
  - active 일반 참여자의 owner-only 정산 확정 → 403 + `TRIP_OWNER_ONLY`
  - 비참여자의 정산 preview → 404 + `TRIP_PARTICIPANT_NOT_FOUND`
  - 빈 refresh token body → 400 + `INVALID_INPUT`
  - 유효 access token logout → 200 + 실제 Redis refresh key 삭제
- 실행:
  - `./gradlew integrationTest --tests 'com.togethertrip.main.global.security.SecurityHttpIntegrationTest' --rerun-tasks`
- 결과:
  - BUILD SUCCESSFUL, 26초
  - 테스트 6개, failures 0, errors 0, skipped 0
  - security filter·AOP·exception-to-status 6/6 일치
  - API 계약 변경 없이 현재 동작을 실제 HTTP 회귀로 고정했다.

### 루프 17 — 서비스 동시성·HTTP 포함 clean 품질 게이트

- 상태: 완료
- 목적: 추가된 service concurrency와 별도 MockMvc context까지 포함해 전체 gate·coverage·문서 위생을 재측정한다.
- 실행:
  - `./gradlew clean check jacocoTestReport --rerun-tasks`
  - JUnit XML suite별 test 수 및 JaCoCo report-level counter 직접 집계
  - `git diff --check`
  - 내부 task URI 패턴 전체 검색
- 결과:
  - BUILD SUCCESSFUL, 53초
  - 단위 테스트 382개 + Testcontainers 통합 테스트 54개 = 총 436개
  - failures 0, errors 0, skipped 0
  - LINE 82.0418% (`covered=7506`, `missed=1643`)
  - BRANCH 66.1169% (`covered=1403`, `missed=719`)
  - 시작 기준선 대비 LINE +3.3126%p, BRANCH +3.0014%p
  - 시작 대비 line covered +307, missed -302; branch covered +70, missed -60
  - `git diff --check` 통과
  - 내부 task URI 참조 0건

### 루프 18 — 핵심 동시성 suite 반복 안정성

- 상태: 완료
- 목적: 한 번의 성공이 아닌 반복 실행으로 scheduling 변화에 따른 flaky·timeout을 확인한다.
- 대상 5개 클래스, 회차당 테스트 8개:
  - `AuthServiceConcurrencyTest`
  - `OutboxEventRepositoryConcurrencyTest`
  - `SettlementTransferRepositoryConcurrencyTest`
  - `SettlementServiceConcurrencyTest`
  - `SettlementTransferServiceConcurrencyTest`
- 실행:
  - `integrationTest --tests '*ConcurrencyTest' --rerun-tasks`를 별도 Gradle process로 5회
  - 최초 sandbox 실행은 Gradle wrapper lock 파일 권한으로 테스트 시작 전 중단됐고, 승인된 동일 명령으로 다시 실행했다. 이는 test failure 집계에 포함하지 않는다.
- 회차 결과:
  - 1회 27초 성공
  - 2회 27초 성공
  - 3회 27초 성공
  - 4회 27초 성공
  - 5회 26초 성공
- 정량 결과:
  - 테스트 실행 40/40 통과(8개 × 5회)
  - failures 0, errors 0, skipped 0, timeout 0
  - 회차당 약 182개 동시 transaction/Redis 작업, 누적 약 910개 경합 작업
  - 각 회차 내부 opposite-side 20라운드, 정산 확정 10라운드, refresh/logout 20라운드가 반복됨
  - 관측된 flaky test 0개

### 루프 19 — OAuth 임시 세션 Redis 계약

- 상태: 완료
- 목적: JaCoCo LINE 약 21.7%였던 가입 임시 세션의 실제 JSON·TTL·만료 경계를 검증한다.
- 계약 테스트 3개:
  - 신규 Kakao session의 provider/user id/nickname/profile URL JSON round-trip과 TTL 598~600초
  - 기존 사용자 session의 `existingUserId` 보존
  - delete 이후 get이 `PHONE_VERIFICATION_TOKEN_EXPIRED` 반환
- 실행:
  - `./gradlew integrationTest --tests 'com.togethertrip.main.auth.service.oauth.OAuthTemporarySessionServiceIntegrationTest' --rerun-tasks`
- 결과:
  - BUILD SUCCESSFUL, 25초
  - 테스트 3개, failures 0, errors 0, skipped 0
  - 신규/기존 사용자 세션 및 명시 삭제 만료 경계 모두 실제 Redis에서 일치
  - 운영 코드 수정 없음

### 루프 20 — 공유 토큰 정산 조회 계약

- 상태: 완료
- 목적: JaCoCo LINE 약 22.7%였던 `SettlementShareService`의 token 공개 범위, snapshot 역직렬화, 송금 표시 상태를 실제 PostgreSQL에서 검증한다.
- 계약 테스트 4개:
  - CONFIRMED token의 snapshot balance 2건과 PENDING transfer 1건, 표시 상태 `IN_PROGRESS`
  - 모든 transfer가 COMPLETED이면 표시 상태 `COMPLETED`
  - DRAFT token은 `SETTLEMENT_NOT_CONFIRMED`
  - soft-deleted settlement token과 미존재 token은 `SETTLEMENT_SHARE_TOKEN_NOT_FOUND`
- 실제 검증 값:
  - total expense/share 각 10,000원
  - participant net +5,000원/-5,000원
  - public response의 sender/receiver display name과 transfer status
- 실행:
  - `./gradlew integrationTest --tests 'com.togethertrip.main.settlement.service.SettlementShareServiceIntegrationTest' --rerun-tasks`
- 결과:
  - BUILD SUCCESSFUL, 23초
  - 테스트 4개, failures 0, errors 0, skipped 0
  - CONFIRMED/DRAFT/deleted/missing token 경계 모두 계약 일치
  - 운영 코드 수정 없음

### 루프 21 — 전화번호 인증 service 정책 분기

- 상태: 완료
- 목적: JaCoCo LINE 약 18.8%였던 `PhoneVerificationService`의 SMS 경계와 인증 상태 전이를 빠른 단위 테스트로 검증한다.
- 계약 테스트 6개:
  - 요청 성공 시 6자리 code, 3분 TTL state 저장, 동일 code SMS 전송, expiresIn 180초
  - 이미 사용 중인 전화번호는 rate limiter와 SMS 호출 전에 차단
  - 정상 code는 state 삭제 후 hash/encryption version·암호문·masked number 반환
  - 만료 state는 삭제 후 `PHONE_VERIFICATION_CODE_EXPIRED`
  - code 불일치는 attempt 증가 후 `INVALID_PHONE_VERIFICATION_CODE`
  - 5번째 불일치는 store의 `PHONE_VERIFICATION_ATTEMPT_EXCEEDED`를 보존
- 테스트 fixture 수정 기록:
  - 최초 실행은 Kotlin non-null 인자에 Java Mockito matcher/captor가 null placeholder를 넣어 3개가 실패했다.
  - matcher를 통한 assertion 약화 대신 mock invocation의 실제 arguments를 읽어 token/state/TTL을 검증했다.
- 실행:
  - Green: `./gradlew test --tests 'com.togethertrip.main.auth.service.phone.PhoneVerificationServiceTest' --rerun-tasks`
- 결과:
  - BUILD SUCCESSFUL, 6초
  - 테스트 6개, failures 0, errors 0, skipped 0
  - 운영 코드 수정 없음

### 루프 22 — Trip optimistic version 동시 갱신

- 상태: 완료
- 목적: 거래 event/projection의 기준인 `Trip.expenseVersion`과 JPA `@Version`이 동일 version 동시 갱신에서 lost update를 막는지 검증한다.
- 동시성 구성:
  - 매 라운드 새로운 trip 생성
  - 독립 transaction 2개가 모두 같은 trip entity version을 load할 때까지 barrier 대기
  - 동시에 `advanceExpenseVersion()` 후 flush
  - 20라운드, 총 40 transaction
- 실행:
  - `./gradlew integrationTest --tests 'com.togethertrip.main.trip.repository.TripOptimisticLockConcurrencyTest' --rerun-tasks`
- 결과:
  - BUILD SUCCESSFUL, 23초
  - 테스트 1개, failures 0, errors 0, skipped 0
  - 20/20 라운드에서 성공 1 + optimistic conflict 1
  - 20/20 최종 `expense_version=1`, entity `version=1`
  - lost update 0건, 두 transaction 동시 성공 0건, 예상 밖 예외 0건
  - 기존 `@Version` 운영 구현 변경 없음

### 루프 23 — 인증·공유·trip version 포함 clean 품질 게이트

- 상태: 완료
- 목적: 최근 추가한 단위 6개·통합 8개와 기존 모든 loop가 함께 실행되는지, 합산 coverage가 실제로 상승했는지 검증한다.
- 실행:
  - `./gradlew clean check jacocoTestReport --rerun-tasks`
  - JUnit XML과 JaCoCo report-level counter 직접 집계
  - `git diff --check` 및 내부 task URI 패턴 검색
- 결과:
  - BUILD SUCCESSFUL, 54초
  - 단위 테스트 388개 + Testcontainers 통합 테스트 62개 = 총 450개
  - failures 0, errors 0, skipped 0
  - LINE 83.1894% (`covered=7611`, `missed=1538`)
  - BRANCH 66.8238% (`covered=1418`, `missed=704`)
  - 시작 기준선 대비 LINE +4.4602%p, BRANCH +3.7083%p
  - 시작 대비 line covered +412, missed -407; branch covered +85, missed -75
  - 직전 clean 대비 LINE +1.1476%p, BRANCH +0.7069%p
  - `git diff --check` 통과, 내부 task URI 참조 0건

### 루프 24 — Kakao OAuth 외부 응답 계약

- 상태: 완료
- 목적: 실제 Kakao 계정을 호출하지 않고 WebClient exchange stub으로 LINE 약 19.4%·BRANCH 0%였던 외부 인증 응답 경계를 검증한다.
- 계약 테스트 7개:
  - local-test ID token과 빈 ID의 swagger fallback, 외부 호출 0회
  - Kakao account profile이 properties보다 우선
  - account 값이 비면 properties nickname/image fallback
  - `/v2/user/me` 경로와 `Bearer` Authorization header
  - 401, empty 200, malformed JSON 200의 business error 변환
- Red:
  - 7개 중 malformed JSON 테스트 1개 실패
  - `tools.jackson` decode의 `DecodingException`이 BusinessException으로 변환되지 않아 전역 500 후보가 됐다.
- 최소 운영 수정:
  - Kakao 호출의 request/status 오류는 `WebClientException`, body decode 오류는 `DecodingException`으로 잡아 모두 `OAUTH_USER_INFO_FAILED`로 변환했다.
  - 성공 응답 mapping과 API 계약은 변경하지 않았다.
- 실행:
  - Green: `./gradlew test --tests 'com.togethertrip.main.auth.client.KakaoOAuthClientTest' --rerun-tasks`
- 결과:
  - BUILD SUCCESSFUL, 9초
  - 테스트 7개, failures 0, errors 0, skipped 0
  - malformed external response의 예상 HTTP 처리 후보가 500 → 기존 OAuth 실패 BusinessException 경로로 개선

### 루프 25 — 한국수출입은행 환율 HTTP 계약

- 상태: 완료
- 목적: 실제 외부 계정을 호출하지 않고 LINE 약 17.2%였던 환율 client의 request·결과 코드·empty/error 경계를 검증한다.
- 계약 테스트 7개:
  - 빈 auth key는 HTTP 호출 0회로 설정 오류
  - `authkey`, `searchdate=yyyyMMdd`, `data` query와 USD/JPY row decode
  - 빈 배열은 `NoData`
  - result 3은 `INVALID_AUTH_KEY`
  - 알 수 없는 result는 `UNKNOWN`
  - result null row는 importer validation이 판단하도록 `Success` 전달
  - HTTP 500은 WebClient exception으로 전달되어 상위 import service의 `Error` 변환 경계 유지
- 실행:
  - `./gradlew test --tests 'com.togethertrip.main.exchange.client.KoreaEximExchangeRateClientTest' --rerun-tasks`
- 결과:
  - BUILD SUCCESSFUL, 8초
  - 테스트 7개, failures 0, errors 0, skipped 0
  - 실제 한국수출입은행 호출·인증 정보 사용 0건
  - 운영 코드 수정 없음

### 루프 26 — 로그 민감정보 마스킹 분기

- 상태: 완료
- 목적: BRANCH 약 16.7%였던 로그 마스커에서 token·전화번호·JSON·요약 타입 누출을 검증한다.
- 추가 테스트 4개:
  - JSON password/refreshToken/apiSecret/code의 quote·key 유지 마스킹
  - 대소문자 alias와 `AUTHORIZATION=Bearer` 복합 token
  - E164/공백 국내 전화번호 마스킹과 주변 숫자 오탐 방지
  - null/scalar/enum/collection/map/array/object 요약과 collection 값 비노출
- Red:
  - 8개 전체 중 복합 authorization 테스트 1개 실패
  - key-value regex가 공백 전 `Bearer`만 `***`로 바꾼 뒤 실제 token 부분을 남겼고, 후속 Bearer regex는 prefix가 사라져 탐지하지 못했다.
- 최소 운영 수정:
  - `bearerTokenPattern`을 key-value pattern보다 먼저 적용해 prefix와 token 전체를 원자적으로 마스킹한다.
  - 기존 mask 문자열과 최대 길이는 변경하지 않았다.
- 실행:
  - Green: `./gradlew test --tests 'com.togethertrip.main.global.logging.SensitiveDataMaskerTest' --rerun-tasks`
- 결과:
  - BUILD SUCCESSFUL, 8초
  - 테스트 8개(기존 4 + 추가 4), failures 0, errors 0, skipped 0
  - `AUTHORIZATION=Bearer <token>`의 token 노출 1건 → 0건

### 루프 27 — Spring Security admin role 거부 경계

- 상태: 완료
- 목적: controller AOP의 403과 별개로 `SecurityFilterChain`의 `/api/admin/** hasRole('ADMIN')` 및 `CustomAccessDeniedHandler`를 실제 HTTP에서 검증한다.
- 계약 테스트:
  - 유효 USER access JWT로 `GET /api/admin/exchange-rates/backfills`
  - 기대 403 + JSON code `ACCESS_DENIED`
- 실행:
  - `./gradlew integrationTest --tests 'com.togethertrip.main.global.security.SecurityHttpIntegrationTest' --rerun-tasks`
- 결과:
  - BUILD SUCCESSFUL, 26초
  - HTTP security 통합 테스트 누적 7개 모두 통과
  - admin service 호출 전 role filter에서 차단됨
  - 운영 코드 수정 없음

### 루프 28 — 정산 계산 projection fallback·참여자 불변식

- 상태: 완료
- 목적: 정산 계산의 projection 최적화가 stale/empty 상태에서 원본 거래로 안전하게 fallback하고 참여자 누락을 숨기지 않는지 검증한다.
- 추가 테스트 5개:
  - projection empty → native payment/share row fallback
  - projection version 하나라도 mismatch → projection 전체 폐기 후 fallback
  - 계산 balance empty → participant repository 호출 0회와 빈 map
  - 계산 participant id의 projection row 누락 → `TRIP_PARTICIPANT_NOT_FOUND`
  - 계산에 없는 추가 participant snapshot → 0.00 paid/share/net 응답
- 실행:
  - `./gradlew test --tests 'com.togethertrip.main.settlement.service.support.SettlementCalculationServiceTest' --rerun-tasks`
- 결과:
  - BUILD SUCCESSFUL, 8초
  - 해당 클래스 테스트 9개(기존 4 + 추가 5), failures 0, errors 0, skipped 0
  - stale projection 사용 0건, 누락 participant 묵인 0건
  - 운영 코드 수정 없음

### 루프 29 — 외부 오류·보안·정산 분기 포함 clean 품질 게이트

- 상태: 완료
- 목적: Kakao/환율 WebClient, 로그 마스킹, admin role, 정산 fallback 변경이 전체 suite에서 함께 동작하는지 검증한다.
- 실행:
  - `./gradlew clean check jacocoTestReport --rerun-tasks`
  - JUnit XML·JaCoCo report counter 직접 집계
  - `git diff --check` 및 내부 task URI 패턴 검색
- 결과:
  - BUILD SUCCESSFUL, 53초
  - 단위 테스트 411개 + Testcontainers 통합 테스트 63개 = 총 474개
  - failures 0, errors 0, skipped 0
  - LINE 84.2077% (`covered=7705`, `missed=1445`)
  - BRANCH 69.5099% (`covered=1475`, `missed=647`)
  - 시작 기준선 대비 LINE +5.4785%p, BRANCH +6.3944%p
  - 시작 대비 line covered +506, missed -500; branch covered +142, missed -132
  - 직전 clean 대비 LINE +1.0183%p, BRANCH +2.6861%p
  - `git diff --check` 통과, 내부 task URI 참조 0건

### 루프 38 — 여행 접근 권한 분기

- 상태: 완료
- 목적: 거래·게시글 등 여러 쓰기 경로가 공유하는 `TripAccessResolver`의 사용자·여행·방장·참여자 판정을 고정한다.
- 추가 단위 테스트 7개:
  - 사용자·여행 없음, 방장 fast path, 활성 참여자 허용과 비참여자 거부
  - 쓰기용 pessimistic lock 조회와 잠금 대상 없음
  - participant id/user id 조회 성공·실패
- 결과:
  - 대상 테스트 7개 통과
  - 전체 단위 428개 + 통합 69개 = 497개 통과
  - LINE 84.9563%, BRANCH 70.1977%로 branch 70% 기준선 최초 통과

### 루프 39 — 거래 allocation·event ledger 계약

- 상태: 완료
- 목적: 부분 수정 wrapper가 payment/share allocation을 보존하고 event ledger가 aggregate version 순서를 유지하는지 실제 PostgreSQL에서 검증한다.
- 추가 통합 테스트 2개:
  - payments 수정 후 shares 수정 시 반대편 allocation 보존, 증분 집계와 event/expense version 확인
  - 생성·수정 event 조회 시 `CREATED`/`UPDATED`, aggregate version 1/2와 transaction id 확인
- 결과:
  - 대상 통합 테스트 통과
  - 거래 생성·수정·삭제의 row lock 직렬화와 event ledger 불변식을 함께 확인

### 루프 40 — mutation 포함 clean 재검증

- 상태: 완료
- 실행:
  - `./gradlew clean check jacocoTestReport --rerun-tasks`
  - JUnit XML·JaCoCo XML·PIT report 직접 집계
- 결과:
  - 단위 테스트 428개 + Testcontainers 통합 테스트 70개 = 총 498개
  - failures 0, errors 0
  - LINE 85.3166% (`covered=7815`, `missed=1345`)
  - BRANCH 70.1977% (`covered=1491`, `missed=633`)
  - PIT 115 mutations: 판정 killed 95(83%), actual killed 92, timed out 3, survived 9, no coverage 11
  - mutated line coverage 92%, test strength 91%
  - 시작 기준선 대비 LINE +6.5874%p, BRANCH +7.0822%p, 테스트 +106개

### 루프 41 — OpenAI 이미지 생성 경계와 참조 선택

- 상태: 완료
- 목적: 실행 라인은 많지만 분기 절반가량이 비어 있던 `OpenAiTripRecapGenerator`에서 외부 응답 신뢰 경계, 설정 검증, 장면 수와 참조 이미지 선택을 고정한다.
- 추가 단위 테스트 6개:
  - model/base URL/quality/image size의 잘못된 설정 12종을 HTTP 요청 전에 거부
  - data 없음·빈 목록·누락/공백 Base64·잘못된 Base64·너무 짧은 이미지·PNG 아닌 이미지 7종 거부
  - illustration + richness 5에서 5장 생성, 국가·장소·활동 prompt fallback 확인
  - richness 10 이상에서 7장 상한과 place 우선 focus 확인
  - 참조 이미지 5개를 장면별 2개로 제한하고 wrap-around 순환 순서 확인
  - 음수 max reference 설정을 0으로 보정해 loader를 호출하지 않고 generation API 사용
- Red:
  - 손상 응답 테스트가 모든 실패를 `IllegalStateException`으로 가정해 PNG 크기/시그니처의 `IllegalArgumentException` 1건 실패
  - 운영 계약은 데이터 누락/decoding 실패와 `require` 기반 이미지 불변식 위반을 구분하고 있었으므로 공통 상위 계약을 `RuntimeException` + 정확한 오류 메시지로 바로잡았다.
- Green 및 누적 결과:
  - 대상 클래스 10개 테스트 통과
  - 전체 단위 434개 + Testcontainers 통합 70개 = 총 504개, failures 0, errors 0
  - 대상 클래스 LINE 154/166 → 166/166(100%), BRANCH 59/112 → 107/112(95.5357%)
  - 전체 LINE 85.3166% → 85.5568% (`7837/9160`, +0.2402%p)
  - 전체 BRANCH 70.1977% → 72.4576% (`1539/2124`, +2.2599%p)
  - 실제 외부 OpenAI 호출과 운영 코드 변경은 없었다.

### 루프 42 — 여행 수정·권한·정산 표시·국가 정규화

- 상태: 완료
- 목적: `TripService`에서 기존 실패 경로 위주의 테스트 때문에 실행되지 않던 정상 수정/삭제와 목록·정산·국가 갱신 경계를 고정한다.
- 추가 단위 테스트 9개:
  - 소유자가 전체 기본 정보를 수정하면 trim/uppercase 후 종료일 기준 `COMPLETED`로 재계산
  - 변경 필드 없음과 역전된 날짜 수정 거부
  - 소유자 삭제 시 soft delete timestamp 기록
  - 사용자 없음·여행 없음·비참여 접근을 각 오류 코드로 구분
  - 송금 완료 요약이 없을 때 trip의 `NOT_STARTED`/`IN_PROGRESS`/`SETTLED`를 표시 상태로 변환
  - 목록 size null/0/101을 20/1/100으로 보정
  - 국가 코드 trim/uppercase/deduplicate, 기존 국가 복원·누락 국가 soft delete
  - 동일 user id 동행자 중복 지정 거부
- 결과:
  - 대상 클래스 테스트와 전체 회귀 통과
  - 전체 단위 443개 + Testcontainers 통합 70개 = 총 513개, failures 0, errors 0
  - 대상 클래스 LINE 242/277 → 274/277(98.9170%), BRANCH 77/121 → 100/121(82.6446%)
  - 전체 LINE 85.5568% → 86.0371% (`7881/9160`, +0.4803%p)
  - 전체 BRANCH 72.4576% → 73.6347% (`1564/2124`, +1.1771%p)
  - API 계약과 운영 코드는 변경하지 않았다.

### 루프 43 — 참여자 접근·조회·profile patch 경계

- 상태: 완료
- 목적: `TripParticipantService`의 상태 없는 일반 조회, 단건 소속 검증, 접근 오류와 nullable profile patch 의미를 고정한다.
- 추가 단위 테스트 6개:
  - 활성 참여자가 status 없이 목록을 조회하고 `USER` 유형만 필터링
  - 참여자 단건 성공과 다른 여행/삭제 참여자 없음 구분
  - 사용자 없음·비활성 사용자·여행 없음·비참여자의 오류 코드 구분
  - 빈 수정 요청과 공백 display name 거부
  - display name만 수정할 때 profile image `Unchanged` 유지
  - 잘못된 participant status/type 파싱 오류 구분
- 결과:
  - 전체 단위 449개 + Testcontainers 통합 70개 = 총 519개, failures 0, errors 0
  - 대상 클래스 LINE 169/190 → 188/190(98.9474%), BRANCH 46/68 → 59/68(86.7647%)
  - 전체 LINE 86.0371% → 86.2555% (`7901/9160`, +0.2184%p)
  - 전체 BRANCH 73.6347% → 74.3409% (`1579/2124`, +0.7062%p)
  - API 계약과 운영 코드는 변경하지 않았다.

### 루프 44 — 환율 관리자 조회와 Batch 실행 실패 수렴

- 상태: 완료
- 목적: 테스트가 백필 정상 실행에 치우쳐 비어 있던 관리자 조회, 입력 경계, unique 충돌 재시도와 Batch launcher 실패를 고정한다.
- 추가 단위 테스트 7개:
  - import run 날짜 범위 검증과 nullable status 필터
  - backfill 목록 limit 0/101을 1/100으로 clamp
  - backfill 단건 존재/없음
  - 역전된 backfill 요청 날짜를 저장 전에 거부
  - Batch launcher 예외를 실패 처리하고 저장된 실패 row 반환
  - 유효하지 않은 execution id를 실패로 수렴하고 저장 row fallback
  - active unique 충돌 2회와 최종 재조회 실패 시 마지막 DB 예외 전달
- Red 1:
  - execution id 누락 테스트가 mock의 primitive `long` 기본값 0을 정상 ID처럼 통과해 실패 처리 호출이 발생하지 않았다.
- Red 2 및 코드 수준 원인:
  - `JobExecution.id`를 null로 stub하려 했지만 Spring Batch 6의 `Entity.getId()` 반환형은 primitive `long`이라 Mockito가 거부했다.
  - 운영 코드의 `execution.id ?: ...`는 Spring Batch 6에서 도달 불가능한 방어문이었다.
- 최소 운영 수정:
  - nullable 검사 대신 `execution.id.takeIf { it > 0 }`로 실제 식별자 유효성을 검증한다.
  - 0/음수 ID는 기존 launch failure 처리 경로로 수렴하며 API·DB 계약은 바뀌지 않는다.
- Green 및 누적 결과:
  - 전체 단위 456개 + Testcontainers 통합 70개 = 총 526개, failures 0, errors 0
  - 대상 클래스 LINE 69/95 → 95/96(98.9583%), BRANCH 15/36 → 38/42(90.4762%)
  - 전체 LINE 86.2555% → 86.7918% (`7951/9161`, +0.5363%p)
  - 전체 BRANCH 74.3409% → 75.2113% (`1602/2130`, +0.8704%p)

### 루프 45 — Trip recap 원천 데이터 수집 계약

- 상태: 완료
- 목적: 테스트가 없던 `TripRecapDataCollector`에서 AI로 전달되는 국가·장소·거래·사진 입력의 선별과 fallback을 고정한다.
- 추가 단위 테스트 2개:
  - source post가 비면 attachment repository를 호출하지 않고 빈 places/photos를 구성
  - 국가 순서, 장소 trim·공백 제외·이름 중복 제거, post/transaction의 `occurredAt ?: createdAt`, 활성 참여자 수 반영
  - IMAGE만 선별하고 repository 정렬 순서에서 최대 20장까지만 photo reference로 전달
  - source post와 expense signal query가 각각 명시적 최대 50개 PageRequest를 사용하는지 확인
- 결과:
  - 전체 단위 458개 + Testcontainers 통합 70개 = 총 528개, failures 0, errors 0
  - 대상 클래스 LINE 7/60 → 60/60(100%), BRANCH 0/18 → 18/18(100%)
  - 전체 LINE 86.7918% → 87.3704% (`8004/9161`, +0.5786%p)
  - 전체 BRANCH 75.2113% → 76.0563% (`1620/2130`, +0.8450%p)
  - 운영 코드와 AI 외부 호출은 변경하지 않았다.

### 루프 46 — 로컬 테스트 인증과 전화번호 멱등성

- 상태: 완료
- 목적: 개발 환경 인증이라도 운영 `UserRole`·전화번호 암호화 모델을 사용하므로, token 파싱·사용자 재사용·관리자 권한 보정이 안전하게 수렴하도록 고정한다.
- 추가 단위 테스트 8개:
  - feature disabled 및 prefix 불일치 token 무시
  - unknown mode 무시
  - verified 사용자 생성과 hash/encrypt/version/mask/verifiedAt 기록
  - 빈 식별자를 `swagger`로 fallback
  - 이미 인증된 verified 사용자의 전화번호 material 재생성 금지
  - unverified 사용자는 전화번호 material 없이 생성
  - 새 admin은 ADMIN 역할과 전화번호 인증으로 생성
  - 기존 USER 역할 admin을 ADMIN으로 보정하되 기존 인증 material 유지
- Red:
  - Java Mockito matcher가 Kotlin non-null `hash/encrypt/mask` 인자에 null placeholder를 전달해 8개 fixture가 모두 실패했다.
- 테스트 fixture 수정:
  - method-name 기반 Mockito Answer로 실제 문자열 인자를 보존하고 호출 이력을 직접 검증했다.
  - 테스트 대상 운영 로직은 변경하지 않았다.
- 결과:
  - 전체 단위 466개 + Testcontainers 통합 70개 = 총 536개, failures 0, errors 0
  - 대상 클래스 LINE 8/69 → 69/69(100%), BRANCH 1/32 → 32/32(100%)
  - 전체 LINE 87.3704% → 88.0362% (`8065/9161`, +0.6658%p)
  - 전체 BRANCH 76.0563% → 77.5117% (`1651/2130`, +1.4554%p)

### 루프 47 — Solapi 설정·HMAC·HTTP 실패 계약

- 상태: 완료
- 목적: 실제 SMS 발송 없이 Solapi 요청 형식, 인증 서명과 오류 변환을 검증한다.
- 추가 단위 테스트 4개:
  - api key/secret/from 각각의 공백 설정을 `SMS_CONFIGURATION_REQUIRED`로 거부
  - E.164 입력을 국내 `010...` 수신번호로 바꾸고 endpoint/body를 확인
  - Authorization의 apiKey/date/32자리 salt/signature를 파싱해 test secret으로 HMAC-SHA256을 재계산
  - provider HTTP 500을 `SMS_SEND_FAILED`로 변환
  - 잘못된 전화번호는 provider 호출 전에 `INVALID_PHONE_NUMBER`로 보존
- 결과:
  - 전체 단위 470개 + Testcontainers 통합 70개 = 총 540개, failures 0, errors 0
  - sender 대상 LINE 5/49 → 47/49(95.9184%), BRANCH 0/6 → 6/6(100%)
  - 전체 LINE 88.0362% → 88.5820% (`8115/9161`, +0.5458%p)
  - 전체 BRANCH 77.5117% → 77.7934% (`1657/2130`, +0.2817%p)
  - 실제 Solapi 외부 호출과 운영 코드 변경은 없었다.

### 루프 48 — Trip recap 상태·재시도·장면 오류 분리

- 상태: 완료
- 목적: 생성·재시도·조회·이미지 조회에서 상태별 오류가 섞이지 않고 명시적 계약으로 유지되도록 한다.
- 추가 단위 테스트 6개:
  - available 여행에 recap 없음 → `available=true`, `NONE`
  - 기존 recap의 id/status/style 반환
  - 종료·정산 조건 미충족 시 create/retry 모두 `TRIP_RECAP_NOT_AVAILABLE`
  - retry 대상 없음과 FAILED가 아닌 상태를 `NOT_FOUND`/`RETRY_NOT_ALLOWED`로 구분
  - 상세 조회의 recap 없음
  - 장면 이미지 조회의 recap 없음·미완료·scene 없음을 각각 구분하고 storage 미호출 확인
- 결과:
  - 전체 단위 476개 + Testcontainers 통합 70개 = 총 546개, failures 0, errors 0
  - 대상 클래스 LINE 69/82 → 82/82(100%), BRANCH 12/22 → 22/22(100%)
  - 전체 LINE 88.5820% → 88.8331% (`8138/9161`, +0.2511%p)
  - 전체 BRANCH 77.7934% → 78.3568% (`1669/2130`, +0.5634%p)
  - 운영 코드와 스토리지 상태는 변경하지 않았다.

### 루프 49 — Trip recap 생성 파이프라인 수렴

- 상태: 완료
- 목적: context load → AI generate → scene sort/store → 완료/실패 반영의 전체 인프로세스 파이프라인을 상태별로 고정한다.
- 추가 단위 테스트 12개:
  - context loader의 recap 없음·creating 아님·creating 정상 수집
  - 이미 처리된 recap은 generator/storage/completion 미호출
  - 뒤섞인 3개 scene을 order 순으로 저장하고 provider/model metadata 전달
  - scene 0개와 8개를 허용 범위 3~7 밖으로 거부
  - storage 실패를 완료 없이 동일 예외로 fail 처리
  - complete 대상 없음, 이미 완료된 recap의 중복 complete no-op
  - fail 대상 없음 no-op, 완료 recap fail 무시
  - failure reason을 message → exception class name → unknown 순으로 fallback
- 결과:
  - 전체 단위 488개 + Testcontainers 통합 70개 = 총 558개, failures 0, errors 0
  - `TripRecapGenerationContextLoader` LINE 15/15·BRANCH 4/4
  - `TripRecapGenerationService` LINE 44/44·BRANCH 8/8
  - `TripRecapGenerationCompletionService` LINE 66/66·BRANCH 12/12
  - 전체 LINE 88.8331% → 89.3134% (`8182/9161`, +0.4803%p)
  - 전체 BRANCH 78.3568% → 79.2958% (`1689/2130`, +0.9390%p)
  - 외부 AI·스토리지와 운영 코드는 변경하지 않았다.

### 루프 50 — 정산 상세 service 권한과 초대 충돌 경계

- 상태: 완료
- 목적: 목표 직전의 미실행 정산 preview/detail 라인을 닫는 과정에서 controller AOP에만 의존하던 service 권한 공백을 제거하고, 초대 생성/참여 충돌을 고정한다.
- Red 및 코드 수준 결함:
  - `SettlementService.getSettlement(userId, ...)`와 `createShareToken(userId, ...)`가 `userId`를 전혀 사용하지 않았다.
  - controller의 `@RequireActiveTripParticipant`/`@RequireTripOwner`를 우회해 service를 직접 호출하면 settlement id를 아는 비참여자가 상세를 읽거나 비방장이 공유 토큰을 발급할 수 있었다.
- 최소 운영 수정:
  - 상세 조회 시작 시 `settlementAccessResolver.getAccessibleTrip(userId, tripId)`를 호출한다.
  - 공유 토큰 발급 시작 시 `settlementAccessResolver.getOwnedTrip(userId, tripId)`를 호출한다.
  - 권한 실패 시 settlement/snapshot repository를 조회하지 않는 것을 테스트로 확인했다.
- 추가 테스트:
  - preview의 expense version·합계·balance·transfer 전체 mapping
  - 상세의 snapshot decode·stored transfer mapping과 service-level 참여 권한
  - token 링크 생성, token/code 5회 충돌 소진, invitation unique 충돌
  - 새 참여자/임시 참여자 link unique 충돌, invitation 없음/비활성, 사용자·여행 오류
- 첫 clean 결과:
  - 단위 497개 + 통합 70개 = 567개, failures 0, errors 0
  - LINE 89.9858% (`8249/9167`, 목표까지 2라인)
  - BRANCH 79.8592% (`1701/2130`, 목표까지 3분기)
  - mutation 83%, mutated line 92%, test strength 91%

### 루프 51 — 초대 lookup 공백 정규화와 link 충돌

- 상태: 완료
- 추가 테스트 2개:
  - 공백 code/token을 없는 값으로 처리하고 남은 token/code를 각각 정상 조회
  - 임시 참여자 link의 `saveAndFlush` unique 충돌을 `TRIP_ALREADY_JOINED`로 변환
- clean 결과:
  - 단위 499개 + 통합 70개 = 569개, failures 0, errors 0
  - BRANCH 80.0469%로 목표 통과
  - LINE 89.9967%로 정확히 1라인 부족
- 남은 공백 분석:
  - `findInvitation`이 앞에서 code/token exact-one을 이미 검증한 뒤 token branch 안에서 nullable Elvis를 다시 검사해 도달 불가능한 예외 1라인을 만들고 있었다.

### 루프 52 — 초대 exact-one 구조 정리와 90/80 ratchet

- 상태: 완료
- 최소 운영 수정:
  - `findInvitation`을 `code only` / `token only` / `else invalid`의 명시적 `when` 구조로 바꿨다.
  - null/null, value/value, blank/value, value/blank의 기존 외부 동작은 유지하면서 도달 불가능한 token Elvis를 제거했다.
- 최종 clean 실행:
  - `./gradlew clean check jacocoTestReport --rerun-tasks`
  - BUILD SUCCESSFUL, 1분 17초
  - 단위 499개 + Testcontainers 통합 70개 = 총 569개
  - failures 0, errors 0, skipped 0
  - LINE 90.0065% (`8250/9166`)
  - BRANCH 80.0752% (`1704/2128`)
  - PIT 115 mutations: 판정 killed 95(83%), actual killed 92, timed out 3, survived 9, no coverage 11
  - mutated line coverage 92%, test strength 91%
  - `git diff --check` 통과, 내부 task URI 참조 0건
- ratchet:
  - JaCoCo 전체 하한을 LINE 84% → 90%, BRANCH 70% → 80%로 상향했다.
  - mutation 하한 83%, mutated-line 하한 90%를 유지했다.

## 코드 수준 변경

| 루프 | 파일 | 변경 | 검증 근거 |
|---|---|---|---|
| 0 | 문서만 | 작업 계획과 실시간 검증 현황 초기화 | 현재 저장소·이슈 #108 감사 |
| 0 | 문서만 | clean 기준선 수치와 실행 시간 기록 | JaCoCo XML과 JUnit XML 직접 집계 |
| 1 | `MainTestcontainersConfiguration.kt` | PostGIS·Redis container와 service connection 구성 | infrastructure acceptance test 진행 중 |
| 1 | 기존 5개 Spring context 테스트 | `@MainIntegrationTest`로 공통 container context 사용 | 전체 회귀 검증 대기 |
| 1 | `application-test.yml` | 고정 datasource·Redis 포트 제거 | 동적 mapped port 검증 대기 |
| 1 | `TestFlywayCleanMigrationConfig.kt` | fresh container에서 불필요한 clean 전략 제거 | 빈 DB Flyway V1~V21 migration 통과 |
| 1 | `TestcontainersInfrastructureTest.kt` | 실제 connection URL·mapped port·Flyway·PostGIS·Redis 검증 | acceptance test 2개 통과 |
| 1 | `build.gradle.kts` | 단위 `test`와 Docker 기반 `integrationTest` 분리, JaCoCo 합산 | 382개 + 12개, 합산 coverage gate 통과 |
| 1 | `.github/workflows/ci.yml` | 고정 service 제거, `check` 기반 검증 | 로컬 clean 검증 통과, GitHub Actions 대기 |
| 2 | `ExchangeRateUpsertRepositoryIntegrationTest.kt` | 실제 PostgreSQL에서 insert·no-op·update·soft-delete 재삽입 계약 4개 추가 | 4개 통과, 24초, 운영 코드 변경 없음 |
| 3 | `SettlementTransferRepositoryIntegrationTest.kt` | projection·복합 필터·soft delete·순차 멱등 계약 6개 추가 | Red에서 deleted settlement row 노출 결함 재현, Green 6개 통과 |
| 3 | `SettlementTransferRepositoryConcurrencyTest.kt` | 16-way 동일 side 경합 및 양측 경합 20회 추가 | update 56회, 수렴 라운드 21/21 통과 |
| 3 | `SettlementTransferRepository.kt` | 단건·settlement별 native query에 부모 `settlement.deleted_at is null` 추가 | 삭제 부모의 transfer projection 0건 확인 |
| 4 | `SettlementTransferRepositoryConcurrencyTest.kt` | 공유 토큰 16-way 최초 발급 경합 추가 | 16회 중 update 1회, 최종 token 1개 확인 |
| 5 | `RefreshTokenServiceIntegrationTest.kt` | 실제 Redis에서 save/match/delete, Lua rotation, TTL, 16-way 경합 4개 추가 | 4개 통과, 동시 rotation 성공 1/16 |
| 6 | `RedisOAuthSignupLockIntegrationTest.kt` | 16-way 획득, owner-token release, transaction 종료 후 release 3개 추가 | success 1/16, conflict 15/16, 소유권 보존 |
| 7 | `PhoneVerificationRateLimiter.kt` | 3개 Redis 명령을 단일 Lua script로 원자화 | 동시 허용 5/16 → 1/16, daily count 1 |
| 7 | `PhoneVerificationRateLimiterIntegrationTest.kt` | TTL·즉시 제한·16-way 경합·일일 한도 계약 3개 추가 | 3개 통과, 일일 6번째 요청 차단 |
| 8 | `RedisPlaceRequestRateLimiterIntegrationTest.kt` | operation 격리·TTL·64-way count 계약 2개 추가 | 허용 60/64, 초과 4/64, 최종 count 64 |
| 9 | `ExchangeRateDistributedLockIntegrationTest.kt` | 실제 Redisson 16-way 경합·예외 해제·thread 소유권 3개 추가 | 실행 1/16, skip 15/16, 재획득 통과 |
| 10 | 전체 변경 | clean `check`와 합산 JaCoCo report 실행 | 422개 통과, LINE 79.6501%, BRANCH 64.0832%, 39초 |
| 11 | `PhoneVerificationStore.kt` | attempt 증가·TTL·임계 삭제를 단일 Lua로 원자화 | exceeded 12/16 → 1/16, expired 11/16, 잔여 key 0 |
| 11 | `PhoneVerificationStoreIntegrationTest.kt` | JSON/TTL/delete·순차 임계·16-way 임계 경합 3개 추가 | 3개 통과, 25초 |
| 12 | `OutboxEventRepository.kt` | `FOR UPDATE SKIP LOCKED` native claim query 추가 | 2-worker 교집합 10 → 0, 2초 내 동시 조회 완료 |
| 12 | `OutboxEventDispatchService.kt` | pageable 상태 조회를 DB claim query로 교체 | 기존 성공·실패·limit 단위 테스트 3개 통과 |
| 12 | `OutboxEventRepositoryConcurrencyTest.kt` | 독립 transaction 2개가 이벤트 10개를 동시에 claim | 합집합 10, 교집합 0, 누락 0 |
| 13 | `SettlementServiceConcurrencyTest.kt` | 실제 정산 확정 transaction 2-way 경합을 10라운드 반복 | 성공 10/20, 중복확정 10/20, snapshot·transfer·Outbox 각 10건 |
| 14 | `AuthServiceConcurrencyTest.kt` | 실제 JWT/user/Redis를 포함한 refresh 16-way 및 refresh/logout 20라운드 | refresh 성공 1/16, logout 후 잔존 0/20 |
| 15 | `SettlementTransferServiceConcurrencyTest.kt` | sender/receiver 중복 확인 각 16-way와 Outbox·trip 최종 상태 검증 | Outbox 각 1건, trip `SETTLED`, 중복 event 0 |
| 16 | `SecurityHttpIntegrationTest.kt` | 실제 MockMvc security/JWT/AOP/validation/logout 통합 6개 추가 | 401×2, 403×1, 404×1, 400×1, 200×1 모두 계약 일치 |
| 17 | 전체 변경 | service 경합·HTTP 포함 clean check와 합산 JaCoCo | 436개 통과, LINE 82.0418%, BRANCH 66.1169%, 53초 |
| 18 | 핵심 `*ConcurrencyTest` 5개 클래스 | 별도 process 5회 반복 | 40/40 테스트, 약 910 경합 작업, flaky·timeout 0 |
| 19 | `OAuthTemporarySessionServiceIntegrationTest.kt` | 실제 Redis JSON·10분 TTL·existing user·삭제 만료 3개 추가 | 3개 통과, TTL 598~600초 |
| 20 | `SettlementShareServiceIntegrationTest.kt` | 실제 DB snapshot/transfer·DRAFT·deleted/missing token 계약 4개 추가 | 4개 통과, 공개 표시 상태 2종 검증 |
| 21 | `PhoneVerificationServiceTest.kt` | 요청·중복·정상 확인·만료·불일치·임계 초과 6개 추가 | 6개 통과, 6초, state TTL·SMS code 동일성 확인 |
| 22 | `TripOptimisticLockConcurrencyTest.kt` | 동일 entity version 2-way 갱신 20라운드 | 성공 20/40, conflict 20/40, 최종 두 version 모두 1 |
| 23 | 전체 변경 | 인증·공유·trip version 포함 clean check와 JaCoCo | 450개 통과, LINE 83.1894%, BRANCH 66.8238%, 54초 |
| 24 | `KakaoOAuthClientTest.kt` | local mode·profile fallback·header·401/empty/malformed 7개 추가 | 7개 통과, 실제 외부 호출 0 |
| 24 | `KakaoOAuthClient.kt` | `WebClientException` + `DecodingException` 오류 변환 | malformed 200이 `OAUTH_USER_INFO_FAILED`로 수렴 |
| 25 | `KoreaEximExchangeRateClientTest.kt` | query·성공 rows·NoData·known/unknown code·500 경계 7개 추가 | 7개 통과, 실제 외부 호출 0 |
| 26 | `SensitiveDataMaskerTest.kt` | JSON/alias/Bearer/전화번호/요약 타입 분기 4개 추가 | 총 8개 통과, token 노출 회귀 고정 |
| 26 | `SensitiveDataMasker.kt` | Bearer 전체 마스킹을 key-value보다 먼저 수행 | 복합 authorization token 노출 제거 |
| 27 | `SecurityHttpIntegrationTest.kt` | USER → admin API role 거부 시나리오 추가 | 403 + `ACCESS_DENIED`, HTTP 통합 7개 통과 |
| 28 | `SettlementCalculationServiceTest.kt` | projection empty/mismatch·빈 계산·participant 누락·zero balance 5개 추가 | 총 9개 통과, fallback·불변식 검증 |
| 29 | 전체 변경 | 외부 오류·보안·정산 분기 포함 clean check와 JaCoCo | 474개 통과, LINE 84.2077%, BRANCH 69.5099%, 53초 |
| 30 | `SettlementAccessResolverTest.kt` | 사용자·여행·활성 참여·방장 권한 경계 8개 추가 | 대상 8개 통과, 권한 거부 시 후속 repository 미호출 확인 |
| 31 | 전체 변경 | 권한 분기 포함 clean check와 JaCoCo 재집계 | 482개 통과, LINE 84.2623%, BRANCH 69.7455%, 53초 |
| 32 | `build.gradle.kts` mutation source set/PIT | Boot 4 JUnit 6는 유지하고 core mutation test만 JUnit 5.13.4로 격리, Kotlin compiler noise mutator 제외 | 56개 mutation source test 통과; 115 mutations, PIT 판정 killed 92(80%), 실제 killed 88·timeout 4·survived 12·no coverage 11, line 92%, strength 88%, 31초 |
| 33 | `TransactionServiceConcurrencyTest.kt` | 동일 여행 2-way 거래 생성을 10라운드, 거래·이벤트·expense version·증분 집계 검증 | Red: 첫 라운드 1/2만 성공, `ObjectOptimisticLockingFailureException`; Green: 20/20 성공, 라운드별 거래·이벤트 2, version 2, paid/share 200/200 |
| 33 | `TripRepository.kt`, `TripAccessResolver.kt`, 거래 쓰기 service | trip row `PESSIMISTIC_WRITE` 조회를 거래 생성·수정·삭제의 직렬화 지점으로 적용 | 동시 생성 10/10 라운드 수렴, 기존 `TransactionServiceTest` 통과 |
| 34 | `SettlementTransferTest.kt`, `SettlementCalculatorTest.kt` | 수금자 자동확인·재호출과 1대다 송금 순서 assertion 강화 | PIT 판정 92/115(80%) → 95/115(83%), actual killed 88→92, survived 12→9, strength 88%→91% |
| 35 | `TransactionServiceConcurrencyTest.kt` | 동시 수정 2-way×10, 수정-삭제 교차×10 추가 | 모든 수정 반영 후 마지막 금액 집계 수렴; 삭제 경합은 `VOIDED`·0원·event/version 2 또는 3으로 수렴 |
| 36 | Outbox repository/service/config + recovery integration | FAILED 재claim과 최대 5회 제한, 성공 회복 및 소진 후 제외 | 실패 1회 후 `PUBLISHED` 회복, retryCount 1 보존; 5회 실패 후 requested 0; 2-worker claim 교집합 0 |
| 37 | 전체 변경 | mutation gate를 포함한 clean check·JaCoCo·PIT | 489개 통과, LINE 84.3013%, BRANCH 69.8211%, mutation 83%, 전체 1분 17초 |
| 38 | `TripAccessResolverTest.kt` | 사용자·여행·방장·활성 참여자·잠금 조회 경계 7개 추가 | 단위 428개 통과, BRANCH 70.1977% |
| 39 | `TransactionServiceConcurrencyTest.kt` | 부분 allocation 수정과 event ledger version 계약 추가 | Testcontainers 통합 70개 통과 |
| 40 | 전체 변경 | mutation 포함 clean check·JaCoCo·PIT 재검증 | 498개 통과, LINE 85.3166%, BRANCH 70.1977%, mutation 83% |
| 41 | `OpenAiTripRecapGeneratorTest.kt` | 설정 12종·손상 응답 7종·장면 수·prompt fallback·참조 cap/rotation 계약 추가 | 504개 통과, 대상 LINE 100%·BRANCH 95.5357%, 전체 BRANCH +2.2599%p |
| 42 | `TripServiceTest.kt` | 정상 수정/삭제·접근 오류·정산 fallback·size clamp·국가 중복/삭제·동행자 중복 계약 9개 추가 | 513개 통과, 대상 LINE 98.9170%·BRANCH 82.6446% |
| 43 | `TripParticipantServiceTest.kt` | 일반 목록·단건 소속·접근 오류·profile patch·파싱 오류 계약 6개 추가 | 519개 통과, 대상 LINE 98.9474%·BRANCH 86.7647% |
| 44 | `ExchangeRateAdminServiceTest.kt`, `ExchangeRateAdminService.kt` | 관리자 조회·입력·재시도·launch failure 계약과 primitive execution id 유효성 검사 | 526개 통과, 대상 LINE 98.9583%·BRANCH 90.4762% |
| 45 | `TripRecapDataCollectorTest.kt` | 빈 source·장소 정규화·시간 fallback·IMAGE 선별·20장 cap·50개 query 계약 | 528개 통과, 대상 LINE/BRANCH 100% |
| 46 | `LocalTestAuthenticationServiceTest.kt` | token mode·사용자 생성/재사용·전화번호 멱등성·admin 권한 보정 계약 8개 | 536개 통과, 대상 LINE/BRANCH 100% |
| 47 | `SolapiSmsSenderTest.kt` | 필수 설정·수신번호·JSON·HMAC 재계산·HTTP 실패·입력 오류 계약 4개 | 540개 통과, sender BRANCH 100% |
| 48 | `TripRecapServiceTest.kt` | available-none·기존 상태·생성 불가·retry 오류·상세/장면 오류 분리 6개 | 546개 통과, 대상 LINE/BRANCH 100% |
| 49 | recap context/generation/completion 테스트 | no-op·scene 범위/정렬·storage 실패·complete/fail 멱등성과 사유 fallback | 558개 통과, 파이프라인 3개 클래스 LINE/BRANCH 100% |
| 50 | `SettlementService.kt`/테스트, `TripInviteServiceTest.kt` | 정산 상세/공유 토큰 service 권한 방어와 초대 생성·참여 충돌 계약 | 권한 우회 차단, 첫 clean LINE 89.9858%·BRANCH 79.8592% |
| 51 | `TripInviteServiceTest.kt` | blank lookup key와 임시 참여자 link unique 충돌 계약 | 569개 통과, BRANCH 80.0469%, LINE 목표까지 1라인 |
| 52 | `TripInviteService.kt`, `build.gradle.kts`, quality gate 문서 | exact-one lookup 구조 정리와 JaCoCo 90/80 ratchet | LINE 90.0065%, BRANCH 80.0752%, mutation 83% |

## 참고 자료

- [이슈 #108](https://github.com/together-trip/togethertrip-server-main/issues/108)
- [Spring Boot Testcontainers](https://docs.spring.io/spring-boot/4.0/reference/testing/testcontainers.html)
- [Testcontainers PostgreSQL/PostGIS](https://java.testcontainers.org/modules/databases/postgres/)
- [Testcontainers JUnit 5](https://java.testcontainers.org/test_framework_integration/junit_5/)
- [Redis Lua scripting과 원자 실행 보장](https://redis.io/docs/latest/develop/interact/programmability/eval-intro/)
- [PostgreSQL SELECT locking clause와 SKIP LOCKED](https://www.postgresql.org/docs/current/sql-select.html)
- [PIT mutation testing 기본 개념](https://pitest.org/quickstart/basic_concepts/)
- [PIT 변이 연산자](https://pitest.org/quickstart/mutators/)
- [Gradle PIT plugin과 JUnit 5 구성](https://gradle-pitest-plugin.solidsoft.info/)

## 남은 위험

- 로컬 clean 검증의 고정 PostgreSQL·Redis 포트 의존은 제거했지만 Docker daemon은 필요하다.
- CI의 service 제거 변경은 실제 GitHub Actions 실행 전까지 외부 환경에서의 성공이 미검증이다.
- Spring context cache와 container lifecycle이 어긋나면 후속 테스트에서 닫힌 container를 참조할 수 있다.
- 동시성 테스트는 모든 thread scheduling을 증명하지 않으므로 반복 실행과 최종 상태 assertion이 필요하다.
- 정산 확정·송금 확인, 거래 생성·수정·삭제, Outbox claim·재시도·복구, refresh rotation/logout, Redis lock/rate-limit, trip optimistic lock은 실제 동시 transaction으로 검증했다. 후속은 이 핵심 suite의 반복 횟수와 다중 worker 수를 늘려 flaky 및 starvation 가능성을 더 낮춘다.
- LINE 90%·BRANCH 80%는 현재 clean suite 기준 최소 여유로 통과하므로, production 분기 추가 시 같은 PR에서 테스트와 gate 여유를 함께 확보해야 한다.
- PIT core gate에는 survivor 9개와 no-coverage 11개가 남아 있다. 현재 하한은 통과하지만 다음 확대 루프에서는 실제 결함 위험과 동등/Kotlin bytecode 변이를 다시 분류한다.
