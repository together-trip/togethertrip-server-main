# Quality Gates

## 기본 검증 명령

```bash
./gradlew check
```

쿼리 변경은 다음 항목을 추가로 확인한다.

- `./gradlew verifyQueryConventions`로 금지된 nullable 조건 우회 패턴이 없는지 검사한다.
- 선택 조건의 null/단일/복합 조합별 Repository 통합 테스트를 실행한다.
- Hibernate가 생성한 SQL에서 null 조건의 Predicate가 실제 제외되는지 확인한다.
- DTO 변환까지 포함한 DB Call 수가 결과 건수에 비례하지 않는지 확인한다.
- 대표 SQL Shape의 `EXPLAIN (ANALYZE, BUFFERS)`를 비교하고 인덱스 추가·유지 근거를 기록한다.
- keyset pagination 정렬 마지막에 PK를 포함하고 커서 동률의 중복·누락을 검증한다.

`check`는 빠른 단위 테스트, Docker 기반 통합 테스트, JaCoCo 커버리지, 핵심 로직 PIT mutation gate를 함께 실행한다. PostgreSQL/PostGIS와 Redis는 Testcontainers가 시작하므로 Docker 실행 환경이 필요하다.

빠른 feedback만 필요하면 Docker 없이 단위 테스트를 실행한다.

```bash
./gradlew test
```

PostGIS·Redis 통합 테스트만 실행하려면 다음 명령을 사용한다.

```bash
./gradlew integrationTest
```

단위·통합 테스트 실행 데이터를 합친 상세 커버리지 보고서는 다음 명령으로 생성한다.

```bash
./gradlew check jacocoTestReport
```

- HTML 보고서: `build/reports/jacoco/test/html/index.html`
- XML 보고서: `build/reports/jacoco/test/jacocoTestReport.xml`
- 단위 테스트 실행 데이터: `build/jacoco/test.exec`
- 통합 테스트 실행 데이터: `build/jacoco/integrationTest.exec`
- 현재 전체 코드 하한: LINE 90%, BRANCH 80%
- 기준선 측정값(2026-07-14): LINE 78.09%, BRANCH 60.18%

## Mutation testing gate

```bash
./gradlew mutationTest pitest
```

- 대상: 정산 계산·권한·송금 상태와 민감정보 마스킹 핵심 클래스
- 하한: mutation score 83%, 대상 line coverage 90%
- 보고서: `build/reports/pitest/index.html`, `build/reports/pitest/mutations.xml`
- Spring Boot 4의 일반 테스트는 JUnit 6를 유지하고, 공개 PIT JUnit plugin 호환을 위한 `mutationTest` source set만 JUnit 5.13.4로 격리한다.
- Kotlin compiler null-check 호출을 변이하는 noise를 방지하기 위해 core gate에서 `VOID_METHOD_CALLS`를 제외한다. Redis Lua, native SQL, JPA lock은 PIT 점수가 아니라 Testcontainers 계약·동시성 테스트로 검증한다.

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
| 현재 달성 하한 | 90% | 80% |
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
