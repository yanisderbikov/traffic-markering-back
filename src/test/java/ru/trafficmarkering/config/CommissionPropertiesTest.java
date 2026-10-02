package ru.trafficmarkering.config;

import org.junit.jupiter.api.Test;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class CommissionPropertiesTest {

    private final CommissionProperties properties = new CommissionProperties();

    @Test
    void takesTenPercentByDefault() {
        assertThat(properties.commissionOf(40_000_00L)).isEqualTo(4_000_00L);
        assertThat(properties.partnerShareOf(4_000_00L)).isEqualTo(400_00L);
    }

    @Test
    void commissionRoundsHalfUpToKopeck() {
        assertThat(properties.commissionOf(40_05L)).isEqualTo(4_01L);
        assertThat(properties.commissionOf(40_04L)).isEqualTo(4_00L);
    }

    @Test
    void partnerShareRoundsDownSoPlatformNeverOverpays() {
        assertThat(properties.partnerShareOf(4_09L)).isEqualTo(40L);
        assertThat(properties.partnerShareOf(9L)).isZero();
    }

    @Test
    void nothingIsTakenFromEmptyAmount() {
        assertThat(properties.commissionOf(0L)).isZero();
        assertThat(properties.commissionOf(-100_00L)).isZero();
        assertThat(properties.partnerShareOf(0L)).isZero();
    }

    @Test
    void supportsFractionalPercent() {
        properties.setPercent(new BigDecimal("7.5"));

        assertThat(properties.commissionOf(1_000_00L)).isEqualTo(75_00L);
    }

    @Test
    void refusesPercentOutsideZeroToHundred() {
        properties.setPercent(BigDecimal.valueOf(100));
        assertThatThrownBy(properties::validate).isInstanceOf(IllegalStateException.class);

        properties.setPercent(BigDecimal.valueOf(-1));
        assertThatThrownBy(properties::validate).isInstanceOf(IllegalStateException.class);

        properties.setPercent(BigDecimal.ZERO);
        properties.setPartnerSharePercent(null);
        assertThatThrownBy(properties::validate).isInstanceOf(IllegalStateException.class);
    }
}
