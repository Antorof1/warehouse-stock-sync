FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY libs/events/pom.xml libs/events/
COPY libs/idempotency/pom.xml libs/idempotency/
COPY libs/kafka-retry/pom.xml libs/kafka-retry/
COPY libs/outbox/pom.xml libs/outbox/
COPY services/warehouse-service/pom.xml services/warehouse-service/
COPY services/inventory-service/pom.xml services/inventory-service/
COPY services/reorder-service/pom.xml services/reorder-service/
COPY services/notification-service/pom.xml services/notification-service/
COPY services/simulator/pom.xml services/simulator/
RUN ./mvnw -B -pl services/simulator -am dependency:go-offline

COPY libs/events/src libs/events/src
COPY services/simulator/src services/simulator/src
RUN ./mvnw -B -pl services/simulator -am clean package -DskipTests \
    && cp services/simulator/target/*.jar app.jar

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring
COPY --from=build /app/app.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
