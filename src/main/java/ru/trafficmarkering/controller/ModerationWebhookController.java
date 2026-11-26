package ru.trafficmarkering.controller;

import com.fasterxml.jackson.databind.JsonNode;
import io.swagger.v3.oas.annotations.Hidden;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RestController;
import ru.trafficmarkering.service.moderation.ModerationBot;
import ru.trafficmarkering.service.moderation.ModerationTelegram;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;

/** Вебхук бота модерации; запрос без верного X-Telegram-Bot-Api-Secret-Token отбрасывается */
@Hidden
@RestController
@RequiredArgsConstructor
public class ModerationWebhookController {

    private final ModerationTelegram telegram;
    private final ModerationBot bot;

    @PostMapping(ModerationTelegram.WEBHOOK_PATH)
    public ResponseEntity<Void> update(@RequestHeader(value = "X-Telegram-Bot-Api-Secret-Token", required = false) String secret,
                                       @RequestBody JsonNode update) {
        if (!telegram.enabled()) {
            return ResponseEntity.notFound().build();
        }
        if (secret == null || !MessageDigest.isEqual(secret.getBytes(StandardCharsets.UTF_8),
                telegram.webhookSecret().getBytes(StandardCharsets.UTF_8))) {
            return ResponseEntity.status(401).build();
        }
        bot.accept(update);
        return ResponseEntity.ok().build();
    }
}
