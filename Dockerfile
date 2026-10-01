FROM eclipse-temurin:21-jre-noble as builder
WORKDIR /app
COPY . .
RUN apt-get update && apt-get install -y maven && mvn clean package -DskipTests

FROM eclipse-temurin:21-jre-noble
WORKDIR /app

COPY --from=builder /app/target/Book-My-Event-1.0.0.jar app.jar

EXPOSE 8080

HEALTHCHECK --interval=30s --timeout=3s --start-period=40s --retries=3 \
    CMD java -cp app.jar org.springframework.boot.loader.JarLauncher -c "curl -f http://localhost:8080/health/live || exit 1"

ENTRYPOINT ["java", "-jar", "app.jar"]
