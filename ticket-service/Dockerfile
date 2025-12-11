FROM openjdk:21-jdk-slim

WORKDIR /app

COPY target/*.jar ticket-service.jar

EXPOSE 8081

ENTRYPOINT ["java", "-jar", "ticket-service.jar"]

