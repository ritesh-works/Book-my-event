FROM eclipse-temurin:21-jdk-jammy AS builder
WORKDIR /app

# Maven is not bundled in the Temurin JDK image, so install it explicitly.
RUN apt-get update && apt-get install -y --no-install-recommends maven curl && rm -rf /var/lib/apt/lists/*

COPY . .
RUN mvn -B clean package -DskipTests

FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*

COPY --from=builder /app/target/Book-My-Event-1.0.0.jar app.jar

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=5s --start-period=45s --retries=3 \
    CMD curl -fsS http://localhost:8080/actuator/health/liveness || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
