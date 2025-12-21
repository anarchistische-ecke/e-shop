FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /app

# Preload dependency graph for better caching
COPY pom.xml mvnw mvnw.cmd ./
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

FROM eclipse-temurin:21-jre
RUN apt-get update && apt-get install -y curl && rm -rf /var/lib/apt/lists/*
WORKDIR /app
COPY --from=build /app/api/target/api-*.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java","-XX:+UseContainerSupport","-jar","/app/app.jar"]
