# =============================================================================
# Multi-stage Dockerfile for Log Intelligence Platform
# =============================================================================
#
# Stage 1 (builder): Uses a full Maven + JDK 17 image to compile the project
#   and package it into a fat JAR.  Maven's local repository cache is populated
#   here so the final image doesn't need any build tooling.
#
# Stage 2 (runtime): Uses a minimal JRE-only image (no JDK, no Maven) to run
#   the pre-built JAR.  This keeps the final image small (~250 MB vs ~600 MB
#   for a JDK-based image) and reduces the attack surface.
# =============================================================================

# ---------------------------------------------------------------------------
# Stage 1 — Build
# ---------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-17 AS builder

WORKDIR /app

# Copy the POM first and download all dependencies.
# Docker caches this layer separately — if only source files change but pom.xml
# is unchanged, this expensive step is skipped on subsequent builds.
COPY pom.xml .
RUN mvn dependency:go-offline --batch-mode --quiet

# Copy source code and produce the executable fat-JAR.
# -DskipTests: tests require a live DB which is not available at build time.
COPY src ./src
RUN mvn package --batch-mode --quiet -DskipTests

# ---------------------------------------------------------------------------
# Stage 2 — Runtime
# ---------------------------------------------------------------------------
FROM eclipse-temurin:17-jre

# Non-root user for security best practice — don't run JVM processes as root
RUN addgroup --system appgroup && adduser --system --ingroup appgroup appuser
USER appuser

WORKDIR /app

# Copy only the built JAR from the builder stage — nothing else
COPY --from=builder /app/target/*.jar app.jar

# Port the Spring Boot app listens on (matches server.port in properties)
EXPOSE 8081

# JAVA_OPTS can be overridden at runtime, e.g. to tune heap size:
#   docker run -e JAVA_OPTS="-Xmx512m" ...
ENV JAVA_OPTS=""

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
