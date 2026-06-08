# Work Plan

## 작업

이슈 #52 `feat: 백엔드 공통 로깅 AOP 구현`을 진행한다.

- GitHub Issue: https://github.com/together-trip/togethertrip-server-main/issues/52

Flutter 클라이언트는 제외하고, `server-main`의 요청 단위 로그, 서비스 실행 시간 로그, 예외 로그, 민감정보 마스킹 기준을 우선 정리한다.

## 배경

운영 및 로컬 디버깅에서 API 요청 흐름, 서비스 처리 시간, 예외 원인을 빠르게 추적할 수 있는 공통 로그가 필요하다.

현재는 기능별 로그 기준이 통일되어 있지 않아 장애 분석 시 요청 식별자, 사용자 식별자, 실행 시간, 예외 정보가 일관되게 남지 않을 수 있다.

## 범위

- HTTP 요청 시작/종료 로그를 추가한다.
- 요청별 `requestId` 또는 `traceId`를 생성하고 MDC에 저장한다.
- 인증된 요청은 가능한 범위에서 `userId`를 로그 컨텍스트에 포함한다.
- Controller 또는 API 요청 단위로 method, path, status, latency를 기록한다.
- 핵심 Service 계층 메서드에 AOP 기반 실행 시간 로그를 적용한다.
- 예외 발생 시 전역 예외 처리 흐름에서 requestId, exception type, message를 일관되게 기록한다.
- 로그 파라미터 출력 시 개인정보, 토큰, 인증번호, 전화번호 등 민감정보를 마스킹한다.
- 운영 로그 과다 발생을 막기 위해 Repository 계층과 단순 getter/setter성 메서드는 제외한다.
- 로깅 설정과 정책을 문서화한다.

## 제외 범위

- Flutter 클라이언트 로깅.
- Gateway, Chat, Notification 서버 전체 반영.
- ELK, Loki, Datadog, OpenTelemetry 등 외부 관측 도구 연동.
- DB 기반 감사 로그 저장.
- 모든 메서드의 argument/result 원문 출력.
- 로그 기반 알림 또는 모니터링 룰 구성.

## 설계

- 요청 로그는 Filter 또는 Interceptor에서 처리한다.
- 요청 시작 시 requestId가 없으면 새로 생성하고, 응답 헤더에도 포함할지 검토한다.
- MDC에는 `requestId`, `userId`, 필요 시 `path`, `method`를 넣고 요청 종료 시 정리한다.
- Service AOP는 `com.togethertrip.main..service..*` 패키지를 중심으로 적용한다.
- AOP 로그는 `className.methodName`, executionTimeMs, success/failure, exception summary 중심으로 남긴다.
- argument/result 로그는 기본 비활성 또는 제한된 요약만 허용한다.
- 민감정보 마스킹 유틸을 두고 토큰, 비밀번호, 인증번호, 전화번호, 이메일 등은 원문 노출을 막는다.
- 로그 레벨은 정상 요청 요약 `INFO`, 상세 디버깅 `DEBUG`, 예외 `ERROR` 기준으로 나눈다.
- Spring Security 인증 객체에서 userId 추출 가능 여부를 확인하고, 불가능하면 anonymous로 남긴다.

## 테스트 계획

- 요청 로그 필터 또는 인터셉터 단위 테스트를 추가한다.
- requestId가 없는 요청은 새 requestId가 생성되는지 검증한다.
- requestId가 있는 요청은 기존 값을 유지하는지 검증한다.
- MDC 값이 요청 종료 후 정리되는지 검증한다.
- Service AOP가 service package 메서드 실행 시간을 기록하는지 검증한다.
- 예외 발생 시 AOP 또는 전역 예외 로그 흐름이 깨지지 않는지 검증한다.
- 민감정보 마스킹 유틸이 토큰, 인증번호, 전화번호 등을 원문으로 남기지 않는지 검증한다.
- 최종 검증 명령은 `./gradlew test`다.

## 위험과 확인 사항

- 로그가 과도하면 운영 비용과 분석 난도가 올라갈 수 있으므로 출력 범위를 제한해야 한다.
- 개인정보와 인증정보가 로그에 남지 않도록 마스킹 기준을 먼저 적용해야 한다.
- Spring Security 필터 체인 순서에 따라 userId를 요청 시작 시점에 얻지 못할 수 있다.
- requestId 또는 traceId 명칭은 추후 Gateway와 다른 백엔드 서버까지 확장할 수 있도록 호환성을 고려한다.
- 외부 관측 도구 연동은 후속 이슈로 분리한다.
