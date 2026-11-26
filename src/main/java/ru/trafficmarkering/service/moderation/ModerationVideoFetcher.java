package ru.trafficmarkering.service.moderation;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.trafficmarkering.config.ModerationProperties;
import ru.trafficmarkering.model.application.Platform;
import ru.trafficmarkering.service.scrape.ScrapeCreatorsApi;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Скачивает ролик с площадки, чтобы менеджер смотрел его прямо в Telegram.
 * Сначала yt-dlp, затем прямая ссылка от ScrapeCreators; не вышло — менеджер получит ссылку.
 */
@Slf4j
@Component
public class ModerationVideoFetcher {

    private static final String USER_AGENT = "Mozilla/5.0 (Windows NT 10.0; Win64; x64) AppleWebKit/537.36 "
            + "(KHTML, like Gecko) Chrome/124.0 Safari/537.36";

    private final ModerationProperties properties;
    private final ScrapeCreatorsApi scrape;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(15))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();
    private volatile boolean ytDlpMissing;

    public ModerationVideoFetcher(ModerationProperties properties, ScrapeCreatorsApi scrape) {
        this.properties = properties;
        this.scrape = scrape;
    }

    /** Временная папка с роликом; вызывающий удаляет её через {@link #cleanup(Path)} */
    public Optional<Path> fetch(Platform platform, String videoUrl) {
        if (videoUrl == null || videoUrl.isBlank()) {
            return Optional.empty();
        }
        Path dir;
        try {
            Path root = Path.of(properties.getWorkDir());
            Files.createDirectories(root);
            dir = Files.createDirectory(root.resolve(UUID.randomUUID().toString()));
        } catch (IOException e) {
            log.warn("Модерация: нет рабочей папки для видео: {}", e.getMessage());
            return Optional.empty();
        }
        long limit = properties.uploadLimit();
        Optional<Path> file = ytDlp(videoUrl, dir, limit);
        if (file.isEmpty() && platform != null && scrape.isConfigured()) {
            file = direct(platform, videoUrl, dir, limit);
        }
        if (file.isEmpty()) {
            cleanup(dir);
        }
        return file;
    }

    private Optional<Path> ytDlp(String videoUrl, Path dir, long limit) {
        String binary = properties.getYtDlp();
        if (binary == null || binary.isBlank() || ytDlpMissing) {
            return Optional.empty();
        }
        List<String> command = new ArrayList<>(List.of(binary,
                "--no-playlist", "--no-progress", "--quiet", "--no-warnings",
                "--socket-timeout", "20",
                "--max-filesize", String.valueOf(limit),
                // Телефонный формат: mp4 до 720p, одним файлом либо видео+звук со склейкой в mp4
                "-S", "res:720,ext:mp4:m4a",
                "--merge-output-format", "mp4",
                "-o", dir.resolve("video.%(ext)s").toString(),
                videoUrl));
        Process process;
        try {
            process = new ProcessBuilder(command).redirectErrorStream(true)
                    .redirectOutput(dir.resolve("yt-dlp.log").toFile())
                    .start();
        } catch (IOException e) {
            ytDlpMissing = true;
            log.warn("Модерация: yt-dlp не запускается ({}), дальше без него", e.getMessage());
            return Optional.empty();
        }
        try {
            if (!process.waitFor(properties.getDownloadTimeoutSeconds(), TimeUnit.SECONDS)) {
                process.destroyForcibly();
                log.warn("Модерация: yt-dlp не уложился в {} с: {}", properties.getDownloadTimeoutSeconds(), videoUrl);
                return Optional.empty();
            }
        } catch (InterruptedException e) {
            process.destroyForcibly();
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
        Optional<Path> file = videoIn(dir, limit);
        if (file.isEmpty()) {
            log.info("Модерация: yt-dlp не скачал {} (код {}): {}", videoUrl, process.exitValue(), tail(dir.resolve("yt-dlp.log")));
        }
        return file;
    }

    private Optional<Path> direct(Platform platform, String videoUrl, Path dir, long limit) {
        Optional<String> fileUrl;
        try {
            fileUrl = scrape.videoFileUrl(platform, videoUrl);
        } catch (RuntimeException e) {
            log.info("Модерация: ScrapeCreators не дал ссылку на файл {}: {}", videoUrl, e.getMessage());
            return Optional.empty();
        }
        if (fileUrl.isEmpty()) {
            return Optional.empty();
        }
        Path target = dir.resolve("direct.mp4");
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(fileUrl.get()))
                    .timeout(Duration.ofSeconds(properties.getDownloadTimeoutSeconds()))
                    .header("User-Agent", USER_AGENT)
                    .header("Referer", platform == Platform.TIKTOK ? "https://www.tiktok.com/" : "https://www.instagram.com/")
                    .GET().build();
            HttpResponse<InputStream> response = http.send(request, HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                response.body().close();
                log.info("Модерация: файл ролика {} ответил HTTP {}", videoUrl, response.statusCode());
                return Optional.empty();
            }
            try (InputStream in = response.body(); OutputStream out = Files.newOutputStream(target)) {
                byte[] buffer = new byte[256 * 1024];
                long total = 0;
                int read;
                while ((read = in.read(buffer)) > 0) {
                    total += read;
                    if (total > limit) {
                        log.info("Модерация: ролик {} больше {} байт — пришлём ссылкой", videoUrl, limit);
                        return Optional.empty();
                    }
                    out.write(buffer, 0, read);
                }
            }
            return Files.size(target) > 0 ? Optional.of(target) : Optional.empty();
        } catch (IOException | IllegalArgumentException e) {
            log.info("Модерация: не скачали файл ролика {}: {}", videoUrl, e.getMessage());
            return Optional.empty();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            return Optional.empty();
        }
    }

    private static Optional<Path> videoIn(Path dir, long limit) {
        try (Stream<Path> files = Files.list(dir)) {
            return files.filter(path -> path.getFileName().toString().startsWith("video."))
                    .filter(path -> !path.getFileName().toString().endsWith(".part"))
                    .filter(path -> {
                        try {
                            long size = Files.size(path);
                            return size > 0 && size <= limit;
                        } catch (IOException e) {
                            return false;
                        }
                    })
                    .findFirst();
        } catch (IOException e) {
            return Optional.empty();
        }
    }

    private static String tail(Path log) {
        try {
            String text = Files.readString(log).strip();
            return text.length() > 300 ? text.substring(text.length() - 300) : text;
        } catch (IOException e) {
            return "";
        }
    }

    public void cleanup(Path fileOrDir) {
        if (fileOrDir == null) {
            return;
        }
        Path dir = Files.isDirectory(fileOrDir) ? fileOrDir : fileOrDir.getParent();
        try (Stream<Path> files = Files.walk(dir)) {
            files.sorted((a, b) -> b.getNameCount() - a.getNameCount()).forEach(path -> {
                try {
                    Files.deleteIfExists(path);
                } catch (IOException ignored) {
                    // временный файл — подчистит перезапуск контейнера
                }
            });
        } catch (IOException ignored) {
            // папки уже нет
        }
    }
}
