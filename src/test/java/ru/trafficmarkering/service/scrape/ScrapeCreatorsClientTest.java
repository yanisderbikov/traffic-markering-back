package ru.trafficmarkering.service.scrape;

import com.sun.net.httpserver.HttpServer;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.util.Map;
import java.util.concurrent.atomic.AtomicReference;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ScrapeCreatorsClientTest {

    private HttpServer server;
    private final AtomicReference<String> rawQuery = new AtomicReference<>();
    private final AtomicReference<String> apiKey = new AtomicReference<>();
    private volatile int status = 200;
    private volatile String body = "{\"success\":true,\"credits_remaining\":10,\"credits_charged\":1,\"id\":\"x\"}";

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/", exchange -> {
            rawQuery.set(exchange.getRequestURI().getRawQuery());
            apiKey.set(exchange.getRequestHeaders().getFirst("x-api-key"));
            byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
            exchange.getResponseHeaders().add("Content-Type", "application/json");
            exchange.sendResponseHeaders(status, bytes.length);
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        });
        server.start();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private ScrapeCreatorsClient client(String key) {
        return new ScrapeCreatorsClient("http://127.0.0.1:" + server.getAddress().getPort() + "/", key);
    }

    @Test
    void sendsKeyHeaderAndEncodesUrlParameterWhole() {
        Map<String, Object> response = client("secret-key")
                .get("/v2/tiktok/video", Map.of("url", "https://www.tiktok.com/@a/video/1?is_from_webapp=1&lang=ru"));

        assertThat(response.get("id")).isEqualTo("x");
        assertThat(apiKey.get()).isEqualTo("secret-key");
        assertThat(rawQuery.get())
                .isEqualTo("url=https%3A%2F%2Fwww.tiktok.com%2F%40a%2Fvideo%2F1%3Fis_from_webapp%3D1%26lang%3Dru");
    }

    @Test
    void mapsErrorStatus() {
        status = 404;
        body = "{\"success\":false,\"message\":\"not_found\"}";

        assertThatThrownBy(() -> client("k").get("/v1/youtube/video", Map.of("url", "https://youtu.be/x")))
                .isInstanceOfSatisfying(ScrapeCreatorsException.class, e -> {
                    assertThat(e.notFound()).isTrue();
                    assertThat(e.fatal()).isFalse();
                });
    }

    @Test
    void outOfCreditsIsFatal() {
        status = 402;
        body = "{\"message\":\"Payment required\"}";

        assertThatThrownBy(() -> client("k").get("/v1/tiktok/profile", Map.of("handle", "a")))
                .isInstanceOfSatisfying(ScrapeCreatorsException.class, e -> assertThat(e.fatal()).isTrue());
    }

    @Test
    void unsuccessfulBodyIsAnError() {
        body = "{\"success\":false,\"error\":\"Profile is private\"}";

        assertThatThrownBy(() -> client("k").get("/v1/instagram/profile", Map.of("handle", "a")))
                .isInstanceOf(ScrapeCreatorsException.class)
                .hasMessage("Profile is private");
    }

    @Test
    void withoutKeyDoesNotCallServer() {
        ScrapeCreatorsClient client = client("");

        assertThat(client.isConfigured()).isFalse();
        assertThatThrownBy(() -> client.get("/v1/tiktok/profile", Map.of("handle", "a")))
                .isInstanceOf(ScrapeCreatorsException.class);
        assertThat(rawQuery.get()).isNull();
    }
}
