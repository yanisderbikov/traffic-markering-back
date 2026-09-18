package ru.trafficmarkering.service.social.impl;

import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.trafficmarkering.model.application.Platform;
import ru.trafficmarkering.model.social.SocialAccount;
import ru.trafficmarkering.model.social.SocialAccountStatus;
import ru.trafficmarkering.repository.GetterSocialAccount;
import ru.trafficmarkering.repository.SaverSocialAccount;
import ru.trafficmarkering.service.social.RefreshedToken;
import ru.trafficmarkering.service.social.SocialOAuthProvider;
import ru.trafficmarkering.service.social.SocialTokenService;
import ru.trafficmarkering.util.TokenCipher;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Log4j2
class SocialTokenServiceImpl implements SocialTokenService {

    private static final Duration REFRESH_MARGIN = Duration.ofMinutes(5);

    private final Map<Platform, SocialOAuthProvider> providers;
    private final GetterSocialAccount getterSocialAccount;
    private final SaverSocialAccount saverSocialAccount;
    private final TokenCipher tokenCipher;

    SocialTokenServiceImpl(List<SocialOAuthProvider> providers,
                           GetterSocialAccount getterSocialAccount,
                           SaverSocialAccount saverSocialAccount,
                           TokenCipher tokenCipher) {
        this.providers = providers.stream()
                .collect(Collectors.toMap(SocialOAuthProvider::platform, Function.identity()));
        this.getterSocialAccount = getterSocialAccount;
        this.saverSocialAccount = saverSocialAccount;
        this.tokenCipher = tokenCipher;
    }

    @Override
    @Transactional
    public Optional<String> accessToken(Long userId, Platform platform) {
        Optional<SocialAccount> found = getterSocialAccount.getActiveByUserIdAndPlatform(userId, platform);
        if (found.isEmpty()) {
            return Optional.empty();
        }
        SocialAccount account = found.get();

        String access;
        String refresh;
        try {
            access = tokenCipher.decrypt(account.getAccessToken());
            refresh = tokenCipher.decrypt(account.getRefreshToken());
        } catch (Exception e) {
            log.error("Токен аккаунта {} не расшифровывается, помечаем как истёкший", account.getId(), e);
            return expire(account);
        }

        if (!needsRefresh(account)) {
            return Optional.ofNullable(access);
        }

        SocialOAuthProvider provider = providers.get(platform);
        if (provider == null || !provider.isConfigured()) {
            return expire(account);
        }

        try {
            Optional<RefreshedToken> refreshed = provider.refresh(access, refresh);
            if (refreshed.isEmpty()) {
                return expire(account);
            }
            RefreshedToken token = refreshed.get();
            account.setAccessToken(tokenCipher.encrypt(token.accessToken()));
            if (token.refreshToken() != null) {
                account.setRefreshToken(tokenCipher.encrypt(token.refreshToken()));
            }
            account.setTokenExpiresAt(token.expiresAt());
            saverSocialAccount.save(account);
            log.info("Токен {} для аккаунта {} обновлён", platform, account.getExternalId());
            return Optional.of(token.accessToken());
        } catch (Exception e) {
            log.warn("Не удалось обновить токен {} для аккаунта {}: {}",
                    platform, account.getExternalId(), e.getMessage());
            return expire(account);
        }
    }

    private boolean needsRefresh(SocialAccount account) {
        Instant expiresAt = account.getTokenExpiresAt();
        return expiresAt != null && Instant.now().plus(REFRESH_MARGIN).isAfter(expiresAt);
    }

    private Optional<String> expire(SocialAccount account) {
        account.setStatus(SocialAccountStatus.EXPIRED);
        saverSocialAccount.save(account);
        return Optional.empty();
    }
}
