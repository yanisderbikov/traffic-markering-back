package ru.trafficmarkering.service.studio.telegram;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Иконки бота из пака t.me/addemoji/tgiosicons. В коде пишем обычные эмодзи, перед отправкой они
 * превращаются в кастомные: в тексте — тег tg-emoji (обычный эмодзи остаётся запасным), в кнопке —
 * icon_custom_emoji_id. Telegram показывает их, если у владельца бота есть Premium; иначе — обычные эмодзи.
 */
final class TelegramEmoji {

    /** Эмодзи без U+FE0F → custom_emoji_id из пака */
    private static final Map<String, String> PACK = new LinkedHashMap<>();

    static {
        PACK.put("✅", "5774022692642492953");
        PACK.put("✓", "5774022692642492953");
        PACK.put("❌", "5774077015388852135");
        PACK.put("⚙", "6032742198179532882");
        PACK.put("🔐", "5778570255555105942");
        PACK.put("🎬", "5937999673510858217");
        PACK.put("📱", "5942734685976138521");
        PACK.put("👋", "5985478698722136468");
        PACK.put("★", "6028338546736107668");
        PACK.put("☆", "6034923938486684992");
    }

    /** Символы, которые в тексте ведут себя как эмодзи, — в tg-emoji; ★ ☆ ✓ в тексте оставляем типографикой */
    private static final Pattern TEXT = Pattern.compile(PACK.keySet().stream()
            .filter(e -> !List.of("✓", "★", "☆").contains(e))
            .map(Pattern::quote)
            .collect(Collectors.joining("|", "(?:", ")️?")));

    /** Эмодзи в начале подписи кнопки */
    private static final Pattern BUTTON = Pattern.compile(PACK.keySet().stream()
            .map(Pattern::quote)
            .collect(Collectors.joining("|", "^(", ")️?(?:\\s+|$)")));

    private TelegramEmoji() {
    }

    /** HTML с parse_mode=HTML: эмодзи пака → tg-emoji */
    static String html(String html) {
        if (html == null || html.isEmpty()) {
            return html;
        }
        Matcher m = TEXT.matcher(html);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String emoji = m.group();
            String id = PACK.get(emoji.replace("️", ""));
            m.appendReplacement(out, Matcher.quoteReplacement("<tg-emoji emoji-id=\"" + id + "\">" + emoji + "</tg-emoji>"));
        }
        m.appendTail(out);
        return out.toString();
    }

    /** reply_markup: ведущий эмодзи подписи кнопки → icon_custom_emoji_id; остальное как было */
    @SuppressWarnings("unchecked")
    static Object markup(Object markup) {
        if (!(markup instanceof Map<?, ?> map) || !(map.get("inline_keyboard") instanceof List<?> rows)) {
            return markup;
        }
        List<List<Map<String, Object>>> converted = new ArrayList<>();
        for (Object row : rows) {
            List<Map<String, Object>> buttons = new ArrayList<>();
            for (Object button : (List<?>) row) {
                buttons.add(button((Map<String, Object>) button));
            }
            converted.add(buttons);
        }
        Map<String, Object> result = new HashMap<>((Map<String, Object>) map);
        result.put("inline_keyboard", converted);
        return result;
    }

    private static Map<String, Object> button(Map<String, Object> button) {
        if (button.containsKey("icon_custom_emoji_id") || !(button.get("text") instanceof String text)) {
            return button;
        }
        Matcher m = BUTTON.matcher(text);
        if (!m.find()) {
            return button;
        }
        String rest = text.substring(m.end());
        // кнопка из одного символа (★ / ☆ избранного): пустую подпись Telegram не примет — оставляем как есть
        if (rest.isBlank()) {
            return button;
        }
        Map<String, Object> result = new LinkedHashMap<>(button);
        result.put("icon_custom_emoji_id", PACK.get(m.group(1)));
        result.put("text", rest);
        return result;
    }
}
