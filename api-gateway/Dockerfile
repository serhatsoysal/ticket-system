FROM openjdk:21-jdk-slim

WORKDIR /app

COPY target/*.jar api-gateway.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "api-gateway.jar"]

