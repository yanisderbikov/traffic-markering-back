package ru.trafficmarkering.service.notify;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.trafficmarkering.model.application.Application;
import ru.trafficmarkering.model.campaign.Campaign;

/** Уведомления по откликам: креатору — решение по ролику, рекламодателю — новый ролик на проверку */
@Component
@RequiredArgsConstructor
public class ApplicationNotifications {

    private final UserNotifier notifier;

    /** Креатор приложил ролик — рекламодателю пора его посмотреть */
    public void submitted(Application application) {
        Campaign campaign = application.getCampaign();
        notifier.notify(campaign.getCustomer(), "Новый ролик по кампании «" + campaign.getTitle() + "»",
                application.getCreator().getName() + " прислал ролик. Посмотрите и одобрите — "
                        + "после одобрения начнут учитываться просмотры.",
                "Посмотреть отклик", "/app/campaigns/" + campaign.getId());
    }

    public void decided(Application application) {
        String campaign = "«" + application.getCampaign().getTitle() + "»";
        switch (application.getStatus()) {
            case APPROVED -> notifier.notify(application.getCreator(), "✅ Ролик одобрен",
                    "Ваш ролик по кампании " + campaign + " одобрили. Просмотры теперь учитываются "
                            + "и начисляются в заработок.", "Мои работы", "/app/applications");
            case REJECTED -> notifier.notify(application.getCreator(), "Ролик отклонён",
                    "Ролик по кампании " + campaign + " не приняли."
                            + (application.getRejectionReason() != null
                            ? "\nПричина: " + application.getRejectionReason() : "")
                            + "\nПосмотрите другие офферы — новые появляются каждый день.",
                    "Открыть офферы", "/app/board");
            case COMPLETED -> notifier.notify(application.getCreator(), "Работа завершена",
                    "Рекламодатель завершил работу по кампании " + campaign + ". Заработок — в разделе «Финансы».",
                    "Мои финансы", "/app/earnings");
            default -> {
            }
        }
    }
}
