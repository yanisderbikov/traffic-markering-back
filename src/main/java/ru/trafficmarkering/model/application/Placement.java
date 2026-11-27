package ru.trafficmarkering.model.application;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import java.time.Instant;

/**
 * Одна надбавка отклика: состояние, доказательство креатора, итог автоматической проверки и решение
 * модератора. Колонки у ссылки и промокода свои (link_*, promo_*), см. {@link Application}.
 */
@Embeddable
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class Placement {

    @Builder.Default
    @Enumerated(EnumType.STRING)
    @Column(name = "state", nullable = false, length = 32)
    private PlacementState state = PlacementState.NOT_SELECTED;

    /** Скриншот или запись экрана в хранилище: профиль со ссылкой, ролик с закреплённым комментарием */
    @Column(name = "proof_key", length = 512)
    private String proofKey;

    /** Ссылка от креатора: на сам закреплённый комментарий или на страницу профиля */
    @Column(name = "proof_url", length = 1024)
    private String proofUrl;

    @Enumerated(EnumType.STRING)
    @Column(name = "check_state", length = 32)
    private PlacementCheck checkState;

    /** Что увидела проверка: ссылки профиля или причина, по которой проверить не вышло */
    @Column(name = "check_detail", columnDefinition = "TEXT")
    private String checkDetail;

    @Column(name = "checked_at")
    private Instant checkedAt;

    /** Причина отказа модератора или пометка системы («ссылку не нашли в конце срока») */
    @Column(name = "note", columnDefinition = "TEXT")
    private String note;

    @Column(name = "decided_at")
    private Instant decidedAt;

    @Column(name = "decided_by")
    private Long decidedBy;

    public static Placement of(boolean selected) {
        return Placement.builder()
                .state(selected ? PlacementState.PENDING : PlacementState.NOT_SELECTED)
                .build();
    }

    public PlacementState state() {
        return state != null ? state : PlacementState.NOT_SELECTED;
    }

    public void recordCheck(PlacementCheck check, String detail, Instant at) {
        this.checkState = check;
        this.checkDetail = detail;
        this.checkedAt = at;
    }

    public void decide(PlacementState next, String note, Long actorId, Instant at) {
        this.state = next;
        this.note = note;
        this.decidedBy = actorId;
        this.decidedAt = at;
    }

    /** Новое доказательство после отказа или вместо старого: надбавка снова ждёт проверки. */
    public void resubmit(String proofKey, String proofUrl) {
        this.state = PlacementState.PENDING;
        this.proofKey = proofKey;
        this.proofUrl = proofUrl;
        this.note = null;
        this.decidedAt = null;
        this.decidedBy = null;
    }

    public void deselect() {
        this.state = PlacementState.NOT_SELECTED;
    }
}
