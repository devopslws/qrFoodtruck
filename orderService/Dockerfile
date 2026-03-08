# orderService/Dockerfile

FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# build/libs에서 jar 복사 (*-SNAPSHOT.jar 패턴)
COPY build/libs/*.jar app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-jar", "app.jar"]
