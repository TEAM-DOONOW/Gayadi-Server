FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace
COPY gradle gradle
COPY gradlew build.gradle settings.gradle ./
RUN chmod +x gradlew && ./gradlew dependencies --no-daemon || true
COPY src src
RUN ./gradlew bootJar --no-daemon

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S gayadi && adduser -S gayadi -G gayadi
COPY --from=build /workspace/build/libs/gayadi-server-*.jar app.jar
USER gayadi
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
