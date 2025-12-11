FROM openjdk:21-jdk-slim

WORKDIR /app

COPY target/*.jar payment-service.jar

EXPOSE 8083

ENTRYPOINT ["java", "-jar", "payment-service.jar"]

