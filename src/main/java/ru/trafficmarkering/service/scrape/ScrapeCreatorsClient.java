package ru.trafficmarkering.service.scrape;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriComponentsBuilder;
import ru.trafficmarkering.service.http.JsonNode;

import java.net.URI;
import java.util.HashMap;
import java.util.Map;

/**
 * HTTP-клиент api.scrapecreators.com. Ключ — в заголовке x-api-key, все ручки — GET
 * с параметрами в query. Площадка отвечает медленно: документация просит ждать до 30 секунд.
 */
@Component
@Log4j2
public class ScrapeCreatorsClient {

    private static final ParameterizedTypeReference<Map<String, Object>> JSON_MAP =
            new ParameterizedTypeReference<>() {
            };
    private static final String API_KEY_HEADER = "x-api-key";
    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS = 45_000;
    private static final int MAX_ERROR_BODY = 300;

    private final RestClient restClient;
    private final String baseUrl;
    private final String apiKey;

    public ScrapeCreatorsClient(@Value("${social.scrape.base-url:https://api.scrapecreators.com}") String baseUrl,
                                @Value("${social.scrape.api-key:}") String apiKey) {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        factory.setReadTimeout(READ_TIMEOUT_MS);
        this.restClient = RestClient.builder().requestFactory(factory).build();
        this.baseUrl = baseUrl != null && baseUrl.endsWith("/") ? baseUrl.substring(0, baseUrl.length() - 1) : baseUrl;
        this.apiKey = apiKey;
    }

    public boolean isConfigured() {
        return StringUtils.hasText(apiKey) && StringUtils.hasText(baseUrl);
    }

    /**
     * GET-запрос к ручке. Параметры с null пропускаются, значения кодируются целиком —
     * ссылки на ролики с ? и & внутри уходят как есть.
     */
    public Map<String, Object> get(String path, Map<String, String> params) {
        if (!isConfigured()) {
            throw new ScrapeCreatorsException(0, "Ключ ScrapeCreators не задан");
        }
        URI uri = buildUri(path, params);
        Map<String, Object> body;
        try {
            body = restClient.get()
                    .uri(uri)
                    .header(API_KEY_HEADER, apiKey)
                    .accept(MediaType.APPLICATION_JSON)
                    .retrieve()
                    .body(JSON_MAP);
        } catch (RestClientResponseException e) {
            int status = e.getStatusCode().value();
            String reason = errorReason(e.getResponseBodyAsString());
            if (status == 402) {
                log.error("ScrapeCreators: закончились кредиты, {} не выполнен", path);
            } else if (status == 401 || status == 403 && reason.toLowerCase().contains("api key")) {
                log.error("ScrapeCreators не принял ключ: {} {}", status, reason);
            } else {
                log.warn("ScrapeCreators {} ответил {}: {}", path, status, reason);
            }
            throw new ScrapeCreatorsException(status, reason, e);
        } catch (Exception e) {
            log.warn("ScrapeCreators {} не ответил: {}", path, e.getMessage());
            throw new ScrapeCreatorsException(0, "ScrapeCreators не ответил: " + e.getMessage(), e);
        }

        if (body == null || body.isEmpty()) {
            throw new ScrapeCreatorsException(502, "ScrapeCreators вернул пустой ответ");
        }
        if (Boolean.FALSE.equals(body.get("success"))) {
            String reason = firstText(body, "message", "error");
            throw new ScrapeCreatorsException(422, reason != null ? reason : "ScrapeCreators не смог получить данные");
        }
        log.debug("ScrapeCreators {}: списано {}, осталось {}", path,
                JsonNode.number(body, "credits_charged"), JsonNode.number(body, "credits_remaining"));
        return body;
    }

    private URI buildUri(String path, Map<String, String> params) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(baseUrl).path(path);
        Map<String, String> variables = new HashMap<>();
        params.forEach((name, value) -> {
            if (value != null) {
                builder.queryParam(name, "{" + name + "}");
                variables.put(name, value);
            }
        });
        return builder.encode().buildAndExpand(variables).toUri();
    }

    private static String errorReason(String body) {
        if (body == null || body.isBlank()) {
            return "без описания";
        }
        String compact = body.replaceAll("\\s+", " ").trim();
        return compact.length() > MAX_ERROR_BODY ? compact.substring(0, MAX_ERROR_BODY) + "…" : compact;
    }

    private static String firstText(Map<String, Object> body, String... keys) {
        for (String key : keys) {
            String text = JsonNode.text(body, key);
            if (text != null) {
                return text;
            }
        }
        return null;
    }
}
