package ru.trafficmarkering.service.studio.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import ru.trafficmarkering.config.StudioProperties;

import java.util.LinkedHashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;

/**
 * Подгружает пак иконок из Telegram (getStickerSet) и отдаёт его {@link TelegramEmoji}: так премиум-иконку
 * получает любой эмодзи, для которого в паке есть иконка, а не только размеченные вручную. Пак общий для
 * обоих ботов; грузится один раз, тем ботом, у которого есть токен. Не загрузился — работает ручная разметка.
 */
@Slf4j
@Component
public class TelegramEmojiPack {

    private final TelegramClient studioClient;
    private final StudioProperties.Telegram studioConfig;
    private final AtomicBoolean loaded = new AtomicBoolean();

    public TelegramEmojiPack(TelegramClient studioClient, StudioProperties properties) {
        this.studioClient = studioClient;
        this.studioConfig = properties.getTelegram();
    }

    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void loadOnStart() {
        if (studioConfig.enabled() && studioConfig.isCustomEmoji()) {
            load(studioClient);
        }
    }

    /** Загрузить через этого бота, если пак ещё не загружен; другие боты зовут это при старте */
    public void loadWith(TelegramClient client) {
        if (!loaded.get()) {
            load(client);
        }
    }

    private synchronized void load(TelegramClient client) {
        String name = studioConfig.getEmojiPack();
        if (loaded.get() || name == null || name.isBlank()) {
            return;
        }
        try {
            JsonNode set = client.call("getStickerSet", Map.of("name", name.strip()));
            int added = TelegramEmoji.register(parse(set));
            loaded.set(true);
            log.info("Telegram: пак иконок {} загружен, добавлено эмодзи сверх ручной разметки: {}", name, added);
        } catch (RuntimeException e) {
            log.warn("Telegram: пак иконок {} не загрузился ({}), работает ручная разметка", name, e.getMessage());
        }
    }

    /** Эмодзи → custom_emoji_id; на один эмодзи в паке бывает несколько иконок — берём первую */
    static Map<String, String> parse(JsonNode set) {
        Map<String, String> result = new LinkedHashMap<>();
        for (JsonNode sticker : set.path("stickers")) {
            String emoji = sticker.path("emoji").asText("");
            String id = sticker.path("custom_emoji_id").asText("");
            if (!emoji.isBlank() && !id.isBlank()) {
                result.putIfAbsent(emoji, id);
            }
        }
        return result;
    }
}
