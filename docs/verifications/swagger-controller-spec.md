# Swagger Controller Spec 분리 검증 리포트

## 검증 대상

- `src/main/kotlin/com/togethertrip/main/**/controller/*Controller.kt`
- `src/main/kotlin/com/togethertrip/main/**/controller/spec/*ApiSpec.kt`
- `docs/work-plans/swagger-controller-spec.md`

## 실행한 명령

```bash
rg -n "io\\.swagger|@Tag|@Operation|@SecurityRequirement" main/src/main/kotlin/com/togethertrip/main -g '*Controller.kt'
rg -n "io\\.swagger|@Tag|@Operation|@SecurityRequirement" main/src/main/kotlin/com/togethertrip/main -g '*ApiSpec.kt'
./gradlew test
```

## 결과

- 컨트롤러 파일에서 Swagger/OpenAPI import와 annotation이 제거되었다.
- Swagger/OpenAPI annotation은 `controller/spec` 패키지의 `ApiSpec` 인터페이스로 분리되었다.
- 컨트롤러는 기존 Spring MVC mapping을 유지하고 각 `ApiSpec`을 구현한다.
- `./gradlew test`가 성공했다.

## 남은 위험

- Kotlin 컴파일과 Spring test context는 통과했지만, 실제 Swagger UI 화면에서 interface annotation 표시 여부는 브라우저로 확인하지 않았다.
- springdoc이 특정 annotation을 기대와 다르게 표시하면 해당 spec 인터페이스 또는 `OpenApiConfig`에서 보정한다.
