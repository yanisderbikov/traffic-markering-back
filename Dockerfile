FROM maven:3.9.9-eclipse-temurin-21 AS build

WORKDIR /app

COPY pom.xml .
RUN mvn dependency:go-offline -B

COPY src ./src

RUN mvn clean package -DskipTests



FROM eclipse-temurin:21-jre

# Видео-студия: FFmpeg (с libass для субтитров) и шрифт DejaVu для ASS
RUN apt-get update \
    && apt-get install -y --no-install-recommends ffmpeg fonts-dejavu-core \
    && rm -rf /var/lib/apt/lists/*

# Бот модерации: yt-dlp скачивает ролик с площадки, чтобы менеджер смотрел его прямо в Telegram.
# Сборка с официального релиза; извлекатели площадок ломаются часто, поэтому версия — свежая на момент сборки
# (конкретная версия: --build-arg YT_DLP_URL=https://github.com/yt-dlp/yt-dlp/releases/download/<тег>/yt-dlp_linux)
ARG YT_DLP_URL=https://github.com/yt-dlp/yt-dlp/releases/latest/download/yt-dlp_linux
ADD ${YT_DLP_URL} /usr/local/bin/yt-dlp
RUN chmod 755 /usr/local/bin/yt-dlp

WORKDIR /app

COPY --from=build /app/target/*.jar app.jar

EXPOSE 8090

ENTRYPOINT ["java", "-Xms256m", "-Xmx384m", "-XX:MetaspaceSize=128m", "-XX:MaxMetaspaceSize=256m", "-XX:+ExitOnOutOfMemoryError", "-XX:+UnlockExperimentalVMOptions", "-XX:G1MaxNewSizePercent=25", "-XX:G1PeriodicGCInterval=900000", "-Xlog:gc=info,gc+metaspace=info", "-jar", "app.jar"]
