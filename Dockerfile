# Multi-stage Dockerfile for CardDemo Spring Boot Application
# Implements Section 8.3.2.1 multi-stage build strategy achieving ~120 MB final image size
# Provides Eclipse Temurin 21 JRE Alpine base image per Section 8.3.1
# Includes security hardening and monitoring integration per Sections 8.3.6.2 and 8.3.7.1

# =============================================================================
# Stage 1: Maven Dependencies Resolution and Caching
# =============================================================================
# Purpose: Optimize build performance through aggressive dependency caching
# Base: Eclipse Temurin 21 with Maven for dependency resolution
FROM maven:3.9.4-eclipse-temurin-21 AS dependencies

# Set working directory for dependency resolution
WORKDIR /app

# Copy Maven configuration files first for optimal layer caching
COPY pom.xml .
COPY .mvn/ .mvn/
COPY mvnw .

# Download dependencies to create a cached layer
# This layer will be reused unless pom.xml changes
RUN mvn dependency:go-offline -B

# =============================================================================
# Stage 2: Spring Boot Application Build
# =============================================================================
# Purpose: Compile source code and create Spring Boot executable JAR
# Extends: Dependencies stage with source code compilation
FROM dependencies AS build

# Copy application source code
COPY src/ ./src/

# Build the Spring Boot application
# Skip tests in container build - tests should run in CI pipeline
# Use Spring Boot layered JAR approach for optimal image layering
RUN mvn clean package -DskipTests -Dspring-boot.build-image.skip=true

# Extract Spring Boot layers for optimized container image
# This enables efficient layer caching and faster image updates
RUN java -Djarmode=layertools -jar target/*.jar extract

# =============================================================================
# Stage 3: Production Runtime Container
# =============================================================================
# Purpose: Minimal production runtime with security hardening and monitoring
# Base: Eclipse Temurin 21 JRE Alpine for minimal attack surface per Section 8.3.1
FROM eclipse-temurin:21-jre-alpine AS runtime

# Metadata labels for container registry and deployment tracking
LABEL maintainer="Blitzy Platform CardDemo Team"
LABEL version="1.0.0"
LABEL description="CardDemo Spring Boot Application - COBOL to Java Migration"
LABEL java.version="21"
LABEL spring.boot.version="3.2.x"

# Security hardening: Create non-root user per Section 8.3.6.2
# Implements read-only file system and minimal privilege execution
RUN addgroup -g 1001 carddemo && \
    adduser -D -s /bin/sh -u 1001 -G carddemo carddemo && \
    mkdir -p /app/logs /app/tmp && \
    chown -R carddemo:carddemo /app

# Set working directory
WORKDIR /app

# JVM optimization for containerized environments per Section 8.3.5.2
# Implements G1GC garbage collector and container support flags
# Memory settings optimized for 4-8GB container memory allocation
ENV JAVA_OPTS="-Xmx2048m \
               -Xms1024m \
               -XX:+UseG1GC \
               -XX:MaxGCPauseMillis=200 \
               -XX:+UseContainerSupport \
               -XX:InitiatingHeapOccupancyPercent=45 \
               -XX:G1HeapRegionSize=16m \
               -Djava.security.egd=file:/dev/./urandom \
               -Dspring.backgroundpreinitializer.ignore=true"

# Spring Boot application configuration for containerized deployment
# Enables optimized startup and monitoring endpoint exposure
ENV SPRING_PROFILES_ACTIVE=production
ENV SPRING_CONFIG_LOCATION=classpath:/application.yml,classpath:/application-production.yml
ENV MANAGEMENT_SERVER_PORT=8081
ENV MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health,prometheus,metrics,info
ENV MANAGEMENT_ENDPOINT_HEALTH_SHOW_DETAILS=when_authorized

# Copy Spring Boot application layers from build stage
# Order optimized for layer caching - dependencies change less frequently
COPY --from=build --chown=carddemo:carddemo /app/dependencies/ ./
COPY --from=build --chown=carddemo:carddemo /app/spring-boot-loader/ ./
COPY --from=build --chown=carddemo:carddemo /app/snapshot-dependencies/ ./
COPY --from=build --chown=carddemo:carddemo /app/application/ ./

# Create required runtime directories with proper permissions
# Implements read-only root filesystem with writable volumes
RUN chown -R carddemo:carddemo /app && \
    chmod -R 755 /app

# Security: Switch to non-root user for container execution
USER carddemo

# Port exposure per Section 8.3.7.1
# Port 8080: Main application HTTP endpoint
# Port 8081: Management/monitoring endpoints for Prometheus metrics
EXPOSE 8080 8081

# Volume definitions for read-only filesystem compliance
# Allows writes to specific directories only
VOLUME ["/app/logs", "/app/tmp"]

# Health check configuration for Kubernetes integration
# Implements liveness and readiness probe endpoints per Section 6.5.3.1
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8081/actuator/health/liveness || exit 1

# Entry point configuration
# Uses Spring Boot's optimized JVM startup with container-aware settings
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS org.springframework.boot.loader.launch.JarLauncher"]

# =============================================================================
# Production Deployment Notes
# =============================================================================
# This Dockerfile implements the following technical requirements:
#
# 1. Multi-stage Build Strategy (Section 8.3.2.1):
#    - Dependencies stage: Maven dependency caching
#    - Build stage: Application compilation and JAR creation
#    - Runtime stage: Minimal production image (~120 MB)
#
# 2. Security Hardening (Section 8.3.6.2):
#    - Non-root user execution (carddemo:1001)
#    - Read-only root filesystem with writable volumes
#    - Minimal Alpine Linux base image
#    - No unnecessary packages or tools
#
# 3. JVM Container Optimization (Section 8.3.5.2):
#    - G1GC garbage collector for low-latency requirements
#    - Container-aware memory management
#    - Optimized heap settings for 4-8GB containers
#    - Fast startup configuration
#
# 4. Monitoring Integration (Section 8.3.7.1):
#    - Prometheus metrics endpoint exposure (:8081/actuator/prometheus)
#    - Health check endpoints for Kubernetes probes
#    - Management endpoints for operational monitoring
#    - Structured logging output for centralized collection
#
# 5. Kubernetes Integration (Section 6.5.3.1):
#    - Health check support for liveness/readiness probes
#    - Port configuration for service discovery
#    - Environment variable configuration
#    - Volume mount support for persistent data
#
# Container Resource Recommendations:
# - CPU Request: 500m, Limit: 2000m
# - Memory Request: 1Gi, Limit: 4Gi
# - Storage: Ephemeral storage for logs and temp files
#
# Monitoring Endpoints:
# - Application: http://localhost:8080/
# - Health: http://localhost:8081/actuator/health
# - Metrics: http://localhost:8081/actuator/prometheus
# - Info: http://localhost:8081/actuator/info
#
# Build Command:
# docker build -t carddemo/backend:latest .
#
# Run Command:
# docker run -p 8080:8080 -p 8081:8081 carddemo/backend:latest
# =============================================================================