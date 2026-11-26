package ru.trafficmarkering.service.application.impl;

import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import ru.trafficmarkering.model.application.Application;
import ru.trafficmarkering.model.campaign.Campaign;
import ru.trafficmarkering.model.campaign.CampaignContentRate;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

class ContentTopicsTest {

    private Campaign campaignWithTopics() {
        return Campaign.builder()
                .ratePerThousandKopecks(115_00L)
                .contentRates(new ArrayList<>(List.of(
                        new CampaignContentRate("CS и Dota 2", 115_00L),
                        new CampaignContentRate("Остальные игры", 90_00L),
                        new CampaignContentRate("Юмор", 15_00L))))
                .build();
    }

    @Test
    void choose_fixesRateOfChosenTopic_ignoringCaseAndSpaces() {
        Application application = new Application();

        ContentTopics.choose(application, campaignWithTopics(), "  юмор ");

        assertEquals("Юмор", application.getContentTopic());
        assertEquals(15_00L, application.getBaseRateKopecks());
    }

    @Test
    void choose_requiresTopicWhenCampaignHasRates() {
        ResponseStatusException error = assertThrows(ResponseStatusException.class,
                () -> ContentTopics.choose(new Application(), campaignWithTopics(), null));
        assertEquals(HttpStatus.BAD_REQUEST, error.getStatusCode());
    }

    @Test
    void choose_rejectsUnknownTopic() {
        assertThrows(ResponseStatusException.class,
                () -> ContentTopics.choose(new Application(), campaignWithTopics(), "Кулинария"));
    }

    @Test
    void choose_ignoresTopicWhenCampaignHasSingleRate() {
        Application application = new Application();

        ContentTopics.choose(application, Campaign.builder().ratePerThousandKopecks(350_00L).build(), "Юмор");

        assertNull(application.getContentTopic());
        assertNull(application.getBaseRateKopecks());
    }
}
