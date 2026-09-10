package ru.trafficmarkering.service.social.impl;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;
import ru.trafficmarkering.dto.social.SocialAccountDTO;
import ru.trafficmarkering.dto.social.SocialAuthorizeResponseDTO;
import ru.trafficmarkering.model.Role;
import ru.trafficmarkering.model.User;
import ru.trafficmarkering.model.social.SocialAccount;
import ru.trafficmarkering.model.social.SocialAccountStatus;
import ru.trafficmarkering.repository.GetterSocialAccount;
import ru.trafficmarkering.repository.SaverSocialAccount;
import ru.trafficmarkering.repository.SocialAccountDeleter;
import ru.trafficmarkering.repository.UserRepository;
import ru.trafficmarkering.service.auth.CurrentUserService;
import ru.trafficmarkering.service.social.OAuthStateService;
import ru.trafficmarkering.service.social.SocialAccountData;
import ru.trafficmarkering.service.social.SocialAccountService;
import ru.trafficmarkering.service.social.SocialOAuthProvider;
import ru.trafficmarkering.util.TokenCipher;

import java.util.List;
import java.util.Map;
import java.util.UUID;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Log4j2
class SocialAccountServiceImpl implements SocialAccountService {

    private final Map<String, SocialOAuthProvider> providers;
    private final GetterSocialAccount getterSocialAccount;
    private final SaverSocialAccount saverSocialAccount;
    private final SocialAccountDeleter socialAccountDeleter;
    private final CurrentUserService currentUserService;
    private final UserRepository userRepository;
    private final OAuthStateService oAuthStateService;
    private final TokenCipher tokenCipher;
    private final String backUrl;
    private final String frontUrl;

    SocialAccountServiceImpl(List<SocialOAuthProvider> providers,
                             GetterSocialAccount getterSocialAccount,
                             SaverSocialAccount saverSocialAccount,
                             SocialAccountDeleter socialAccountDeleter,
                             CurrentUserService currentUserService,
                             UserRepository userRepository,
                             OAuthStateService oAuthStateService,
                             TokenCipher tokenCipher,
                             @Value("${url.back}") String backUrl,
                             @Value("${app.front.url}") String frontUrl) {
        this.providers = providers.stream()
                .collect(Collectors.toMap(SocialOAuthProvider::slug, Function.identity()));
        this.getterSocialAccount = getterSocialAccount;
        this.saverSocialAccount = saverSocialAccount;
        this.socialAccountDeleter = socialAccountDeleter;
        this.currentUserService = currentUserService;
        this.userRepository = userRepository;
        this.oAuthStateService = oAuthStateService;
        this.tokenCipher = tokenCipher;
        this.backUrl = trimTrailingSlash(backUrl);
        this.frontUrl = trimTrailingSlash(frontUrl);
    }

    @Override
    @Transactional(readOnly = true)
    public List<SocialAccountDTO> listMine() {
        User user = currentUserService.require(Role.CREATOR);
        return getterSocialAccount.getByUserId(user.getId()).stream()
                .map(SocialAccountDTO::from)
                .toList();
    }

    @Override
    public SocialAuthorizeResponseDTO authorize(String platformSlug) {
        User user = currentUserService.require(Role.CREATOR);
        SocialOAuthProvider provider = requireProvider(platformSlug);
        String state = oAuthStateService.issue(user.getId(), provider.platform());
        return new SocialAuthorizeResponseDTO(
                provider.platform().name(),
                provider.authorizationUrl(state, redirectUri(provider)));
    }

    @Override
    @Transactional
    public SocialAccountDTO connect(String platformSlug, String code, String state) {
        SocialOAuthProvider provider = requireProvider(platformSlug);
        if (!StringUtils.hasText(code)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Площадка не вернула код подтверждения");
        }
        Long userId = oAuthStateService.verify(state, provider.platform());
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.UNAUTHORIZED,
                        "Учётная запись не найдена, войдите заново"));
        if (user.getRole() != Role.CREATOR && user.getRole() != Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Привязывать соцсети может только криатор");
        }

        SocialAccountData data = provider.exchangeCode(code, redirectUri(provider));
        SocialAccount account = getterSocialAccount
                .getByPlatformAndExternalId(provider.platform(), data.externalId())
                .orElseGet(() -> SocialAccount.builder()
                        .user(user)
                        .platform(provider.platform())
                        .externalId(data.externalId())
                        .build());

        if (!account.getUser().getId().equals(user.getId())) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Этот аккаунт уже привязан к другому криатору");
        }

        account.setUsername(data.username());
        account.setDisplayName(data.displayName());
        account.setAvatarUrl(data.avatarUrl());
        account.setFollowers(data.followers());
        account.setAccessToken(tokenCipher.encrypt(data.accessToken()));
        account.setRefreshToken(tokenCipher.encrypt(data.refreshToken()));
        account.setTokenExpiresAt(data.expiresAt());
        account.setScopes(data.scopes());
        account.setStatus(SocialAccountStatus.ACTIVE);

        SocialAccount saved = saverSocialAccount.save(account);
        log.info("Криатор {} привязал аккаунт {} на площадке {}",
                user.getId(), saved.getExternalId(), saved.getPlatform());
        return SocialAccountDTO.from(saved);
    }

    @Override
    @Transactional
    public void disconnect(UUID id) {
        User user = currentUserService.require(Role.CREATOR);
        SocialAccount account = getterSocialAccount.getById(id)
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Аккаунт не найден"));
        if (!account.getUser().getId().equals(user.getId()) && user.getRole() != Role.ADMIN) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Это чужой аккаунт");
        }
        socialAccountDeleter.deleteById(id);
    }

    @Override
    public String frontRedirect(String platformSlug, String status, String message) {
        UriComponentsBuilder builder = UriComponentsBuilder.fromUriString(frontUrl + "/app/profile/socials")
                .queryParam("social", platformSlug)
                .queryParam("status", status);
        if (StringUtils.hasText(message)) {
            builder.queryParam("message", message);
        }
        return builder.encode().toUriString();
    }

    private SocialOAuthProvider requireProvider(String platformSlug) {
        SocialOAuthProvider provider = platformSlug == null
                ? null
                : providers.get(platformSlug.toLowerCase());
        if (provider == null) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "Площадка не поддерживается");
        }
        if (!provider.isConfigured()) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE,
                    "Подключение площадки ещё не настроено, напишите в поддержку");
        }
        return provider;
    }

    private String redirectUri(SocialOAuthProvider provider) {
        return backUrl + "/api/social/callback/" + provider.slug();
    }

    private static String trimTrailingSlash(String url) {
        return url != null && url.endsWith("/") ? url.substring(0, url.length() - 1) : url;
    }
}
