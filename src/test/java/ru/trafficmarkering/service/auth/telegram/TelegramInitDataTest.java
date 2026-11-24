package ru.trafficmarkering.service.auth.telegram;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import ru.trafficmarkering.config.StudioProperties;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.HexFormat;
import java.util.Map;
import java.util.TreeMap;
import java.util.stream.Collectors;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class TelegramInitDataTest {

    private static final String TOKEN = "123456:TEST-token";
    private static final long NOW = 1_800_000_000L;

    private final TelegramInitData verifier = verifier(TOKEN);

    private static TelegramInitData verifier(String token) {
        StudioProperties properties = new StudioProperties();
        properties.getTelegram().setBotToken(token);
        return new TelegramInitData(properties, new ObjectMapper(),
                Clock.fixed(Instant.ofEpochSecond(NOW), ZoneOffset.UTC));
    }

    /** Подписывает поля так же, как это делает Telegram */
    private static String signed(Map<String, String> fields, String token) {
        Map<String, String> sorted = new TreeMap<>(fields);
        String check = sorted.entrySet().stream().map(e -> e.getKey() + "=" + e.getValue())
                .collect(Collectors.joining("\n"));
        byte[] secret = TelegramInitData.hmac("WebAppData".getBytes(StandardCharsets.UTF_8),
                token.getBytes(StandardCharsets.UTF_8));
        String hash = HexFormat.of().formatHex(TelegramInitData.hmac(secret, check.getBytes(StandardCharsets.UTF_8)));
        return fields.entrySet().stream()
                .map(e -> e.getKey() + "=" + URLEncoder.encode(e.getValue(), StandardCharsets.UTF_8))
                .collect(Collectors.joining("&")) + "&hash=" + hash;
    }

    private static Map<String, String> fields(long authDate) {
        return Map.of(
                "query_id", "AAE",
                "user", "{\"id\":555,\"first_name\":\"Аня\",\"last_name\":\"К\",\"username\":\"anya\"}",
                "auth_date", String.valueOf(authDate),
                "start_param", "studio");
    }

    @Test
    void acceptsDataSignedByOurBot() {
        var launch = verifier.verify(signed(fields(NOW - 60), TOKEN));
        assertThat(launch.user().id()).isEqualTo(555L);
        assertThat(launch.user().username()).isEqualTo("anya");
        assertThat(launch.user().displayName()).isEqualTo("Аня К");
        assertThat(launch.startParam()).isEqualTo("studio");
    }

    @Test
    void rejectsOtherBotOrTamperedData() {
        assertThatThrownBy(() -> verifier.verify(signed(fields(NOW), "999:other")))
                .isInstanceOf(ResponseStatusException.class);
        String tampered = signed(fields(NOW), TOKEN).replace("555", "556");
        assertThatThrownBy(() -> verifier.verify(tampered)).isInstanceOf(ResponseStatusException.class);
        assertThatThrownBy(() -> verifier.verify("user=x")).isInstanceOf(ResponseStatusException.class);
    }

    @Test
    void rejectsStaleData() {
        assertThatThrownBy(() -> verifier.verify(signed(fields(NOW - TelegramInitData.MAX_AGE.getSeconds() - 1), TOKEN)))
                .isInstanceOf(ResponseStatusException.class)
                .hasMessageContaining("устарела");
    }
}
