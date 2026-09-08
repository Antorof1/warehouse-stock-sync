FROM eclipse-temurin:21-jdk-alpine AS build
WORKDIR /app

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
COPY services/warehouse-service/pom.xml services/warehouse-service/
RUN ./mvnw -B -pl services/warehouse-service -am dependency:go-offline

COPY services/warehouse-service/src services/warehouse-service/src
RUN ./mvnw -B -pl services/warehouse-service -am clean package -DskipTests \
    && cp services/warehouse-service/target/*.jar app.jar

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring
COPY --from=build /app/app.jar app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]
