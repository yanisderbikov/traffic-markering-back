package ru.trafficmarkering.repository;

import ru.trafficmarkering.model.social.SocialAccount;

public interface SaverSocialAccount {
    SocialAccount save(SocialAccount socialAccount);
}
