package ru.trafficmarkering.service.email.impl;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.client.SimpleClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import ru.trafficmarkering.service.email.EmailService;

import java.nio.charset.StandardCharsets;

@Log4j2
@Component
class NotiSendEmailService implements EmailService {

    private static final String NOTISEND_BASE_URL = "https://api.notisend.ru/v1";
    private static final int CONNECT_TIMEOUT_MS = 5_000;
    private static final int READ_TIMEOUT_MS = 10_000;

    private final RestClient restClient;
    private final String apiKey;
    private final String emailAddress;
    private final String fromName;

    NotiSendEmailService(@Value("${email.notisend.api.key}") String apiKey,
                         @Value("${email.notisend.email.address}") String emailAddress,
                         @Value("${email.notisend.from.name}") String fromName) {
        this.apiKey = apiKey;
        this.emailAddress = emailAddress;
        this.fromName = fromName;
        SimpleClientHttpRequestFactory factory = new SimpleClientHttpRequestFactory();
        factory.setConnectTimeout(CONNECT_TIMEOUT_MS);
        factory.setReadTimeout(READ_TIMEOUT_MS);
        this.restClient = RestClient.builder()
                .requestFactory(factory)
                .baseUrl(NOTISEND_BASE_URL)
                .build();
    }

    @Override
    public void sendEmail(String to, String subject, String htmlBody) {
        if (apiKey == null || apiKey.isBlank()) {
            log.warn("NotiSend не настроен (EMAIL_NOTISEND_API_KEY пуст). Письмо для {} «{}» не отправлено. Тело:\n{}",
                    to, subject, htmlBody);
            return;
        }
        send(new NotisendEmailRequest(emailAddress, fromName, to, subject, htmlBody));
    }

    private void send(NotisendEmailRequest request) {
        SendResult result = restClient.post()
                .uri("/email/messages")
                .header(HttpHeaders.AUTHORIZATION, "Bearer " + apiKey)
                .contentType(MediaType.APPLICATION_JSON)
                .body(request)
                .exchange((req, resp) -> new SendResult(
                        resp.getStatusCode().value(),
                        new String(resp.getBody().readAllBytes(), StandardCharsets.UTF_8)));

        if (result == null) {
            throw new RuntimeException("NotiSend: пустой ответ при отправке письма на " + request.to());
        }
        if (result.status() < 200 || result.status() >= 300) {
            log.error("NotiSend отклонил письмо на {}: HTTP {} body: {}", request.to(), result.status(), result.body());
            throw new RuntimeException("NotiSend вернул HTTP " + result.status() + ": " + result.body());
        }
        log.info("NotiSend принял письмо на {}: HTTP {}", request.to(), result.status());
    }

    private record NotisendEmailRequest(
            @JsonProperty("from_email") String fromEmail,
            @JsonProperty("from_name") String fromName,
            String to,
            String subject,
            String html
    ) {
    }

    private record SendResult(int status, String body) {
    }
}
