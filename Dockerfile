# Imagem de produção do Chat Jr. Duas etapas: compila com Maven e roda só com o Java necessário.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn -B -ntp -q dependency:go-offline
COPY src ./src
RUN mvn -B -ntp -q -DskipTests package && mv target/chat-jr-*.jar target/app.jar

FROM eclipse-temurin:21-jre-alpine
RUN addgroup -S chatjr && adduser -S chatjr -G chatjr && mkdir -p /data && chown chatjr:chatjr /data
WORKDIR /app
COPY --from=build /build/target/app.jar app.jar
USER chatjr
ENV CHATJR_DATA=/data
VOLUME /data
EXPOSE 8080
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD wget -qO- http://localhost:8080/actuator/health | grep -q '"status":"UP"' || exit 1
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
