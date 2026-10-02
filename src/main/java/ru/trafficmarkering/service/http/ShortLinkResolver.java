package ru.trafficmarkering.service.http;

import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;

import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;

@Component
@Log4j2
public class ShortLinkResolver {

    private static final int MAX_HOPS = 3;
    private static final Duration TIMEOUT = Duration.ofSeconds(5);

    private final HttpClient client = HttpClient.newBuilder()
            .followRedirects(HttpClient.Redirect.NEVER)
            .connectTimeout(TIMEOUT)
            .build();

    public String resolve(String url) {
        String current = url;
        for (int hop = 0; hop < MAX_HOPS; hop++) {
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(current))
                        .method("HEAD", HttpRequest.BodyPublishers.noBody())
                        .timeout(TIMEOUT)
                        .build();
                HttpResponse<Void> response = client.send(request, HttpResponse.BodyHandlers.discarding());
                if (response.statusCode() / 100 != 3) {
                    return current;
                }
                String location = response.headers().firstValue("location").orElse(null);
                if (location == null || location.isBlank()) {
                    return current;
                }
                current = URI.create(current).resolve(location).toString();
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return current;
            } catch (Exception e) {
                log.debug("Короткая ссылка {} не развернулась: {}", current, e.getMessage());
                return current;
            }
        }
        return current;
    }
}
