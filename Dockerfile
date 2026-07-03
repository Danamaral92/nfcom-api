# Build stage
FROM maven:3.9-eclipse-temurin-21-alpine AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn package -DskipTests -q

# Run stage
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app
COPY --from=build /app/target/*-runner.jar /app/app.jar
COPY --from=build /app/target/lib/ /app/lib/

# Create user for security
RUN addgroup -S nfcom && adduser -S nfcom -G nfcom
USER nfcom

EXPOSE 8080

ENV NFCOM_CERT_PATH=/app/cert.p12
ENV NFCOM_CERT_PASSWORD=
ENV NFCOM_SEFAZ_URL=https://dfe-portal.svrs.rs.gov.br/NFCom

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
