package ru.trafficmarkering.service.social.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import ru.trafficmarkering.model.User;
import ru.trafficmarkering.model.application.Platform;
import ru.trafficmarkering.model.social.SocialAccount;
import ru.trafficmarkering.repository.GetterSocialAccount;
import ru.trafficmarkering.service.scrape.ScrapeCreatorsApi;
import ru.trafficmarkering.service.scrape.ScrapeCreatorsException;
import ru.trafficmarkering.service.scrape.ScrapeVideo;
import ru.trafficmarkering.service.social.SocialIntegration;
import ru.trafficmarkering.service.social.SocialVideoOwnership;

import java.util.List;

@Service
@RequiredArgsConstructor
class ScrapeVideoOwnershipImpl implements SocialVideoOwnership {

    private final SocialIntegration socialIntegration;
    private final ScrapeCreatorsApi scrapeCreatorsApi;
    private final GetterSocialAccount getterSocialAccount;

    @Override
    public void requireOwnedBy(User creator, Platform platform, String videoUrl) {
        if (!socialIntegration.scrape()) {
            return;
        }
        if (!scrapeCreatorsApi.isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Проверка роликов ещё не настроена, напишите в поддержку");
        }
        List<SocialAccount> accounts = getterSocialAccount.getAllActiveByUserIdAndPlatform(creator.getId(), platform);
        if (accounts.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Привяжите аккаунт " + platform.getDescription()
                            + " в профиле: ролик должен быть выложен с подключённого аккаунта");
        }

        ScrapeVideo video;
        try {
            video = scrapeCreatorsApi.video(platform, videoUrl);
        } catch (ScrapeCreatorsException e) {
            if (e.badInput()) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Не разобрали ссылку на ролик " + platform.getDescription()
                                + ": вставьте ссылку на сам ролик, а не на профиль, клип или подборку");
            }
            if (e.notFound() || e.status() == 422) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Не открыли ролик по ссылке: проверьте, что он опубликован и доступен всем");
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Не удалось проверить ролик, попробуйте через пару минут");
        }

        boolean owned = accounts.stream()
                .anyMatch(account -> video.publishedBy(account.getExternalId(), account.getUsername()));
        if (!owned) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Ролик выложен с аккаунта " + video.authorLabel()
                            + ", а он не привязан к вашему профилю. Привяжите его и подайте ролик ещё раз");
        }
    }
}
