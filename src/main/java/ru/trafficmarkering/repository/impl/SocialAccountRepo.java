package ru.trafficmarkering.repository.impl;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;
import ru.trafficmarkering.model.application.Platform;
import ru.trafficmarkering.model.social.SocialAccount;
import ru.trafficmarkering.model.social.SocialAccountStatus;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
interface SocialAccountRepo extends JpaRepository<SocialAccount, UUID> {

    List<SocialAccount> findByUserIdOrderByConnectedAtAsc(Long userId);

    Optional<SocialAccount> findByPlatformAndExternalId(Platform platform, String externalId);

    Optional<SocialAccount> findFirstByUserIdAndPlatformAndStatusOrderByConnectedAtAsc(
            Long userId, Platform platform, SocialAccountStatus status);
}
