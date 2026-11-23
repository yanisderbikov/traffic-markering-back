package ru.trafficmarkering.service.studio;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.trafficmarkering.config.StudioProperties;
import ru.trafficmarkering.dto.studio.StudioAssetDTO;
import ru.trafficmarkering.dto.studio.StudioAssetRequestDTO;
import ru.trafficmarkering.dto.studio.StudioJobDTO;
import ru.trafficmarkering.dto.studio.StudioJobRequestDTO;
import ru.trafficmarkering.dto.studio.StudioOverviewDTO;
import ru.trafficmarkering.dto.studio.StudioPresignRequestDTO;
import ru.trafficmarkering.dto.studio.StudioPresignResponseDTO;
import ru.trafficmarkering.dto.studio.StudioTelegramLinkDTO;
import ru.trafficmarkering.dto.studio.StudioTemplateDTO;
import ru.trafficmarkering.dto.studio.StudioTemplateRequestDTO;
import ru.trafficmarkering.model.User;
import ru.trafficmarkering.model.studio.StudioAsset;
import ru.trafficmarkering.model.studio.StudioAssetKind;
import ru.trafficmarkering.model.studio.StudioJob;
import ru.trafficmarkering.model.studio.StudioJobOrigin;
import ru.trafficmarkering.model.studio.StudioJobStatus;
import ru.trafficmarkering.model.studio.StudioProfile;
import ru.trafficmarkering.model.studio.StudioTemplate;
import ru.trafficmarkering.repository.StudioAssetRepository;
import ru.trafficmarkering.repository.StudioJobRepository;
import ru.trafficmarkering.repository.StudioProfileRepository;
import ru.trafficmarkering.repository.StudioTemplateRepository;
import ru.trafficmarkering.service.storage.FileStorage;
import ru.trafficmarkering.service.studio.telegram.StudioBotInfo;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;

import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDate;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.EnumSet;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Видео-студия: очередь монтажа, библиотека, шаблоны и привязка Telegram.
 * Одни и те же методы обслуживают сайт и бота — у них общий кабинет пользователя.
 */
@Slf4j
@Service
public class StudioService {

    public static final int MAX_TEMPLATES = 30;
    public static final int MAX_OWN_ASSETS = 60;
    private static final Duration LINK_TTL = Duration.ofMinutes(30);
    /** Код живёт полчаса; пока осталось больше этого запаса — отдаём тот же, чтобы вкладки не перебивали друг друга */
    private static final Duration LINK_REUSE_MARGIN = Duration.ofMinutes(10);
    private static final Set<StudioJobStatus> ACTIVE = EnumSet.of(StudioJobStatus.QUEUED, StudioJobStatus.RUNNING);
    private static final Set<String> VIDEO_TYPES = Set.of("video/mp4", "video/quicktime", "video/webm", "video/x-matroska");
    private static final Set<String> IMAGE_TYPES = Set.of("image/png", "image/jpeg", "image/webp");
    private static final SecureRandom RANDOM = new SecureRandom();

    private final StudioJobRepository jobs;
    private final StudioAssetRepository assets;
    private final StudioTemplateRepository templates;
    private final StudioProfileRepository profiles;
    private final StudioStorage storage;
    private final FileStorage fileStorage;
    private final StudioProperties properties;
    private final StudioBotInfo botInfo;
    private final StudioRendererHealth rendererHealth;
    private final ObjectMapper json;
    private final String backUrl;

    public StudioService(StudioJobRepository jobs, StudioAssetRepository assets, StudioTemplateRepository templates,
                         StudioProfileRepository profiles, StudioStorage storage, FileStorage fileStorage,
                         StudioProperties properties, StudioBotInfo botInfo, StudioRendererHealth rendererHealth,
                         ObjectMapper json, @Value("${url.back}") String backUrl) {
        this.jobs = jobs;
        this.assets = assets;
        this.templates = templates;
        this.profiles = profiles;
        this.storage = storage;
        this.fileStorage = fileStorage;
        this.properties = properties;
        this.botInfo = botInfo;
        this.rendererHealth = rendererHealth;
        this.json = json;
        this.backUrl = backUrl == null ? "" : backUrl.replaceAll("/+$", "");
    }

    // ───────────────────────── обзор и лимиты ─────────────────────────

    public static boolean admin(User user) {
        return user.getRole() != null && user.getRole().isAdmin();
    }

    static Instant dayStart(Instant now) {
        return LocalDate.ofInstant(now, ZoneOffset.UTC).atStartOfDay(ZoneOffset.UTC).toInstant();
    }

    public int remaining(User user) {
        if (admin(user)) {
            return Integer.MAX_VALUE;
        }
        long used = jobs.countUsedSince(user.getId(), dayStart(Instant.now()));
        return (int) Math.max(0, properties.getDailyLimit() - used);
    }

    @Transactional
    public StudioOverviewDTO overview(User user) {
        StudioProfile profile = profile(user.getId());
        boolean admin = admin(user);
        long active = jobs.countByUserIdAndStatusIn(user.getId(), ACTIVE);
        String username = botInfo.username();
        List<StudioOverviewDTO.Preset> presets = StudioSettings.PRESET_NAMES.entrySet().stream()
                .map(e -> new StudioOverviewDTO.Preset(e.getKey(), e.getValue(), presetUrl(e.getKey())))
                .toList();
        return new StudioOverviewDTO(
                admin, admin, properties.getDailyLimit(),
                admin ? properties.getDailyLimit() : remaining(user),
                properties.getMaxActive(), (int) active,
                dayStart(Instant.now()).plus(Duration.ofDays(1)),
                properties.getSourceMaxBytes(), properties.getBannerMaxBytes(), properties.getBackgroundMaxBytes(),
                rendererHealth.available(),
                profile.getActiveTemplateId(), profile.getFavoriteTemplateId(),
                new StudioOverviewDTO.Telegram(botInfo.enabled(), username,
                        username == null ? null : "https://t.me/" + username,
                        profile.getTelegramId() != null, profile.getTelegramUsername(), profile.getTelegramLinkedAt()),
                presets);
    }

    public String presetUrl(String id) {
        return backUrl + "/api/public/studio/presets/" + id + ".jpg";
    }

    @Transactional
    public StudioProfile profile(Long userId) {
        return profiles.findById(userId).orElseGet(() -> profiles.save(StudioProfile.builder().userId(userId).build()));
    }

    // ───────────────────────── загрузки ─────────────────────────

    public StudioPresignResponseDTO presign(User user, StudioPresignRequestDTO request) {
        String purpose = request.purpose().toUpperCase(Locale.ROOT);
        String type = request.contentType().toLowerCase(Locale.ROOT);
        boolean video = VIDEO_TYPES.contains(type);
        boolean image = IMAGE_TYPES.contains(type);
        long limit = switch (purpose) {
            case "SOURCE" -> {
                if (!video) {
                    throw new StudioException("Исходник — видео MP4, MOV, WebM или MKV.");
                }
                yield properties.getSourceMaxBytes();
            }
            case "BANNER" -> properties.getBannerMaxBytes();
            case "BACKGROUND" -> properties.getBackgroundMaxBytes();
            default -> throw new StudioException("Неизвестное назначение файла.");
        };
        if (!video && !image) {
            throw new StudioException("Загрузите видео MP4, MOV, WebM или MKV либо картинку PNG, JPG или WebP.");
        }
        if (!admin(user) && request.size() > limit) {
            throw new StudioException("Файл больше " + (limit / 1024 / 1024) + " МиБ.");
        }
        FileStorage.PresignedUpload upload = fileStorage.presignUpload(request.filename(), type,
                StudioStorage.UPLOADS + "/" + user.getId());
        return new StudioPresignResponseDTO(upload.uploadUrl(), upload.key());
    }

    // ───────────────────────── задачи ─────────────────────────

    /** Входные файлы задачи: исходник и (опционально) баннер и фон */
    public record Inputs(String sourceKey, String bannerKey, String backgroundKey) {}

    /** Откуда пришла задача и куда сообщить о готовности */
    public record Delivery(StudioJobOrigin origin, Long chatId, Long replyTo) {
        public static Delivery web() {
            return new Delivery(StudioJobOrigin.WEB, null, null);
        }
    }

    @Transactional
    public List<StudioJobDTO> createFromWeb(User user, StudioJobRequestDTO request) {
        boolean admin = admin(user);
        if (!StudioStorage.ownedUpload(request.sourceKey(), user.getId())) {
            throw new StudioException("Загрузите исходное видео ещё раз.");
        }
        HeadObjectResponse head = storage.head(request.sourceKey())
                .orElseThrow(() -> new StudioException("Исходное видео не загрузилось. Попробуйте ещё раз."));
        if (!admin && head.contentLength() > properties.getSourceMaxBytes()) {
            storage.delete(request.sourceKey());
            throw new StudioException("Исходник — до 80 МиБ.");
        }
        List<StudioCaptions.Cue> cues = StudioCaptions.fromText(request.captions());
        StudioSettings settings = request.settings().validate(admin, !cues.isEmpty(), false);
        String bannerKey = !"none".equals(settings.getMode())
                ? resolveInput(user, request.bannerKey(), request.bannerAssetId(), StudioAssetKind.BANNER,
                properties.getBannerMaxBytes(), "Добавьте файл баннера.") : null;
        String backgroundKey = "file".equals(settings.getBackground())
                ? resolveInput(user, request.backgroundKey(), request.backgroundAssetId(), StudioAssetKind.BACKGROUND,
                properties.getBackgroundMaxBytes(), settings.split() ? "Добавьте второе видео." : "Добавьте файл фона.")
                : null;
        List<StudioJob> created = enqueue(user, request.filename(), settings, cues,
                new Inputs(request.sourceKey(), bannerKey, backgroundKey), Delivery.web(), null);
        return created.stream().map(this::toDTO).toList();
    }

    private String resolveInput(User user, String key, UUID assetId, StudioAssetKind kind, long limit, String missing) {
        if (assetId != null) {
            StudioAsset asset = visibleAsset(user, assetId);
            if (asset.getKind() != kind && !(kind == StudioAssetKind.BACKGROUND && asset.isVideo())) {
                throw new StudioException("Выбран файл другого типа.");
            }
            return asset.getStorageKey();
        }
        if (key == null || key.isBlank()) {
            throw new StudioException(missing);
        }
        if (!StudioStorage.ownedUpload(key, user.getId())) {
            throw new StudioException("Загрузите файл ещё раз.");
        }
        HeadObjectResponse head = storage.head(key).orElseThrow(() -> new StudioException("Файл не загрузился. Попробуйте ещё раз."));
        if (!admin(user) && head.contentLength() > limit) {
            throw new StudioException("Файл больше " + (limit / 1024 / 1024) + " МиБ.");
        }
        return key;
    }

    /**
     * Ставит задачу (или пару 9:16 + 1:1) в очередь: проверяет дневной лимит и число активных задач.
     */
    @Transactional
    public List<StudioJob> enqueue(User user, String filename, StudioSettings settings, List<StudioCaptions.Cue> cues,
                                   Inputs inputs, Delivery delivery, UUID retryOf) {
        boolean admin = admin(user);
        List<String> formats = "vertical_square".equals(settings.getExportProfile())
                ? List.of("vertical", "square") : List.of(settings.getFormat());
        if (!admin) {
            long active = jobs.countByUserIdAndStatusIn(user.getId(), ACTIVE);
            if (active + formats.size() > properties.getMaxActive()) {
                throw new StudioException(HttpStatus.TOO_MANY_REQUESTS,
                        formats.size() > 1 ? "Для пакета нужно два свободных места в очереди. Дождитесь готовности текущих роликов."
                                : "Одновременно можно обрабатывать до " + properties.getMaxActive()
                                + " роликов. Дождитесь готовности или отмените задачу.");
            }
            if (remaining(user) < formats.size()) {
                throw new StudioException(HttpStatus.TOO_MANY_REQUESTS,
                        "Дневной лимит исчерпан. Он обновится в 00:00 UTC.");
            }
        }
        StudioProfile profile = profile(user.getId());
        String preset = null;
        if ("preset".equals(settings.getBackground())) {
            preset = "random".equals(settings.getPreset()) ? nextPreset(profile) : settings.getPreset();
        }
        Instant now = Instant.now();
        UUID batchId = formats.size() > 1 ? UUID.randomUUID() : null;
        String captions = cues.isEmpty() ? null : write(cues);
        List<StudioJob> created = new ArrayList<>();
        for (String format : formats) {
            StudioSettings own = settings.copy();
            own.setFormat(format);
            created.add(jobs.save(StudioJob.builder()
                    .id(UUID.randomUUID())
                    .userId(user.getId())
                    .origin(delivery.origin())
                    .status(StudioJobStatus.QUEUED)
                    .filename(trimName(filename))
                    .settings(write(own))
                    .captions(captions)
                    .preset(preset)
                    .sourceKey(inputs.sourceKey())
                    .bannerKey(inputs.bannerKey())
                    .backgroundKey(inputs.backgroundKey())
                    .batchId(batchId)
                    .retryOf(retryOf)
                    .telegramChatId(delivery.chatId())
                    .telegramReplyTo(delivery.replyTo())
                    .createdAt(now)
                    .build()));
        }
        return created;
    }

    static String trimName(String filename) {
        String name = filename == null ? "" : filename.replace('\\', '/');
        name = name.substring(name.lastIndexOf('/') + 1).strip();
        if (name.isEmpty()) {
            name = "video.mp4";
        }
        return name.length() > 160 ? name.substring(0, 160) : name;
    }

    /** Случайный фон: колода без повторов, после 16 вариантов перемешивается заново */
    String nextPreset(StudioProfile profile) {
        List<String> queue = new ArrayList<>(Arrays.stream(profile.getPresetQueue().split(","))
                .filter(StudioSettings.PRESETS::contains).toList());
        if (queue.isEmpty()) {
            queue = new ArrayList<>(StudioSettings.PRESETS);
            Collections.shuffle(queue, RANDOM);
        }
        String next = queue.remove(0);
        profile.setPresetQueue(String.join(",", queue));
        profiles.save(profile);
        return next;
    }

    public List<StudioJobDTO> list(User user, String filter, int limit) {
        PageRequest page = PageRequest.of(0, Math.max(1, Math.min(limit, 100)));
        List<StudioJob> rows = switch (filter == null ? "all" : filter) {
            case "active" -> jobs.findByUserIdAndStatusInOrderByCreatedAtDesc(user.getId(), ACTIVE, page);
            case "done" -> jobs.findByUserIdAndStatusInOrderByCreatedAtDesc(user.getId(), EnumSet.of(StudioJobStatus.DONE), page);
            case "failed" -> jobs.findByUserIdAndStatusInOrderByCreatedAtDesc(user.getId(),
                    EnumSet.of(StudioJobStatus.FAILED, StudioJobStatus.CANCELLED, StudioJobStatus.EXPIRED), page);
            default -> jobs.findByUserIdOrderByCreatedAtDesc(user.getId(), page);
        };
        return rows.stream().map(this::toDTO).toList();
    }

    public StudioJob own(User user, UUID id) {
        return jobs.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new StudioException(HttpStatus.NOT_FOUND, "Задача не найдена."));
    }

    public StudioJobDTO get(User user, UUID id) {
        return toDTO(own(user, id));
    }

    @Transactional
    public StudioJobDTO cancel(User user, UUID id) {
        StudioJob job = own(user, id);
        if (!job.getStatus().active()) {
            throw new StudioException(HttpStatus.CONFLICT, "Задача уже завершена.");
        }
        // Работающую задачу воркер увидит по статусу и остановит FFmpeg
        job.setStatus(StudioJobStatus.CANCELLED);
        job.setFinishedAt(Instant.now());
        if (!settingsOf(job).isKeepSource()) {
            job.setInputsUntil(null);
        } else {
            job.setInputsUntil(Instant.now().plus(Duration.ofHours(properties.getInputsTtlHours())));
        }
        jobs.save(job);
        if (job.getInputsUntil() == null && job.getStatus() == StudioJobStatus.CANCELLED) {
            releaseInputsIfUnused(job);
        }
        return toDTO(job);
    }

    @Transactional
    public StudioJobDTO retry(User user, UUID id, String quality) {
        StudioJob job = own(user, id);
        if (job.getStatus().active()) {
            throw new StudioException(HttpStatus.CONFLICT, "Задача ещё в работе.");
        }
        if (job.getInputsUntil() == null || job.getInputsUntil().isBefore(Instant.now()) || job.getSourceKey() == null) {
            throw new StudioException(HttpStatus.GONE, "Исходники уже удалены. Загрузите видео заново.");
        }
        StudioSettings settings = settingsOf(job);
        if (quality != null) {
            if (!Set.of("720", "1080").contains(quality)) {
                throw new StudioException("Качество — 720 или 1080.");
            }
            settings.setQuality(quality);
        }
        settings.setExportProfile("single");
        List<StudioJob> created = enqueue(user, job.getFilename(), settings, cuesOf(job),
                new Inputs(job.getSourceKey(), job.getBannerKey(), job.getBackgroundKey()),
                new Delivery(job.getOrigin(), job.getTelegramChatId(), job.getTelegramReplyTo()), job.getId());
        // Исходники теперь принадлежат новой задаче
        job.setInputsUntil(null);
        jobs.save(job);
        return toDTO(created.get(0));
    }

    /** Удаляет временные входные файлы, если их не использует другая активная задача */
    public void releaseInputsIfUnused(StudioJob job) {
        String source = job.getSourceKey();
        if (source != null && jobs.countOtherActiveWithSource(source, job.getId()) > 0) {
            return;
        }
        for (String key : new String[]{job.getSourceKey(), job.getBannerKey(), job.getBackgroundKey()}) {
            if (StudioStorage.disposable(key)) {
                storage.delete(key);
            }
        }
    }

    public StudioSettings settingsOf(StudioJob job) {
        return read(job.getSettings(), StudioSettings.class);
    }

    public List<StudioCaptions.Cue> cuesOf(StudioJob job) {
        if (job.getCaptions() == null) {
            return List.of();
        }
        return read(job.getCaptions(), new TypeReference<List<StudioCaptions.Cue>>() {});
    }

    public StudioJobDTO toDTO(StudioJob job) {
        StudioSettings settings = settingsOf(job);
        boolean done = job.getStatus() == StudioJobStatus.DONE && job.getResultKey() != null;
        String name = downloadName(job, settings);
        Long position = job.getStatus() == StudioJobStatus.QUEUED ? jobs.countQueuedBefore(job.getCreatedAt()) : null;
        Instant retryUntil = !job.getStatus().active() && job.getInputsUntil() != null
                && job.getInputsUntil().isAfter(Instant.now()) ? job.getInputsUntil() : null;
        boolean linked = profiles.findById(job.getUserId()).map(p -> p.getTelegramId() != null).orElse(false);
        return new StudioJobDTO(job.getId(), job.getStatus().name(), job.getOrigin().name(), job.getFilename(),
                job.getStatus() == StudioJobStatus.DONE ? 100 : job.getProgress(), position, job.getError(), settings,
                job.getPreset(), job.getPreset() == null ? null : StudioSettings.PRESET_NAMES.get(job.getPreset()),
                job.getBatchId(), job.getResultSize(),
                done ? fileStorage.presignedUrl(job.getResultKey(), name, false) : null,
                done ? fileStorage.presignedUrl(job.getResultKey(), name, true) : null,
                retryUntil,
                done && job.getFinishedAt() != null ? job.getFinishedAt().plus(Duration.ofHours(properties.getResultTtlHours())) : null,
                done && linked && botInfo.enabled(),
                job.getCreatedAt(), job.getStartedAt(), job.getFinishedAt());
    }

    public static String downloadName(StudioJob job, StudioSettings settings) {
        String base = job.getFilename().replaceAll("\\.[A-Za-z0-9]{1,5}$", "");
        base = base.replaceAll("[\\\\/:*?\"<>|]+", "_").strip();
        if (base.isEmpty()) {
            base = "offer";
        }
        String suffix = "source".equals(settings.getFormat()) ? "" : "-" + settings.getFormat();
        return base + "-offer" + suffix + ".mp4";
    }

    // ───────────────────────── библиотека ─────────────────────────

    public List<StudioAssetDTO> assets(User user) {
        return assets.findVisible(user.getId()).stream().map(a -> assetDTO(a, user)).toList();
    }

    private StudioAssetDTO assetDTO(StudioAsset asset, User user) {
        return new StudioAssetDTO(asset.getId(), asset.getKind().name(), asset.getName(), asset.isShared(),
                Objects.equals(asset.getUserId(), user.getId()), asset.isVideo(), asset.getSizeBytes(),
                fileStorage.presignedUrl(asset.getStorageKey(), null, true), asset.getCreatedAt());
    }

    private StudioAsset visibleAsset(User user, UUID id) {
        StudioAsset asset = assets.findById(id).orElseThrow(() -> new StudioException(HttpStatus.NOT_FOUND, "Файл не найден в библиотеке."));
        if (!asset.isShared() && !Objects.equals(asset.getUserId(), user.getId())) {
            throw new StudioException(HttpStatus.NOT_FOUND, "Файл не найден в библиотеке.");
        }
        return asset;
    }

    @Transactional
    public StudioAssetDTO saveAsset(User user, StudioAssetRequestDTO request) {
        StudioAssetKind kind;
        try {
            kind = StudioAssetKind.valueOf(request.kind().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException e) {
            throw new StudioException("Тип файла — BANNER или BACKGROUND.");
        }
        if (request.shared() && !admin(user)) {
            throw new StudioException(HttpStatus.FORBIDDEN, "Общий каталог пополняет администратор.");
        }
        if (!request.shared() && assets.countByUserIdAndSharedFalse(user.getId()) >= MAX_OWN_ASSETS) {
            throw new StudioException("В библиотеке до " + MAX_OWN_ASSETS + " файлов. Удалите ненужные.");
        }
        if (!StudioStorage.ownedUpload(request.key(), user.getId())) {
            throw new StudioException("Загрузите файл ещё раз.");
        }
        HeadObjectResponse head = storage.head(request.key()).orElseThrow(() -> new StudioException("Файл не загрузился."));
        String type = head.contentType() == null ? "" : head.contentType().toLowerCase(Locale.ROOT);
        long limit = kind == StudioAssetKind.BANNER ? properties.getBannerMaxBytes() : properties.getBackgroundMaxBytes();
        if (!admin(user) && head.contentLength() > limit) {
            throw new StudioException("Файл больше " + (limit / 1024 / 1024) + " МиБ.");
        }
        UUID id = UUID.randomUUID();
        // Копию в библиотеке не удаляет очистка временных загрузок
        String key = StudioStorage.ASSETS + "/" + user.getId() + "/" + id + extension(request.key());
        copy(request.key(), key, type);
        StudioAsset asset = assets.save(StudioAsset.builder()
                .id(id).userId(user.getId()).kind(kind).shared(request.shared())
                .name(request.name().strip()).storageKey(key).contentType(type.isEmpty() ? "application/octet-stream" : type)
                .sizeBytes(head.contentLength()).video(type.startsWith("video/"))
                .createdAt(Instant.now())
                .build());
        return assetDTO(asset, user);
    }

    private void copy(String from, String to, String type) {
        java.nio.file.Path temp = null;
        try {
            temp = java.nio.file.Files.createTempFile("studio-asset", ".bin");
            storage.download(from, temp);
            storage.upload(temp, to, type.isEmpty() ? "application/octet-stream" : type);
        } catch (java.io.IOException e) {
            throw new IllegalStateException("asset copy failed", e);
        } finally {
            if (temp != null) {
                try {
                    java.nio.file.Files.deleteIfExists(temp);
                } catch (java.io.IOException ignored) {
                    // временный файл
                }
            }
        }
    }

    private static String extension(String key) {
        int dot = key.lastIndexOf('.');
        return dot > key.lastIndexOf('/') ? key.substring(dot) : "";
    }

    @Transactional
    public void deleteAsset(User user, UUID id) {
        StudioAsset asset = assets.findById(id).orElseThrow(() -> new StudioException(HttpStatus.NOT_FOUND, "Файл не найден."));
        boolean mine = Objects.equals(asset.getUserId(), user.getId());
        if (!mine && !(asset.isShared() && admin(user))) {
            throw new StudioException(HttpStatus.NOT_FOUND, "Файл не найден.");
        }
        assets.delete(asset);
        storage.delete(asset.getStorageKey());
    }

    // ───────────────────────── шаблоны ─────────────────────────

    public List<StudioTemplateDTO> templates(User user) {
        StudioProfile profile = profiles.findById(user.getId()).orElse(null);
        Map<UUID, String> names = assets.findVisible(user.getId()).stream()
                .collect(Collectors.toMap(StudioAsset::getId, StudioAsset::getName, (a, b) -> a));
        return templates.findByUserIdOrderByUpdatedAtDesc(user.getId()).stream()
                .map(t -> templateDTO(t, profile, names::get))
                .toList();
    }

    private StudioTemplateDTO templateDTO(StudioTemplate t, StudioProfile profile, Function<UUID, String> names) {
        return new StudioTemplateDTO(t.getId(), t.getName(), read(t.getSettings(), StudioSettings.class), t.getCaptions(),
                t.getBannerAssetId(), t.getBannerAssetId() == null ? null : names.apply(t.getBannerAssetId()),
                t.getBackgroundAssetId(), t.getBackgroundAssetId() == null ? null : names.apply(t.getBackgroundAssetId()),
                profile != null && t.getId().equals(profile.getActiveTemplateId()),
                profile != null && t.getId().equals(profile.getFavoriteTemplateId()),
                t.getUpdatedAt());
    }

    @Transactional
    public StudioTemplateDTO saveTemplate(User user, UUID id, StudioTemplateRequestDTO request) {
        boolean admin = admin(user);
        List<StudioCaptions.Cue> cues = StudioCaptions.fromText(request.captions());
        StudioSettings settings = request.settings().validate(admin, !cues.isEmpty(), true);
        // Шаблон применяется к видео из чата: фон-файл и баннер берутся только из библиотеки
        if (!"none".equals(settings.getMode()) && request.bannerAssetId() == null) {
            throw new StudioException("Для шаблона с баннером сохраните баннер в библиотеку.");
        }
        if ("file".equals(settings.getBackground()) && request.backgroundAssetId() == null) {
            throw new StudioException("Для шаблона с фоном-файлом сохраните фон в библиотеку.");
        }
        if (request.bannerAssetId() != null) {
            visibleAsset(user, request.bannerAssetId());
        }
        if (request.backgroundAssetId() != null) {
            visibleAsset(user, request.backgroundAssetId());
        }
        Instant now = Instant.now();
        StudioTemplate template;
        if (id == null) {
            if (templates.countByUserId(user.getId()) >= MAX_TEMPLATES) {
                throw new StudioException("Можно сохранить до " + MAX_TEMPLATES + " шаблонов.");
            }
            template = StudioTemplate.builder().id(UUID.randomUUID()).userId(user.getId()).createdAt(now).build();
        } else {
            template = templates.findByIdAndUserId(id, user.getId())
                    .orElseThrow(() -> new StudioException(HttpStatus.NOT_FOUND, "Шаблон не найден."));
        }
        template.setName(request.name().strip());
        template.setSettings(write(settings));
        template.setCaptions(request.captions() == null || request.captions().isBlank() ? null : request.captions());
        template.setBannerAssetId("none".equals(settings.getMode()) ? null : request.bannerAssetId());
        template.setBackgroundAssetId("file".equals(settings.getBackground()) ? request.backgroundAssetId() : null);
        template.setUpdatedAt(now);
        templates.save(template);
        Map<UUID, String> names = assets.findVisible(user.getId()).stream()
                .collect(Collectors.toMap(StudioAsset::getId, StudioAsset::getName, (a, b) -> a));
        return templateDTO(template, profiles.findById(user.getId()).orElse(null), names::get);
    }

    @Transactional
    public void deleteTemplate(User user, UUID id) {
        StudioTemplate template = templates.findByIdAndUserId(id, user.getId())
                .orElseThrow(() -> new StudioException(HttpStatus.NOT_FOUND, "Шаблон не найден."));
        templates.delete(template);
    }

    /** Автошаблон для видео из Telegram (null — выключить) или избранный шаблон */
    @Transactional
    public void setActiveTemplate(User user, UUID templateId, boolean favorite) {
        if (templateId != null) {
            templates.findByIdAndUserId(templateId, user.getId())
                    .orElseThrow(() -> new StudioException(HttpStatus.NOT_FOUND, "Шаблон не найден."));
        }
        StudioProfile profile = profile(user.getId());
        if (favorite) {
            profile.setFavoriteTemplateId(templateId);
        } else {
            profile.setActiveTemplateId(templateId);
        }
        profiles.save(profile);
    }

    public StudioTemplate template(Long userId, UUID id) {
        return templates.findByIdAndUserId(id, userId)
                .orElseThrow(() -> new StudioException(HttpStatus.NOT_FOUND, "Шаблон не найден."));
    }

    public List<StudioTemplate> ownTemplates(Long userId) {
        return templates.findByUserIdOrderByUpdatedAtDesc(userId);
    }

    /** Ставит задачу по шаблону для уже загруженного исходника (видео из чата) */
    @Transactional
    public List<StudioJob> enqueueFromTemplate(User user, StudioTemplate template, String filename, String sourceKey,
                                               Delivery delivery) {
        StudioSettings settings = read(template.getSettings(), StudioSettings.class);
        String bannerKey = null;
        String backgroundKey = null;
        if (!"none".equals(settings.getMode())) {
            if (template.getBannerAssetId() == null) {
                throw new StudioException("Баннер шаблона удалён из библиотеки. Обновите шаблон на сайте.");
            }
            bannerKey = visibleAsset(user, template.getBannerAssetId()).getStorageKey();
        }
        if ("file".equals(settings.getBackground())) {
            if (template.getBackgroundAssetId() == null) {
                throw new StudioException("Фон шаблона удалён из библиотеки. Обновите шаблон на сайте.");
            }
            backgroundKey = visibleAsset(user, template.getBackgroundAssetId()).getStorageKey();
        }
        List<StudioCaptions.Cue> cues = StudioCaptions.fromText(template.getCaptions());
        settings.validate(admin(user), !cues.isEmpty(), true);
        return enqueue(user, filename, settings, cues, new Inputs(sourceKey, bannerKey, backgroundKey), delivery, null);
    }

    // ───────────────────────── Telegram ─────────────────────────

    @Transactional
    public StudioTelegramLinkDTO createTelegramLink(User user) {
        String username = botInfo.username();
        if (username == null) {
            throw new StudioException(HttpStatus.SERVICE_UNAVAILABLE, "Telegram-бот ещё не подключён.");
        }
        StudioProfile profile = profile(user.getId());
        if (profile.getLinkCode() != null && profile.getLinkCodeExpiresAt() != null
                && profile.getLinkCodeExpiresAt().isAfter(Instant.now().plus(LINK_REUSE_MARGIN))) {
            return new StudioTelegramLinkDTO("https://t.me/" + username + "?start=" + profile.getLinkCode(),
                    profile.getLinkCodeExpiresAt());
        }
        byte[] raw = new byte[16];
        RANDOM.nextBytes(raw);
        String code = "link_" + HexFormat.of().formatHex(raw);
        Instant expires = Instant.now().plus(LINK_TTL);
        profile.setLinkCode(code);
        profile.setLinkCodeExpiresAt(expires);
        profiles.save(profile);
        return new StudioTelegramLinkDTO("https://t.me/" + username + "?start=" + code, expires);
    }

    @Transactional
    public void unlinkTelegram(User user) {
        StudioProfile profile = profile(user.getId());
        profile.setTelegramId(null);
        profile.setTelegramUsername(null);
        profile.setTelegramLinkedAt(null);
        profiles.save(profile);
    }

    /**
     * Подтверждение привязки из бота по одноразовому коду.
     *
     * @return id пользователя сайта
     */
    @Transactional
    public Long confirmTelegramLink(String code, long telegramId, String telegramUsername) {
        StudioProfile profile = profiles.findByLinkCode(code)
                .filter(p -> p.getLinkCodeExpiresAt() != null && p.getLinkCodeExpiresAt().isAfter(Instant.now()))
                .orElseThrow(() -> new StudioException("Ссылка устарела. Откройте «Видео-студию» на сайте и нажмите «Привязать Telegram» ещё раз."));
        profiles.findByTelegramId(telegramId)
                .filter(other -> !other.getUserId().equals(profile.getUserId()))
                .ifPresent(other -> {
                    other.setTelegramId(null);
                    other.setTelegramUsername(null);
                    other.setTelegramLinkedAt(null);
                    profiles.saveAndFlush(other);
                });
        profile.setTelegramId(telegramId);
        profile.setTelegramUsername(telegramUsername);
        profile.setTelegramLinkedAt(Instant.now());
        profile.setLinkCode(null);
        profile.setLinkCodeExpiresAt(null);
        profiles.save(profile);
        return profile.getUserId();
    }

    // ───────────────────────── JSON ─────────────────────────

    String write(Object value) {
        try {
            return json.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    <T> T read(String value, Class<T> type) {
        try {
            return json.readValue(value, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }

    <T> T read(String value, TypeReference<T> type) {
        try {
            return json.readValue(value, type);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException(e);
        }
    }
}
