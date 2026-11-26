package ru.trafficmarkering.service.moderation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import ru.trafficmarkering.config.ModerationProperties;
import ru.trafficmarkering.service.studio.telegram.TelegramClient;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * Бот модерации: свой токен (MODERATION_TELEGRAM_BOT_TOKEN), свой вебхук. Клиент тот же, что у бота студии,
 * но отдельным экземпляром — в контексте Spring он не бин, чтобы не путаться с клиентом студии.
 */
@Slf4j
@Component
public class ModerationTelegram {

    public static final String WEBHOOK_PATH = "/api/telegram/moderation/webhook";

    private final ModerationProperties properties;
    private final TelegramClient client;
    private final String backUrl;
    private volatile String username;

    public ModerationTelegram(ModerationProperties properties, ObjectMapper json, @Value("${url.back}") String backUrl) {
        this.properties = properties;
        this.client = new TelegramClient(properties.getTelegram(), json);
        this.backUrl = backUrl;
        String configured = properties.getTelegram().getBotUsername();
        this.username = configured == null || configured.isBlank() ? null : configured.replace("@", "").strip();
    }

    public boolean enabled() {
        return properties.getTelegram().enabled();
    }

    public TelegramClient client() {
        return client;
    }

    /** Имя бота без @; null — бот не настроен */
    public String username() {
        if (!enabled()) {
            return null;
        }
        if (username == null) {
            try {
                username = client.call("getMe", Map.of()).path("username").asText(null);
            } catch (RuntimeException e) {
                log.warn("Модерация: Telegram getMe: {}", e.getMessage());
            }
        }
        return username;
    }

    public String webhookSecret() {
        String secret = properties.getTelegram().getWebhookSecret();
        if (secret != null && !secret.isBlank()) {
            return secret.strip();
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(("offer-moderation-webhook:" + properties.getTelegram().getBotToken()).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 48);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public String webhookUrl() {
        String base = properties.getTelegram().getWebhookBaseUrl();
        if (base == null || base.isBlank()) {
            base = backUrl;
        }
        return base.replaceAll("/+$", "") + WEBHOOK_PATH;
    }

    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void register() {
        if (!enabled() || !properties.getTelegram().isRegisterWebhook()) {
            log.info("Модерация: Telegram-бот выключен (нет MODERATION_TELEGRAM_BOT_TOKEN)");
            return;
        }
        String url = webhookUrl();
        if (!url.startsWith("https://")) {
            log.warn("Модерация: вебхук Telegram требует HTTPS, адрес {} пропущен", url);
            return;
        }
        try {
            if (properties.getTelegram().localServer()) {
                client.logOutFromCloud();
            }
            client.call("setWebhook", Map.of(
                    "url", url,
                    "secret_token", webhookSecret(),
                    "allowed_updates", List.of("message", "callback_query"),
                    "max_connections", 20));
            client.call("setMyCommands", Map.of("commands", List.of(
                    Map.of("command", "start", "description", "Начать и статус"),
                    Map.of("command", "hours", "description", "Часы, когда приходят заявки"),
                    Map.of("command", "pause", "description", "Перерыв — заявки не приходят"),
                    Map.of("command", "resume", "description", "Снова принимать заявки"),
                    Map.of("command", "stats", "description", "Сколько проверено"),
                    Map.of("command", "help", "description", "Как это работает"))));
            JsonNode me = client.call("getMe", Map.of());
            username = me.path("username").asText(username);
            log.info("Модерация: вебхук Telegram @{} → {}", username, url);
        } catch (RuntimeException e) {
            log.error("Модерация: не удалось зарегистрировать вебхук Telegram: {}", e.getMessage());
        }
    }
}
