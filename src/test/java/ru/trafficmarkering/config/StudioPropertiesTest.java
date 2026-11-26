package ru.trafficmarkering.config;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class StudioPropertiesTest {

    @Test
    void cloudBotKeepsTelegramLimits() {
        StudioProperties p = new StudioProperties();
        assertThat(p.getTelegram().localServer()).isFalse();
        assertThat(p.telegramIncomingLimit()).isEqualTo(20_000_000);
        assertThat(p.telegramOutgoingLimit()).isEqualTo(50_000_000);
        assertThat(p.telegramIncomingLabel()).isEqualTo("20 МБ");
    }

    @Test
    void ownBotApiServerTakesVideosLikeTheSite() {
        StudioProperties p = new StudioProperties();
        p.getTelegram().setApiUrl("http://telegram-bot-api.railway.internal:8080");
        assertThat(p.getTelegram().localServer()).isTrue();
        assertThat(p.telegramIncomingLimit()).isEqualTo(p.getSourceMaxBytes());
        assertThat(p.telegramIncomingLabel()).isEqualTo("80 МБ");
        assertThat(p.telegramOutgoingLimit()).isEqualTo(2_000_000_000L);
    }
}
