package ru.trafficmarkering.service.fraud.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.trafficmarkering.model.application.Application;
import ru.trafficmarkering.model.application.ApplicationViewSnapshot;
import ru.trafficmarkering.model.fraud.FraudStatus;
import ru.trafficmarkering.model.social.SocialAccount;
import ru.trafficmarkering.repository.GetterSocialAccount;
import ru.trafficmarkering.repository.GetterViewSnapshot;
import ru.trafficmarkering.repository.SaverApplication;
import ru.trafficmarkering.service.fraud.FraudAssessment;
import ru.trafficmarkering.service.fraud.FraudCheckService;
import ru.trafficmarkering.service.fraud.FraudScorer;

import java.time.Instant;
import java.util.List;

@Service
@RequiredArgsConstructor
@Log4j2
class FraudCheckServiceImpl implements FraudCheckService {

    private final FraudScorer scorer;
    private final GetterViewSnapshot getterViewSnapshot;
    private final GetterSocialAccount getterSocialAccount;
    private final SaverApplication saverApplication;

    @Override
    @Transactional
    public boolean check(Application application) {
        List<ApplicationViewSnapshot> snapshots = getterViewSnapshot.getByApplicationId(application.getId());
        Long followers = application.getCreator() != null && application.getPlatform() != null
                ? getterSocialAccount.getActiveByUserIdAndPlatform(application.getCreator().getId(), application.getPlatform())
                        .map(SocialAccount::getFollowers)
                        .orElse(null)
                : null;

        FraudAssessment assessment = scorer.assess(application, application.getCampaign(), snapshots, followers,
                Instant.now());
        FraudStatus before = application.fraudStatus();
        application.setFraudScore(assessment.score());
        application.setFraudFlags(assessment.flags());
        application.setFraudCheckedAt(Instant.now());
        if (!application.isFraudReviewed()) {
            application.setFraudStatus(scorer.statusFor(assessment.score()));
        }
        saverApplication.save(application);

        FraudStatus after = application.fraudStatus();
        if (before != after) {
            log.info("Антифрод: отклик {} {} → {} ({} баллов: {})", application.getPublicId(), before, after,
                    assessment.score(), assessment.flags().stream().map(flag -> flag.code()).toList());
        }
        return before.blocksAccrual() != after.blocksAccrual();
    }
}
