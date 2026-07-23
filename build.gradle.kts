plugins {
    kotlin("jvm") version "2.2.21"
    kotlin("plugin.spring") version "2.2.21"
    id("org.springframework.boot") version "4.0.6"
    id("io.spring.dependency-management") version "1.1.7"
    id("org.sonarqube") version "7.3.1.8318"
    id("info.solidsoft.pitest") version "1.19.0"
    kotlin("plugin.jpa") version "2.2.21"
    jacoco
}

group = "com.togethertrip"
version = "0.0.1-SNAPSHOT"
description = "main"

java {
    toolchain {
        languageVersion = JavaLanguageVersion.of(21)
    }
}

repositories {
    mavenCentral()
}

val mutationTestSourceSet = sourceSets.create("mutationTest") {
    compileClasspath += sourceSets.main.get().output
    runtimeClasspath += output + compileClasspath
}

kotlin.sourceSets.named("mutationTest") {
    kotlin.srcDir("src/test/kotlin")
    kotlin.include(
        "com/togethertrip/main/global/logging/SensitiveDataMaskerTest.kt",
        "com/togethertrip/main/settlement/domain/SettlementTransferDirectionTest.kt",
        "com/togethertrip/main/settlement/domain/SettlementTransferTest.kt",
        "com/togethertrip/main/settlement/domain/calculation/SettlementCalculatorTest.kt",
        "com/togethertrip/main/settlement/service/SettlementTransferServiceTest.kt",
        "com/togethertrip/main/settlement/service/support/SettlementAccessResolverTest.kt",
        "com/togethertrip/main/settlement/service/support/SettlementCalculationServiceTest.kt",
        "com/togethertrip/main/settlement/service/support/TripParticipantBalanceSummaryProjectionServiceTest.kt",
    )
}

configurations[mutationTestSourceSet.implementationConfigurationName]
    .extendsFrom(configurations.testImplementation.get())
configurations[mutationTestSourceSet.runtimeOnlyConfigurationName]
    .extendsFrom(configurations.testRuntimeOnly.get())

val mutationJUnit5Modules = listOf(
    "org.junit.jupiter:junit-jupiter-api:5.13.4",
    "org.junit.jupiter:junit-jupiter-engine:5.13.4",
    "org.junit.jupiter:junit-jupiter-params:5.13.4",
    "org.junit.platform:junit-platform-commons:1.13.4",
    "org.junit.platform:junit-platform-engine:1.13.4",
    "org.junit.platform:junit-platform-launcher:1.13.4",
)

listOf(
    mutationTestSourceSet.compileClasspathConfigurationName,
    mutationTestSourceSet.runtimeClasspathConfigurationName,
    "pitest",
).forEach { configurationName ->
    configurations[configurationName].resolutionStrategy.force(mutationJUnit5Modules)
}

dependencies {
    implementation(platform("org.springframework.ai:spring-ai-bom:2.0.0"))
    implementation("org.springframework.ai:spring-ai-openai")
    implementation("org.springframework.boot:spring-boot-starter-data-jpa")
    implementation("org.hibernate.orm:hibernate-spatial")
    implementation("org.springframework.boot:spring-boot-starter-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-security")
    implementation("org.springframework.boot:spring-boot-starter-session-data-redis")
    implementation("org.springframework.boot:spring-boot-starter-validation")
    implementation("org.springframework.boot:spring-boot-starter-webmvc")
    implementation("org.springframework.boot:spring-boot-starter-batch")
    implementation("org.springframework:spring-aop")
    implementation("org.aspectj:aspectjweaver:1.9.25.1")
    implementation("org.redisson:redisson:3.40.2")
    implementation("org.jetbrains.kotlin:kotlin-reflect")
    implementation("tools.jackson.module:jackson-module-kotlin")
    implementation("io.jsonwebtoken:jjwt-api:0.12.6")
    implementation("org.springframework.boot:spring-boot-starter-webflux")
    implementation("org.springframework.boot:spring-boot-flyway")
    implementation("org.flywaydb:flyway-core")
    implementation("software.amazon.awssdk:sqs:2.25.70")
    runtimeOnly("io.jsonwebtoken:jjwt-impl:0.12.6")
    runtimeOnly("io.jsonwebtoken:jjwt-jackson:0.12.6")
    runtimeOnly("org.flywaydb:flyway-database-postgresql")
    implementation("org.springdoc:springdoc-openapi-starter-webmvc-ui:2.8.6")
    compileOnly("org.projectlombok:lombok")
    runtimeOnly("org.postgresql:postgresql")
    annotationProcessor("org.projectlombok:lombok")
    testImplementation("org.springframework.boot:spring-boot-starter-data-jpa-test")
    testImplementation("org.springframework.boot:spring-boot-starter-data-redis-test")
    testImplementation("org.springframework.boot:spring-boot-starter-security-test")
    testImplementation("org.springframework.boot:spring-boot-starter-session-data-redis-test")
    testImplementation("org.springframework.boot:spring-boot-starter-webmvc-test")
    testImplementation("org.springframework.boot:spring-boot-testcontainers")
    testImplementation("org.springframework.batch:spring-batch-test")
    testImplementation("org.jetbrains.kotlin:kotlin-test-junit5")
    testImplementation("org.testcontainers:testcontainers-junit-jupiter")
    testImplementation("org.testcontainers:testcontainers-postgresql")
    testCompileOnly("org.projectlombok:lombok")
    testRuntimeOnly("org.junit.platform:junit-platform-launcher")
    testAnnotationProcessor("org.projectlombok:lombok")
    pitest("org.junit.platform:junit-platform-commons:1.13.4")
    pitest("org.junit.platform:junit-platform-engine:1.13.4")
    pitest("org.junit.platform:junit-platform-launcher:1.13.4")
}

kotlin {
    compilerOptions {
        freeCompilerArgs.addAll("-Xjsr305=strict", "-Xannotation-default-target=param-property")
    }
}

allOpen {
    annotation("jakarta.persistence.Entity")
    annotation("jakarta.persistence.MappedSuperclass")
    annotation("jakarta.persistence.Embeddable")
}

tasks.withType<Test> {
    useJUnitPlatform()
}

tasks.test {
    useJUnitPlatform {
        excludeTags("integration")
    }
}

val integrationTest = tasks.register<Test>("integrationTest") {
    description = "Runs Docker-based integration tests."
    group = "verification"
    testClassesDirs = sourceSets["test"].output.classesDirs
    classpath = sourceSets["test"].runtimeClasspath
    shouldRunAfter(tasks.test)

    useJUnitPlatform {
        includeTags("integration")
    }
}

val mutationTest = tasks.register<Test>("mutationTest") {
    description = "Runs the unit-test subset used by PIT with a JUnit 5 compatible runtime."
    group = "verification"
    testClassesDirs = mutationTestSourceSet.output.classesDirs
    classpath = mutationTestSourceSet.runtimeClasspath
    useJUnitPlatform()
}

val jacocoExecutionData = fileTree(layout.buildDirectory.dir("jacoco")) {
    include("*.exec")
}

tasks.jacocoTestReport {
    dependsOn(tasks.test, integrationTest)
    executionData(jacocoExecutionData)

    reports {
        html.required = true
        xml.required = true
        csv.required = false
    }
}

tasks.jacocoTestCoverageVerification {
    dependsOn(tasks.test, integrationTest)
    executionData(jacocoExecutionData)

    violationRules {
        rule {
            limit {
                counter = "LINE"
                value = "COVEREDRATIO"
                minimum = "0.90".toBigDecimal()
            }

            limit {
                counter = "BRANCH"
                value = "COVEREDRATIO"
                minimum = "0.80".toBigDecimal()
            }
        }
    }
}

tasks.check {
    dependsOn(integrationTest, tasks.jacocoTestCoverageVerification, tasks.named("pitest"))
}

tasks.named("sonar") {
    dependsOn(tasks.jacocoTestReport)
}

sonar {
    properties {
        property(
            "sonar.projectKey",
            providers.environmentVariable("SONAR_PROJECT_KEY")
                .getOrElse("together-trip_togethertrip-server-main"),
        )
        property(
            "sonar.organization",
            providers.environmentVariable("SONAR_ORGANIZATION").getOrElse("together-trip"),
        )
        property("sonar.host.url", "https://sonarcloud.io")
        property(
            "sonar.coverage.jacoco.xmlReportPaths",
            layout.buildDirectory.file("reports/jacoco/test/jacocoTestReport.xml").get().asFile.path,
        )
    }
}

pitest {
    pitestVersion.set("1.25.7")
    junit5PluginVersion.set("1.2.3")
    targetClasses.set(
        listOf(
            "com.togethertrip.main.global.logging.SensitiveDataMasker",
            "com.togethertrip.main.settlement.domain.SettlementTransfer",
            "com.togethertrip.main.settlement.domain.SettlementTransferDirection",
            "com.togethertrip.main.settlement.domain.calculation.SettlementCalculator",
            "com.togethertrip.main.settlement.service.support.SettlementAccessResolver",
            "com.togethertrip.main.settlement.service.support.SettlementCalculationService",
            "com.togethertrip.main.settlement.service.support.SettlementTransferConfirmationProcessor",
            "com.togethertrip.main.settlement.service.support.TripParticipantBalanceSummaryProjectionService",
        )
    )
    targetTests.set(
        listOf(
            "com.togethertrip.main.global.logging.SensitiveDataMaskerTest",
            "com.togethertrip.main.settlement.domain.SettlementTransferDirectionTest",
            "com.togethertrip.main.settlement.domain.SettlementTransferTest",
            "com.togethertrip.main.settlement.domain.calculation.SettlementCalculatorTest",
            "com.togethertrip.main.settlement.service.SettlementTransferServiceTest",
            "com.togethertrip.main.settlement.service.support.SettlementAccessResolverTest",
            "com.togethertrip.main.settlement.service.support.SettlementCalculationServiceTest",
            "com.togethertrip.main.settlement.service.support.TripParticipantBalanceSummaryProjectionServiceTest",
        )
    )
    excludedTestClasses.set(listOf("*IntegrationTest", "*ConcurrencyTest"))
    testSourceSets.set(listOf(mutationTestSourceSet))
    outputFormats.set(listOf("HTML", "XML"))
    mutators.set(
        listOf(
            "CONDITIONALS_BOUNDARY",
            "INCREMENTS",
            "INVERT_NEGS",
            "MATH",
            "NEGATE_CONDITIONALS",
            "EMPTY_RETURNS",
            "FALSE_RETURNS",
            "TRUE_RETURNS",
            "NULL_RETURNS",
            "PRIMITIVE_RETURNS",
        )
    )
    timestampedReports.set(false)
    threads.set(maxOf(1, Runtime.getRuntime().availableProcessors() / 2))
    jvmArgs.set(listOf("-Xmx1536m"))
    mutationThreshold.set(83)
    coverageThreshold.set(90)
}
