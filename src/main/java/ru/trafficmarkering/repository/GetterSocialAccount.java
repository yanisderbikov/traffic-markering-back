package ru.trafficmarkering.repository;

import ru.trafficmarkering.model.application.Platform;
import ru.trafficmarkering.model.social.SocialAccount;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

public interface GetterSocialAccount {

    List<SocialAccount> getByUserId(Long userId);

    Optional<SocialAccount> getById(UUID id);

    Optional<SocialAccount> getByPlatformAndExternalId(Platform platform, String externalId);

    Optional<SocialAccount> getActiveByUserIdAndPlatform(Long userId, Platform platform);
}
