package ru.trafficmarkering.model.application;

import jakarta.persistence.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;
import lombok.ToString;

import ru.trafficmarkering.model.campaign.ViewRegion;

import java.time.Instant;
import java.util.Map;

@Entity
@Table(name = "application_view_snapshot")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
@ToString
public class ApplicationViewSnapshot {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ToString.Exclude
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "application_id", nullable = false)
    private Application application;

    @Column(name = "captured_at", nullable = false)
    private Instant capturedAt;

    @Column(nullable = false)
    private Long views;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private ViewSource source;

    @Convert(converter = CountryViewsConverter.class)
    @Column(name = "country_views", columnDefinition = "TEXT")
    private Map<String, Long> countryViews;

    // Метрики ролика на момент замера — сырьё для антифрода. null — площадка не отдала.

    private Long likes;

    private Long comments;

    private Long shares;

    private Long saves;

    /** Уникальные аккаунты (Instagram): просмотры / охват показывают долю повторов */
    private Long reach;

    /** YouTube: просмотры без мгновенных пролистываний — честнее общего счётчика Shorts */
    @Column(name = "engaged_views")
    private Long engagedViews;

    @Column(name = "avg_watch_seconds")
    private Double avgWatchSeconds;

    @Column(name = "avg_view_percentage")
    private Double avgViewPercentage;

    /** YouTube: просмотры по источникам трафика (SHORTS, EXT_URL, ADVERTISING, …) */
    @Convert(converter = CountryViewsConverter.class)
    @Column(name = "traffic_sources", columnDefinition = "TEXT")
    private Map<String, Long> trafficSources;

    public long totalViews() {
        return views != null ? views : 0L;
    }

    public boolean geographyKnown() {
        return countryViews != null;
    }

    /** Те же правила региона, что у отклика: по снимку считается «созревшая» за окно удержания сумма. */
    public PayableViews payableViews(ViewRegion region) {
        if (region == null || region.isWorld()) {
            return PayableViews.of(totalViews());
        }
        if (!geographyKnown()) {
            return PayableViews.withoutGeography();
        }
        return PayableViews.of(Math.min(totalViews(), region.viewsWithin(countryViews)));
    }
}
