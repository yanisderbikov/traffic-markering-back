package ru.trafficmarkering.service.views.impl;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import ru.trafficmarkering.model.application.Application;
import ru.trafficmarkering.model.application.ApplicationStatus;
import ru.trafficmarkering.model.campaign.Campaign;
import ru.trafficmarkering.model.campaign.Region;
import ru.trafficmarkering.repository.GetterApplication;
import ru.trafficmarkering.repository.GetterCampaign;
import ru.trafficmarkering.repository.SaverApplication;
import ru.trafficmarkering.service.campaign.CampaignAccrualService;
import ru.trafficmarkering.service.geo.VideoGeoViews;
import ru.trafficmarkering.util.RegionViewsCalculator;

import java.time.Instant;
import java.util.Objects;
import java.util.UUID;

/** Short transactions after provider calls; never merge the pre-fetch entities. */
@Service
@RequiredArgsConstructor
@Transactional(propagation = Propagation.REQUIRES_NEW)
class ViewsSyncResultWriter {
    private final GetterCampaign getterCampaign;
    private final GetterApplication getterApplication;
    private final SaverApplication saverApplication;
    private final CampaignAccrualService campaignAccrualService;

    public boolean applyViews(Application original, long views, Instant capturedAt) {
        Campaign campaign = getterCampaign.getByIdForUpdate(original.getCampaign().getId()).orElse(null);
        if (campaign == null) {
            return false;
        }
        Application current = currentApplication(original);
        if (current == null) {
            return false;
        }
        long previous = current.getViews() != null ? current.getViews() : 0L;
        current.setViews(Math.max(previous, views));
        current.setViewsSyncedAt(capturedAt);
        saverApplication.save(current);
        return current.getViews() != previous;
    }

    public boolean applyGeoViews(Application original, VideoGeoViews geoViews) {
        Campaign campaign = getterCampaign.getByIdForUpdate(original.getCampaign().getId()).orElse(null);
        if (campaign == null || campaign.getRegion() == null || campaign.getRegion() == Region.WORLDWIDE) {
            return false;
        }
        Application current = currentApplication(original);
        if (current == null) {
            return false;
        }
        long rawViews = current.getViews() != null ? current.getViews() : 0L;
        long regionViews = RegionViewsCalculator.viewsForRegion(
                campaign.getRegion(), geoViews.viewsByCountry(), rawViews);
        if (Objects.equals(current.getRegionViews(), regionViews)) {
            return false;
        }
        current.setRegionViews(regionViews);
        saverApplication.save(current);
        return true;
    }

    public void recalculate(UUID campaignId) {
        getterCampaign.getByIdForUpdate(campaignId).ifPresent(campaignAccrualService::recalculate);
    }

    private Application currentApplication(Application original) {
        return getterApplication.getById(original.getId())
                .filter(current -> current.getStatus() == ApplicationStatus.APPROVED)
                .filter(current -> current.getCampaign().getId().equals(original.getCampaign().getId()))
                .filter(current -> Objects.equals(current.getVideoUrl(), original.getVideoUrl()))
                .orElse(null);
    }
}
