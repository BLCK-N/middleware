FROM maven:3.9.9-eclipse-temurin-21 AS builder
WORKDIR /app

#COPY settings.xml /root/.m2/settings.xml
COPY pom.xml .

RUN mvn -B -ntp -DskipTests dependency:go-offline

COPY src ./src

RUN mvn -B -ntp clean package

# ← this was missing
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN apk add --no-cache curl && \
    addgroup -S spring && adduser -S spring -G spring

COPY --from=builder /app/target/myrtle_middleware-*.jar app.jar

RUN chown spring:spring app.jar

USER spring:spring

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]