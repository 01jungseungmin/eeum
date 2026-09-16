FROM eclipse-temurin:17-jdk-jammy AS build

WORKDIR /workspace/backend

COPY backend/gradle gradle
COPY backend/gradlew backend/build.gradle backend/settings.gradle ./
RUN chmod +x gradlew

COPY backend/src src
RUN ./gradlew --no-daemon bootJar
RUN cp "$(find build/libs -name '*.jar' ! -name '*-plain.jar' -print -quit)" /tmp/app.jar

FROM eclipse-temurin:17-jre-jammy

WORKDIR /app

RUN useradd --system --uid 10001 spring
COPY --from=build --chown=spring:spring /tmp/app.jar app.jar

USER spring

EXPOSE 8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
