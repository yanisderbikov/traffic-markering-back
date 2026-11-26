package ru.trafficmarkering.util;

import java.net.IDN;
import java.net.URI;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Optional;
import java.util.regex.Pattern;

/**
 * Рекламная ссылка кампании: проверка того, что ввёл заказчик, и сравнение с тем, что стоит в профиле криатора.
 * Сравниваются разобранные адреса — хост, путь и обязательные параметры, — а не начало строки:
 * {@code example.com.evil} не равен {@code example.com}, и {@code example.com/a} не равен {@code example.com/ab}.
 * Обёртки площадок (l.instagram.com/?u=…, youtube.com/redirect?q=…) разворачиваются до исходного адреса.
 */
public final class PromoLinks {

    private static final int MAX_LENGTH = 1024;
    private static final int MAX_UNWRAP = 3;
    private static final Pattern IPV4 = Pattern.compile("\\d{1,3}(\\.\\d{1,3}){3}");
    private static final Pattern PROMO_CODE = Pattern.compile("[\\p{L}\\p{N}_\\-]{2,64}");

    private PromoLinks() {
    }

    /**
     * Ссылка заказчика пригодна для кампании: HTTPS, нормальный публичный хост, без логина и пароля в адресе.
     *
     * @return сообщение об ошибке; empty — ссылка в порядке
     */
    public static Optional<String> validateCampaignUrl(String url) {
        if (url == null || url.isBlank()) {
            return Optional.of("Укажите рекламную ссылку");
        }
        String trimmed = url.trim();
        if (trimmed.length() > MAX_LENGTH) {
            return Optional.of("Рекламная ссылка не длиннее " + MAX_LENGTH + " символов");
        }
        URI uri;
        try {
            uri = new URI(trimmed);
        } catch (Exception e) {
            return Optional.of("Рекламная ссылка не похожа на адрес страницы");
        }
        if (!"https".equalsIgnoreCase(uri.getScheme())) {
            return Optional.of("Рекламная ссылка должна начинаться с https://");
        }
        if (uri.getRawUserInfo() != null) {
            return Optional.of("В рекламной ссылке не должно быть логина и пароля");
        }
        String host = normalizeHost(uri.getHost());
        if (host == null || !host.contains(".") || host.startsWith(".") || host.endsWith(".")) {
            return Optional.of("В рекламной ссылке нет адреса сайта");
        }
        if (IPV4.matcher(host).matches() || host.contains(":") || host.equals("localhost") || host.endsWith(".localhost")
                || host.endsWith(".local") || host.endsWith(".internal")) {
            return Optional.of("Рекламная ссылка должна вести на публичный сайт, а не на IP-адрес или внутренний хост");
        }
        return Optional.empty();
    }

    /**
     * Промокод: буквы, цифры, дефис и подчёркивание, от 2 до 64 символов, без пробелов.
     *
     * @return сообщение об ошибке; empty — код в порядке
     */
    public static Optional<String> validatePromoCode(String code) {
        if (code == null || code.isBlank()) {
            return Optional.of("Укажите промокод");
        }
        if (!PROMO_CODE.matcher(code.trim()).matches()) {
            return Optional.of("Промокод — от 2 до 64 букв и цифр, можно дефис и подчёркивание, без пробелов");
        }
        return Optional.empty();
    }

    /** Есть ли среди ссылок профиля рекламная ссылка кампании. */
    public static boolean containsMatch(String expected, List<String> observed) {
        if (observed == null) {
            return false;
        }
        return observed.stream().anyMatch(link -> matches(expected, link));
    }

    /**
     * Ссылка из профиля ведёт туда же, куда рекламная: тот же хост (www. не в счёт), тот же путь
     * (закрывающий слэш не в счёт) и все параметры рекламной ссылки с теми же значениями.
     * Лишние параметры в профиле допустимы: площадки и сокращатели дописывают свои метки.
     */
    public static boolean matches(String expected, String observed) {
        Parsed want = parse(expected);
        Parsed got = parse(unwrap(observed));
        if (want == null || got == null) {
            return false;
        }
        if (!want.host().equals(got.host()) || !want.path().equals(got.path())) {
            return false;
        }
        for (Map.Entry<String, String> param : want.query().entrySet()) {
            if (!param.getValue().equals(got.query().get(param.getKey()))) {
                return false;
            }
        }
        return true;
    }

    /** Обёртки переходов площадок — до исходного адреса. Чужие сокращатели не открываем. */
    static String unwrap(String link) {
        String current = link;
        for (int i = 0; i < MAX_UNWRAP && current != null; i++) {
            Parsed parsed = parse(current);
            if (parsed == null) {
                return current;
            }
            String target = switch (parsed.host()) {
                case "l.instagram.com", "l.facebook.com", "lm.facebook.com" -> parsed.query().get("u");
                case "youtube.com", "m.youtube.com" -> "/redirect".equals(parsed.path()) ? parsed.query().get("q") : null;
                default -> null;
            };
            if (target == null || target.isBlank()) {
                return current;
            }
            current = target;
        }
        return current;
    }

    private static Parsed parse(String link) {
        if (link == null || link.isBlank()) {
            return null;
        }
        String raw = link.trim();
        if (!raw.contains("://")) {
            raw = "https://" + raw;
        }
        URI uri;
        try {
            uri = new URI(raw);
        } catch (Exception e) {
            return null;
        }
        String scheme = uri.getScheme() == null ? "" : uri.getScheme().toLowerCase(Locale.ROOT);
        if (!scheme.equals("https") && !scheme.equals("http")) {
            return null;
        }
        String host = normalizeHost(uri.getHost());
        if (host == null) {
            return null;
        }
        String path = uri.getRawPath() == null || uri.getRawPath().isEmpty() ? "/" : uri.getRawPath();
        if (path.length() > 1 && path.endsWith("/")) {
            path = path.substring(0, path.length() - 1);
        }
        return new Parsed(host, decode(path), query(uri.getRawQuery()));
    }

    private static String normalizeHost(String host) {
        if (host == null || host.isBlank()) {
            return null;
        }
        String lower;
        try {
            lower = IDN.toASCII(host.trim(), IDN.ALLOW_UNASSIGNED).toLowerCase(Locale.ROOT);
        } catch (IllegalArgumentException e) {
            return null;
        }
        return lower.startsWith("www.") ? lower.substring(4) : lower;
    }

    private static Map<String, String> query(String rawQuery) {
        Map<String, String> params = new LinkedHashMap<>();
        if (rawQuery == null || rawQuery.isEmpty()) {
            return params;
        }
        List<String> pairs = new ArrayList<>(List.of(rawQuery.split("&")));
        for (String pair : pairs) {
            if (pair.isEmpty()) {
                continue;
            }
            int eq = pair.indexOf('=');
            String key = decode(eq >= 0 ? pair.substring(0, eq) : pair);
            String value = eq >= 0 ? decode(pair.substring(eq + 1)) : "";
            params.putIfAbsent(key, value);
        }
        return params;
    }

    private static String decode(String value) {
        try {
            return URLDecoder.decode(value.replace("+", "%2B"), StandardCharsets.UTF_8);
        } catch (IllegalArgumentException e) {
            return value;
        }
    }

    private record Parsed(String host, String path, Map<String, String> query) {
    }
}
