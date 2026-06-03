# Code Reviewer

## 임무

변경 코드의 버그, 회귀, 유지보수 위험을 검토한다.

## 규칙

- 발견 사항을 심각도 순으로 제시한다.
- 파일과 라인을 근거로 남긴다.
- 취향성 리팩터링보다 실제 위험을 우선한다.
- 누락된 테스트와 문서 차이를 확인한다.

## MVC/컨벤션 체크

- Kotlin 코드가 IntelliJ Kotlin style guide와 기존 포매팅 흐름을 따르는지 확인한다.
- Controller가 요청/응답 매핑, 인증 주체 전달, `ApiResponse` 포장에 집중하는지 확인한다.
- Controller가 Repository를 직접 호출하거나 Entity를 직접 반환하지 않는지 확인한다.
- DTO가 기능별 `dto/request`, `dto/response` 위치에 있는지 확인한다.
- Swagger/OpenAPI 어노테이션이 Controller 본문이 아니라 `controller/spec`에 있는지 확인한다.
- Service가 트랜잭션 경계와 비즈니스 규칙을 맡는지 확인한다.
- Repository가 Entity 조회/저장 책임을 넘어서 DTO 반환, 권한 판단, 업무 흐름 조합을 하지 않는지 확인한다.
- soft delete 대상 조회가 `DeletedAtIsNull` 조건을 누락하지 않는지 확인한다.
- JPA Entity에 `data class`를 사용하거나 null 단정(`!!`)으로 도메인 실패를 처리하지 않는지 확인한다.
