package ru.trafficmarkering.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Модерация роликов через Telegram: отдельный бот, очередь заявок между менеджерами. */
@Configuration
@ConfigurationProperties(prefix = "moderation")
@Getter
@Setter
public class ModerationProperties {

    /** Сколько минут заявка висит у менеджера, потом уходит следующему */
    private int holdMinutes = 30;
    /** Сколько заявок подряд можно пропустить, прежде чем менеджер встанет на паузу; 0 — не ставить */
    private int pauseAfterMissed = 2;
    /** Как часто диспетчер раздаёт заявки и снимает просроченные, мс */
    private long dispatchRateMs = 15_000;
    /** yt-dlp для скачивания ролика с площадки; пусто или нет в системе — только ScrapeCreators и ссылка */
    private String ytDlp = "yt-dlp";
    private int downloadTimeoutSeconds = 120;
    private String workDir = System.getProperty("java.io.tmpdir") + "/offer-moderation";

    /** Настройки бота: токен свой, отдельный от бота студии */
    private StudioProperties.Telegram telegram = defaultTelegram();

    private static StudioProperties.Telegram defaultTelegram() {
        StudioProperties.Telegram telegram = new StudioProperties.Telegram();
        // Иконки пака видны только при Premium у владельца бота; у бота модерации по умолчанию обычные эмодзи
        telegram.setCustomEmoji(false);
        return telegram;
    }

    /** Облако Bot API отправляет файлы до 50 МБ, свой сервер — до 2 ГБ */
    public long uploadLimit() {
        return telegram.localServer() ? 2_000_000_000L : telegram.getOutgoingMaxBytes();
    }
}
