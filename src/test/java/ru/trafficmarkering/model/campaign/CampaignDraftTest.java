package ru.trafficmarkering.model.campaign;

import org.junit.jupiter.api.Test;
import ru.trafficmarkering.model.application.Platform;

import java.util.EnumSet;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CampaignDraftTest {

    private static Campaign filled() {
        return Campaign.builder()
                .title("Обзор приложения")
                .description("Снять короткий ролик")
                .photoKey("campaign-photos/cover.png")
                .platforms(EnumSet.of(Platform.TIKTOK))
                .ratePerThousandKopecks(35_000L)
                .budgetKopecks(0L)
                .minPayoutKopecks(300_000L)
                .build();
    }

    @Test
    void blankDraftMissesEveryRequiredField() {
        Campaign draft = Campaign.builder().build();

        assertThat(draft.missingForLaunch()).containsExactly(
                "название", "описание", "обложка", "площадки", "ставка", "бюджет", "порог вывода");
    }

    @Test
    void filledCampaignIsReadyEvenWithZeroBudget() {
        assertThat(filled().missingForLaunch()).isEmpty();
    }

    @Test
    void blankTextAndNonPositiveMoneyCountAsMissing() {
        Campaign campaign = filled();
        campaign.setTitle("   ");
        campaign.setRatePerThousandKopecks(0L);
        campaign.getPlatforms().clear();

        assertThat(campaign.missingForLaunch()).isEqualTo(List.of("название", "площадки", "ставка"));
    }

    @Test
    void displayTitleFallsBackForEmptyDraft() {
        assertThat(Campaign.builder().build().displayTitle()).isEqualTo("Без названия");
        assertThat(filled().displayTitle()).isEqualTo("Обзор приложения");
    }
}
