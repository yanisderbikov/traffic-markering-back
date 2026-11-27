package ru.trafficmarkering.service.social;

import ru.trafficmarkering.model.User;
import ru.trafficmarkering.model.application.Platform;

public interface SocialVideoOwnership {

    /**
     * Под флагом ScrapeCreators проверяет, что ролик выложен с одного из привязанных аккаунтов
     * креатора; иначе отказ. В режиме OAuth ничего не делает — там всё остаётся как было:
     * TikTok и Instagram по токену креатора чужие ролики не отдают.
     */
    void requireOwnedBy(User creator, Platform platform, String videoUrl);
}
