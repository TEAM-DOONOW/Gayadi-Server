FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /workspace
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw --batch-mode dependency:go-offline
COPY src src
RUN ./mvnw --batch-mode clean package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S gayadi && adduser -S gayadi -G gayadi
COPY --from=build /workspace/target/gayadi-server-*.jar app.jar
USER gayadi
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
