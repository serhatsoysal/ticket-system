FROM openjdk:21-jdk-slim

WORKDIR /app

COPY target/*.jar user-service.jar

EXPOSE 8082

ENTRYPOINT ["java", "-jar", "user-service.jar"]

