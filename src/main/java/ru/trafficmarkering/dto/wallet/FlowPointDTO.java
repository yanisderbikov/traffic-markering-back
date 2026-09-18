package ru.trafficmarkering.dto.wallet;

import io.swagger.v3.oas.annotations.media.Schema;
import ru.trafficmarkering.model.User;
import ru.trafficmarkering.model.campaign.Campaign;

import java.util.UUID;

@Schema(description = "Одна сторона движения денег: откуда они ушли или куда пришли")
public record FlowPointDTO(
        @Schema(description = "EXTERNAL, CUSTOMER_WALLET, CAMPAIGN, CREATOR_WALLET или TRON") String kind,
        @Schema(description = "Подпись для человека", example = "Кошелёк заказчика · Иван") String label,
        @Schema(description = "Владелец кошелька, если сторона — кошелёк") Long userId,
        @Schema(description = "Объявление, если сторона — бюджет объявления") UUID campaignId,
        String campaignPublicId
) {
    public static FlowPointDTO external() {
        return new FlowPointDTO("EXTERNAL", "Вне платформы", null, null, null);
    }

    public static FlowPointDTO customerWallet(User owner) {
        return wallet("CUSTOMER_WALLET", "Кошелёк заказчика", owner);
    }

    public static FlowPointDTO creatorWallet(User owner) {
        return wallet("CREATOR_WALLET", "Кошелёк криатора", owner);
    }

    public static FlowPointDTO campaign(Campaign campaign) {
        if (campaign == null) {
            return new FlowPointDTO("CAMPAIGN", "Удалённое объявление", null, null, null);
        }
        return new FlowPointDTO("CAMPAIGN", "Объявление «" + campaign.getTitle() + "»", null,
                campaign.getId(), campaign.getPublicId());
    }

    public static FlowPointDTO tron(String address) {
        return new FlowPointDTO("TRON", address == null ? "Кошелёк TRON" : "TRON · " + address, null, null, null);
    }

    private static FlowPointDTO wallet(String kind, String prefix, User owner) {
        if (owner == null) {
            return new FlowPointDTO(kind, prefix, null, null, null);
        }
        return new FlowPointDTO(kind, prefix + " · " + owner.getName(), owner.getId(), null, null);
    }
}
