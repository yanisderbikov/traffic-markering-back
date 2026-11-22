package ru.trafficmarkering.service.studio.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import ru.trafficmarkering.config.StudioProperties;
import ru.trafficmarkering.dto.studio.StudioJobDTO;
import ru.trafficmarkering.model.User;
import ru.trafficmarkering.model.studio.StudioChatDraft;
import ru.trafficmarkering.model.studio.StudioJob;
import ru.trafficmarkering.model.studio.StudioJobOrigin;
import ru.trafficmarkering.model.studio.StudioJobStatus;
import ru.trafficmarkering.model.studio.StudioProfile;
import ru.trafficmarkering.model.studio.StudioTemplate;
import ru.trafficmarkering.repository.StudioChatDraftRepository;
import ru.trafficmarkering.repository.StudioJobRepository;
import ru.trafficmarkering.repository.StudioProfileRepository;
import ru.trafficmarkering.repository.UserRepository;
import ru.trafficmarkering.service.studio.StudioException;
import ru.trafficmarkering.service.studio.StudioService;
import ru.trafficmarkering.service.studio.StudioSettings;
import ru.trafficmarkering.service.studio.StudioStorage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static ru.trafficmarkering.service.studio.telegram.TelegramClient.button;
import static ru.trafficmarkering.service.studio.telegram.TelegramClient.escape;
import static ru.trafficmarkering.service.studio.telegram.TelegramClient.keyboard;
import static ru.trafficmarkering.service.studio.telegram.TelegramClient.urlButton;

/**
 * Telegram-бот видео-студии. Обновления приходят вебхуком; контроллер сразу отвечает 200,
 * а разбор идёт здесь в отдельном пуле — скачивание видео не держит запрос Telegram.
 */
@Slf4j
@Component
public class StudioBot {

    /** Быстрые режимы для видео из чата — как в исходном боте */
    static final Map<String, QuickMode> QUICK_MODES = quickModes();
    private static final Duration DRAFT_TTL = Duration.ofMinutes(30);
    private static final Map<String, String> STATUS = Map.of(
            "QUEUED", "В очереди", "RUNNING", "Обработка", "DONE", "Готово",
            "FAILED", "Ошибка", "CANCELLED", "Отменено", "EXPIRED", "Файл удалён");

    record QuickMode(String label, String quality, String format, String background, String shape, int sizeLimit) {
        StudioSettings settings() {
            StudioSettings s = new StudioSettings();
            s.setQuality(quality);
            if (format != null) {
                s.setFormat(format);
            }
            if (background != null) {
                s.setBackground(background);
            }
            if (shape != null) {
                s.setShape(shape);
            }
            s.setSizeLimit(sizeLimit);
            return s;
        }
    }

    private static Map<String, QuickMode> quickModes() {
        Map<String, QuickMode> modes = new LinkedHashMap<>();
        modes.put("small", new QuickMode("720p · исходный кадр", "720", null, null, null, 0));
        modes.put("compact", new QuickMode("Сжать до 25 МБ", "720", null, null, null, 25));
        modes.put("vertical", new QuickMode("9:16 · размытый фон", "720", "vertical", "blur", null, 0));
        modes.put("circle", new QuickMode("9:16 · видео в круге", "720", "vertical", "preset", "circle", 0));
        return Collections.unmodifiableMap(modes);
    }

    private final TelegramClient client;
    private final StudioService studio;
    private final StudioNotifier notifier;
    private final StudioStorage storage;
    private final StudioProfileRepository profiles;
    private final StudioChatDraftRepository drafts;
    private final StudioJobRepository jobs;
    private final UserRepository users;
    private final StudioProperties properties;
    private final String studioUrl;
    private final Map<Long, Boolean> seenUpdates = Collections.synchronizedMap(new LinkedHashMap<>(256, 0.75f, false) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, Boolean> eldest) {
            return size() > 2000;
        }
    });
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(2, 4, 60, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(500), r -> {
        Thread thread = new Thread(r, "studio-bot");
        thread.setDaemon(true);
        return thread;
    });

    public StudioBot(TelegramClient client, StudioService studio, StudioNotifier notifier, StudioStorage storage,
                     StudioProfileRepository profiles, StudioChatDraftRepository drafts, StudioJobRepository jobs,
                     UserRepository users, StudioProperties properties, @Value("${app.front.url}") String frontUrl) {
        this.client = client;
        this.studio = studio;
        this.notifier = notifier;
        this.storage = storage;
        this.profiles = profiles;
        this.drafts = drafts;
        this.jobs = jobs;
        this.users = users;
        this.properties = properties;
        this.studioUrl = (frontUrl == null ? "" : frontUrl.replaceAll("/+$", "")) + "/app/studio";
    }

    @PreDestroy
    public void stop() {
        executor.shutdown();
    }

    /** Ставит обновление в обработку; повторы одного update_id отбрасываются */
    public void accept(JsonNode update) {
        long updateId = update.path("update_id").asLong(-1);
        if (updateId >= 0 && seenUpdates.put(updateId, Boolean.TRUE) != null) {
            return;
        }
        try {
            executor.execute(() -> handle(update));
        } catch (RejectedExecutionException e) {
            log.warn("Видео-студия: очередь бота переполнена, обновление {} пропущено", updateId);
        }
    }

    void handle(JsonNode update) {
        try {
            if (update.has("message")) {
                onMessage(update.get("message"));
            } else if (update.has("callback_query")) {
                onCallback(update.get("callback_query"));
            }
        } catch (RuntimeException e) {
            log.error("Видео-студия: ошибка обработки обновления Telegram", e);
        }
    }

    // ───────────────────────── сообщения ─────────────────────────

    private void onMessage(JsonNode message) {
        JsonNode chat = message.path("chat");
        if (!"private".equals(chat.path("type").asText())) {
            return;
        }
        long chatId = chat.path("id").asLong();
        long telegramId = message.path("from").path("id").asLong();
        String tgUsername = message.path("from").path("username").asText(null);
        long messageId = message.path("message_id").asLong();
        String text = message.path("text").asText(message.path("caption").asText("")).strip();
        String command = "";
        String argument = "";
        if (text.startsWith("/")) {
            int space = text.indexOf(' ');
            command = (space < 0 ? text : text.substring(0, space)).toLowerCase(Locale.ROOT);
            command = command.contains("@") ? command.substring(0, command.indexOf('@')) : command;
            argument = space < 0 ? "" : text.substring(space + 1).strip();
        }

        if ("/start".equals(command) && argument.startsWith("link_")) {
            link(chatId, telegramId, tgUsername, argument);
            return;
        }
        if ("/id".equals(command)) {
            reply(chatId, "Ваш Telegram ID: <code>" + telegramId + "</code>", null);
            return;
        }
        Optional<User> user = userOf(telegramId);
        if (user.isEmpty()) {
            reply(chatId, "Это бот видео-студии <b>offer</b>.\n\nЧтобы монтировать ролики здесь и видеть их в кабинете, "
                    + "привяжите Telegram: откройте «Видео-студию» на сайте и нажмите «Привязать Telegram».",
                    keyboard(List.of(List.of(urlButton("Открыть видео-студию", studioUrl)))));
            return;
        }
        User owner = user.get();
        JsonNode media = videoOf(message);
        if (media != null) {
            onVideo(owner, chatId, messageId, message, media, "/choose".equals(command));
            return;
        }
        switch (command) {
            case "/start", "/help" -> welcome(owner, chatId);
            case "/jobs", "/status" -> reply(chatId, queueText(owner), queueMarkup(owner));
            case "/cancel" -> cancelLatest(owner, chatId);
            case "/latest" -> latest(owner, chatId);
            case "/history" -> history(owner, chatId);
            case "/templates" -> reply(chatId, templatesText(owner), templatesMarkup(owner));
            case "/account" -> reply(chatId, "Telegram привязан к аккаунту <b>" + escape(owner.getName()) + "</b> ("
                    + escape(owner.getUsername()) + ").\nОтвязать можно в «Видео-студии» на сайте.",
                    keyboard(List.of(List.of(urlButton("Видео-студия на сайте", studioUrl)))));
            default -> reply(chatId, "Пришлите видео до 20 МБ — бот смонтирует его по шаблону или предложит режимы. "
                    + "Команды: /jobs, /latest, /history, /templates, /help.", null);
        }
    }

    private void link(long chatId, long telegramId, String tgUsername, String code) {
        try {
            Long userId = studio.confirmTelegramLink(code, telegramId, tgUsername);
            User user = users.findById(userId).orElseThrow();
            reply(chatId, "Готово! Telegram привязан к аккаунту <b>" + escape(user.getName()) + "</b>.\n\n"
                    + "Пришлите видео до 20 МБ — бот смонтирует его. Ролики и шаблоны общие с сайтом.",
                    keyboard(List.of(List.of(urlButton("Видео-студия на сайте", studioUrl)))));
        } catch (StudioException e) {
            reply(chatId, escape(e.getMessage()), keyboard(List.of(List.of(urlButton("Открыть видео-студию", studioUrl)))));
        }
    }

    private void welcome(User owner, long chatId) {
        StudioProfile profile = studio.profile(owner.getId());
        String auto = profile.getActiveTemplateId() == null ? "выключен"
                : studio.ownTemplates(owner.getId()).stream()
                .filter(t -> t.getId().equals(profile.getActiveTemplateId()))
                .map(t -> "«" + escape(t.getName()) + "»").findFirst().orElse("выключен");
        reply(chatId, "<b>Видео-студия offer</b>\n\n"
                + "Пришлите видео, видеодокумент или кружок до 20 МБ.\n"
                + "• Автошаблон: " + auto + " (/templates)\n"
                + "• Без автошаблона бот предложит быстрые режимы и ваши шаблоны.\n"
                + "• Подпись <code>/choose</code> к видео — выбрать режим разово.\n\n"
                + "/jobs — очередь и прогресс\n/latest — последний ролик\n/history — история\n/cancel — снять ожидающее видео\n\n"
                + "Сегодня осталось задач: " + remainingText(owner) + ". Большие исходники, баннеры и фоны — на сайте.",
                keyboard(List.of(List.of(urlButton("Открыть видео-студию", studioUrl)))));
    }

    private String remainingText(User owner) {
        return StudioService.admin(owner) ? "без лимита" : String.valueOf(studio.remaining(owner));
    }

    /** Видео, видеосообщение или документ-видео; null — не видео */
    static JsonNode videoOf(JsonNode message) {
        if (message.has("video")) {
            return message.get("video");
        }
        if (message.has("video_note")) {
            return message.get("video_note");
        }
        if (message.has("animation")) {
            return message.get("animation");
        }
        JsonNode document = message.path("document");
        if (!document.isMissingNode() && document.path("mime_type").asText("").startsWith("video/")) {
            return document;
        }
        return null;
    }

    private void onVideo(User owner, long chatId, long messageId, JsonNode message, JsonNode media, boolean choose) {
        long size = media.path("file_size").asLong(0);
        if (size > properties.getTelegram().getIncomingMaxBytes()) {
            reply(chatId, "Бот принимает видео до 20 МБ. Для больших исходников используйте сайт.",
                    keyboard(List.of(List.of(urlButton("Открыть видео-студию", studioUrl)))), messageId);
            return;
        }
        String filename = media.path("file_name").asText(message.has("video_note") ? "video-note.mp4" : "telegram-video.mp4");
        String fileId = media.path("file_id").asText();
        StudioProfile profile = studio.profile(owner.getId());
        if (!choose && profile.getActiveTemplateId() != null) {
            try {
                StudioTemplate template = studio.template(owner.getId(), profile.getActiveTemplateId());
                importAndEnqueue(owner, chatId, messageId, fileId, filename, template, null);
                return;
            } catch (StudioException e) {
                reply(chatId, escape(e.getMessage()), null, messageId);
                return;
            }
        }
        StudioChatDraft draft = drafts.save(StudioChatDraft.builder()
                .id(UUID.randomUUID()).userId(owner.getId()).chatId(chatId).messageId(messageId)
                .fileId(fileId).filename(StudioServiceNames.trim(filename)).createdAt(Instant.now()).build());
        reply(chatId, "Как смонтировать <b>" + escape(draft.getFilename()) + "</b>?\nВыбор действует 30 минут.",
                draftMarkup(owner, draft), messageId);
    }

    private Object draftMarkup(User owner, StudioChatDraft draft) {
        List<List<Map<String, Object>>> rows = new ArrayList<>();
        String prefix = "d:" + draft.getId() + ":";
        List<StudioTemplate> templates = new ArrayList<>(studio.ownTemplates(owner.getId()));
        UUID favorite = studio.profile(owner.getId()).getFavoriteTemplateId();
        templates.sort((a, b) -> Boolean.compare(b.getId().equals(favorite), a.getId().equals(favorite)));
        for (StudioTemplate template : templates.subList(0, Math.min(6, templates.size()))) {
            rows.add(List.of(button((template.getId().equals(favorite) ? "★ " : "") + label(template.getName(), 40),
                    prefix + "t:" + shortId(template.getId()))));
        }
        List<Map<String, Object>> row = new ArrayList<>();
        for (Map.Entry<String, QuickMode> mode : QUICK_MODES.entrySet()) {
            row.add(button(mode.getValue().label(), prefix + "q:" + mode.getKey()));
            if (row.size() == 2) {
                rows.add(row);
                row = new ArrayList<>();
            }
        }
        if (!row.isEmpty()) {
            rows.add(row);
        }
        rows.add(List.of(urlButton("Настроить на сайте", studioUrl)));
        return keyboard(rows);
    }

    static String shortId(UUID id) {
        return id.toString().replace("-", "").substring(0, 12);
    }

    static String label(String value, int limit) {
        return value.length() > limit ? value.substring(0, limit - 1) + "…" : value;
    }

    /** Скачивает видео из Telegram в хранилище и ставит задачу (по шаблону или быстрому режиму) */
    private void importAndEnqueue(User owner, long chatId, long replyTo, String fileId, String filename,
                                  StudioTemplate template, StudioSettings quick) {
        // Лимиты проверяем до скачивания, чтобы не гонять файл зря
        if (!StudioService.admin(owner) && studio.remaining(owner) <= 0) {
            reply(chatId, "Дневной лимит исчерпан. Он обновится в 00:00 UTC.", null, replyTo);
            return;
        }
        String key = StudioStorage.UPLOADS + "/" + owner.getId() + "/" + UUID.randomUUID() + ".mp4";
        Path temp = null;
        try {
            temp = Files.createTempFile("studio-tg", ".mp4");
            downloadWithRetry(fileId, temp);
            storage.upload(temp, key, "video/mp4");
            StudioService.Delivery delivery = new StudioService.Delivery(StudioJobOrigin.TELEGRAM, chatId, replyTo);
            List<StudioJob> created;
            if (template != null) {
                created = studio.enqueueFromTemplate(owner, template, filename, key, delivery);
            } else {
                StudioSettings settings = quick.validate(StudioService.admin(owner), false, true);
                created = studio.enqueue(owner, filename, settings, List.of(),
                        new StudioService.Inputs(key, null, null), delivery, null);
            }
            StudioJobDTO first = studio.toDTO(created.get(0));
            String what = template != null ? "по шаблону «" + escape(template.getName()) + "»" : "";
            reply(chatId, "Принято " + what + (created.size() > 1 ? " — пакет из " + created.size() + " MP4" : "")
                    + ". " + position(first) + "\nГотовое видео придёт в этот чат.",
                    keyboard(List.of(List.of(button("Статус", "j:" + first.id()), button("Отменить", "c:" + first.id())))),
                    replyTo);
        } catch (StudioException e) {
            storage.delete(key);
            reply(chatId, escape(e.getMessage()), null, replyTo);
        } catch (TelegramClient.TelegramException e) {
            storage.delete(key);
            reply(chatId, e.getCode() == 413 ? "Бот принимает видео до 20 МБ. Для больших исходников используйте сайт."
                    : "Не удалось получить видео из Telegram. Пришлите его ещё раз.", null, replyTo);
        } catch (IOException e) {
            storage.delete(key);
            log.error("Видео-студия: импорт из Telegram", e);
            reply(chatId, "Не удалось сохранить видео. Попробуйте ещё раз.", null, replyTo);
        } finally {
            if (temp != null) {
                try {
                    Files.deleteIfExists(temp);
                } catch (IOException ignored) {
                    // временный файл
                }
            }
        }
    }

    private void downloadWithRetry(String fileId, Path target) {
        long[] delays = {5, 20, 60};
        for (int attempt = 0; ; attempt++) {
            try {
                client.download(fileId, target, properties.getTelegram().getIncomingMaxBytes());
                return;
            } catch (TelegramClient.TelegramException e) {
                boolean permanent = e.getCode() == 400 || e.getCode() == 413 || e.getCode() == 404;
                if (permanent || attempt >= delays.length) {
                    throw e;
                }
                try {
                    Thread.sleep(delays[attempt] * 1000);
                } catch (InterruptedException interrupted) {
                    Thread.currentThread().interrupt();
                    throw e;
                }
            }
        }
    }

    private static String position(StudioJobDTO job) {
        if (job.queuePosition() == null || job.queuePosition() == 0) {
            return "Начинаю обработку.";
        }
        return "Перед вами в очереди: " + job.queuePosition() + ".";
    }

    // ───────────────────────── очередь, история ─────────────────────────

    private String queueText(User owner) {
        List<StudioJobDTO> rows = studio.list(owner, "all", 6);
        StringBuilder out = new StringBuilder("<b>Ваши ролики</b>\n");
        if (rows.isEmpty()) {
            out.append("Пока пусто. Пришлите видео.\n");
        }
        for (StudioJobDTO job : rows) {
            out.append("\n• ").append(escape(label(job.filename(), 40))).append(" — ").append(statusText(job));
        }
        out.append("\n\nОсталось задач сегодня: ").append(remainingText(owner));
        return out.toString();
    }

    private Object queueMarkup(User owner) {
        List<List<Map<String, Object>>> rows = new ArrayList<>();
        for (StudioJobDTO job : studio.list(owner, "active", 4)) {
            if ("QUEUED".equals(job.status())) {
                rows.add(List.of(button("Отменить: " + label(job.filename(), 24), "c:" + job.id())));
            }
        }
        rows.add(List.of(button("Обновить", "q"), urlButton("На сайте", studioUrl)));
        return keyboard(rows);
    }

    private static String statusText(StudioJobDTO job) {
        String status = STATUS.getOrDefault(job.status(), job.status());
        if ("RUNNING".equals(job.status())) {
            return status + " " + job.progress() + "%";
        }
        if ("QUEUED".equals(job.status()) && job.queuePosition() != null && job.queuePosition() > 0) {
            return status + ", перед вами " + job.queuePosition();
        }
        if ("FAILED".equals(job.status()) && job.error() != null) {
            return status + ": " + escape(job.error());
        }
        return status;
    }

    private void cancelLatest(User owner, long chatId) {
        List<StudioJob> queued = jobs.findByUserIdAndStatusInOrderByCreatedAtDesc(owner.getId(),
                List.of(StudioJobStatus.QUEUED), PageRequest.of(0, 1));
        if (queued.isEmpty()) {
            reply(chatId, "Ожидающих видео нет.", null);
            return;
        }
        studio.cancel(owner, queued.get(0).getId());
        reply(chatId, "Снято с очереди: " + escape(queued.get(0).getFilename()), null);
    }

    private void latest(User owner, long chatId) {
        List<StudioJobDTO> done = studio.list(owner, "done", 1);
        if (done.isEmpty()) {
            reply(chatId, "Готовых роликов пока нет.", null);
            return;
        }
        card(owner, chatId, done.get(0).id(), null);
    }

    private void history(User owner, long chatId) {
        List<StudioJobDTO> rows = studio.list(owner, "all", 8);
        if (rows.isEmpty()) {
            reply(chatId, "История пуста.", null);
            return;
        }
        List<List<Map<String, Object>>> buttons = new ArrayList<>();
        for (StudioJobDTO job : rows) {
            buttons.add(List.of(button(label(job.filename(), 28) + " · " + STATUS.getOrDefault(job.status(), job.status()),
                    "j:" + job.id())));
        }
        reply(chatId, "<b>История</b> — выберите ролик:", keyboard(buttons));
    }

    /** Карточка задачи; messageId != null — обновить существующее сообщение */
    private void card(User owner, long chatId, UUID jobId, Long messageId) {
        StudioJobDTO job = studio.get(owner, jobId);
        StringBuilder text = new StringBuilder("<b>").append(escape(job.filename())).append("</b>\n")
                .append(statusText(job));
        if (job.presetName() != null) {
            text.append("\nФон: ").append(escape(job.presetName()));
        }
        if (job.resultSize() != null && job.downloadUrl() != null) {
            text.append(String.format(Locale.ROOT, "\nРазмер: %.1f МБ", job.resultSize() / 1_000_000.0));
        }
        List<List<Map<String, Object>>> rows = new ArrayList<>();
        if (job.downloadUrl() != null) {
            rows.add(List.of(urlButton("Скачать MP4", job.downloadUrl()), button("Прислать в чат", "s:" + job.id())));
        }
        if ("QUEUED".equals(job.status()) || "RUNNING".equals(job.status())) {
            rows.add(List.of(button("Обновить", "j:" + job.id()), button("Отменить", "c:" + job.id())));
        }
        if (job.retryUntil() != null) {
            rows.add(List.of(button("Повторить", "r:" + job.id()), button("В 720p", "r7:" + job.id())));
        }
        Object markup = keyboard(rows);
        if (messageId != null) {
            client.editMessage(chatId, messageId, text.toString(), markup);
        } else {
            reply(chatId, text.toString(), markup);
        }
    }

    // ───────────────────────── шаблоны ─────────────────────────

    private String templatesText(User owner) {
        List<StudioTemplate> templates = studio.ownTemplates(owner.getId());
        if (templates.isEmpty()) {
            return "Шаблонов пока нет. Настройте монтаж на сайте и сохраните его в «Шаблоны монтажа».";
        }
        UUID active = studio.profile(owner.getId()).getActiveTemplateId();
        return "<b>Автошаблон</b> применяется к каждому присланному видео.\nСейчас: "
                + templates.stream().filter(t -> t.getId().equals(active)).map(t -> "«" + escape(t.getName()) + "»")
                .findFirst().orElse("выключен");
    }

    private Object templatesMarkup(User owner) {
        List<List<Map<String, Object>>> rows = new ArrayList<>();
        UUID active = studio.profile(owner.getId()).getActiveTemplateId();
        UUID favorite = studio.profile(owner.getId()).getFavoriteTemplateId();
        for (StudioTemplate t : studio.ownTemplates(owner.getId()).stream().limit(10).toList()) {
            String mark = t.getId().equals(active) ? "✓ " : "";
            rows.add(List.of(button(mark + label(t.getName(), 30), "a:" + t.getId()),
                    button(t.getId().equals(favorite) ? "★" : "☆", "f:" + t.getId())));
        }
        if (active != null) {
            rows.add(List.of(button("Выключить автошаблон", "a:off")));
        }
        rows.add(List.of(urlButton("Шаблоны на сайте", studioUrl)));
        return keyboard(rows);
    }

    // ───────────────────────── кнопки ─────────────────────────

    private void onCallback(JsonNode callback) {
        String callbackId = callback.path("id").asText();
        String data = callback.path("data").asText("");
        long telegramId = callback.path("from").path("id").asLong();
        JsonNode message = callback.path("message");
        long chatId = message.path("chat").path("id").asLong(telegramId);
        Long messageId = message.has("message_id") ? message.get("message_id").asLong() : null;
        Optional<User> user = userOf(telegramId);
        if (user.isEmpty()) {
            client.answerCallback(callbackId, "Сначала привяжите Telegram на сайте");
            return;
        }
        User owner = user.get();
        try {
            if (data.equals("q")) {
                client.answerCallback(callbackId, null);
                if (messageId != null) {
                    client.editMessage(chatId, messageId, queueText(owner), queueMarkup(owner));
                }
            } else if (data.startsWith("d:")) {
                onDraftChoice(owner, chatId, messageId, callbackId, data);
            } else if (data.startsWith("j:")) {
                client.answerCallback(callbackId, null);
                card(owner, chatId, UUID.fromString(data.substring(2)), messageId);
            } else if (data.startsWith("c:")) {
                studio.cancel(owner, UUID.fromString(data.substring(2)));
                client.answerCallback(callbackId, "Задача отменена");
                card(owner, chatId, UUID.fromString(data.substring(2)), messageId);
            } else if (data.startsWith("r:") || data.startsWith("r7:")) {
                boolean low = data.startsWith("r7:");
                UUID id = UUID.fromString(data.substring(low ? 3 : 2));
                StudioJobDTO created = studio.retry(owner, id, low ? "720" : null);
                client.answerCallback(callbackId, "Повтор поставлен в очередь");
                reply(chatId, "Повтор принят. " + position(created) + "\nГотовое видео придёт в этот чат.",
                        keyboard(List.of(List.of(button("Статус", "j:" + created.id())))));
            } else if (data.startsWith("s:")) {
                client.answerCallback(callbackId, "Отправляю…");
                notifier.sendExisting(studio.own(owner, UUID.fromString(data.substring(2))), chatId);
            } else if (data.startsWith("a:")) {
                UUID id = "a:off".equals(data) ? null : UUID.fromString(data.substring(2));
                studio.setActiveTemplate(owner, id, false);
                client.answerCallback(callbackId, id == null ? "Автошаблон выключен" : "Автошаблон включён");
                if (messageId != null) {
                    client.editMessage(chatId, messageId, templatesText(owner), templatesMarkup(owner));
                }
            } else if (data.startsWith("f:")) {
                UUID id = UUID.fromString(data.substring(2));
                UUID current = studio.profile(owner.getId()).getFavoriteTemplateId();
                studio.setActiveTemplate(owner, id.equals(current) ? null : id, true);
                client.answerCallback(callbackId, id.equals(current) ? "Убрано из избранного" : "Шаблон в избранном");
                if (messageId != null) {
                    client.editMessage(chatId, messageId, templatesText(owner), templatesMarkup(owner));
                }
            } else {
                client.answerCallback(callbackId, null);
            }
        } catch (StudioException e) {
            client.answerCallback(callbackId, label(e.getMessage(), 190));
        } catch (IllegalArgumentException e) {
            client.answerCallback(callbackId, "Кнопка устарела");
        }
    }

    private void onDraftChoice(User owner, long chatId, Long messageId, String callbackId, String data) {
        // d:<draft uuid>:q:<mode> | d:<draft uuid>:t:<12 hex шаблона>
        String[] parts = data.split(":");
        if (parts.length != 4) {
            client.answerCallback(callbackId, "Кнопка устарела");
            return;
        }
        StudioChatDraft draft = drafts.findById(UUID.fromString(parts[1]))
                .filter(d -> d.getUserId().equals(owner.getId()))
                .filter(d -> d.getCreatedAt().isAfter(Instant.now().minus(DRAFT_TTL)))
                .orElse(null);
        if (draft == null) {
            client.answerCallback(callbackId, "Выбор устарел. Пришлите видео ещё раз.");
            return;
        }
        StudioTemplate template = null;
        StudioSettings quick = null;
        if ("q".equals(parts[2]) && QUICK_MODES.containsKey(parts[3])) {
            quick = QUICK_MODES.get(parts[3]).settings();
        } else if ("t".equals(parts[2])) {
            template = studio.ownTemplates(owner.getId()).stream()
                    .filter(t -> shortId(t.getId()).equals(parts[3])).findFirst().orElse(null);
        }
        if (template == null && quick == null) {
            client.answerCallback(callbackId, "Шаблон не найден");
            return;
        }
        client.answerCallback(callbackId, "Загружаю видео…");
        if (messageId != null) {
            String chosen = template != null ? "шаблон «" + escape(template.getName()) + "»" : QUICK_MODES.get(parts[3]).label();
            client.editMessage(chatId, messageId, "<b>" + escape(draft.getFilename()) + "</b>: " + chosen, null);
        }
        importAndEnqueue(owner, chatId, draft.getMessageId(), draft.getFileId(), draft.getFilename(), template, quick);
    }

    // ───────────────────────── утилиты ─────────────────────────

    private Optional<User> userOf(long telegramId) {
        return profiles.findByTelegramId(telegramId).flatMap(p -> users.findById(p.getUserId()));
    }

    private void reply(long chatId, String html, Object markup) {
        reply(chatId, html, markup, null);
    }

    private void reply(long chatId, String html, Object markup, Long replyTo) {
        try {
            client.sendMessage(chatId, html, markup, replyTo);
        } catch (TelegramClient.TelegramException e) {
            log.warn("Видео-студия: сообщение в чат {} не отправлено: {}", chatId, e.getMessage());
        }
    }

    /** Имя файла из Telegram в пределах колонки */
    static final class StudioServiceNames {
        private StudioServiceNames() {}

        static String trim(String filename) {
            String name = filename == null || filename.isBlank() ? "telegram-video.mp4" : filename.strip();
            return name.length() > 160 ? name.substring(0, 160) : name;
        }
    }
}
