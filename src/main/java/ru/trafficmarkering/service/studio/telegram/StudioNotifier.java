package ru.trafficmarkering.service.studio.telegram;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Component;
import ru.trafficmarkering.config.StudioProperties;
import ru.trafficmarkering.model.studio.StudioJob;
import ru.trafficmarkering.model.studio.StudioJobStatus;
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

    public StudioNotifier(TelegramClient client, StudioProperties properties, FileStorage fileStorage,
                          StudioStorage storage, @Lazy StudioService studio) {
        this.client = client;
        this.properties = properties;
        this.fileStorage = fileStorage;
        this.storage = storage;
        this.studio = studio;
    }

    /** Задача завершилась: сообщаем в чат, если она пришла из Telegram */
    public void finished(StudioJob job, Path localResult) {
        if (!client.enabled() || job.getTelegramChatId() == null) {
            return;
        }
        try {
            if (job.getStatus() == StudioJobStatus.DONE) {
                deliver(job, job.getTelegramChatId(), job.getTelegramReplyTo(), localResult);
            } else if (job.getStatus() == StudioJobStatus.FAILED) {
                Object markup = job.getInputsUntil() != null
                        ? keyboard(List.of(List.of(button("Повторить", "r:" + job.getId())))) : null;
                client.sendMessage(job.getTelegramChatId(),
                        "Не получилось собрать <b>" + escape(job.getFilename()) + "</b>.\n" + escape(job.getError()),
                        markup, job.getTelegramReplyTo());
            }
        } catch (RuntimeException e) {
            log.warn("Видео-студия: не удалось уведомить чат {} о задаче {}: {}", job.getTelegramChatId(), job.getId(), e.getMessage());
        }
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
