FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -q -DskipTests dependency:go-offline

COPY src src
RUN ./mvnw -q -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app

ENV TZ=Asia/Shanghai
ENV SERVER_PORT=8123

COPY --from=build /workspace/target/*.jar /app/app.jar

EXPOSE 8123
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
