# syntax=docker/dockerfile:1

# --- Build stage -------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Cache dependencies first.
COPY pom.xml .
RUN mvn -B -q dependency:go-offline

COPY src ./src
RUN mvn -B -q clean package -DskipTests

# --- Runtime stage -----------------------------------------------------------
FROM eclipse-temurin:21-jre AS runtime
WORKDIR /app

# Non-root user.
RUN groupadd --system app && useradd --system --gid app --home /app app

COPY --from=build /workspace/target/sppo-gtfs-service-*.jar app.jar
RUN chown -R app:app /app
USER app

EXPOSE 8080

# Readiness, not plain health: /actuator/health is UP as soon as Spring is up, while the
# in-memory index is still being built from BigQuery (~20 s). The readiness probe only
# reports UP after the startup load (see IndexReloadRunner), so a consumer gated by
# `depends_on: condition: service_healthy` never queries an empty index.
HEALTHCHECK --interval=15s --timeout=3s --start-period=120s --retries=5 \
    CMD ["sh", "-c", "wget -qO- http://localhost:8080/actuator/health/readiness | grep -q '\"status\":\"UP\"'"]

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "/app/app.jar"]
