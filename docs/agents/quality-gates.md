# Quality Gates

## 기본 검증 명령

```bash
./gradlew check
```

`check`는 테스트와 JaCoCo 커버리지 검증을 함께 실행한다. 상세 커버리지 보고서는 다음 명령으로 생성한다.

```bash
./gradlew test jacocoTestReport jacocoTestCoverageVerification
```

- HTML 보고서: `build/reports/jacoco/test/html/index.html`
- XML 보고서: `build/reports/jacoco/test/jacocoTestReport.xml`
- 초기 전체 코드 하한: LINE 77%, BRANCH 59%
- 기준선 측정값(2026-07-14): LINE 78.09%, BRANCH 60.18%

SonarQube Cloud 분석은 CI 기반으로 실행한다. GitHub Actions 저장소 secret `SONAR_TOKEN`이 설정된 경우 `./gradlew sonar`가 실행되며, JaCoCo XML을 커버리지 입력으로 사용한다. 기본 프로젝트 키는 `together-trip_togethertrip-server-main`, 조직 키는 `together-trip`이며 필요하면 `SONAR_PROJECT_KEY`, `SONAR_ORGANIZATION` 환경 변수로 재정의할 수 있다. PR 기본 대상이 `develop`이므로 Sonar 프로젝트의 기준 브랜치도 `develop`으로 맞춘다.

## 보안 체크리스트

- OAuth/Kakao 인증과 토큰 처리
- 사용자 권한, 여행/정산 접근 제어
- 개인정보, 탈퇴/삭제 정책
- 정산 금액 무결성과 금액 관련 수정 제한
- 자기 자신 알림 제외 요청

## 완료 기준

- 관련 테스트 또는 분석 명령을 실행했다.
- 실행하지 못한 검증은 이유를 남겼다.
- 사용자 흐름, 권한, 입력 제한이 기획과 충돌하지 않는다.
- 문서 변경이 필요한 경우 함께 갱신했다.
