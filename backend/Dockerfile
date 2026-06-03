# syntax=docker/dockerfile:1.7

FROM eclipse-temurin:25-jdk AS build
WORKDIR /app

COPY gradlew build.gradle settings.gradle ./
COPY gradle ./gradle
COPY backend ./backend
# agent/build.gradle é necessário para o Gradle configurar o multi-project,
# mas a src não precisa ser copiada pois :backend:bootJar não depende de :agent
COPY agent/build.gradle ./agent/build.gradle

RUN --mount=type=cache,target=/root/.gradle \
    chmod +x ./gradlew && \
    JAVA_TOOL_OPTIONS="-Xmx1g -XX:MaxMetaspaceSize=384m" \
    GRADLE_OPTS="-Dorg.gradle.jvmargs=-Xmx1g -Dorg.gradle.daemon=false" \
    ./gradlew :backend:bootJar -x test --no-daemon

FROM eclipse-temurin:25-jre
WORKDIR /app

COPY --from=build /app/backend/build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
