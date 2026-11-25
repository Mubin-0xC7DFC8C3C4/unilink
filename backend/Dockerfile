# UniLink backend — multi-stage build (Java 21 / Spring Boot).
# NOTE: prepared for the production compose stack; not build-verified in
# environments without Docker. Build from the repository root compose file.
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /src
ENV MAVEN_CONFIG=""
COPY pom.xml ./
COPY .mvn ./.mvn
COPY mvnw ./
RUN ./mvnw -q -B dependency:go-offline -DskipTests || true
COPY src ./src
RUN ./mvnw -q -B package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
# D-172 (2026-11-25): OCR needs the `tesseract` binary at runtime —
# OcrJobService shells out to it for ID verification. Without it every
# verification fails with "Cannot run program tesseract: No such file or
# directory" (observed in the production log 2026-11-25; ocrStatus FAILED
# for every application). Installed here, as root, before USER unilink.
RUN apt-get update && apt-get install -y --no-install-recommends tesseract-ocr && rm -rf /var/lib/apt/lists/*
RUN useradd -r -m unilink && mkdir -p /data/unilink/storage && chown -R unilink:unilink /data
COPY --from=build /src/target/unilink-backend-*.jar /app/app.jar
USER unilink
EXPOSE 8080
ENV SPRING_PROFILES_ACTIVE=prod
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s \
  CMD bash -c 'exec 3<>/dev/tcp/127.0.0.1/8080 && printf "GET /api/health HTTP/1.0\r\n\r\n" >&3 && grep -q "200" <&3'
# JVM sized for a 512 MB container (Render Free): heap capped by
# MaxRAMPercentage, metaspace bounded, thin thread stacks, SerialGC for
# the smallest footprint. Override via JAVA_TOOL_OPTIONS if the host
# provides more memory.
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=65", "-XX:MaxMetaspaceSize=256m", "-Xss512k", "-XX:+UseSerialGC", "-jar", "/app/app.jar"]
