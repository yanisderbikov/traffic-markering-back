package ru.trafficmarkering.model.studio;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "studio_job")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class StudioJob {

    @Id
    private UUID id;

    @Column(name = "user_id", nullable = false, updatable = false)
    private Long userId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16, updatable = false)
    private StudioJobOrigin origin;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 16)
    private StudioJobStatus status;

    @Column(nullable = false, length = 160)
    private String filename;

    /** StudioSettings в JSON */
    @Column(nullable = false, columnDefinition = "TEXT")
    private String settings;

    /** Субтитры в JSON: [{start, end, text}] */
    @Column(columnDefinition = "TEXT")
    private String captions;

    /** Встроенный фон, выбранный при постановке в очередь (для случайного режима) */
    @Column(length = 32)
    private String preset;

    @Column(nullable = false)
    private int progress;

    @Column(columnDefinition = "TEXT")
    private String error;

    @Column(name = "source_key", length = 512)
    private String sourceKey;

    @Column(name = "banner_key", length = 512)
    private String bannerKey;

    @Column(name = "background_key", length = 512)
    private String backgroundKey;

    @Column(name = "result_key", length = 512)
    private String resultKey;

    @Column(name = "result_size")
    private Long resultSize;

    @Column(name = "batch_id")
    private UUID batchId;

    @Column(name = "retry_of")
    private UUID retryOf;

    @Column(name = "telegram_chat_id")
    private Long telegramChatId;

    @Column(name = "telegram_reply_to")
    private Long telegramReplyTo;

    /** До какого момента хранятся исходники для повтора; null — исходники не храним */
    @Column(name = "inputs_until")
    private Instant inputsUntil;

    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt;

    @Column(name = "started_at")
    private Instant startedAt;

    /** Сколько раз задачу брали в работу: перезапуск сервера возвращает её в очередь */
    @Column(nullable = false)
    private int attempts;

    /** Воркер жив и собирает: обновляется каждые несколько секунд */
    @Column(name = "heartbeat_at")
    private Instant heartbeatAt;

    /** Сообщение с процентами в Telegram — после перезапуска правим его же */
    @Column(name = "progress_message_id")
    private Long progressMessageId;

    @Column(name = "finished_at")
    private Instant finishedAt;
}
