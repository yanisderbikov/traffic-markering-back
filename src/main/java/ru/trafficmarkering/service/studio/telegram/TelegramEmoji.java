package ru.trafficmarkering.service.studio.telegram;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;

/**
 * Иконки бота из пака t.me/addemoji/tgiosicons. В коде пишем обычные эмодзи, перед отправкой они
 * превращаются в кастомные: в тексте — тег tg-emoji (обычный эмодзи остаётся запасным), в кнопке —
 * icon_custom_emoji_id. Telegram показывает их, если у владельца бота есть Premium; иначе — обычные эмодзи.
 *
 * Разметка двух уровней: ручная (PACK) подобрана по смыслу и всегда главнее; остальное подгружается из самого
 * пака при старте ({@link TelegramEmojiPack}) — каждой иконке пака Telegram сопоставляет обычный эмодзи.
 */
final class TelegramEmoji {

    /** Эмодзи без U+FE0F → custom_emoji_id из пака, подобранные вручную */
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

    /** В тексте не трогаем: ★ ☆ ✓ — типографика, остальное — обычные символы, которые Telegram считает эмодзи */
    private static final Set<String> TEXT_EXCLUDED = Set.of("✓", "★", "☆", "©", "®", "™", "#", "*",
            "0", "1", "2", "3", "4", "5", "6", "7", "8", "9");

    /** Текущая разметка: ручная плюс подгруженная из пака; меняется целиком, чтобы потоки видели её согласованной */
    private record Mapping(Map<String, String> ids, Pattern text, Pattern button) {

        static Mapping of(Map<String, String> ids) {
            // длинные последовательности первыми: 👨‍💻 не должен разобраться как 👨 и хвост
            List<String> keys = ids.keySet().stream()
                    .sorted(Comparator.comparingInt(String::length).reversed())
                    .toList();
            Pattern text = Pattern.compile(keys.stream()
                    .filter(e -> !TEXT_EXCLUDED.contains(e))
                    .map(Pattern::quote)
                    .collect(Collectors.joining("|", "(?:", ")️?")));
            Pattern button = Pattern.compile(keys.stream()
                    .filter(e -> !TEXT_EXCLUDED.contains(e) || List.of("✓", "★", "☆").contains(e))
                    .map(Pattern::quote)
                    .collect(Collectors.joining("|", "^(", ")️?(?:\\s+|$)")));
            return new Mapping(Map.copyOf(ids), text, button);
        }
    }

    private static volatile Mapping mapping = Mapping.of(PACK);

    private TelegramEmoji() {
    }

    /** Эмодзи → id из пака, подгруженные из Telegram; ручная разметка не перезаписывается. Возвращает, сколько добавлено */
    static int register(Map<String, String> loaded) {
        Map<String, String> merged = new LinkedHashMap<>(PACK);
        int added = 0;
        for (Map.Entry<String, String> entry : loaded.entrySet()) {
            String emoji = normalize(entry.getKey());
            if (emoji.isEmpty() || entry.getValue() == null || entry.getValue().isBlank()
                    || TEXT_EXCLUDED.contains(emoji) || merged.containsKey(emoji)) {
                continue;
            }
            merged.put(emoji, entry.getValue());
            added++;
        }
        mapping = Mapping.of(merged);
        return added;
    }

    /** Для тестов: только ручная разметка */
    static void reset() {
        mapping = Mapping.of(PACK);
    }

    static boolean known(String emoji) {
        return mapping.ids().containsKey(normalize(emoji));
    }

    private static String normalize(String emoji) {
        return emoji == null ? "" : emoji.replace("️", "").strip();
    }

    /** HTML с parse_mode=HTML: эмодзи пака → tg-emoji */
    static String html(String html) {
        if (html == null || html.isEmpty()) {
            return html;
        }
        Mapping current = mapping;
        Matcher m = current.text().matcher(html);
        StringBuilder out = new StringBuilder();
        while (m.find()) {
            String emoji = m.group();
            String id = current.ids().get(normalize(emoji));
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
        Mapping current = mapping;
        Matcher m = current.button().matcher(text);
        if (!m.find()) {
            return button;
        }
        String rest = text.substring(m.end());
        // кнопка из одного символа (★ / ☆ избранного): пустую подпись Telegram не примет — оставляем как есть
        if (rest.isBlank()) {
            return button;
        }
        Map<String, Object> result = new LinkedHashMap<>(button);
        result.put("icon_custom_emoji_id", current.ids().get(normalize(m.group(1))));
        result.put("text", rest);
        return result;
    }
}
