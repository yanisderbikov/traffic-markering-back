package ru.trafficmarkering.service.notify;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import ru.trafficmarkering.model.application.Application;
import ru.trafficmarkering.model.application.PlacementKind;
import ru.trafficmarkering.model.campaign.Campaign;

/** Уведомления по откликам: креатору — решение по ролику, рекламодателю — новый ролик на проверку */
@Component
@RequiredArgsConstructor
public class ApplicationNotifications {

    private final UserNotifier notifier;

    /** Креатор приложил ролик — рекламодателю пора его посмотреть */
    public void submitted(Application application) {
        Campaign campaign = application.getCampaign();
        String body = campaign.paysBySnapshot()
                ? application.getCreator().getName() + " подал работу. Просмотры уже зафиксированы на момент подачи — "
                        + "проверьте ролик и выбранные надбавки за ссылку и промокод."
                : application.getCreator().getName() + " прислал ролик. Посмотрите и одобрите — "
                        + "после одобрения начнут учитываться просмотры.";
        notifier.notify(campaign.getCustomer(), "Новый ролик по кампании «" + campaign.getTitle() + "»",
                body, "Посмотреть отклик", "/app/campaigns/" + campaign.getId());
    }

    /** Надбавку не подтвердили: криатор может исправить размещение или продолжить без неё */
    public void placementRejected(Application application, PlacementKind kind) {
        String reason = application.placement(kind).getNote();
        notifier.notify(application.getCreator(), "Надбавка не подтверждена",
                kind.getDescription() + " по кампании «" + application.getCampaign().getTitle() + "» не подтверждена."
                        + (reason != null ? "\nПричина: " + reason : "")
                        + "\nИсправьте размещение и приложите новое доказательство или продолжите без этой надбавки — "
                        + "базовая часть за ролик от этого не зависит.",
                "Мои работы", "/app/applications");
    }

    /** В конце срока размещения ссылку в профиле не нашли — надбавка снова ждёт проверки */
    public void linkMissingAtRetentionEnd(Application application) {
        notifier.notify(application.getCreator(), "Ссылки нет в профиле",
                "Срок размещения по кампании «" + application.getCampaign().getTitle() + "» закончился, но рекламной "
                        + "ссылки в профиле мы не нашли. Верните её и нажмите «Проверить снова» или приложите скриншот — "
                        + "иначе надбавку за ссылку не засчитают. Базовая часть за ролик сохраняется.",
                "Мои работы", "/app/applications");
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
