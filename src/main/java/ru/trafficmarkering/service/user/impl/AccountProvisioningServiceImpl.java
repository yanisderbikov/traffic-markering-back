package ru.trafficmarkering.service.user.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import ru.trafficmarkering.model.User;
import ru.trafficmarkering.model.profile.CreatorProfile;
import ru.trafficmarkering.model.profile.CustomerProfile;
import ru.trafficmarkering.model.wallet.Wallet;
import ru.trafficmarkering.repository.GetterCreatorProfile;
import ru.trafficmarkering.repository.GetterCustomerProfile;
import ru.trafficmarkering.repository.GetterWallet;
import ru.trafficmarkering.repository.SaverCreatorProfile;
import ru.trafficmarkering.repository.SaverCustomerProfile;
import ru.trafficmarkering.repository.SaverWallet;
import ru.trafficmarkering.service.user.AccountProvisioningService;

@Service
@RequiredArgsConstructor
class AccountProvisioningServiceImpl implements AccountProvisioningService {

    private final GetterCreatorProfile getterCreatorProfile;
    private final SaverCreatorProfile saverCreatorProfile;
    private final GetterCustomerProfile getterCustomerProfile;
    private final SaverCustomerProfile saverCustomerProfile;
    private final GetterWallet getterWallet;
    private final SaverWallet saverWallet;

    @Override
    @Transactional
    public void provision(User user) {
        if (user.getRole() == null) {
            return;
        }
        switch (user.getRole()) {
            case CREATOR -> ensureCreatorProfile(user);
            case CUSTOMER -> {
                ensureCustomerProfile(user);
                ensureWallet(user);
            }
            default -> {
            }
        }
    }

    private void ensureCreatorProfile(User user) {
        if (getterCreatorProfile.getByUserId(user.getId()).isEmpty()) {
            saverCreatorProfile.save(CreatorProfile.builder().user(user).build());
        }
    }

    private void ensureCustomerProfile(User user) {
        if (getterCustomerProfile.getByUserId(user.getId()).isEmpty()) {
            saverCustomerProfile.save(CustomerProfile.builder().user(user).build());
        }
    }

    private void ensureWallet(User user) {
        if (getterWallet.getByUserId(user.getId()).isEmpty()) {
            saverWallet.save(Wallet.builder().user(user).build());
        }
    }
}
