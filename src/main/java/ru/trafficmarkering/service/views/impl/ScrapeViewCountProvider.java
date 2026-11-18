package ru.trafficmarkering.service.views.impl;

import lombok.extern.log4j.Log4j2;
import ru.trafficmarkering.model.application.Platform;
import ru.trafficmarkering.model.application.ViewSource;
import ru.trafficmarkering.model.social.SocialAccount;
import ru.trafficmarkering.repository.GetterSocialAccount;
import ru.trafficmarkering.service.scrape.ScrapeCreatorsApi;
import ru.trafficmarkering.service.scrape.ScrapeCreatorsException;
import ru.trafficmarkering.service.scrape.ScrapeVideo;
import ru.trafficmarkering.service.views.VideoMetrics;
import ru.trafficmarkering.service.views.ViewCount;
import ru.trafficmarkering.service.views.ViewCountProvider;

import java.util.Collection;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Просмотры через ScrapeCreators: по запросу на ролик, 1 кредит за каждый. Отдаёт счётчики
 * публичной страницы — без географии и удержания, поэтому офферы с регионом просмотров
 * такие ролики не оплачивают. Ролик считается, только если он выложен с аккаунта,
 * который криатор подтвердил кодом: иначе можно подать чужой популярный ролик.
 * Неудачный запрос ролик не обнуляет — остаются прошлые просмотры, как советует документация.
 */
@Log4j2
class ScrapeViewCountProvider implements ViewCountProvider {

    private final Platform platform;
    private final ScrapeCreatorsApi scrapeCreatorsApi;
    private final GetterSocialAccount getterSocialAccount;

    ScrapeViewCountProvider(Platform platform, ScrapeCreatorsApi scrapeCreatorsApi,
                            GetterSocialAccount getterSocialAccount) {
        this.platform = platform;
        this.scrapeCreatorsApi = scrapeCreatorsApi;
        this.getterSocialAccount = getterSocialAccount;
    }

    @Override
    public Platform platform() {
        return platform;
    }

    @Override
    public ViewSource source() {
        return ViewSource.SCRAPE_CREATORS;
    }

    @Override
    public boolean isConfigured() {
        return scrapeCreatorsApi.isConfigured();
    }

    @Override
    public Map<String, ViewCount> fetchViews(Long creatorId, Collection<String> videoUrls) {
        List<SocialAccount> accounts = getterSocialAccount.getAllActiveByUserIdAndPlatform(creatorId, platform);
        if (accounts.isEmpty()) {
            log.debug("У криатора {} нет подтверждённого аккаунта {}, пропускаем {} роликов",
                    creatorId, platform, videoUrls.size());
            return Map.of();
        }

        Map<String, ViewCount> result = new LinkedHashMap<>();
        for (String url : videoUrls) {
            ScrapeVideo video;
            try {
                video = scrapeCreatorsApi.video(platform, url);
            } catch (ScrapeCreatorsException e) {
                if (e.fatal()) {
                    throw e;
                }
                log.warn("ScrapeCreators не отдал ролик {} криатора {}: {}", url, creatorId, e.getMessage());
                continue;
            }
            if (accounts.stream().noneMatch(account -> video.publishedBy(account.getExternalId(), account.getUsername()))) {
                log.warn("Ролик {} криатора {} выложен с аккаунта {}, который не привязан — просмотры не учитываем",
                        url, creatorId, video.authorLabel());
                continue;
            }
            if (video.views() == null) {
                log.debug("ScrapeCreators не отдал просмотры ролика {}", url);
                continue;
            }
            VideoMetrics metrics = VideoMetrics.empty()
                    .withPublishedAt(video.publishedAt())
                    .withEngagement(video.likes(), video.comments(), video.shares())
                    .withSaves(video.saves());
            result.put(url, ViewCount.total(video.views()).withMetrics(metrics));
        }
        return result;
    }
}
