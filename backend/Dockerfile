# syntax=docker/dockerfile:1
# ---- Build stage: compile and package the Spring Boot jar on JDK 21 ----
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
# Resolve dependencies first (pom changes rarely relative to source) so the layer caches well.
COPY pom.xml lombok.config ./
RUN mvn -B -ntp dependency:go-offline
COPY src ./src
RUN mvn -B -ntp clean package -DskipTests

# ---- Runtime stage: minimal JRE, non-root, read-only-friendly ----
FROM eclipse-temurin:21-jre AS runtime
# Create an unprivileged user; never run the app as root.
RUN groupadd --system sentinel && useradd --system --gid sentinel --home /app --shell /usr/sbin/nologin sentinel
WORKDIR /app
COPY --from=build /build/target/*.jar /app/app.jar
# curl is only used by the healthcheck.
RUN apt-get update && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && chown -R sentinel:sentinel /app
USER sentinel
EXPOSE 8080
ENV JAVA_OPTS="-XX:MaxRAMPercentage=75.0"
HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD curl -fsS http://localhost:8080/api/health || exit 1
ENTRYPOINT ["sh", "-c", "exec java $JAVA_OPTS -jar /app/app.jar"]
