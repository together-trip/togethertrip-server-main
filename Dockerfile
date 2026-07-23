FROM eclipse-temurin:21-jdk AS builder

WORKDIR /app

COPY gradlew .
COPY gradle gradle
COPY build.gradle.kts .
COPY settings.gradle.kts .
COPY src src

RUN chmod +x ./gradlew
RUN ./gradlew clean bootJar

FROM eclipse-temurin:21-jre

WORKDIR /app

RUN groupadd --system --gid 10001 togethertrip \
    && useradd --system --uid 10001 --gid togethertrip --home-dir /app --shell /usr/sbin/nologin togethertrip

COPY --from=builder --chown=10001:10001 /app/build/libs/*.jar app.jar

USER 10001:10001

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
