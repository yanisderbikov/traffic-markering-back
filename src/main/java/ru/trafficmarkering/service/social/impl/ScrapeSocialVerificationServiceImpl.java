package ru.trafficmarkering.service.social.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import ru.trafficmarkering.dto.social.SocialAccountDTO;
import ru.trafficmarkering.dto.social.SocialVerificationDTO;
import ru.trafficmarkering.model.Role;
import ru.trafficmarkering.model.User;
import ru.trafficmarkering.model.application.Platform;
import ru.trafficmarkering.model.social.SocialAccount;
import ru.trafficmarkering.model.social.SocialAccountStatus;
import ru.trafficmarkering.model.social.SocialLinkMethod;
import ru.trafficmarkering.repository.GetterSocialAccount;
import ru.trafficmarkering.repository.SaverSocialAccount;
import ru.trafficmarkering.service.auth.CurrentUserService;
import ru.trafficmarkering.service.scrape.ProfileRef;
import ru.trafficmarkering.service.scrape.ScrapeCreatorsApi;
import ru.trafficmarkering.service.scrape.ScrapeCreatorsException;
import ru.trafficmarkering.service.scrape.ScrapeProfile;
import ru.trafficmarkering.service.social.SocialIntegration;
import ru.trafficmarkering.service.social.SocialPlatforms;
import ru.trafficmarkering.service.social.SocialVerificationService;

import java.time.Instant;
import java.util.Optional;

@Service
@RequiredArgsConstructor
@Log4j2
class ScrapeSocialVerificationServiceImpl implements SocialVerificationService {

    private final SocialIntegration socialIntegration;
    private final ScrapeCreatorsApi scrapeCreatorsApi;
    private final CurrentUserService currentUserService;
    private final GetterSocialAccount getterSocialAccount;
    private final SaverSocialAccount saverSocialAccount;
    private final VerificationCodes verificationCodes;

    @Override
    public SocialVerificationDTO start(String platformSlug, String account) {
        requireEnabled();
        User user = currentUserService.require(Role.CREATOR);
        Platform platform = SocialPlatforms.require(platformSlug);
        ProfileRef ref = requireRef(platform, account);
        ScrapeProfile profile = lookup(platform, ref);
        Optional<SocialAccount> existing = requireLinkable(platform, profile, user);

        String code = verificationCodes.codeFor(user.getId(), platform, profile.externalId());
        return new SocialVerificationDTO(
                platform.name(),
                platform.getDescription(),
                profile.externalId(),
                profile.username(),
                profile.displayName(),
                profile.avatarUrl(),
                profile.followers(),
                code,
                codePlacement(platform),
                VerificationCodes.present(profile.bio(), code),
                existing.isPresent());
    }

    /** Без общей транзакции: запрос к ScrapeCreators идёт до 45 секунд, соединение с базой на это время не держим. */
    @Override
    public SocialAccountDTO confirm(String platformSlug, String account) {
        requireEnabled();
        User user = currentUserService.require(Role.CREATOR);
        Platform platform = SocialPlatforms.require(platformSlug);
        ProfileRef ref = requireRef(platform, account);
        ScrapeProfile profile = lookup(platform, ref);
        Optional<SocialAccount> existing = requireLinkable(platform, profile, user);

        String code = verificationCodes.codeFor(user.getId(), platform, profile.externalId());
        if (!VerificationCodes.present(profile.bio(), code)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Не нашли код " + code + " в " + codePlacement(platform) + " " + name(profile, ref)
                            + ". Сохраните описание с кодом и проверьте ещё раз — площадке иногда нужна "
                            + "пара минут, чтобы показать изменения");
        }

        SocialAccount linked = existing.orElseGet(() -> SocialAccount.builder()
                .user(user)
                .platform(platform)
                .externalId(profile.externalId())
                .linkMethod(SocialLinkMethod.SCRAPE)
                .build());
        linked.setUsername(profile.username());
        linked.setDisplayName(profile.displayName());
        linked.setAvatarUrl(profile.avatarUrl());
        linked.setFollowers(profile.followers());
        linked.setAccessToken(null);
        linked.setRefreshToken(null);
        linked.setTokenExpiresAt(null);
        linked.setScopes(null);
        linked.setLinkMethod(SocialLinkMethod.SCRAPE);
        linked.setStatus(SocialAccountStatus.ACTIVE);
        linked.setLastSyncedAt(Instant.now());

        SocialAccount saved = saverSocialAccount.save(linked);
        log.info("Криатор {} подтвердил кодом аккаунт {} ({}) на площадке {}",
                user.getId(), saved.getExternalId(), saved.getUsername(), platform);
        return SocialAccountDTO.from(saved);
    }

    private void requireEnabled() {
        if (!socialIntegration.scrape()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                    "Привязка по коду выключена: подключите аккаунт через вход на площадке");
        }
        if (!scrapeCreatorsApi.isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Подключение площадок ещё не настроено, напишите в поддержку");
        }
    }

    private static ProfileRef requireRef(Platform platform, String account) {
        return ProfileRef.parse(platform, account)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.BAD_REQUEST,
                        "Не разобрали аккаунт " + platform.getDescription()
                                + ": укажите ник вида @name или ссылку на профиль"));
    }

    private ScrapeProfile lookup(Platform platform, ProfileRef ref) {
        try {
            return scrapeCreatorsApi.profile(platform, ref);
        } catch (ScrapeCreatorsException e) {
            if (e.notFound()) {
                throw new ResponseStatusException(HttpStatus.NOT_FOUND,
                        "Не нашли аккаунт " + ref.label() + " в " + platform.getDescription()
                                + " — проверьте ник или ссылку");
            }
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY,
                    "Не удалось открыть профиль " + platform.getDescription() + ", попробуйте через пару минут");
        }
    }

    /** Закрытый профиль не годится: его ролики и просмотры не видны. Чужая привязка — конфликт. */
    private Optional<SocialAccount> requireLinkable(Platform platform, ScrapeProfile profile, User user) {
        if (profile.privateAccount()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Профиль закрыт: откройте его, иначе мы не увидим просмотры роликов");
        }
        Optional<SocialAccount> existing = getterSocialAccount.getByPlatformAndExternalId(platform, profile.externalId());
        if (existing.isPresent() && !existing.get().getUser().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Этот аккаунт уже привязан к другому криатору");
        }
        return existing;
    }

    private static String name(ScrapeProfile profile, ProfileRef ref) {
        return profile.username() != null ? "@" + profile.username() : ref.label();
    }

    private static String codePlacement(Platform platform) {
        return switch (platform) {
            case TIKTOK -> "описании профиля TikTok (Bio)";
            case INSTAGRAM -> "описании профиля Instagram (Bio)";
            case YOUTUBE_SHORTS -> "описании канала YouTube (раздел «О канале»)";
            default -> "описании профиля";
        };
    }
}
