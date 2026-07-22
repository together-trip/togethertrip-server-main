# 이슈 #108 Testcontainers 기반 TDD 작업 계획

## 목표

- PostgreSQL/PostGIS와 Redis 통합 테스트가 고정 로컬 포트에 의존하지 않도록 한다.
- 정산, 인증, 권한, Outbox, native query의 실제 인프라 동작을 테스트로 고정한다.
- LINE 90%, BRANCH 80%를 지향하되 테스트 개수가 아니라 결함 탐지력과 상태 수렴을 우선한다.
- 각 루프의 실패, 원인, 수정, 검증 수치를 `docs/verifications-jaewan/issue-108-testcontainers-tdd.md`에 누적한다.

## 구현 순서

1. clean 기준선 재측정
2. PostGIS·Redis Testcontainers와 Spring Boot service connection 구성
3. 단위·통합 테스트 task와 JaCoCo 집계 분리
4. PostgreSQL/Flyway/native query 계약 테스트
5. Redis Lua·TTL·rate limit·분산 lock 계약 테스트
6. 정산·인증·권한·Outbox 동시성 및 HTTP 테스트
7. 반복 안정성, coverage ratchet, CI, 문서 최종 검증
8. PIT mutation testing 기준선 측정, survivor 분류, mutation score ratchet

## Mutation testing 전략

- JaCoCo 구조 coverage와 별도로 killed/survived/no-coverage 수치를 누적한다.
- 초기 core gate는 직접 unit test가 있는 정산 계산·권한·송금 상태, 잔액 projection, 민감정보 마스킹 명시적 클래스 8개로 제한한다.
- Redis Lua·native SQL·JPA locking·Spring Security filter는 bytecode mutation score에 섞지 않고 Testcontainers/HTTP 계약·동시성 gate로 다룬다.
- Spring Boot 4의 JUnit Platform 6과 공개 PIT JUnit plugin의 Platform 1.x 호환성을 분리하기 위해 mutation 전용 JUnit 5.13.4 source set을 사용한다. 일반 `test`는 Boot가 관리하는 JUnit 6를 유지한다.
- 무료 PIT가 Kotlin compiler의 `Intrinsics.checkNotNull*` 호출을 변이하는 noise를 점수에 섞지 않도록 `VOID_METHOD_CALLS`는 core gate에서 제외한다. 조건·경계·산술·반환값 변이를 작업 가능한 지표로 삼는다.
- 기준선에서 survivor를 실제 결함 위험·동등 변이·Kotlin bytecode noise로 분류한 후 테스트와 gate를 단계적으로 강화한다.
- CI 차단선은 현재 실측 가능한 `mutation score >= 83%`, `mutated line coverage >= 90%`로 시작한다. 점수를 낮춰 통과시키지 않고 survivor 제거 후에만 ratchet한다.
- 확대 순서는 `금액·상태 전이` → `권한·멱등성` → `외부 응답 변환`이다. 한 번에 3~5개 production class만 추가하고 신규 survivor를 분류한 뒤 다음 묶음으로 넘어간다.
- 전체 mutation은 야간/수동 진단으로만 사용한다. PR 필수 gate에는 1분 안팎의 core mutation만 포함해 개발 피드백 시간을 통제한다.
- `NO_COVERAGE`는 점수에서 숨기지 않고 목록으로 관리한다. 접근 가능한 분기이면 테스트를 추가하고, 생성 코드·동등 변이면 근거를 문서화한다.

## 변경 예상 파일

- `build.gradle.kts`
- `.github/workflows/ci.yml`
- `src/test/resources/application-test.yml`
- `src/test/kotlin/com/togethertrip/main/global/config/*`
- PostgreSQL·Redis·정산·인증·Outbox 관련 테스트
- `docs/agents/quality-gates.md`
- `docs/verifications-jaewan/issue-108-testcontainers-tdd.md`

## 승인과 정지 조건

- 이슈 #108과 사용자 요청으로 테스트 기반·테스트 코드·CI 변경은 승인된 범위로 본다.
- API 계약, DB 구조, 정산 정책 변경은 별도 승인을 받기 전에는 진행하지 않는다.
- 같은 실패를 세 번 수정해도 원인이 분류되지 않으면 해당 경로의 자동 수정을 중단하고 기록한다.
- 테스트 삭제·약화 또는 coverage 하향으로 실패를 우회하지 않는다.
