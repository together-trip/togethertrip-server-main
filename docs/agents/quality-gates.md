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
- 현재 전체 코드 하한: LINE 78%, BRANCH 60%
- 기준선 측정값(2026-07-14): LINE 78.09%, BRANCH 60.18%

## 커버리지 기준 운영 정책

커버리지 기준은 신규 코드와 전체 코드를 분리해 관리한다.

### 신규 코드

PR에서 추가·변경한 신규 코드는 SonarQube Cloud 기본 `Sonar way` Quality Gate를 적용한다.

- 신규 코드 커버리지 80% 이상
- 신규 코드 중복률 3% 이하
- 신규 버그와 취약점 없음
- 신규 보안 핫스팟 검토 완료

신규 코드는 기존 전체 커버리지에 맞춰 기준을 낮추지 않는다. SonarQube Cloud의 PR 분석은 신규 코드 조건을 평가하고, JaCoCo XML을 커버리지 입력으로 사용한다.

### 전체 코드

전체 LINE/BRANCH 커버리지는 Gradle `jacocoTestCoverageVerification`으로 검증하고, 달성한 품질을 다시 잃지 않는 래칫(ratchet) 방식으로 올린다.

1. `develop`에서 측정된 실제 커버리지의 정수 내림값을 다음 하한 후보로 사용한다.
2. 후보가 현재 하한보다 높을 때 커버리지 개선 PR에서 코드와 하한을 함께 올린다.
3. 날짜만을 기준으로 하한을 자동 인상하지 않는다. 테스트로 먼저 달성한 뒤 하한을 갱신한다.
4. 하한은 원칙적으로 낮추지 않는다. 불가피하게 낮추는 경우 원인, 영향 범위, 복구 목표를 이슈와 PR에 기록한다.
5. 커버리지 확보를 목적으로 동작이 있는 코드를 광범위하게 분석 제외하지 않는다.

예를 들어 `develop`의 실제 커버리지가 LINE 82.73%, BRANCH 68.41%가 되면 다음 하한은 LINE 82%, BRANCH 68%로 올린다. 현재 기준선은 LINE 78.09%, BRANCH 60.18%이므로 최초 하한은 각각 정수 내림한 78%, 60%다.

단계별 목표는 다음과 같다.

| 단계 | LINE | BRANCH |
|---|---:|---:|
| 현재 기준선 | 78% | 60% |
| 1차 목표 | 80% | 65% |
| 2차 목표 | 85% | 70% |
| 3차 목표 | 90% | 80% |

정산 금액 계산, 인증·인가, 여행 권한처럼 실패 영향이 큰 핵심 로직은 전체 평균과 별도로 90~100% 커버리지와 정상·실패·경계값 테스트를 지향한다. 전체 100% 자체를 금지하지는 않지만 단순 DTO, 프레임워크 설정이나 생성 코드에 숫자만 채우기 위한 테스트를 추가하기보다 실제 분기와 행위를 검증한다.

### 기준 선택 근거

Spring 자체에는 공통으로 강제하는 SonarQube 커버리지 비율이 없다. Spring Security, Spring Authorization Server, Spring Petclinic 등 공식 프로젝트도 JaCoCo 보고서를 생성하지만 동일한 숫자 하한을 공통 정책으로 제시하지 않는다. 따라서 신규 코드는 SonarQube Cloud 공식 기본 Quality Gate인 80%를 사용하고, 기존 전체 코드는 이 저장소에서 측정한 기준선을 출발점으로 점진적으로 개선한다.

- [SonarQube Cloud Quality Gates](https://docs.sonarsource.com/sonarqube-cloud/standards/managing-quality-gates/introduction-to-quality-gates)
- [SonarQube Cloud New Code](https://docs.sonarsource.com/sonarqube-cloud/standards/about-new-code)
- [Spring Security JaCoCo 설정](https://github.com/spring-projects/spring-security/blob/e1ad5d23922ac7bc6ac924bb5ef594d9b89172af/buildSrc/src/main/groovy/io/spring/gradle/convention/JacocoPlugin.groovy)
- [Spring Authorization Server JaCoCo 설정](https://github.com/spring-projects/spring-authorization-server/blob/b90fb09362aae9eb6ee64e536d0ad7d589af917f/buildSrc/src/main/java/org/springframework/gradle/jacoco/SpringJacocoPlugin.java)
- [Spring Petclinic JaCoCo 설정](https://github.com/spring-projects/spring-petclinic/blob/51045d1648dad955df586150c1a1a6e22ef400c2/pom.xml)

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
