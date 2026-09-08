package ru.trafficmarkering.service.social.impl;

import lombok.extern.log4j.Log4j2;
import org.springframework.core.ParameterizedTypeReference;
import org.springframework.http.client.ClientHttpRequestFactory;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.MultiValueMap;
import org.springframework.util.StringUtils;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;

import java.util.Map;

@Component
@Log4j2
class SocialHttpClient {

    private static final ParameterizedTypeReference<Map<String, Object>> JSON_MAP =
            new ParameterizedTypeReference<>() {
            };

    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS = 10_000;

    private final RestClient restClient = RestClient.builder()
            .requestFactory(requestFactory())
            .build();

    private static ClientHttpRequestFactory requestFactory() {
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        factory.setReadTimeout(READ_TIMEOUT_MS);
        return factory;
    }

    Map<String, Object> postForm(String url, MultiValueMap<String, String> form, String platformName) {
        try {
            Map<String, Object> body = restClient.post()
                    .uri(url)
                    .contentType(MediaType.APPLICATION_FORM_URLENCODED)
                    .accept(MediaType.APPLICATION_JSON)
                    .body(form)
                    .retrieve()
                    .body(JSON_MAP);
            return requireBody(body, platformName);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("Обмен кода на токен {} не удался", platformName, e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    platformName + " не отдал токен доступа, попробуйте позже");
        }
    }

    Map<String, Object> getJson(String url, String bearerToken, String platformName) {
        try {
            RestClient.RequestHeadersSpec<?> spec = restClient.get()
                    .uri(url)
                    .accept(MediaType.APPLICATION_JSON);
            if (StringUtils.hasText(bearerToken)) {
                spec = spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + bearerToken);
            }
            return requireBody(spec.retrieve().body(JSON_MAP), platformName);
        } catch (ResponseStatusException e) {
            throw e;
        } catch (Exception e) {
            log.error("Запрос профиля {} не удался", platformName, e);
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    platformName + " не отдал данные аккаунта, попробуйте позже");
        }
    }

    private Map<String, Object> requireBody(Map<String, Object> body, String platformName) {
        if (body == null || body.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    platformName + " вернул пустой ответ, попробуйте позже");
        }
        return body;
    }
}
