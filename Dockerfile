FROM openjdk:17-ea-33-jdk-slim-buster

WORKDIR /app
COPY ./target/lisa-0.0.24.jar /app

CMD ["java", "-jar", "lisa-0.0.24.jar"]
