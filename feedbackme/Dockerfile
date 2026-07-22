FROM node:22-alpine AS frontend-build
WORKDIR /frontend

COPY frontend/package.json ./
RUN npm install

COPY frontend ./
RUN npm run build

FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

COPY gradlew settings.gradle build.gradle gradle.properties ./
COPY gradle ./gradle
RUN chmod +x ./gradlew

COPY src ./src
COPY --from=frontend-build /frontend/dist ./src/main/resources/static
RUN --mount=type=cache,target=/root/.gradle ./gradlew test --no-daemon
RUN --mount=type=cache,target=/root/.gradle ./gradlew clean bootJar --no-daemon

FROM eclipse-temurin:21-jre
WORKDIR /app

RUN addgroup --system spring && adduser --system spring --ingroup spring
USER spring:spring

COPY --from=build /workspace/build/libs/*.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
