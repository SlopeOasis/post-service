FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /workspace

COPY pom.xml ./
COPY src ./src
RUN mvn -DskipTests package

FROM eclipse-temurin:17-jre
WORKDIR /app

COPY --from=build /workspace/target/post-service-1.0-SNAPSHOT.jar ./app.jar

EXPOSE 8081
ENV SERVER_PORT=8081

ENTRYPOINT ["java","-jar","app.jar"]