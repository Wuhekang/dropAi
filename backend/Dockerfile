FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY backend/pom.xml .
RUN mvn -q -DskipTests dependency:go-offline
COPY backend/src ./src
RUN mvn -q -Dmaven.test.skip=true package

FROM python:3.11-slim-bookworm
WORKDIR /app
ENV TZ=Asia/Shanghai
ENV WORD_FORMAT_PYTHON=python
ENV WORD_FORMAT_WORKER=/app/document-format-tool/format_cli.py
ENV WORD_FORMAT_DATA_DIR=/app/storage/word-format
ENV WORD_FORMAT_LEGACY_TEMPLATES_ENABLED=false
RUN apt-get update \
    && apt-get install -y --no-install-recommends openjdk-17-jre-headless ca-certificates \
    && rm -rf /var/lib/apt/lists/* \
    && mkdir -p /app/storage/uploads /app/storage/outputs /app/storage/jobs /app/storage/word-format
COPY document-format-tool /app/document-format-tool
RUN pip install --no-cache-dir -r /app/document-format-tool/requirements-web.txt
COPY --from=build /app/target/*.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
