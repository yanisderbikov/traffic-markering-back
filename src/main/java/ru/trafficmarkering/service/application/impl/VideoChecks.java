package ru.trafficmarkering.service.application.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;
import ru.trafficmarkering.model.User;
import ru.trafficmarkering.model.application.Application;
import ru.trafficmarkering.model.application.Platform;
import ru.trafficmarkering.model.campaign.Campaign;
import ru.trafficmarkering.repository.GetterApplication;
import ru.trafficmarkering.repository.GetterSocialAccount;
import ru.trafficmarkering.service.http.ShortLinkResolver;
import ru.trafficmarkering.service.social.SocialVideoOwnership;
import ru.trafficmarkering.util.VideoUrls;

import java.util.Objects;
import java.util.stream.Collectors;

/**
 * Проверки ролика перед тем, как он ляжет в отклик: и для обычного отклика, и для работы «ссылка и промокод».
 * Площадка из ссылки, аккаунт этой площадки в профиле, короткая ссылка развёрнута до полной, тот же ролик
 * нигде больше не подан, а под флагом ScrapeCreators ещё и выложен с привязанного аккаунта.
 */
@Component
@RequiredArgsConstructor
class VideoChecks {

    private final GetterApplication getterApplication;
    private final GetterSocialAccount getterSocialAccount;
    private final ShortLinkResolver shortLinkResolver;
    private final SocialVideoOwnership socialVideoOwnership;

    /** Прописывает проверенный ролик в отклик: площадка, полная ссылка, ключ. Статус не трогает. */
    void attach(Application application, Campaign campaign, User creator, String rawVideoUrl) {
        Platform platform = requirePlatform(rawVideoUrl);
        requireAcceptedByCampaign(campaign, platform);
        requireConnectedAccount(creator, platform);
        String videoUrl = resolveVideoUrl(platform, rawVideoUrl);
        String videoKey = VideoUrls.videoKey(platform, videoUrl);
        requireVideoNotSubmitted(videoKey, creator, application);
        socialVideoOwnership.requireOwnedBy(creator, platform, videoUrl);

        application.setPlatform(platform);
        application.setVideoUrl(videoUrl);
        application.setVideoKey(videoKey);
    }

    /**
     * Короткие ссылки (vm.tiktok.com, youtu.be в редиректе) разворачиваем до полной:
     * иначе один и тот же ролик под двумя ссылками пройдёт как два разных.
     * Площадка недоступна — работаем с тем, что прислали.
     */
    private String resolveVideoUrl(Platform platform, String videoUrl) {
        boolean identified = switch (platform) {
            case TIKTOK -> VideoUrls.tiktokVideoId(videoUrl) != null;
            case YOUTUBE_SHORTS -> VideoUrls.youtubeVideoId(videoUrl) != null;
            default -> true;
        };
        return identified ? videoUrl : shortLinkResolver.resolve(videoUrl);
    }

    private Platform requirePlatform(String videoUrl) {
        Platform platform = VideoUrls.detectPlatform(videoUrl);
        if (platform == null) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Не удалось определить площадку по ссылке: принимаются ролики YouTube, TikTok и Instagram");
        }
        return platform;
    }

    private void requireAcceptedByCampaign(Campaign campaign, Platform platform) {
        if (campaign.acceptsPlatform(platform)) {
            return;
        }
        String accepted = campaign.getPlatforms().stream()
                .sorted()
                .map(Platform::getDescription)
                .collect(Collectors.joining(", "));
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                "Объявление не принимает ролики с " + platform.getDescription()
                        + (accepted.isEmpty() ? "" : ": подходят только " + accepted));
    }

    private void requireConnectedAccount(User creator, Platform platform) {
        if (getterSocialAccount.getActiveByUserIdAndPlatform(creator.getId(), platform).isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Привяжите аккаунт " + platform.getDescription()
                            + " в профиле: ролик должен быть выложен с подключённого аккаунта");
        }
    }

    /** Тот же ролик в другом отклике — 409; в этом же (повторный расчёт по работе) — можно. */
    private void requireVideoNotSubmitted(String videoKey, User creator, Application current) {
        getterApplication.getActiveByVideoKey(videoKey).ifPresent(existing -> {
            if (current.getId() != null && Objects.equals(existing.getId(), current.getId())) {
                return;
            }
            if (Objects.equals(existing.getCreator().getId(), creator.getId())) {
                throw new ResponseStatusException(HttpStatus.CONFLICT,
                        "Этот ролик уже подан по объявлению «" + existing.getCampaign().getTitle() + "»");
            }
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Этот ролик уже подан на площадке другим криатором");
        });
    }
}
