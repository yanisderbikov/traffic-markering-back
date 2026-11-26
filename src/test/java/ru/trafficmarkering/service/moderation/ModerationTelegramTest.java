package ru.trafficmarkering.service.moderation;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import ru.trafficmarkering.config.ModerationProperties;
import ru.trafficmarkering.config.StudioProperties;

import static org.junit.jupiter.api.Assertions.assertEquals;

class ModerationTelegramTest {

    private static String webhook(String own, String studio, String back) {
        ModerationProperties moderation = new ModerationProperties();
        moderation.getTelegram().setWebhookBaseUrl(own);
        StudioProperties studioProperties = new StudioProperties();
        studioProperties.getTelegram().setWebhookBaseUrl(studio);
        return new ModerationTelegram(moderation, studioProperties, new ObjectMapper(), back).webhookUrl();
    }

    @Test
    void ownBaseUrlWins() {
        assertEquals("https://own.example/api/telegram/moderation/webhook",
                webhook("https://own.example/", "https://studio.example", "https://back.example"));
    }

    @Test
    void fallsBackToStudioBotAddressThenBackUrl() {
        assertEquals("https://offernow.up.railway.app/api/telegram/moderation/webhook",
                webhook("", "https://offernow.up.railway.app", "https://dev.getoffer.now"));
        assertEquals("https://back.example/api/telegram/moderation/webhook", webhook("", "", "https://back.example"));
    }
}
