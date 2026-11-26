package ru.trafficmarkering.service.studio.telegram;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class TelegramEmojiTest {

    @Test
    void textEmojiBecomeTgEmojiWithFallback() {
        assertThat(TelegramEmoji.html("⚙️ Собираю <b>a.mp4</b>"))
                .isEqualTo("<tg-emoji emoji-id=\"6032742198179532882\">⚙️</tg-emoji> Собираю <b>a.mp4</b>");
        assertThat(TelegramEmoji.html("✅ Готово")).startsWith("<tg-emoji emoji-id=\"5774022692642492953\">✅</tg-emoji>");
        assertThat(TelegramEmoji.html("⏸ пауза ★")).isEqualTo("⏸ пауза ★");
    }

    @Test
    @SuppressWarnings("unchecked")
    void buttonLeadingEmojiBecomesIcon() {
        Object markup = TelegramEmoji.markup(TelegramClient.keyboard(List.of(
                List.of(TelegramClient.appButton("📱 Открыть Offer", "https://x"), TelegramClient.button("★", "f:1")),
                List.of(TelegramClient.button("Отмена", "no")))));
        List<List<Map<String, Object>>> rows = (List<List<Map<String, Object>>>) ((Map<String, Object>) markup).get("inline_keyboard");
        assertThat(rows.get(0).get(0)).containsEntry("text", "Открыть Offer")
                .containsEntry("icon_custom_emoji_id", "5942734685976138521")
                .containsKey("web_app");
        assertThat(rows.get(0).get(1)).isEqualTo(Map.of("text", "★", "callback_data", "f:1"));
        assertThat(rows.get(1).get(0)).isEqualTo(Map.of("text", "Отмена", "callback_data", "no"));
    }
}
