package ru.trafficmarkering.controller;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import ru.trafficmarkering.service.studio.telegram.StudioBot;
import ru.trafficmarkering.service.studio.telegram.StudioBotInfo;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/**
 * Вебхук Telegram-бота видео-студии. Telegram подписывает каждый запрос заголовком
 * X-Telegram-Bot-Api-Secret-Token — без него запрос отбрасывается.
 */
@Hidden
@RestController
@RequiredArgsConstructor
public class TelegramWebhookController {

    private final StudioBotInfo botInfo;
    private final StudioBot bot;

    @PostMapping(StudioBotInfo.WEBHOOK_PATH)
    public ResponseEntity<Void> update(@RequestHeader(value = "X-Telegram-Bot-Api-Secret-Token", required = false) String secret,
                                       @RequestBody JsonNode update) {
        if (!botInfo.enabled()) {
            return ResponseEntity.notFound().build();
        }
        if (secret == null || !MessageDigest.isEqual(secret.getBytes(StandardCharsets.UTF_8),
                botInfo.webhookSecret().getBytes(StandardCharsets.UTF_8))) {
            return ResponseEntity.status(401).build();
        }
        bot.accept(update);
        return ResponseEntity.ok().build();
    }
}
