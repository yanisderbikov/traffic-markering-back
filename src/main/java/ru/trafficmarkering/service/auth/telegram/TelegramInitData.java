package ru.trafficmarkering.service.auth.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import ru.trafficmarkering.config.StudioProperties;
import ru.trafficmarkering.service.auth.telegram.TelegramAuthService.TgUser;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

/**
 * Проверка initData мини-приложения Telegram: подпись HMAC-SHA256 ключом от токена бота
 * (core.telegram.org/bots/webapps#validating-data-received-via-the-mini-app) и свежесть auth_date.
 */
@Component
public class TelegramInitData {

    /** Мини-приложение могут держать открытым долго, но старые initData не принимаем */
    static final Duration MAX_AGE = Duration.ofHours(24);

    public record Launch(TgUser user, String startParam) {
    }

    private final StudioProperties.Telegram config;
    private final ObjectMapper json;
    private final Clock clock;

    public TelegramInitData(StudioProperties properties, ObjectMapper json) {
        this(properties, json, Clock.systemUTC());
    }

    TelegramInitData(StudioProperties properties, ObjectMapper json, Clock clock) {
        this.config = properties.getTelegram();
        this.json = json;
        this.clock = clock;
    }

    public Launch verify(String initData) {
        if (!config.enabled()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Вход через Telegram временно недоступен.");
        }
        if (initData == null || initData.isBlank() || initData.length() > 8192) {
            throw invalid();
        }
        Map<String, String> fields = new TreeMap<>();
        for (String pair : initData.split("&")) {
            int eq = pair.indexOf('=');
            if (eq <= 0) {
                continue;
            }
            fields.put(URLDecoder.decode(pair.substring(0, eq), StandardCharsets.UTF_8),
                    URLDecoder.decode(pair.substring(eq + 1), StandardCharsets.UTF_8));
        }
        String hash = fields.remove("hash");
        if (hash == null) {
            throw invalid();
        }
        String dataCheck = fields.entrySet().stream()
                .map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("\n"));
        byte[] expected = hmac(hmac("WebAppData".getBytes(StandardCharsets.UTF_8),
                config.getBotToken().strip().getBytes(StandardCharsets.UTF_8)), dataCheck.getBytes(StandardCharsets.UTF_8));
        byte[] given;
        try {
            given = HexFormat.of().parseHex(hash);
        } catch (IllegalArgumentException e) {
            throw invalid();
        }
        if (!MessageDigest.isEqual(expected, given)) {
            throw invalid();
        }
        long authDate;
        try {
            authDate = Long.parseLong(fields.getOrDefault("auth_date", ""));
        } catch (NumberFormatException e) {
            throw invalid();
        }
        if (Instant.ofEpochSecond(authDate).plus(MAX_AGE).isBefore(clock.instant())) {
            throw new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                    "Сессия мини-приложения устарела — закройте его и откройте заново.");
        }
        JsonNode user;
        try {
            user = json.readTree(fields.getOrDefault("user", ""));
        } catch (Exception e) {
            throw invalid();
        }
        if (user == null || !user.path("id").canConvertToLong() || user.path("id").asLong() <= 0) {
            throw invalid();
        }
        TgUser tg = new TgUser(user.path("id").asLong(), user.path("username").asText(null),
                user.path("first_name").asText(null), user.path("last_name").asText(null));
        return new Launch(tg, fields.get("start_param"));
    }

    static byte[] hmac(byte[] key, byte[] data) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(new SecretKeySpec(key, "HmacSHA256"));
            return mac.doFinal(data);
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException(e);
        }
    }

    private static ResponseStatusException invalid() {
        return new ResponseStatusException(HttpStatus.UNAUTHORIZED, "Не удалось проверить вход из Telegram.");
    }
}
