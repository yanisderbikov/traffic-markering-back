package ru.trafficmarkering.service.user;

import ru.trafficmarkering.model.User;

public interface AccountProvisioningService {

    void provision(User user);
}
