package ru.trafficmarkering.service.application.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import ru.trafficmarkering.model.application.Application;
import ru.trafficmarkering.model.application.PlacementCheck;
import ru.trafficmarkering.model.application.Platform;
import ru.trafficmarkering.model.social.SocialAccount;
import ru.trafficmarkering.repository.GetterSocialAccount;
import ru.trafficmarkering.service.scrape.ProfileRef;
import ru.trafficmarkering.service.scrape.ScrapeCreatorsApi;
import ru.trafficmarkering.service.scrape.ScrapeCreatorsException;
import ru.trafficmarkering.service.scrape.ScrapeProfile;
import ru.trafficmarkering.service.social.SocialIntegration;
import ru.trafficmarkering.util.PromoLinks;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.regex.Pattern;

/**
 * Автопроверка рекламной ссылки в профиле криатора по публичным данным ScrapeCreators.
 * Смотрим только кликабельные ссылки профиля (TikTok bioLink, Instagram bio_links и external_url,
 * ссылки канала YouTube) — строка с адресом в тексте описания размещением не считается.
 * «Не найдено» говорим, только когда источник ссылки профиля отдал и нашей среди них нет;
 * сбой источника или площадка без такого поля — «не удалось проверить», решает модератор по скриншоту.
 */
@Log4j2
@Component
@RequiredArgsConstructor
class ProfileLinkChecker {

    private static final Pattern YOUTUBE_CHANNEL_ID = Pattern.compile("UC[A-Za-z0-9_\\-]{22}");

    private final SocialIntegration socialIntegration;
    private final ScrapeCreatorsApi scrapeCreatorsApi;
    private final GetterSocialAccount getterSocialAccount;

    /** Проверяет профиль и записывает итог в надбавку за ссылку. */
    PlacementCheck check(Application application) {
        Result result = run(application);
        application.link().recordCheck(result.check(), result.detail(), Instant.now());
        return result.check();
    }

    private Result run(Application application) {
        String expected = application.getCampaign().getPromoUrl();
        Platform platform = application.getPlatform();
        if (expected == null || platform == null) {
            return new Result(PlacementCheck.UNKNOWN, "Нет ролика или рекламной ссылки для сравнения");
        }
        if (!socialIntegration.scrape() || !scrapeCreatorsApi.isConfigured()) {
            return new Result(PlacementCheck.UNKNOWN,
                    "Автопроверка ссылок профиля не подключена — проверит модератор по скриншоту");
        }
        List<SocialAccount> accounts = getterSocialAccount.getAllActiveByUserIdAndPlatform(
                application.getCreator().getId(), platform);
        if (accounts.isEmpty()) {
            return new Result(PlacementCheck.UNKNOWN, "Нет подключённого аккаунта " + platform.getDescription());
        }

        boolean linksKnown = false;
        List<String> seen = new ArrayList<>();
        List<String> failures = new ArrayList<>();
        for (SocialAccount account : accounts) {
            ProfileRef ref = refOf(platform, account);
            if (ref == null) {
                continue;
            }
            ScrapeProfile profile;
            try {
                profile = scrapeCreatorsApi.profile(platform, ref);
            } catch (ScrapeCreatorsException e) {
                log.info("Профиль {} {} для проверки ссылки не открылся: {}", platform, ref.label(), e.getMessage());
                failures.add(labelOf(account, ref));
                continue;
            } catch (RuntimeException e) {
                log.warn("Сбой проверки ссылки профиля {} {}", platform, ref.label(), e);
                failures.add(labelOf(account, ref));
                continue;
            }
            if (!profile.linksKnown()) {
                continue;
            }
            linksKnown = true;
            for (String link : profile.links()) {
                if (PromoLinks.matches(expected, link)) {
                    return new Result(PlacementCheck.FOUND, "Ссылка в профиле " + labelOf(account, ref) + ": " + link);
                }
                seen.add(link);
            }
        }
        if (linksKnown) {
            return new Result(PlacementCheck.MISSING, seen.isEmpty()
                    ? "В профиле нет ни одной ссылки"
                    : "В профиле другие ссылки: " + String.join(", ", seen));
        }
        if (!failures.isEmpty()) {
            return new Result(PlacementCheck.UNKNOWN,
                    "Профиль сейчас не открылся (" + String.join(", ", failures) + ") — попробуйте проверить позже");
        }
        return new Result(PlacementCheck.UNKNOWN, platform.getDescription()
                + " не отдаёт ссылки профиля автоматически — проверит модератор по скриншоту");
    }

    /** Для людей — @ник аккаунта; id канала YouTube им ничего не говорит. */
    private static String labelOf(SocialAccount account, ProfileRef ref) {
        String username = account.getUsername();
        if (username == null || username.isBlank()) {
            return ref.label();
        }
        return username.startsWith("@") ? username : "@" + username;
    }

    /** Профиль для ScrapeCreators: у YouTube по id канала, у остальных по нику. */
    private static ProfileRef refOf(Platform platform, SocialAccount account) {
        if (platform == Platform.YOUTUBE_SHORTS && account.getExternalId() != null
                && YOUTUBE_CHANNEL_ID.matcher(account.getExternalId()).matches()) {
            return new ProfileRef(ProfileRef.Kind.CHANNEL_ID, account.getExternalId());
        }
        String username = account.getUsername();
        if (username == null || username.isBlank()) {
            return null;
        }
        return new ProfileRef(ProfileRef.Kind.HANDLE, username.startsWith("@") ? username.substring(1) : username);
    }

    record Result(PlacementCheck check, String detail) {
    }
}
