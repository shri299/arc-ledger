FROM eclipse-temurin:17-jre-jammy

RUN apt-get update \
    && apt-get install -y --no-install-recommends ca-certificates curl \
    && rm -rf /var/lib/apt/lists/*

WORKDIR /app
COPY arcledger.jar /app/arcledger.jar

USER 1001:1001
EXPOSE 8080

ENTRYPOINT ["java", "-Xms128m", "-Xmx1024m", "-jar", "/app/arcledger.jar"]
