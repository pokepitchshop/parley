# Native linux/amd64 image for Azure Container Apps (built via az acr build on Mac/CI).
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
# Boot JAR only — parley-*.jar also matches *-plain.jar from the jar task.
COPY build/libs/parley-*-SNAPSHOT.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
