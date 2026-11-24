package ru.trafficmarkering.service.notify;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.trafficmarkering.dto.notify.NotificationPayload;
import ru.trafficmarkering.service.email.EmailService;
import ru.trafficmarkering.service.studio.telegram.TelegramClient;

import java.util.List;
import java.util.Map;

import static ru.trafficmarkering.service.studio.telegram.TelegramClient.appButton;
import static ru.trafficmarkering.service.studio.telegram.TelegramClient.escape;
import static ru.trafficmarkering.service.studio.telegram.TelegramClient.keyboard;
import static ru.trafficmarkering.service.studio.telegram.TelegramClient.urlButton;

/** Отправляет одно сообщение: в Telegram — с кнопкой мини-приложения, на почту — письмом с кнопкой */
@Component
public class NotificationSender {

    private final TelegramClient telegram;
    private final EmailService email;
    private final String siteUrl;

    public NotificationSender(TelegramClient telegram, EmailService email, @Value("${studio.site-url}") String siteUrl) {
        this.telegram = telegram;
        this.email = email;
        this.siteUrl = siteUrl == null ? "" : siteUrl.replaceAll("/+$", "");
    }

    public void send(NotificationPayload message) {
        if (message.getChatId() != null) {
            telegram.sendMessage(message.getChatId(), telegramHtml(message), telegramMarkup(message), null);
        } else if (message.getEmail() != null) {
            email.sendEmail(message.getEmail(), message.getTitle(), emailHtml(message));
        } else {
            throw new IllegalArgumentException("У сообщения нет ни чата Telegram, ни почты");
        }
    }

    String absolute(String url) {
        if (url == null || url.isBlank()) {
            return siteUrl + "/app";
        }
        return url.startsWith("/") ? siteUrl + url : url;
    }

    static String telegramHtml(NotificationPayload message) {
        return "<b>" + escape(message.getTitle()) + "</b>\n\n" + escape(message.getText());
    }

    Map<String, Object> telegramMarkup(NotificationPayload message) {
        String url = absolute(message.getButtonUrl());
        String text = label(message);
        // Ссылки на наш сайт открываем мини-приложением — там вход уже выполнен через Telegram
        return keyboard(List.of(List.of(url.startsWith(siteUrl + "/") ? appButton(text, url) : urlButton(text, url))));
    }

    String emailHtml(NotificationPayload message) {
        String paragraphs = String.join("", escapeHtml(message.getText()).lines()
                .map(line -> line.isBlank() ? "<br>" : "<p style=\"margin:0 0 12px\">" + line + "</p>")
                .toList());
        return "<!doctype html><html><body style=\"margin:0;padding:24px;background:#f4f4f5;"
                + "font-family:Arial,Helvetica,sans-serif;color:#18181b\">"
                + "<div style=\"max-width:520px;margin:0 auto;background:#fff;border-radius:16px;padding:28px\">"
                + "<div style=\"font-weight:700;font-size:20px;margin-bottom:16px\">offer</div>"
                + "<h1 style=\"font-size:20px;margin:0 0 16px\">" + escapeHtml(message.getTitle()) + "</h1>"
                + "<div style=\"font-size:15px;line-height:1.5\">" + paragraphs + "</div>"
                + "<a href=\"" + escapeHtml(absolute(message.getButtonUrl())) + "\" style=\"display:inline-block;"
                + "margin-top:12px;padding:12px 20px;border-radius:10px;background:#18181b;color:#fff;"
                + "text-decoration:none;font-weight:700\">" + escapeHtml(label(message)) + "</a>"
                + "</div></body></html>";
    }

    private static String label(NotificationPayload message) {
        return message.getButtonText() == null || message.getButtonText().isBlank() ? "Открыть Offer" : message.getButtonText();
    }

    private static String escapeHtml(String text) {
        return escape(text).replace("\"", "&quot;");
    }
}
