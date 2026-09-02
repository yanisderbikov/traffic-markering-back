package ru.trafficmarkering.repository;

import ru.trafficmarkering.model.profile.CreatorProfile;

public interface SaverCreatorProfile {
    CreatorProfile save(CreatorProfile creatorProfile);
}
