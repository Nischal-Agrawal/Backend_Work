# Multi-stage Dockerfile for Personal Finance Manager API
# Stage 1: Build the application
FROM eclipse-temurin:17-jdk-alpine AS builder
WORKDIR /workspace

# Copy maven wrapper and pom.xml
COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

# Ensure execute permissions and unix line endings for maven wrapper
RUN tr -d '\r' < ./mvnw > ./mvnw_clean && mv ./mvnw_clean ./mvnw && chmod +x ./mvnw

# Download dependencies offline
RUN sh ./mvnw dependency:go-offline -B

# Copy application source code
COPY src/ src/

# Build production JAR without re-running tests
RUN sh ./mvnw clean package -DskipTests -B

# Stage 2: Runtime image
FROM eclipse-temurin:17-jre-alpine
WORKDIR /app

# Run as non-privileged user for container security
RUN addgroup -S appgroup && adduser -S appuser -G appgroup
USER appuser

# Copy JAR from builder stage
COPY --from=builder /workspace/target/personal-finance-manager-1.0.0.jar app.jar

ENV PORT=8080
EXPOSE 8080

ENTRYPOINT ["java", "-Djava.security.egd=file:/dev/./urandom", "-jar", "app.jar"]
