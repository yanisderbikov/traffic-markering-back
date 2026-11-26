package ru.trafficmarkering.dto.notify;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Одно сообщение одному человеку: в Telegram (chatId) или на почту (email).
 * Текст простой — разметку под канал собирает {@code NotificationSender}.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class NotificationPayload {
    private Long chatId;
    private String email;
    private String title;
    private String text;
    private String buttonText;
    /** Путь на сайте (/app/...) или полный адрес */
    private String buttonUrl;
    /** Рассылка, к которой относится сообщение; null — уведомление о событии */
    private Long broadcastId;
}
