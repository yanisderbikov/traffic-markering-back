package ru.trafficmarkering.service.email.impl;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import ru.trafficmarkering.service.email.EmailService;

import java.nio.charset.StandardCharsets;
import java.util.UUID;

/**
 * Отправка через anyforms-email-service: письмо ставится в его очередь, доставляет Postbox.
 * Включается флагом REMOTE_EMAIL_SERVICE_ENABLED; иначе работает {@link NotiSendEmailService}.
 * Под флагом без EMAIL_SERVICE_URL или EMAIL_SERVICE_TOKEN бэк не стартует: молча уйти на NotiSend хуже.
 */
@Log4j2
@Component
@ConditionalOnProperty(name = "email.remote.enabled", havingValue = "true")
class AnyformsEmailService implements EmailService {

    private static final String CLIENT_ID = "traffic-markering-back";
    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS = 10_000;
    private static final int MAX_ATTEMPTS = 3;

    private final RestClient restClient;

    AnyformsEmailService(@Value("${email.service.url}") String baseUrl,
                         @Value("${email.service.token}") String token) {
        if (baseUrl == null || baseUrl.isBlank() || token == null || token.isBlank()) {
            throw new IllegalStateException(
                    "REMOTE_EMAIL_SERVICE_ENABLED=true, но EMAIL_SERVICE_URL или EMAIL_SERVICE_TOKEN не заданы");
        }
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        factory.setReadTimeout(READ_TIMEOUT_MS);
        this.restClient = RestClient.builder()
                .requestFactory(factory)
                .baseUrl(baseUrl)
                .defaultHeader(HttpHeaders.AUTHORIZATION, "Bearer " + token)
                .defaultHeader("X-Client-Id", CLIENT_ID)
                .build();
        log.info("Письма уходят через anyforms-email-service: {}", baseUrl);
    }

    @Override
    public void sendEmail(String to, String subject, String htmlBody) {
        // Один ключ на все повторы: сервис не создаст дубль, если первый запрос дошёл, а ответ потерялся
        String idempotencyKey = UUID.randomUUID().toString();
        SendRequest request = new SendRequest("SYSTEM", to, subject, htmlBody);
        RuntimeException last = null;
        for (int attempt = 1; attempt <= MAX_ATTEMPTS; attempt++) {
            try {
                SendResult result = restClient.post()
                        .uri("/api/v1/emails")
                        .header("Idempotency-Key", idempotencyKey)
                        .contentType(MediaType.APPLICATION_JSON)
                        .body(request)
                        .exchange((req, resp) -> new SendResult(
                                resp.getStatusCode().value(),
                                new String(resp.getBody().readAllBytes(), StandardCharsets.UTF_8)));
                if (result.status() >= 200 && result.status() < 300) {
                    log.info("Email-сервис принял письмо на {}: {}", to, result.body());
                    return;
                }
                last = new RuntimeException("Email-сервис вернул HTTP " + result.status() + ": " + result.body());
                if (result.status() < 500 && result.status() != 429) {
                    break;
                }
            } catch (ResourceAccessException e) {
                last = e;
            }
            log.warn("Email-сервис: попытка {} отправки на {} не удалась: {}", attempt, to, last.getMessage());
        }
        throw last;
    }

    private record SendRequest(String type, String to, String subject, String html) {
    }

    private record SendResult(int status, String body) {
    }
}
