package ru.trafficmarkering.repository.impl;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.trafficmarkering.model.application.Platform;
import ru.trafficmarkering.model.social.SocialAccount;
import ru.trafficmarkering.model.social.SocialAccountStatus;
import ru.trafficmarkering.model.social.SocialLinkMethod;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
interface SocialAccountRepo extends JpaRepository<SocialAccount, UUID> {

    List<SocialAccount> findByUserIdAndLinkMethodOrderByConnectedAtAsc(Long userId, SocialLinkMethod linkMethod);

    Optional<SocialAccount> findByPlatformAndLinkMethodAndExternalId(
            Platform platform, SocialLinkMethod linkMethod, String externalId);

    Optional<SocialAccount> findFirstByUserIdAndPlatformAndLinkMethodAndStatusOrderByConnectedAtAsc(
            Long userId, Platform platform, SocialLinkMethod linkMethod, SocialAccountStatus status);

    List<SocialAccount> findByUserIdAndPlatformAndLinkMethodAndStatusOrderByConnectedAtAsc(
            Long userId, Platform platform, SocialLinkMethod linkMethod, SocialAccountStatus status);
}
