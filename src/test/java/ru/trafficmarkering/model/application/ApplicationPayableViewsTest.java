package ru.trafficmarkering.model.application;

import org.junit.jupiter.api.Test;
import ru.trafficmarkering.model.campaign.ViewRegion;

import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;

class ApplicationPayableViewsTest {

    @Test
    void worldPaysTotalEvenWithoutGeography() {
        Application application = application(1_000L, null);

        PayableViews payable = application.payableViews(ViewRegion.WORLD);

        assertThat(payable.views()).isEqualTo(1_000L);
        assertThat(payable.geographyKnown()).isTrue();
    }

    @Test
    void regionWithoutGeographyPaysNothing() {
        Application application = application(1_000L, null);

        PayableViews payable = application.payableViews(ViewRegion.RUSSIA);

        assertThat(payable.views()).isZero();
        assertThat(payable.geographyKnown()).isFalse();
        assertThat(application.geographyKnown()).isFalse();
    }

    @Test
    void russiaPaysOnlyRussianViews() {
        Application application = application(1_000L, Map.of("RU", 800L, "KZ", 100L, "ZZ", 50L));

        PayableViews payable = application.payableViews(ViewRegion.RUSSIA);

        assertThat(payable.views()).isEqualTo(800L);
        assertThat(payable.geographyKnown()).isTrue();
    }

    @Test
    void cisPaysMemberStateViews() {
        Application application = application(1_000L, Map.of("RU", 800L, "KZ", 100L, "ZZ", 50L));

        PayableViews payable = application.payableViews(ViewRegion.CIS);

        assertThat(payable.views()).isEqualTo(900L);
        assertThat(payable.geographyKnown()).isTrue();
    }

    @Test
    void regionalViewsAreCappedByTotal() {
        Application application = application(1_000L, Map.of("RU", 1_500L));

        PayableViews payable = application.payableViews(ViewRegion.RUSSIA);

        assertThat(payable.views()).isEqualTo(1_000L);
        assertThat(payable.geographyKnown()).isTrue();
    }

    @Test
    void nullViewsCountAsZero() {
        Application application = application(null, Map.of("RU", 10L));

        assertThat(application.totalViews()).isZero();
        assertThat(application.payableViews(ViewRegion.WORLD).views()).isZero();
        assertThat(application.payableViews(ViewRegion.RUSSIA).views()).isZero();
    }

    private Application application(Long views, Map<String, Long> countryViews) {
        return Application.builder()
                .views(views)
                .countryViews(countryViews)
                .build();
    }
}
