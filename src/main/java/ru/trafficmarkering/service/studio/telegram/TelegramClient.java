package ru.trafficmarkering.service.studio.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import ru.trafficmarkering.config.StudioProperties;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.UUID;

/** Тонкий клиент Bot API. Обновления приходят вебхуком, здесь только исходящие вызовы. */
@Slf4j
@Component
public class TelegramClient {

    /** Ошибка Bot API с описанием от Telegram */
    public static class TelegramException extends RuntimeException {
        private final int code;

        public TelegramException(int code, String description) {
            super(description);
            this.code = code;
        }

        public int getCode() {
            return code;
        }
    }

    private final StudioProperties.Telegram config;
    private final ObjectMapper json;
    private final HttpClient http = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(10))
            .followRedirects(HttpClient.Redirect.NORMAL)
            .build();

    public TelegramClient(StudioProperties properties, ObjectMapper json) {
        this.config = properties.getTelegram();
        this.json = json;
    }

    public boolean enabled() {
        return config.enabled();
    }

    private String base() {
        return config.getApiUrl().replaceAll("/+$", "") + "/bot" + config.getBotToken();
    }

    public JsonNode call(String method, Map<String, ?> params) {
        try {
            String body = json.writeValueAsString(params == null ? Map.of() : params);
            HttpRequest request = HttpRequest.newBuilder(URI.create(base() + "/" + method))
                    .timeout(Duration.ofSeconds(40))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(body, StandardCharsets.UTF_8))
                    .build();
            return unwrap(http.send(request, HttpResponse.BodyHandlers.ofByteArray()).body());
        } catch (IOException e) {
            throw new TelegramException(0, "Telegram недоступен: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TelegramException(0, "interrupted");
        }
    }

    private JsonNode unwrap(byte[] body) throws IOException {
        JsonNode node = json.readTree(body);
        if (!node.path("ok").asBoolean(false)) {
            throw new TelegramException(node.path("error_code").asInt(0), node.path("description").asText("Bot API error"));
        }
        return node.path("result");
    }

    // ───────── удобные обёртки ─────────

    public JsonNode sendMessage(long chatId, String html, Object replyMarkup, Long replyTo) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("chat_id", chatId);
        params.put("text", html);
        params.put("parse_mode", "HTML");
        params.put("link_preview_options", Map.of("is_disabled", true));
        if (replyMarkup != null) {
            params.put("reply_markup", replyMarkup);
        }
        if (replyTo != null) {
            params.put("reply_parameters", Map.of("message_id", replyTo, "allow_sending_without_reply", true));
        }
        return call("sendMessage", params);
    }

    public void editMessage(long chatId, long messageId, String html, Object replyMarkup) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("chat_id", chatId);
        params.put("message_id", messageId);
        params.put("text", html);
        params.put("parse_mode", "HTML");
        params.put("link_preview_options", Map.of("is_disabled", true));
        if (replyMarkup != null) {
            params.put("reply_markup", replyMarkup);
        }
        try {
            call("editMessageText", params);
        } catch (TelegramException e) {
            // «message is not modified» — не ошибка; остальное шлём новым сообщением
            if (!e.getMessage().contains("not modified")) {
                sendMessage(chatId, html, replyMarkup, null);
            }
        }
    }

    public void answerCallback(String callbackId, String text) {
        Map<String, Object> params = new LinkedHashMap<>();
        params.put("callback_query_id", callbackId);
        if (text != null) {
            params.put("text", text);
        }
        try {
            call("answerCallbackQuery", params);
        } catch (TelegramException e) {
            log.debug("answerCallbackQuery: {}", e.getMessage());
        }
    }

    /** Скачивает файл из Telegram; бросает TelegramException, если файл больше лимита */
    public void download(String fileId, Path target, long limit) {
        JsonNode file = call("getFile", Map.of("file_id", fileId));
        long size = file.path("file_size").asLong(0);
        if (size > limit) {
            throw new TelegramException(413, "too large");
        }
        String path = file.path("file_path").asText("");
        if (path.isEmpty()) {
            throw new TelegramException(400, "no file path");
        }
        URI uri = URI.create(config.getApiUrl().replaceAll("/+$", "") + "/file/bot" + config.getBotToken() + "/" + path);
        try {
            HttpResponse<InputStream> response = http.send(HttpRequest.newBuilder(uri).timeout(Duration.ofMinutes(3)).GET().build(),
                    HttpResponse.BodyHandlers.ofInputStream());
            if (response.statusCode() != 200) {
                response.body().close();
                throw new TelegramException(response.statusCode(), "download failed");
            }
            try (InputStream in = response.body(); OutputStream out = Files.newOutputStream(target)) {
                byte[] buffer = new byte[256 * 1024];
                long total = 0;
                int read;
                while ((read = in.read(buffer)) > 0) {
                    total += read;
                    if (total > limit) {
                        throw new TelegramException(413, "too large");
                    }
                    out.write(buffer, 0, read);
                }
            }
        } catch (IOException e) {
            throw new TelegramException(0, "download failed: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TelegramException(0, "interrupted");
        }
    }

    /** sendVideo с загрузкой файла (multipart) */
    public void sendVideo(long chatId, Path file, String filename, String caption, Object replyMarkup, Long replyTo) {
        String boundary = "----offer" + UUID.randomUUID().toString().replace("-", "");
        try {
            ByteArrayOutputStream head = new ByteArrayOutputStream();
            writeField(head, boundary, "chat_id", String.valueOf(chatId));
            writeField(head, boundary, "supports_streaming", "true");
            if (caption != null) {
                writeField(head, boundary, "caption", caption);
                writeField(head, boundary, "parse_mode", "HTML");
            }
            if (replyMarkup != null) {
                writeField(head, boundary, "reply_markup", json.writeValueAsString(replyMarkup));
            }
            if (replyTo != null) {
                writeField(head, boundary, "reply_parameters",
                        json.writeValueAsString(Map.of("message_id", replyTo, "allow_sending_without_reply", true)));
            }
            head.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"video\"; filename=\""
                    + filename.replace("\"", "") + "\"\r\nContent-Type: video/mp4\r\n\r\n").getBytes(StandardCharsets.UTF_8));
            byte[] tail = ("\r\n--" + boundary + "--\r\n").getBytes(StandardCharsets.UTF_8);
            HttpRequest.BodyPublisher body = HttpRequest.BodyPublishers.concat(
                    HttpRequest.BodyPublishers.ofByteArray(head.toByteArray()),
                    HttpRequest.BodyPublishers.ofFile(file),
                    HttpRequest.BodyPublishers.ofByteArray(tail));
            HttpRequest request = HttpRequest.newBuilder(URI.create(base() + "/sendVideo"))
                    .timeout(Duration.ofMinutes(5))
                    .header("Content-Type", "multipart/form-data; boundary=" + boundary)
                    .POST(body)
                    .build();
            unwrap(http.send(request, HttpResponse.BodyHandlers.ofByteArray()).body());
        } catch (IOException e) {
            throw new TelegramException(0, "sendVideo failed: " + e.getMessage());
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new TelegramException(0, "interrupted");
        }
    }

    private static void writeField(ByteArrayOutputStream out, String boundary, String name, String value) throws IOException {
        out.write(("--" + boundary + "\r\nContent-Disposition: form-data; name=\"" + name + "\"\r\n\r\n")
                .getBytes(StandardCharsets.UTF_8));
        out.write(value.getBytes(StandardCharsets.UTF_8));
        out.write("\r\n".getBytes(StandardCharsets.UTF_8));
    }

    // ───────── разметка ─────────

    public static Map<String, Object> keyboard(List<List<Map<String, Object>>> rows) {
        return Map.of("inline_keyboard", rows);
    }

    public static Map<String, Object> button(String text, String callbackData) {
        return Map.of("text", text, "callback_data", callbackData);
    }

    public static Map<String, Object> urlButton(String text, String url) {
        return Map.of("text", text, "url", url);
    }

    public static String escape(String text) {
        return text == null ? "" : text.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }
}
