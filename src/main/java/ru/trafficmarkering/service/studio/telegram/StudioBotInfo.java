package ru.trafficmarkering.service.studio.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import ru.trafficmarkering.config.StudioProperties;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.List;
import java.util.Map;

/**
 * Бот студии: имя, секрет вебхука и регистрация вебхука при старте.
 * Обновления Telegram приходят POST-запросами на /api/telegram/webhook — без long-polling.
 */
@Slf4j
@Component
public class StudioBotInfo {

    public static final String WEBHOOK_PATH = "/api/telegram/webhook";

    private final StudioProperties.Telegram config;
    private final TelegramClient client;
    private final String backUrl;
    private volatile String username;

    public StudioBotInfo(StudioProperties properties, TelegramClient client, @Value("${url.back}") String backUrl) {
        this.config = properties.getTelegram();
        this.client = client;
        this.backUrl = backUrl;
        String configured = config.getBotUsername();
        this.username = configured == null || configured.isBlank() ? null : configured.replace("@", "").strip();
    }

    public boolean enabled() {
        return config.enabled();
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
                log.warn("Telegram getMe: {}", e.getMessage());
            }
        }
        return username;
    }

    /** Секрет из настроек либо производный от токена (стабилен между перезапусками) */
    public String webhookSecret() {
        String secret = config.getWebhookSecret();
        if (secret != null && !secret.isBlank()) {
            return secret.strip();
        }
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(("offer-studio-webhook:" + config.getBotToken()).getBytes(StandardCharsets.UTF_8));
            return HexFormat.of().formatHex(digest).substring(0, 48);
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    public String webhookUrl() {
        String base = config.getWebhookBaseUrl();
        if (base == null || base.isBlank()) {
            base = backUrl;
        }
        return base.replaceAll("/+$", "") + WEBHOOK_PATH;
    }

    @Async
    @EventListener(ApplicationReadyEvent.class)
    public void register() {
        if (!enabled() || !config.isRegisterWebhook()) {
            log.info("Видео-студия: Telegram-бот выключен (нет STUDIO_TELEGRAM_BOT_TOKEN)");
            return;
        }
        String url = webhookUrl();
        if (!url.startsWith("https://")) {
            log.warn("Видео-студия: вебхук Telegram требует HTTPS, адрес {} пропущен", url);
            return;
        }
        try {
            client.call("setWebhook", Map.of(
                    "url", url,
                    "secret_token", webhookSecret(),
                    "allowed_updates", List.of("message", "callback_query"),
                    "max_connections", 20));
            client.call("setMyCommands", Map.of("commands", List.of(
                    Map.of("command", "start", "description", "Начать и привязать аккаунт"),
                    Map.of("command", "jobs", "description", "Очередь и прогресс"),
                    Map.of("command", "latest", "description", "Последний готовый ролик"),
                    Map.of("command", "history", "description", "История роликов"),
                    Map.of("command", "templates", "description", "Автошаблон для новых видео"),
                    Map.of("command", "cancel", "description", "Снять ожидающее видео"),
                    Map.of("command", "account", "description", "Привязка к кабинету"),
                    Map.of("command", "help", "description", "Как пользоваться"))));
            JsonNode me = client.call("getMe", Map.of());
            username = me.path("username").asText(username);
            log.info("Видео-студия: вебхук Telegram @{} → {}", username, url);
        } catch (RuntimeException e) {
            log.error("Видео-студия: не удалось зарегистрировать вебхук Telegram: {}", e.getMessage());
        }
    }
}
