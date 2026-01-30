# Multi-stage Dockerfile for URL Shortener Service
# Stage 1: Build stage
FROM maven:3.9-eclipse-temurin-24 AS build

LABEL stage=builder
LABEL description="Build stage for URL Shortener"

WORKDIR /app

# Copy Maven configuration and source code
COPY pom.xml .
COPY src ./src

# Build the application in one step (avoids dependency download issues)
# Skip tests in Docker build for faster builds (tests should run in CI/CD)
RUN mvn clean package -DskipTests -B

# Verify the JAR was created
RUN ls -lh /app/target/*.jar

# Stage 2: Runtime stage
FROM eclipse-temurin:24-jre-alpine

LABEL maintainer="URL Shortener Team"
LABEL description="URL Shortener Service - Production Runtime"
LABEL version="1.0.0"

WORKDIR /app

# Install curl for health checks
RUN apk add --no-cache curl

# Create non-root user for security
RUN addgroup -S appgroup && \
    adduser -S appuser -G appgroup

# Copy the JAR from build stage
COPY --from=build /app/target/*.jar app.jar

# Change ownership to non-root user
RUN chown -R appuser:appgroup /app

# Switch to non-root user
USER appuser

# Expose application port
EXPOSE 8080

# Health check configuration
# Checks /actuator/health endpoint every 30 seconds
# Waits 40 seconds before starting checks (application startup time)
# Times out after 3 seconds
# Retries 3 times before marking as unhealthy
HEALTHCHECK --interval=30s \
            --timeout=3s \
            --start-period=40s \
            --retries=3 \
    CMD curl -f http://localhost:8080/actuator/health || exit 1

# JVM options for containerized environment
ENV JAVA_OPTS="-XX:+UseContainerSupport \
               -XX:MaxRAMPercentage=75.0 \
               -XX:+UseG1GC \
               -XX:+DisableExplicitGC \
               -Djava.security.egd=file:/dev/./urandom"

# Run the application
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]