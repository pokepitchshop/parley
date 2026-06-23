# Native linux/amd64 image for Azure Container Apps (built via az acr build on Mac/CI).
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
# bootJar only — plain jar task is disabled in build.gradle
COPY build/libs/parley-*.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
