# syntax=docker/dockerfile:1.7
FROM eclipse-temurin:26-jdk-alpine AS builder
WORKDIR /app
COPY build.gradle settings.gradle gradlew ./
COPY gradle/ gradle/
COPY src/ src/
RUN --mount=type=secret,id=github_token,required=true \
    GITHUB_TOKEN="$(cat /run/secrets/github_token)" ./gradlew build --no-daemon -x test

FROM eclipse-temurin:26-jre
RUN addgroup -S gym && adduser -S gym -G gym
WORKDIR /app
COPY --from=builder /app/build/libs/*.jar app.jar
USER gym
EXPOSE 8080 50051
ENTRYPOINT ["java", "-jar", "app.jar"]
