package ru.trafficmarkering.service.application.impl;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import ru.trafficmarkering.model.application.Application;
import ru.trafficmarkering.model.campaign.Campaign;
import ru.trafficmarkering.model.campaign.CampaignContentRate;

import java.util.stream.Collectors;

/**
 * Тематика ролика при подаче. Если заказчик задал ставки по тематикам, криатор обязан выбрать одну:
 * её ставка фиксируется в отклике и дальнейшие правки объявления ролик уже не трогают.
 */
final class ContentTopics {

    private ContentTopics() {
    }

    static void choose(Application application, Campaign campaign, String topic) {
        if (!campaign.hasContentRates()) {
            return;
        }
        String name = topic != null ? topic.strip() : "";
        if (name.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Выберите тематику ролика: от неё зависит ставка. Варианты: " + options(campaign));
        }
        CampaignContentRate rate = campaign.contentRate(name)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Такой тематики нет в объявлении: " + name + ". Варианты: " + options(campaign)));
        application.setContentTopic(rate.getName());
        application.setBaseRateKopecks(rate.getRatePerThousandKopecks());
    }

    private static String options(Campaign campaign) {
        return campaign.getContentRates().stream()
                .map(CampaignContentRate::getName)
                .collect(Collectors.joining(", "));
    }
}
