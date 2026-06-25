FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app

# Preload dependency graph for better caching
COPY pom.xml mvnw mvnw.cmd ./
COPY .mvn .mvn
COPY common/pom.xml common/pom.xml
COPY catalog/pom.xml catalog/pom.xml
COPY cart/pom.xml cart/pom.xml
COPY customer/pom.xml customer/pom.xml
COPY order/pom.xml order/pom.xml
COPY payment/pom.xml payment/pom.xml
COPY shipment/pom.xml shipment/pom.xml
COPY api/pom.xml api/pom.xml
COPY admin/pom.xml admin/pom.xml
RUN ./mvnw -B -ntp -pl api -am dependency:go-offline

# Build all sources
COPY . .
RUN ./mvnw -B -ntp -pl api -am package -DskipTests

FROM node:22-bookworm-slim AS media
WORKDIR /media
COPY scripts/media-derivatives/package.json scripts/media-derivatives/package-lock.json ./
RUN npm ci --omit=dev
COPY scripts/media-derivatives/config.mjs scripts/media-derivatives/process-image.mjs ./
COPY api/src/main/resources/media-derivatives.json ./media-derivatives.json

FROM eclipse-temurin:21-jre
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /app/api/target/api-*.jar /app/app.jar
COPY --from=media /usr/local/bin/node /usr/local/bin/node
COPY --from=media /media /app/media
ENV MEDIA_DERIVATIVES_CONFIG=/app/media/media-derivatives.json
EXPOSE 8080
ENTRYPOINT ["java","-XX:+UseContainerSupport","-jar","/app/app.jar"]
