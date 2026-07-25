# 이슈 129 Kotlin JDSL 동적 쿼리 검증

## 자동 검증

| 항목 | 결과 |
|---|---|
| `./gradlew test` | 통과, 582개 |
| `./gradlew integrationTest` | 통과, 79개 중 1개 기존 조건부 skip |
| `./gradlew verifyQueryConventions` | 통과 |
| `./gradlew check` | 통과 |
| 전체 LINE 커버리지 | 90.91% (9,083 / 9,991) |
| 전체 BRANCH 커버리지 | 80.32% (2,081 / 2,591) |
| PIT | 115개 중 95개 kill, mutation score 83%, 대상 line coverage 92% |
| 빈 PostGIS Flyway | V25까지 통과 |

통합 테스트는 로컬 Gradle 데몬에 남아 있던 잘못된 Apple 키의 영향을 제거하기 위해 다음처럼 테스트 프로필에서 비활성화된 키를 비우고 실행했다.

```bash
env APPLE_TOKEN_ENCRYPTION_KEY= ./gradlew --no-daemon check
```

## Repository 계약

- 여행: 소유자/참여자 `EXISTS`, 상태 null/단일, 커서 동률과 결정적 정렬 검증
- 거래: 유형·참여자·커서 복합 조건과 payment/share 의미적 OR 검증
- 게시글: 비로그인/로그인 양방향 차단, EXPENSE 예외, moderation visibility 회귀 검증
- 댓글: 조회자와 커서 유무, root count 및 다건 count projection 검증
- 신고: 상태·대상·오름차순 커서 동률 검증
- 정산: settlement·participant·status 조건의 전체/선택 Shape와 native row mapping 검증
- 통계: 기간 없음/from/to/from+to Shape와 발생 시각 fallback 검증

## 실행계획 검증

로컬 개발 PostgreSQL의 비식별 집계를 사용했다.

- active transaction 100,641건, 622개 여행
- 대표 여행 1개에 transaction 100,000건
- 대표 참여자 payment 10,000건, share 50,000건

대표 거래 목록의 중간 커서 조건은 기존에는 커서 Predicate가 index condition으로 내려가지 않아 50,001 row를 filter하고 49.4ms가 걸렸다. `created_at <= :cursorCreatedAt` 상한 Shape를 함께 제공한 뒤 기존 `idx_transactions_read_list_active`가 range condition을 사용했다.

| Shape | 변경 전 | 변경 후 |
|---|---:|---:|
| 유형 + 중간 커서 | 49.4ms, filter 50,001 rows, buffers 1,357 | 0.113ms, filter 1 row, buffers 7 |

참여자 payment/share 조회는 역방향 인덱스가 없어 두 allocation 테이블을 sequential scan했다. V25 후보 인덱스를 한 트랜잭션 안에서 생성한 뒤 실행계획을 측정하고 rollback했다.

| Shape | 변경 전 | V25 적용 계획 |
|---|---:|---:|
| 참여자 + 중간 커서 | 73.6ms, buffers 17,736, filter 50,021 rows | 11.7ms, buffers 246, filter 21 rows |
| payment 접근 | Seq Scan, filter 92,043 rows | Index Only Scan, heap fetch 0 |
| share 접근 | Seq Scan, filter 456,126 rows | Index Only Scan, heap fetch 0 |

측정용 인덱스는 `ROLLBACK`하여 로컬 DB에 남기지 않았으며, 실제 변경은 V25 Flyway migration에만 포함했다. 데이터가 작은 여행에서는 planner가 sequential scan을 선택할 수 있으므로 scan 이름만으로 실패 처리하지 않고 rows, buffers, 실행 시간을 함께 판단한다.

## DB Call 및 트랜잭션

- 대상 목록 조회는 각 Repository 호출당 1 SQL이며 결과 건수에 따라 SQL 수가 증가하지 않는다.
- 게시글 첨부와 댓글 수는 기존 일괄 조회 계약을 유지한다.
- 컬렉션 fetch join과 메모리 페이징을 추가하지 않았다.
- 서비스의 `@Transactional(readOnly = true)` 경계와 native update 원자성을 변경하지 않았다.
