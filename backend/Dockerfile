# syntax=docker/dockerfile:1

FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
RUN mvn -B dependency:go-offline
COPY src ./src
RUN mvn -B clean package -DskipTests

FROM eclipse-temurin:17-jre
WORKDIR /app
RUN mkdir -p /app/database && \
    useradd -r -u 1001 -g root bmsuser && \
    chown -R bmsuser:root /app
COPY --from=build /app/target/*.jar /app/app.jar
USER bmsuser

ENV PORT=3000 \
    DB_PATH=/app/database/bms.db \
    JAVA_OPTS="-XX:MaxRAMPercentage=75.0 -XX:+UseSerialGC"

EXPOSE 3000
CMD ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
