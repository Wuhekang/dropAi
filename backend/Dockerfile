FROM maven:3.9.9-eclipse-temurin-17 AS build
WORKDIR /app
COPY backend/pom.xml .
RUN mvn -q -DskipTests dependency:go-offline
COPY backend/src ./src
RUN mvn -q -Dmaven.test.skip=true package

FROM python:3.11-slim-bookworm
WORKDIR /app
ARG DOKIAI_GIT_COMMIT
ENV TZ=Asia/Shanghai \
    WORD_FORMAT_PYTHON=python \
    WORD_FORMAT_WORKER=/app/document-format-tool/format_cli.py \
    WORD_FORMAT_DATA_DIR=/app/storage/word-format \
    WORD_FORMAT_LEGACY_TEMPLATES_ENABLED=false \
    DOKIAI_PPT_FONT_FILES="400=/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc,500=/usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc,600=/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc,700=/usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc" \
    DOKIAI_GIT_COMMIT="${DOKIAI_GIT_COMMIT}"
RUN apt-get update \
    && apt-get install -y --no-install-recommends openjdk-17-jre-headless ca-certificates fontconfig fonts-noto-cjk \
    && fc-cache -f \
    && test -r /usr/share/fonts/opentype/noto/NotoSansCJK-Regular.ttc \
    && test -r /usr/share/fonts/opentype/noto/NotoSansCJK-Bold.ttc \
    && echo "$DOKIAI_GIT_COMMIT" | grep -Eq '^([a-f0-9]{40}|[a-f0-9]{64})$' \
    && rm -rf /var/lib/apt/lists/* \
    && mkdir -p /app/storage/uploads /app/storage/outputs /app/storage/jobs /app/storage/word-format
COPY document-format-tool /app/document-format-tool
RUN pip install --no-cache-dir -r /app/document-format-tool/requirements-web.txt
COPY --from=build /app/target/*.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
