package ru.trafficmarkering.service.studio.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import ru.trafficmarkering.config.StudioProperties;
import ru.trafficmarkering.model.studio.StudioJob;
import ru.trafficmarkering.model.studio.StudioJobStatus;
import ru.trafficmarkering.model.studio.StudioProfile;
import ru.trafficmarkering.repository.StudioProfileRepository;
import ru.trafficmarkering.service.storage.FileStorage;
import ru.trafficmarkering.service.studio.StudioException;
import ru.trafficmarkering.service.studio.StudioService;
import ru.trafficmarkering.service.studio.StudioSettings;
import ru.trafficmarkering.service.studio.StudioStorage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;

import static ru.trafficmarkering.service.studio.telegram.TelegramClient.button;
import static ru.trafficmarkering.service.studio.telegram.TelegramClient.escape;
import static ru.trafficmarkering.service.studio.telegram.TelegramClient.keyboard;
import static ru.trafficmarkering.service.studio.telegram.TelegramClient.urlButton;

/** Доставка результата в Telegram: файлом до 50 МБ, иначе ссылкой. */
@Slf4j
@Component
public class StudioNotifier {

    private final TelegramClient client;
    private final StudioProperties properties;
    private final FileStorage fileStorage;
    private final StudioStorage storage;
    private final StudioService studio;
    private final StudioProfileRepository profiles;

    public StudioNotifier(TelegramClient client, StudioProperties properties, FileStorage fileStorage,
                          StudioStorage storage, @Lazy StudioService studio, StudioProfileRepository profiles) {
        this.profiles = profiles;
        this.client = client;
        this.properties = properties;
        this.fileStorage = fileStorage;
        this.storage = storage;
        this.studio = studio;
    }

    /** Сообщение «собираю…» с процентами; его правят по ходу рендера, готовый файл приходит отдельно */
    public static final class Progress {
        private static final long MIN_INTERVAL_MS = 3000;

        private final TelegramClient client;
        private final long chatId;
        private final long messageId;
        private final String name;
        private int shown;
        private long shownAt;

        Progress(TelegramClient client, long chatId, long messageId, String name) {
            this.client = client;
            this.chatId = chatId;
            this.messageId = messageId;
            this.name = name;
            this.shownAt = System.currentTimeMillis();
        }

        /** Не чаще раза в 3 секунды и только при сдвиге на 5% — у Telegram лимит на правки */
        public void update(int percent) {
            long now = System.currentTimeMillis();
            if (percent - shown < 5 || now - shownAt < MIN_INTERVAL_MS) {
                return;
            }
            shown = percent;
            shownAt = now;
            client.tryEditMessage(chatId, messageId, progressText(name, percent));
        }

        public void cancelled(String filename) {
            client.tryEditMessage(chatId, messageId, "⏹ Сборка отменена: <b>" + escape(filename) + "</b>");
        }

        void close(String html) {
            client.tryEditMessage(chatId, messageId, html);
        }
    }

    static String progressText(String name, int percent) {
        int filled = Math.max(0, Math.min(10, percent / 10));
        return "⚙️ Собираю видео <b>" + escape(name) + "</b>\n"
                + "▓".repeat(filled) + "░".repeat(10 - filled) + " " + percent + "%";
    }

    /** Рендер начался: в чат владельца уходит сообщение с процентами. null — слать некуда */
    public Progress started(StudioJob job) {
        Long chatId = chatOf(job);
        if (!client.enabled() || chatId == null) {
            return null;
        }
        try {
            JsonNode sent = client.sendMessage(chatId, progressText(job.getFilename(), 0), null, job.getTelegramReplyTo());
            long messageId = sent == null ? 0 : sent.path("message_id").asLong();
            return messageId == 0 ? null : new Progress(client, chatId, messageId, job.getFilename());
        } catch (RuntimeException e) {
            log.warn("Видео-студия: не удалось сообщить о старте задачи {}: {}", job.getId(), e.getMessage());
            return null;
        }
    }

    public void finished(StudioJob job, Path localResult) {
        finished(job, localResult, null);
    }

    /**
     * Задача завершилась: ролик уходит в чат, откуда пришёл, а задача с сайта — в привязанный Telegram владельца.
     * Не привязан — сайт просит привязать.
     */
    public void finished(StudioJob job, Path localResult, Progress progress) {
        Long chatId = chatOf(job);
        if (!client.enabled() || chatId == null) {
            return;
        }
        if (progress != null) {
            progress.close(job.getStatus() == StudioJobStatus.DONE
                    ? "✅ Собрано: <b>" + escape(job.getFilename()) + "</b> — файл ниже"
                    : "❌ Не собрано: <b>" + escape(job.getFilename()) + "</b>");
        }
        try {
            if (job.getStatus() == StudioJobStatus.DONE) {
                deliver(job, chatId, job.getTelegramReplyTo(), localResult);
            } else if (job.getStatus() == StudioJobStatus.FAILED) {
                Object markup = job.getInputsUntil() != null
                        ? keyboard(List.of(List.of(button("Повторить", "r:" + job.getId())))) : null;
                client.sendMessage(chatId,
                        "Не получилось собрать <b>" + escape(job.getFilename()) + "</b>.\n" + escape(job.getError()),
                        markup, job.getTelegramReplyTo());
            }
        } catch (RuntimeException e) {
            log.warn("Видео-студия: не удалось уведомить чат {} о задаче {}: {}", chatId, job.getId(), e.getMessage());
        }
    }

    /** Чат, откуда пришла задача, а для задач с сайта — привязанный Telegram владельца */
    private Long chatOf(StudioJob job) {
        return job.getTelegramChatId() != null ? job.getTelegramChatId() : linkedChat(job.getUserId());
    }

    private Long linkedChat(Long userId) {
        return userId == null ? null : profiles.findById(userId).map(StudioProfile::getTelegramId).orElse(null);
    }

    /** Готовый ролик в чат по кнопке на сайте или в боте; квоту не тратит */
    public void sendExisting(StudioJob job, long chatId) {
        if (!client.enabled()) {
            throw new StudioException("Telegram-бот не подключён.");
        }
        if (job.getStatus() != StudioJobStatus.DONE || job.getResultKey() == null) {
            throw new StudioException("Файл ещё не готов или уже удалён.");
        }
        Path temp = null;
        try {
            if (job.getResultSize() != null && job.getResultSize() <= properties.getTelegram().getOutgoingMaxBytes()) {
                temp = Files.createTempFile("studio-send", ".mp4");
                storage.download(job.getResultKey(), temp);
            }
            deliver(job, chatId, null, temp);
        } catch (IOException e) {
            throw new IllegalStateException(e);
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

    private void deliver(StudioJob job, long chatId, Long replyTo, Path file) {
        StudioSettings settings = studio.settingsOf(job);
        String name = StudioService.downloadName(job, settings);
        String url = fileStorage.presignedUrl(job.getResultKey(), name, false);
        String caption = "Готово: <b>" + escape(job.getFilename()) + "</b>"
                + (job.getPreset() != null ? "\nФон: " + escape(StudioSettings.PRESET_NAMES.get(job.getPreset())) : "")
                + "\nФайл хранится " + properties.getResultTtlHours() + " ч.";
        Object markup = keyboard(List.of(List.of(urlButton("Скачать MP4", url))));
        long size = job.getResultSize() == null ? Long.MAX_VALUE : job.getResultSize();
        if (file != null && size <= properties.getTelegram().getOutgoingMaxBytes()) {
            try {
                client.sendVideo(chatId, file, name, caption, markup, replyTo);
                return;
            } catch (TelegramClient.TelegramException e) {
                log.info("Видео-студия: Telegram не принял файл {}: {}", job.getId(), e.getMessage());
            }
        }
        client.sendMessage(chatId, caption + "\nTelegram не принимает такой большой файл — скачайте по ссылке (действует час).",
                markup, replyTo);
    }
}
