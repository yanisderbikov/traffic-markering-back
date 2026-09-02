package ru.trafficmarkering.repository;

import ru.trafficmarkering.model.profile.CustomerProfile;

public interface SaverCustomerProfile {
    CustomerProfile save(CustomerProfile customerProfile);
}
