package ru.trafficmarkering.model.moderation;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import java.time.Instant;
import java.time.ZoneOffset;

/**
 * Менеджер модерации в Telegram. Супер-админ добавляет его по нику, менеджер заходит в бота
 * модерации и сам выбирает часовой пояс и часы, в которые ему приходят заявки.
 */
@Entity
@Table(name = "moderator")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class Moderator {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    /** Ник Telegram без @ в нижнем регистре */
    @Column(nullable = false, unique = true, length = 64)
    private String username;

    @Builder.Default
    @Column(nullable = false)
    private boolean enabled = true;

    @Column(name = "telegram_id", unique = true)
    private Long telegramId;

    @Column(name = "chat_id")
    private Long chatId;

    @Column(name = "display_name")
    private String displayName;

    /** «+03:00»; null — менеджер ещё не настроил часы */
    @Column(name = "utc_offset", length = 8)
    private String utcOffset;

    @Column(name = "work_from")
    private Integer workFrom;

    @Column(name = "work_to")
    private Integer workTo;

    @Builder.Default
    @Column(nullable = false)
    private boolean paused = false;

    @Builder.Default
    @Column(name = "missed_in_row", nullable = false)
    private int missedInRow = 0;

    @Column(name = "last_seen_at")
    private Instant lastSeenAt;

    @Builder.Default
    @Column(name = "created_at", nullable = false, updatable = false)
    private Instant createdAt = Instant.now();

    public boolean scheduled() {
        return utcOffset != null && workFrom != null && workTo != null;
    }

    public ZoneOffset offset() {
        return utcOffset == null ? ZoneOffset.UTC : ZoneOffset.of(utcOffset);
    }

    /** Окно приёма: from == to — круглосуточно, from > to — через полночь (22–6) */
    public boolean worksAt(Instant now) {
        if (!scheduled()) {
            return false;
        }
        int hour = now.atOffset(offset()).getHour();
        return inWindow(hour, workFrom, workTo);
    }

    public static boolean inWindow(int hour, int from, int to) {
        if (from == to) {
            return true;
        }
        return from < to ? hour >= from && hour < to : hour >= from || hour < to;
    }

    /** Заявки можно слать прямо сейчас */
    public boolean available(Instant now) {
        return enabled && chatId != null && !paused && worksAt(now);
    }

    /** «@ivan_moder (Иван Петров)» — подпись менеджера на ролике */
    public String label() {
        return displayName != null && !displayName.isBlank() ? "@" + username + " (" + displayName.strip() + ")" : "@" + username;
    }

    /** Активен — получает заявки в свои часы; неактивен — сам выключился, выключил админ или пропустил заявки подряд */
    public boolean active() {
        return !paused;
    }

    public String scheduleLabel() {
        if (!scheduled()) {
            return "часы не настроены";
        }
        String zone = "UTC" + (utcOffset.equals("Z") || utcOffset.equals("+00:00") ? "" : shortOffset(utcOffset));
        if (workFrom.equals(workTo)) {
            return "круглосуточно (" + zone + ")";
        }
        return String.format("%02d:00–%02d:00 (%s)", workFrom, workTo, zone);
    }

    /** «+03:00» → «+3», «+05:30» → «+5:30» */
    static String shortOffset(String offset) {
        String sign = offset.substring(0, 1);
        String[] parts = offset.substring(1).split(":");
        int hours = Integer.parseInt(parts[0]);
        String minutes = parts.length > 1 && !parts[1].equals("00") ? ":" + parts[1] : "";
        return sign + hours + minutes;
    }
}
