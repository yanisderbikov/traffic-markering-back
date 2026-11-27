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
    /** После скольких пропущенных подряд заявок менеджер становится неактивным; 0 — не выключать */
    private int pauseAfterMissed = 3;
    /** Как часто диспетчер раздаёт заявки и снимает просроченные, мс */
    private long dispatchRateMs = 15_000;
    /** yt-dlp для скачивания ролика с площадки; пусто или нет в системе — только ScrapeCreators и ссылка */
    private String ytDlp = "yt-dlp";
    private int downloadTimeoutSeconds = 120;
    private String workDir = System.getProperty("java.io.tmpdir") + "/offer-moderation";

    /**
     * Настройки бота: токен свой, отдельный от бота студии. Иконки пака tgiosicons включены, как у бота студии:
     * видны при Premium у владельца бота, иначе Telegram показывает обычные эмодзи
     */
    private StudioProperties.Telegram telegram = new StudioProperties.Telegram();

    /** Облако Bot API отправляет файлы до 50 МБ, свой сервер — до 2 ГБ */
    public long uploadLimit() {
        return telegram.localServer() ? 2_000_000_000L : telegram.getOutgoingMaxBytes();
    }
}
