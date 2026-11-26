package ru.trafficmarkering.service.studio;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;
import ru.trafficmarkering.config.StudioProperties;
import ru.trafficmarkering.model.User;
import ru.trafficmarkering.model.studio.StudioJob;
import ru.trafficmarkering.model.studio.StudioJobStatus;
import ru.trafficmarkering.repository.StudioJobRepository;
import ru.trafficmarkering.repository.UserRepository;
import ru.trafficmarkering.service.studio.telegram.StudioNotifier;

import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardCopyOption;
import java.time.Duration;
import java.time.Instant;
import java.util.Comparator;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

/**
 * Воркер рендера: забирает задачи из очереди в БД по одной и собирает их FFmpeg-ом.
 * Отдельный поток, чтобы долгий рендер не занимал общий пул планировщика.
 */
@Slf4j
@Component
public class StudioRenderWorker {

    private final StudioJobRepository jobs;
    private final UserRepository users;
    private final StudioService studio;
    private final StudioStorage storage;
    private final StudioProperties properties;
    private final StudioRendererHealth health;
    private final StudioNotifier notifier;
    private final TransactionTemplate tx;
    private final StudioRenderer renderer;
    private final ScheduledExecutorService executor = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "studio-render");
        thread.setDaemon(true);
        return thread;
    });
    /** Пульс текущей задачи и поиск брошенных — отдельно, рендер занимает свой поток целиком */
    private final ScheduledExecutorService watchdog = Executors.newSingleThreadScheduledExecutor(r -> {
        Thread thread = new Thread(r, "studio-watchdog");
        thread.setDaemon(true);
        return thread;
    });

    /** Пульс реже этого — экземпляр считается упавшим, задача уходит другому */
    static final Duration STALE_AFTER = Duration.ofMinutes(2);
    /** Сколько раз пробуем собрать после падений сервера, потом — ошибка */
    static final int MAX_ATTEMPTS = 3;
    /** Остановка сервера: столько ждём, пока текущая сборка доделается, потом возвращаем её в очередь */
    static final long SHUTDOWN_WAIT_SECONDS = 50;

    private volatile boolean stopping;
    /** Текущую задачу при остановке отдали в очередь — этот экземпляр её больше не трогает */
    private volatile boolean abandoned;
    private volatile UUID current;
    private volatile StudioNotifier.Progress currentProgress;

    public StudioRenderWorker(StudioJobRepository jobs, UserRepository users, StudioService studio, StudioStorage storage,
                              StudioProperties properties, StudioRendererHealth health, StudioNotifier notifier,
                              TransactionTemplate tx) {
        this.jobs = jobs;
        this.users = users;
        this.studio = studio;
        this.storage = storage;
        this.properties = properties;
        this.health = health;
        this.notifier = notifier;
        this.tx = tx;
        this.renderer = new StudioRenderer(properties.getFfmpeg(), properties.getFfprobe(), properties.getRenderTimeoutSeconds());
    }

    @EventListener(ApplicationReadyEvent.class)
    public void start() {
        if (!properties.isWorkerEnabled()) {
            log.info("Видео-студия: воркер рендера выключен на этом экземпляре");
            return;
        }
        if (!health.available()) {
            log.warn("Видео-студия: FFmpeg не найден — воркер не запущен, задачи останутся в очереди");
            return;
        }
        watchdog.scheduleWithFixedDelay(this::beat, 10, 15, TimeUnit.SECONDS);
        watchdog.scheduleWithFixedDelay(this::recoverStale, 0, 1, TimeUnit.MINUTES);
        executor.scheduleWithFixedDelay(this::drain, 5, 3, TimeUnit.SECONDS);
        log.info("Видео-студия: воркер рендера запущен");
    }

    /**
     * Остановка (деплой, перезапуск): новые задачи не берём, текущую даём доделать,
     * не успела — возвращаем в очередь, её соберёт следующий экземпляр, в Telegram — «продолжится сама».
     */
    @PreDestroy
    public void stop() {
        stopping = true;
        executor.shutdown();
        try {
            if (!executor.awaitTermination(SHUTDOWN_WAIT_SECONDS, TimeUnit.SECONDS)) {
                UUID id = current;
                StudioNotifier.Progress progress = currentProgress;
                abandoned = true;
                if (id != null && Boolean.TRUE.equals(tx.execute(st -> jobs.requeue(id) > 0))) {
                    log.info("Видео-студия: сервер останавливается — задача {} возвращена в очередь", id);
                    if (progress != null) {
                        progress.paused();
                    }
                }
                executor.shutdownNow();
                executor.awaitTermination(10, TimeUnit.SECONDS);
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            executor.shutdownNow();
        } catch (RuntimeException e) {
            log.warn("Видео-студия: не удалось вернуть задачу в очередь при остановке: {}", e.getMessage());
            executor.shutdownNow();
        }
        watchdog.shutdownNow();
    }

    private void beat() {
        UUID id = current;
        if (id == null) {
            return;
        }
        try {
            tx.executeWithoutResult(st -> jobs.heartbeat(id, Instant.now()));
        } catch (RuntimeException e) {
            log.warn("Видео-студия: пульс задачи {} не записан: {}", id, e.getMessage());
        }
    }

    /**
     * Задачи в RUNNING без пульса — экземпляр упал посреди рендера, не успев вернуть их в очередь.
     * Возвращаем в очередь; после {@link #MAX_ATTEMPTS} падений подряд — ошибка, чтобы не крутить вечно.
     */
    void recoverStale() {
        try {
            Instant before = Instant.now().minus(STALE_AFTER);
            List<StudioJob> failed = new java.util.ArrayList<>();
            tx.executeWithoutResult(status -> jobs.findStale(before).forEach(job -> {
                if (job.getAttempts() < MAX_ATTEMPTS) {
                    log.info("Видео-студия: задача {} брошена упавшим экземпляром — снова в очереди", job.getId());
                    jobs.requeue(job.getId());
                    return;
                }
                job.setStatus(StudioJobStatus.FAILED);
                job.setFinishedAt(Instant.now());
                job.setError("Обработка прервалась. Повторите задачу или загрузите ролик ещё раз.");
                if (studio.settingsOf(job).isKeepSource()) {
                    job.setInputsUntil(Instant.now().plus(Duration.ofHours(properties.getInputsTtlHours())));
                }
                failed.add(jobs.save(job));
            }));
            failed.forEach(job -> notifier.finished(job, null, notifier.progressOf(job)));
        } catch (RuntimeException e) {
            log.warn("Видео-студия: не удалось проверить брошенные задачи: {}", e.getMessage());
        }
    }

    private void drain() {
        try {
            Optional<StudioJob> next;
            while (!stopping && (next = claim()).isPresent()) {
                process(next.get());
            }
        } catch (Throwable e) {
            log.error("Видео-студия: сбой воркера", e);
        }
    }

    Optional<StudioJob> claim() {
        return tx.execute(status -> jobs.lockNextQueued().map(job -> {
            job.setStatus(StudioJobStatus.RUNNING);
            job.setStartedAt(Instant.now());
            job.setHeartbeatAt(Instant.now());
            job.setAttempts(job.getAttempts() + 1);
            job.setProgress(0);
            return jobs.save(job);
        }));
    }

    void process(StudioJob job) {
        UUID id = job.getId();
        Path folder = Paths.get(properties.getWorkDir(), id.toString());
        StudioSettings settings = studio.settingsOf(job);
        User user = users.findById(job.getUserId()).orElse(null);
        boolean admin = user != null && StudioService.admin(user);
        int[] lastSaved = {0};
        long[] lastSavedAt = {0};
        StudioNotifier.Progress progress = notifier.started(job);
        if (progress != null && !Long.valueOf(progress.messageId()).equals(job.getProgressMessageId())) {
            tx.executeWithoutResult(s -> jobs.setProgressMessage(id, progress.messageId()));
        }
        current = id;
        currentProgress = progress;
        try {
            Files.createDirectories(folder);
            storage.download(job.getSourceKey(), folder.resolve("video"));
            if (job.getBannerKey() != null) {
                storage.download(job.getBannerKey(), folder.resolve("banner"));
            }
            if (job.getBackgroundKey() != null) {
                storage.download(job.getBackgroundKey(), folder.resolve("background"));
            }
            Path preset = null;
            if (job.getPreset() != null) {
                preset = folder.resolve("preset.jpg");
                try (InputStream in = new ClassPathResource("studio/presets/" + job.getPreset() + ".jpg").getInputStream()) {
                    Files.copy(in, preset, StandardCopyOption.REPLACE_EXISTING);
                }
            }
            Path result = renderer.render(folder, settings, studio.cuesOf(job), preset, admin,
                    percent -> {
                        if (progress != null) {
                            progress.update(percent);
                        }
                        long now = System.currentTimeMillis();
                        if (percent - lastSaved[0] >= 3 || now - lastSavedAt[0] > 3000) {
                            lastSaved[0] = percent;
                            lastSavedAt[0] = now;
                            tx.executeWithoutResult(s -> jobs.updateProgress(id, percent));
                        }
                    },
                    () -> abandoned || jobs.findStatus(id).map(s -> s != StudioJobStatus.RUNNING).orElse(true));
            if (abandoned) {
                return;
            }
            String key = StudioStorage.RESULTS + "/" + id + ".mp4";
            storage.upload(result, key, "video/mp4");
            long size = Files.size(result);
            StudioJob done = tx.execute(s -> {
                StudioJob fresh = jobs.findById(id).orElseThrow();
                if (abandoned || fresh.getStatus() != StudioJobStatus.RUNNING) {
                    return null;
                }
                fresh.setStatus(StudioJobStatus.DONE);
                fresh.setProgress(100);
                fresh.setResultKey(key);
                fresh.setResultSize(size);
                fresh.setFinishedAt(Instant.now());
                fresh.setInputsUntil(settings.isKeepSource()
                        ? Instant.now().plus(Duration.ofHours(properties.getInputsTtlHours())) : null);
                return jobs.save(fresh);
            });
            if (done == null) {
                storage.delete(key);
                return;
            }
            if (done.getInputsUntil() == null) {
                studio.releaseInputsIfUnused(done);
            }
            notifier.finished(done, result, progress);
        } catch (StudioRenderer.CancelledException e) {
            log.info("Видео-студия: задача {} отменена во время рендера", id);
            if (progress != null && !stopping) {
                progress.cancelled(job.getFilename());
            }
        } catch (StudioException e) {
            fail(id, settings, e.getMessage(), progress);
        } catch (Exception e) {
            log.error("Видео-студия: задача {} упала", id, e);
            fail(id, settings, "Не удалось собрать видео. Попробуйте ещё раз или выберите другой исходник.", progress);
        } finally {
            current = null;
            currentProgress = null;
            deleteFolder(folder);
        }
    }

    private void fail(UUID id, StudioSettings settings, String message, StudioNotifier.Progress progress) {
        StudioJob failed = tx.execute(s -> {
            StudioJob fresh = jobs.findById(id).orElse(null);
            if (abandoned || fresh == null || fresh.getStatus() != StudioJobStatus.RUNNING) {
                return null;
            }
            fresh.setStatus(StudioJobStatus.FAILED);
            fresh.setError(message);
            fresh.setFinishedAt(Instant.now());
            fresh.setInputsUntil(settings.isKeepSource()
                    ? Instant.now().plus(Duration.ofHours(properties.getInputsTtlHours())) : null);
            return jobs.save(fresh);
        });
        if (failed != null) {
            if (failed.getInputsUntil() == null) {
                studio.releaseInputsIfUnused(failed);
            }
            notifier.finished(failed, null, progress);
        }
    }

    private static void deleteFolder(Path folder) {
        if (!Files.exists(folder)) {
            return;
        }
        try (Stream<Path> walk = Files.walk(folder)) {
            List<Path> paths = walk.sorted(Comparator.reverseOrder()).toList();
            for (Path path : paths) {
                Files.deleteIfExists(path);
            }
        } catch (IOException e) {
            log.warn("Видео-студия: не удалось очистить {}: {}", folder, e.getMessage());
        }
    }
}
