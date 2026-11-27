package ru.trafficmarkering.service.moderation;

import com.fasterxml.jackson.databind.JsonNode;
import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.trafficmarkering.config.ModerationProperties;
import ru.trafficmarkering.model.application.ApplicationStatus;
import ru.trafficmarkering.model.moderation.Moderator;
import ru.trafficmarkering.service.studio.telegram.TelegramClient;

import java.time.Instant;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;

import static ru.trafficmarkering.service.studio.telegram.TelegramClient.button;
import static ru.trafficmarkering.service.studio.telegram.TelegramClient.escape;
import static ru.trafficmarkering.service.studio.telegram.TelegramClient.keyboard;

/**
 * Бот модерации: менеджеру лентой приходят ролики, он жмёт «Принять» или «Отклонить».
 * Пускает только тех, кого супер-админ внёс в список; первым делом менеджер выбирает часы приёма.
 */
@Slf4j
@Component
public class ModerationBot {

    static final int REASON_MAX = 500;

    /** Часовые пояса для кнопок: смещение и подсказка-город */
    static final Map<String, String> ZONES = zones();

    private static Map<String, String> zones() {
        Map<String, String> zones = new LinkedHashMap<>();
        zones.put("+02:00", "UTC+2 · Калининград");
        zones.put("+03:00", "UTC+3 · Москва");
        zones.put("+04:00", "UTC+4 · Самара");
        zones.put("+05:00", "UTC+5 · Екатеринбург");
        zones.put("+06:00", "UTC+6 · Омск");
        zones.put("+07:00", "UTC+7 · Новосибирск");
        zones.put("+08:00", "UTC+8 · Иркутск");
        zones.put("+09:00", "UTC+9 · Якутск");
        zones.put("+10:00", "UTC+10 · Владивосток");
        zones.put("+12:00", "UTC+12 · Камчатка");
        zones.put("+00:00", "UTC+0 · Лондон");
        zones.put("+01:00", "UTC+1 · Берлин");
        return Collections.unmodifiableMap(zones);
    }

    private final ModerationTelegram telegram;
    private final ModerationQueue queue;
    private final ModeratorRoster roster;
    private final ModerationProperties properties;
    private final Map<Long, Boolean> seenUpdates = Collections.synchronizedMap(new LinkedHashMap<>(256, 0.75f, false) {
        @Override
        protected boolean removeEldestEntry(Map.Entry<Long, Boolean> eldest) {
            return size() > 2000;
        }
    });
    private final ThreadPoolExecutor executor = new ThreadPoolExecutor(2, 4, 60, TimeUnit.SECONDS,
            new ArrayBlockingQueue<>(500), r -> {
        Thread thread = new Thread(r, "moderation-bot");
        thread.setDaemon(true);
        return thread;
    });

    public ModerationBot(ModerationTelegram telegram, ModerationQueue queue, ModeratorRoster roster,
                         ModerationProperties properties) {
        this.properties = properties;
        this.telegram = telegram;
        this.queue = queue;
        this.roster = roster;
    }

    @PreDestroy
    public void stop() {
        executor.shutdown();
    }

    private TelegramClient client() {
        return telegram.client();
    }

    public void accept(JsonNode update) {
        long updateId = update.path("update_id").asLong(-1);
        if (updateId >= 0 && seenUpdates.put(updateId, Boolean.TRUE) != null) {
            return;
        }
        try {
            executor.execute(() -> handle(update));
        } catch (RejectedExecutionException e) {
            log.warn("Модерация: очередь бота переполнена, обновление {} пропущено", updateId);
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
            log.error("Модерация: ошибка обработки обновления Telegram", e);
        }
    }

    // ───────────────────────── сообщения ─────────────────────────

    private void onMessage(JsonNode message) {
        JsonNode chat = message.path("chat");
        if (!"private".equals(chat.path("type").asText())) {
            return;
        }
        long chatId = chat.path("id").asLong();
        JsonNode from = message.path("from");
        Optional<Moderator> found = access(from, chatId);
        if (found.isEmpty()) {
            deny(chatId, from);
            return;
        }
        Moderator moderator = found.get();
        String text = message.path("text").asText("").strip();
        String command = "";
        if (text.startsWith("/")) {
            int space = text.indexOf(' ');
            command = (space < 0 ? text : text.substring(0, space)).toLowerCase(Locale.ROOT);
            command = command.contains("@") ? command.substring(0, command.indexOf('@')) : command;
        }

        if (command.isEmpty() && !text.isEmpty()) {
            Optional<Long> awaiting = queue.awaitingReason(from.path("id").asLong());
            if (awaiting.isPresent()) {
                String reason = text.length() > REASON_MAX ? text.substring(0, REASON_MAX) : text;
                ModerationQueue.Outcome outcome = queue.decide(awaiting.get(), from.path("id").asLong(),
                        ApplicationStatus.REJECTED, reason);
                reply(chatId, outcome.done() ? "❌ Отклонено. Следующий ролик — уже в пути." : escape(outcome.toast()), null);
                return;
            }
        }

        switch (command) {
            case "/start" -> {
                if (!moderator.scheduled()) {
                    reply(chatId, "Привет, " + escape(name(moderator)) + "! Вы в списке менеджеров модерации.\n\n"
                            + "Сюда будут приходить ролики: смотрите и жмите «Принять» или «Отклонить». "
                            + "На каждую заявку — " + holdLabel() + ", потом она уйдёт другому менеджеру.\n\n"
                            + "Сначала настроим, когда вам присылать заявки. Ваш часовой пояс:", zonesKeyboard());
                } else {
                    reply(chatId, statusText(moderator), statusKeyboard(moderator));
                }
            }
            case "/hours" -> reply(chatId, "Ваш часовой пояс:", zonesKeyboard());
            case "/off", "/pause" -> pause(chatId, moderator, null);
            case "/on", "/resume" -> resume(chatId, moderator, null);
            case "/stats" -> reply(chatId, statsText(moderator), null);
            case "/help" -> reply(chatId, helpText(), null);
            default -> reply(chatId, statusText(moderator), statusKeyboard(moderator));
        }
    }

    private Optional<Moderator> access(JsonNode from, long chatId) {
        String first = from.path("first_name").asText("");
        String last = from.path("last_name").asText("");
        String display = (first + " " + last).strip();
        return roster.access(from.path("id").asLong(), from.path("username").asText(null), display, chatId);
    }

    private void deny(long chatId, JsonNode from) {
        String username = from.path("username").asText("");
        if (username.isBlank()) {
            reply(chatId, "Сорри, этот бот только для менеджеров модерации. У вас в Telegram не задан ник (username) — "
                    + "задайте его в настройках Telegram и попросите администратора добавить вас в список.", null);
        } else {
            reply(chatId, "Сорри, вас нет в списке менеджеров модерации. Попросите администратора добавить "
                    + "<b>@" + escape(username) + "</b> — и нажмите /start ещё раз.", null);
        }
    }

    // ───────────────────────── кнопки ─────────────────────────

    private void onCallback(JsonNode query) {
        String callbackId = query.path("id").asText();
        String data = query.path("data").asText("");
        JsonNode from = query.path("from");
        long telegramId = from.path("id").asLong();
        long chatId = query.path("message").path("chat").path("id").asLong();
        long messageId = query.path("message").path("message_id").asLong();

        Optional<Moderator> found = access(from, chatId);
        if (found.isEmpty()) {
            client().answerCallback(callbackId, "Вас нет в списке менеджеров");
            return;
        }
        Moderator moderator = found.get();
        String[] parts = data.split(":", 3);
        String action = parts[0];
        try {
            switch (action) {
                case "ok" -> {
                    ModerationQueue.Outcome outcome = queue.decide(Long.parseLong(parts[1]), telegramId,
                            ApplicationStatus.APPROVED, null);
                    client().answerCallback(callbackId, outcome.toast());
                }
                case "no" -> {
                    long id = Long.parseLong(parts[1]);
                    if (queue.ownsActive(id, telegramId)) {
                        client().editMarkup(chatId, messageId, ModerationCard.reasonsKeyboard(id));
                        client().answerCallback(callbackId, "Выберите причину — её увидит креатор");
                    } else {
                        client().answerCallback(callbackId, "Заявка уже закрыта");
                    }
                }
                case "rj" -> {
                    int index = Integer.parseInt(parts[2]);
                    String reason = index >= 0 && index < ModerationCard.REASONS.size() ? ModerationCard.REASONS.get(index) : null;
                    if (reason == null) {
                        client().answerCallback(callbackId, "Причина не найдена");
                        return;
                    }
                    ModerationQueue.Outcome outcome = queue.decide(Long.parseLong(parts[1]), telegramId,
                            ApplicationStatus.REJECTED, reason);
                    client().answerCallback(callbackId, outcome.toast());
                }
                case "rt" -> {
                    if (queue.awaitReason(Long.parseLong(parts[1]), telegramId, true)) {
                        client().answerCallback(callbackId, null);
                        client().sendMessage(chatId, "✍️ Напишите причину отказа одним сообщением — её увидит креатор.",
                                Map.of("force_reply", true, "input_field_placeholder", "Причина отказа"), messageId);
                    } else {
                        client().answerCallback(callbackId, "Заявка уже закрыта");
                    }
                }
                case "bk" -> {
                    long id = Long.parseLong(parts[1]);
                    if (queue.awaitReason(id, telegramId, false)) {
                        client().editMarkup(chatId, messageId, ModerationCard.decisionKeyboard(id));
                    }
                    client().answerCallback(callbackId, null);
                }
                case "tz" -> {
                    String offset = data.substring(3);
                    if (!ZONES.containsKey(offset)) {
                        client().answerCallback(callbackId, "Неизвестный пояс");
                        return;
                    }
                    roster.update(moderator.getId(), m -> m.setUtcOffset(offset));
                    client().answerCallback(callbackId, null);
                    client().editMessage(chatId, messageId, "Пояс: <b>" + ZONES.get(offset) + "</b>.\n\n"
                            + "С какого часа присылать заявки?", fromHoursKeyboard());
                }
                case "hf" -> {
                    int fromHour = hour(parts[1]);
                    client().answerCallback(callbackId, null);
                    client().editMessage(chatId, messageId, String.format("С <b>%02d:00</b>. До какого часа?", fromHour),
                            toHoursKeyboard(fromHour));
                }
                case "ht" -> saveHours(callbackId, chatId, messageId, moderator, hour(parts[1]), hour(parts[2]));
                case "h24" -> saveHours(callbackId, chatId, messageId, moderator, 0, 0);
                case "ps" -> {
                    client().answerCallback(callbackId, null);
                    pause(chatId, moderator, messageId);
                }
                case "go" -> {
                    client().answerCallback(callbackId, null);
                    resume(chatId, moderator, messageId);
                }
                case "hr" -> {
                    client().answerCallback(callbackId, null);
                    client().editMessage(chatId, messageId, "Ваш часовой пояс:", zonesKeyboard());
                }
                default -> client().answerCallback(callbackId, null);
            }
        } catch (NumberFormatException | ArrayIndexOutOfBoundsException e) {
            client().answerCallback(callbackId, "Кнопка устарела");
        }
    }

    private static int hour(String value) {
        int hour = Integer.parseInt(value);
        if (hour < 0 || hour > 23) {
            throw new NumberFormatException("hour");
        }
        return hour;
    }

    private void saveHours(String callbackId, long chatId, long messageId, Moderator moderator, int from, int to) {
        Moderator updated = roster.update(moderator.getId(), m -> {
            m.setWorkFrom(from);
            m.setWorkTo(to);
            if (m.getUtcOffset() == null) {
                m.setUtcOffset("+03:00");
            }
        });
        client().answerCallback(callbackId, "Сохранено");
        String now = !updated.active()
                ? "Сейчас вы неактивны — чтобы получать заявки, нажмите /on."
                : updated.worksAt(Instant.now())
                ? "Сейчас рабочее время — первая заявка придёт, как только появится ролик на модерации."
                : "Сейчас не ваше время — заявки начнут приходить в начале окна.";
        client().editMessage(chatId, messageId, "✅ Заявки будут приходить: <b>" + escape(updated.scheduleLabel()) + "</b>.\n"
                + now + "\n\nПоменять часы — /hours, стать неактивным — /off.", statusKeyboard(updated));
    }

    private void pause(long chatId, Moderator moderator, Long messageId) {
        Moderator updated = roster.update(moderator.getId(), m -> m.setPaused(true));
        String text = "⚪ Вы <b>неактивны</b>: новые заявки не приходят. Текущую, если она есть, ещё можно решить.\n"
                + "Снова принимать заявки — /on";
        show(chatId, messageId, text, statusKeyboard(updated));
    }

    private void resume(long chatId, Moderator moderator, Long messageId) {
        Moderator updated = roster.update(moderator.getId(), m -> {
            m.setPaused(false);
            m.setMissedInRow(0);
        });
        if (!updated.scheduled()) {
            show(chatId, messageId, "Сначала выберите часовой пояс:", zonesKeyboard());
            return;
        }
        String text = updated.worksAt(Instant.now())
                ? "🟢 Вы <b>активны</b> — заявки уже идут. Роликов в очереди: " + roster.waiting() + "."
                : "🟢 Вы <b>активны</b> — заявки придут в ваши часы: " + escape(updated.scheduleLabel()) + ".";
        show(chatId, messageId, text, statusKeyboard(updated));
    }

    private void show(long chatId, Long messageId, String text, Map<String, Object> markup) {
        if (messageId != null) {
            client().editMessage(chatId, messageId, text, markup);
        } else {
            reply(chatId, text, markup);
        }
    }

    // ───────────────────────── тексты и разметка ─────────────────────────

    private String statusText(Moderator moderator) {
        StringBuilder text = new StringBuilder("👤 <b>").append(escape(name(moderator))).append("</b>, менеджер модерации\n");
        text.append("Часы: ").append(escape(moderator.scheduleLabel())).append('\n');
        if (!moderator.active()) {
            text.append("Статус: ⚪ неактивен — заявки не приходят, включиться — /on\n");
        } else if (moderator.worksAt(Instant.now())) {
            text.append("Статус: 🟢 активен, принимаете заявки\n");
        } else {
            text.append("Статус: 🟢 активен, но сейчас не ваши часы\n");
        }
        text.append("Роликов в очереди: ").append(roster.waiting());
        return text.toString();
    }

    private String statsText(Moderator moderator) {
        long[] stats = roster.stats(moderator.getId());
        return "📊 <b>Ваша статистика</b>\n"
                + "За сутки решено: " + stats[3] + "\n"
                + "Всего принято: " + stats[0] + "\n"
                + "Всего отклонено: " + stats[1] + "\n"
                + "Пропущено (время вышло): " + stats[2];
    }

    private String helpText() {
        return "Как это работает:\n"
                + "• Ролики на модерации раздаются менеджерам по одному. Пока вы не ответили, следующий не придёт.\n"
                + "• На заявку — " + holdLabel() + ". Не успели — она уйдёт другому менеджеру.\n"
                + "• Пропустили " + properties.getPauseAfterMissed() + " подряд — станете неактивны, заявки перестанут приходить. "
                + "Включиться снова — /on.\n"
                + "• «Отклонить» просит причину: выберите готовую или напишите свою — её увидит креатор.\n\n"
                + "/on — активен, /off — неактивен (перерыв), /hours — часы приёма, /stats — статистика.";
    }

    private String holdLabel() {
        return minutes(properties.getHoldMinutes());
    }

    static String minutes(int minutes) {
        int mod10 = minutes % 10;
        int mod100 = minutes % 100;
        String word = mod10 == 1 && mod100 != 11 ? "минута"
                : mod10 >= 2 && mod10 <= 4 && (mod100 < 12 || mod100 > 14) ? "минуты" : "минут";
        return minutes + " " + word;
    }

    private static String name(Moderator moderator) {
        return moderator.getDisplayName() != null && !moderator.getDisplayName().isBlank()
                ? moderator.getDisplayName() : "@" + moderator.getUsername();
    }

    private static Map<String, Object> statusKeyboard(Moderator moderator) {
        List<Map<String, Object>> row = new ArrayList<>();
        row.add(moderator.active() ? button("⚪ Стать неактивным", "ps") : button("🟢 Стать активным", "go"));
        row.add(button("🕘 Часы", "hr"));
        return keyboard(List.of(row));
    }

    static Map<String, Object> zonesKeyboard() {
        List<List<Map<String, Object>>> rows = new ArrayList<>();
        List<Map<String, Object>> row = new ArrayList<>();
        for (Map.Entry<String, String> zone : ZONES.entrySet()) {
            row.add(button(zone.getValue(), "tz:" + zone.getKey()));
            if (row.size() == 2) {
                rows.add(row);
                row = new ArrayList<>();
            }
        }
        if (!row.isEmpty()) {
            rows.add(row);
        }
        return keyboard(rows);
    }

    static Map<String, Object> fromHoursKeyboard() {
        List<List<Map<String, Object>>> rows = hours(hour -> "hf:" + hour);
        rows.add(List.of(button("Круглосуточно", "h24")));
        return keyboard(rows);
    }

    static Map<String, Object> toHoursKeyboard(int from) {
        List<List<Map<String, Object>>> rows = new ArrayList<>();
        List<Map<String, Object>> row = new ArrayList<>();
        // «до» — следующие 23 часа после «с», по кругу: окно может идти через полночь
        for (int step = 1; step < 24; step++) {
            int hour = (from + step) % 24;
            row.add(button(String.format("%02d:00", hour), "ht:" + from + ":" + hour));
            if (row.size() == 6) {
                rows.add(row);
                row = new ArrayList<>();
            }
        }
        if (!row.isEmpty()) {
            rows.add(row);
        }
        return keyboard(rows);
    }

    private static List<List<Map<String, Object>>> hours(java.util.function.IntFunction<String> data) {
        List<List<Map<String, Object>>> rows = new ArrayList<>();
        List<Map<String, Object>> row = new ArrayList<>();
        for (int hour = 0; hour < 24; hour++) {
            row.add(button(String.format("%02d:00", hour), data.apply(hour)));
            if (row.size() == 6) {
                rows.add(row);
                row = new ArrayList<>();
            }
        }
        return rows;
    }

    private void reply(long chatId, String html, Object markup) {
        try {
            client().sendMessage(chatId, html, markup, null);
        } catch (RuntimeException e) {
            log.warn("Модерация: не удалось ответить в чат {}: {}", chatId, e.getMessage());
        }
    }
}
