package ru.trafficmarkering.repository.impl;

import lombok.AllArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import ru.trafficmarkering.model.application.Platform;
import ru.trafficmarkering.model.social.SocialAccount;
import ru.trafficmarkering.model.social.SocialAccountStatus;
import ru.trafficmarkering.repository.GetterSocialAccount;
import ru.trafficmarkering.repository.SaverSocialAccount;
import ru.trafficmarkering.repository.SocialAccountDeleter;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Component
@AllArgsConstructor
@Log4j2
class SocialAccountManager implements GetterSocialAccount, SaverSocialAccount, SocialAccountDeleter {

    private final SocialAccountRepo socialAccountRepo;

    @Override
    public List<SocialAccount> getByUserId(Long userId) {
        if (userId == null) {
            return List.of();
        }
        try {
            return socialAccountRepo.findByUserIdOrderByConnectedAtAsc(userId);
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }

    @Override
    public Optional<SocialAccount> getById(UUID id) {
        if (id == null) {
            return Optional.empty();
        }
        try {
            return socialAccountRepo.findById(id);
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }

    @Override
    public Optional<SocialAccount> getByPlatformAndExternalId(Platform platform, String externalId) {
        if (platform == null || externalId == null) {
            return Optional.empty();
        }
        try {
            return socialAccountRepo.findByPlatformAndExternalId(platform, externalId);
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }

    @Override
    public Optional<SocialAccount> getActiveByUserIdAndPlatform(Long userId, Platform platform) {
        if (userId == null || platform == null) {
            return Optional.empty();
        }
        try {
            return socialAccountRepo.findFirstByUserIdAndPlatformAndStatusOrderByConnectedAtAsc(
                    userId, platform, SocialAccountStatus.ACTIVE);
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }

    @Override
    public SocialAccount save(SocialAccount socialAccount) {
        try {
            return socialAccountRepo.saveAndFlush(socialAccount);
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }

    @Override
    public void deleteById(UUID id) {
        try {
            socialAccountRepo.deleteById(id);
        } catch (Exception e) {
            log.error(e);
            throw new RuntimeException("Database exception", e);
        }
    }
}
