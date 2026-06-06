# Swagger Controller Spec 분리 작업 계획

## 작업

컨트롤러에 직접 선언된 Swagger/OpenAPI annotation을 별도 `ApiSpec` 인터페이스로 분리한다.

## 가정

- Spring MVC route mapping은 현재 컨트롤러에 유지한다.
- Swagger 전용 annotation인 `@Tag`, `@Operation`, `@SecurityRequirement`만 별도 파일로 옮긴다.
- 컨트롤러는 `ApiSpec` 인터페이스를 구현하고, 메서드에는 `override`만 추가한다.
- feature-based MVC 패키지 구조를 유지하되, Swagger 문서 책임은 `controller/spec` 패키지에 둔다.

## 계획

1. 각 컨트롤러의 Swagger annotation과 메서드 시그니처를 확인한다.
2. feature별 `controller/spec/*ApiSpec.kt` 인터페이스를 생성한다.
3. 컨트롤러에서 Swagger imports와 annotation을 제거하고 `ApiSpec`을 구현한다.
4. `rg`로 컨트롤러에 Swagger annotation이 남지 않았는지 확인한다.
5. `./gradlew test`로 Kotlin 컴파일과 Spring context를 확인한다.

## 성공 기준

- 컨트롤러 파일에는 `io.swagger.v3.oas.annotations.*` import가 남지 않는다.
- Swagger annotation은 `controller/spec` 패키지에 모인다.
- 기존 endpoint mapping은 변경하지 않는다.
- 테스트가 통과한다.

## 검증

- `rg -n "io\\.swagger|@Tag|@Operation|@SecurityRequirement" src/main/kotlin/com/togethertrip/main -g '*Controller.kt'`
- `rg -n "io\\.swagger|@Tag|@Operation|@SecurityRequirement" src/main/kotlin/com/togethertrip/main -g '*ApiSpec.kt'`
- `./gradlew test`

## 위험

- springdoc이 interface annotation을 인식하지 못하면 OpenAPI 문서가 비어 보일 수 있다. 이 경우 `OpenApiCustomizer` 또는 controller-level minimal annotation 유지 방식을 재검토한다.

## 다음 추천 에이전트

TDD Guide
