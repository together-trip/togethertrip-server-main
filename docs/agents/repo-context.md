# Repo Context

## 리포 역할

`main`은 TogetherTrip의 Spring Boot 메인 API 서버다.

## 책임 범위

- 사용자/인증, 카카오 로그인 연동, 회원 프로필
- 여행 생성, 여행 정보, 동행자 초대/제거, 방장 위임
- 기록/소비, 댓글/대댓글, 권한 정책
- 정산 상태, 정산 계산, 송금/수금 확인, 정산 확정
- 탈퇴/퇴장 사용자 표시 정책과 데이터 생명주기

## 아키텍처 원칙

- feature-based MVC 패턴을 기본으로 한다.
- 기능별 패키지는 현재 코드처럼 `controller`, `service`, `repository`, `domain`, `dto` 책임으로 나눈다.
- Controller는 요청/응답 매핑과 인증 주체 전달에 집중하고, 비즈니스 판단은 Service에 둔다.
- Repository는 JPA Entity 조회/저장 책임을 맡고, Controller에서 직접 호출하지 않는다.
- 정산 계산과 권한 정책은 테스트 가능한 Service/domain 메서드에 둔다.

## Kotlin/MVC 코드 컨벤션

- Kotlin 코드는 IntelliJ Kotlin style guide를 따른다.
- 기능 패키지의 기본 구조는 `{domain}/controller`, `{domain}/service`, `{domain}/repository`, `{domain}/domain`, `{domain}/dto`로 둔다.
- HTTP 요청 DTO는 `{domain}/dto/request`, 응답 DTO는 `{domain}/dto/response`에 둔다.
- Swagger/OpenAPI 문서 책임은 현재 구조처럼 `{domain}/controller/spec`에 둔다.
- Controller는 얇게 유지하며 route mapping, 인증 주체 전달, request/response 변환, `ApiResponse` 포장만 담당한다.
- Controller는 Entity를 직접 반환하지 않고, Repository를 직접 호출하지 않는다.
- Request DTO에는 Bean Validation을 둔다. Kotlin에서는 `@field:NotBlank`, `@field:NotNull`, `@field:NotEmpty`처럼 field target을 명시한다.
- Response DTO는 Entity 노출 대신 API 응답 형상만 표현한다. Entity에서 변환할 때는 `from(...)` 팩토리 메서드를 사용할 수 있다.
- Service가 트랜잭션 경계를 가진다. 클래스 기본값은 `@Transactional(readOnly = true)`, 쓰기 메서드는 별도 `@Transactional`을 사용한다.
- Service는 비즈니스 규칙과 권한 판단을 담당한다. 여러 Service 조합이 반복될 때만 Facade 도입을 검토한다.
- Repository는 Entity 조회/저장 책임만 가진다. DTO 반환, 권한 판단, 업무 흐름 조합을 Repository에 두지 않는다.
- 선택 조건이 있는 JPQL 조회는 Kotlin JDSL 기반 `*QueryRepository`와 `*SearchCondition`을 기본으로 사용한다.
- 단순 CRUD와 고정 조건은 Spring Data JPA 메서드 쿼리를 유지하고, PostgreSQL 전용 집계·잠금·bulk update는 native SQL을 유지한다.
- null 선택 조건은 생성 SQL에서 제외하며 boolean enable flag, sentinel, `:param IS NULL OR ...` 우회 패턴을 사용하지 않는다.
- soft delete 대상 Entity는 기본적으로 `@SQLRestriction("deleted_at IS NULL")`을 적용한다.
- soft delete 대상 조회는 `@SQLRestriction`을 기본 안전장치로 두고, 명시성이 필요한 Repository 메서드는 `DeletedAtIsNull` 조건을 포함한다.
- `findById()` 직접 사용은 `@SQLRestriction` 적용 Entity에서만 허용한다. 삭제 데이터 포함 조회가 필요한 경우는 별도 명시 쿼리로 분리한다.
- JPA Entity에는 Kotlin `data class`를 사용하지 않는다.
- Entity 상태 변경은 외부 필드 대입보다 도메인 메서드로 표현한다.
- 삭제 API 메서드명은 REST 관례상 `deleteXxx`를 사용할 수 있지만, 도메인 데이터는 `deleted_at`을 기록하는 soft delete로 처리한다.
- 원장 시점은 `Instant`, 여행 날짜 같은 달력 날짜는 `LocalDate`를 사용한다.
- 금액과 환율은 다국화폐/정산 정밀도를 고려해 `BigDecimal`을 사용한다.
- null 단정(`!!`)은 사용하지 않는다. nullable 값은 명시적으로 검사하고 `BusinessException(ErrorCode.X)`로 실패시킨다.
- 메서드 이름은 단건 조회 `getXxx`, 목록 조회 `getXxxs`, 생성 `createXxx`, 수정 `updateXxx`, 삭제 `deleteXxx`를 기본으로 한다.
- Kotlin scope function(`let`, `run`, `apply`, `also`)은 null 처리나 객체 초기화가 명확해질 때만 사용하고 과용하지 않는다.

## 통신 규칙

- `app -> gateway -> main` 흐름의 API 진입을 기본으로 한다.
- `main -> notification` 알림 생성/발송 요청을 허용한다.
- 다른 서비스의 DB에 직접 접근하지 않는다.
- 서비스 간 통신 방식이 확정되기 전까지 Service 내부의 명시적인 클라이언트/설정으로 분리하고, 별도 아키텍처 계층 패키지는 만들지 않는다.

## 핵심 도메인 주의점

- 기록은 언제든 등록 가능하지만 소비는 여행 기간과 정산 상태 제약을 따른다.
- 정산 시작 이후 소비 금액 관련 수정/삭제는 불가하다.
- 자기 자신이 발생시킨 행동에는 알림을 보내지 않는다.
- 동행자, 방장, 탈퇴/퇴장 사용자 표시 정책을 UI에서 혼동하지 않는다.
