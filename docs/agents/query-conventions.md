# 동적 쿼리 컨벤션

## 도구 선택

| 조회 유형 | 기본 도구 |
|---|---|
| CRUD, 단순 고정 조건 | Spring Data JPA 메서드 쿼리 |
| 선택 조건이 있는 Entity/DTO 조회 | Kotlin JDSL `*QueryRepository` |
| PostgreSQL 전용 집계·projection | native SQL |
| `FOR UPDATE SKIP LOCKED`, `UPDATE ... FROM` | native SQL |

Querydsl, jOOQ, MyBatis를 함께 도입하지 않는다. CTE, UNION, window function 중심의 조회가 늘어나면 별도 ADR에서 jOOQ를 검토한다.

## 조건 분류

1. 필수 조건: soft delete, 도메인 ID, 고정 상태, 접근 권한
2. 선택 조건: 상태, 유형, 기간, 조회자, 커서
3. 검색 경로 결정 조건: 소유자/참여자, 결제자/부담자, 송금자/수금자

선택 조건은 값이 있을 때만 JDSL Predicate 또는 native SQL의 고정 fragment를 추가한다. 아래 패턴은 사용하지 않는다.

```sql
(:param IS NULL OR column = :param)
(:filterEnabled = false OR column = :param)
column = COALESCE(:param, column)
```

## Kotlin JDSL 구조

도메인 Repository는 CRUD 인터페이스와 동적 조회 fragment를 합성한다.

```kotlin
interface ExampleRepository : JpaRepository<Example, Long>, ExampleQueryRepository

data class ExampleSearchCondition(
    val status: ExampleStatus?,
    val cursorCreatedAt: Instant?,
    val cursorId: Long?,
)
```

구현에서는 nullable Predicate를 조건부로 조립하고 결정적 정렬을 유지한다.

```kotlin
.whereAnd(
    example(BaseEntity::deletedAt).isNull(),
    condition.status?.let { example(Example::status).eq(it) },
    condition.cursorCreatedAt?.let { createdAt ->
        example(BaseEntity::createdAt).lt(createdAt)
            .or(example(BaseEntity::createdAt).eq(createdAt)
                .and(example(BaseEntity::id).lt(requireNotNull(condition.cursorId))))
    },
)
.orderBy(example(BaseEntity::createdAt).desc(), example(BaseEntity::id).desc())
```

## Native SQL

- SQL fragment는 코드에 정의된 고정 문자열만 선택한다.
- 사용자 입력을 SQL 문자열에 연결하지 않고 bind parameter로 전달한다.
- 기간 조건은 없음/from/to/from+to처럼 실제 조건에 맞는 Shape를 생성한다.
- enum은 애플리케이션에서 검증한 뒤 안정적인 `name` 값을 바인딩한다.

## 검증 체크리스트

- null/단일/복합 조건별 결과와 생성 SQL
- soft delete, 권한, 차단, moderation visibility 회귀
- keyset cursor 동률에서 중복·누락 없음
- 1건/100건/1,000건 응답 변환까지 DB Call 수
- 컬렉션 fetch join과 메모리 페이징 부재
- 대표 Shape별 `EXPLAIN (ANALYZE, BUFFERS)`의 rows, loops, scan, sort, buffer, 실행 시간
- 신규 인덱스의 기존 prefix 중복과 쓰기·저장 공간 비용

실데이터가 없으면 전체 1만/100만/1천만 건과 함께 여행당 거래·게시글·댓글 수, 거래당 payment/share 수, 상태·유형 분포를 가정으로 명시한다.
