package ru.trafficmarkering.config;

import lombok.Getter;
import lombok.Setter;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

/** Видео-студия: рендер FFmpeg, лимиты и Telegram-бот. */
@Configuration
@ConfigurationProperties(prefix = "studio")
@Getter
@Setter
public class StudioProperties {

    /** Воркер рендера на этом экземпляре; false — задачи только ставятся в очередь */
    private boolean workerEnabled = true;
    private String ffmpeg = "ffmpeg";
    private String ffprobe = "ffprobe";
    private String workDir = System.getProperty("java.io.tmpdir") + "/offer-studio";
    /** Задач в сутки (UTC) на аккаунт; администраторам без лимита */
    private int dailyLimit = 5;
    private int maxActive = 2;
    private long renderTimeoutSeconds = 300;
    private long sourceMaxBytes = 80L * 1024 * 1024;
    private long bannerMaxBytes = 20L * 1024 * 1024;
    private long backgroundMaxBytes = 30L * 1024 * 1024;
    /** Готовый ролик хранится, часов */
    private int resultTtlHours = 48;
    /** Исходники для повтора, часов */
    private int inputsTtlHours = 24;
    private int historyTtlDays = 30;

    private Telegram telegram = new Telegram();

    @Getter
    @Setter
    public static class Telegram {
        /** Токен от @BotFather; пусто — бот выключен, сайт работает */
        private String botToken = "";
        /** Имя бота без @; пусто — берётся из getMe */
        private String botUsername = "";
        /** Секрет заголовка X-Telegram-Bot-Api-Secret-Token; пусто — выводится из токена */
        private String webhookSecret = "";
        /** Публичный адрес бэка для вебхука; пусто — url.back */
        private String webhookBaseUrl = "";
        /** Регистрировать вебхук при старте */
        private boolean registerWebhook = true;
        private String apiUrl = "https://api.telegram.org";
        /** Видео в чат принимаем до 20 МБ — больше Bot API не отдаёт */
        private long incomingMaxBytes = 20_000_000;
        /** Отправить файлом в чат — до 50 МБ */
        private long outgoingMaxBytes = 50_000_000;

        public boolean enabled() {
            return botToken != null && !botToken.isBlank();
        }
    }
}
