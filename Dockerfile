# syntax=docker/dockerfile:1

# ---- build stage: compile and package with Maven ----
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace
# Dependencies first so this layer is cached until the pom changes
COPY pom.xml .
RUN mvn -B -q dependency:go-offline
COPY src ./src
RUN mvn -B -q -DskipTests package

# ---- runtime stage: a small JRE image, no build tools ----
FROM eclipse-temurin:17-jre-alpine
RUN addgroup -S app && adduser -S app -G app
WORKDIR /app
COPY --from=build /workspace/target/career-backend-*.jar app.jar
USER app
ENV SPRING_PROFILES_ACTIVE=prod
ENV PORT=8080
EXPOSE 8080
# Spring Actuator health also checks PostgreSQL and Redis. $PORT lets Render (or any PaaS) override the port.
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -qO- http://localhost:${PORT}/actuator/health || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
