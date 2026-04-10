FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /app

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./

RUN chmod +x mvnw
RUN ./mvnw -B -DskipTests dependency:go-offline

COPY src/ src/

RUN ./mvnw -B -DskipTests package


FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

RUN groupadd --system spring \
    && useradd --system --gid spring --create-home spring \
    && mkdir -p /app/data/uploads \
    && chown -R spring:spring /app

ENV SPRING_PROFILES_ACTIVE=prod \
    APP_DOCUMENTS_UPLOAD_DIR=/app/data/uploads \
    SERVER_PORT=8081

COPY --from=build /app/target/*.jar /app/app.jar

EXPOSE 8081

VOLUME ["/app/data"]

USER spring:spring

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
