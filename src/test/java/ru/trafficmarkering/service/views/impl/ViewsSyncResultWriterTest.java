package ru.trafficmarkering.service.views.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import ru.trafficmarkering.model.application.Application;
import ru.trafficmarkering.model.application.ApplicationStatus;
import ru.trafficmarkering.model.campaign.Campaign;
import ru.trafficmarkering.model.campaign.Region;
import ru.trafficmarkering.repository.GetterApplication;
import ru.trafficmarkering.repository.GetterCampaign;
import ru.trafficmarkering.repository.SaverApplication;
import ru.trafficmarkering.service.campaign.CampaignAccrualService;
import ru.trafficmarkering.service.geo.VideoGeoViews;

import java.time.Instant;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

class ViewsSyncResultWriterTest {
    private final GetterCampaign campaigns = mock(GetterCampaign.class);
    private final GetterApplication applications = mock(GetterApplication.class);
    private final SaverApplication saver = mock(SaverApplication.class);
    private final CampaignAccrualService accrual = mock(CampaignAccrualService.class);
    private final ViewsSyncResultWriter writer = new ViewsSyncResultWriter(campaigns, applications, saver, accrual);
    private Campaign currentCampaign;
    private Application original;
    private Application current;

    @BeforeEach
    void setup() {
        UUID campaignId = UUID.randomUUID();
        Campaign oldCampaign = Campaign.builder().id(campaignId).region(Region.RUSSIA).build();
        currentCampaign = Campaign.builder().id(campaignId).region(Region.CIS).build();
        original = Application.builder().id(UUID.randomUUID()).campaign(oldCampaign)
                .videoUrl("video").views(100L).regionViews(20L).status(ApplicationStatus.APPROVED).build();
        current = Application.builder().id(original.getId()).campaign(currentCampaign)
                .videoUrl("video").views(200L).regionViews(null).status(ApplicationStatus.APPROVED).build();
        when(campaigns.getByIdForUpdate(campaignId)).thenReturn(Optional.of(currentCampaign));
        lenient().when(applications.getById(original.getId())).thenReturn(Optional.of(current));
    }

    @Test
    void appliesGeoUsingCurrentRegionAndApplicationAfterReset() {
        assertTrue(writer.applyGeoViews(original, new VideoGeoViews(200, Map.of("RU", 20L, "BY", 130L))));
        assertEquals(150L, current.getRegionViews());
        assertEquals(20L, original.getRegionViews());
        verify(saver).save(current);
        verify(saver, never()).save(original);
    }

    @Test
    void worldwideChangeKeepsRegionViewsReset() {
        currentCampaign.setRegion(Region.WORLDWIDE);
        assertFalse(writer.applyGeoViews(original, new VideoGeoViews(200, Map.of("RU", 20L))));
        assertNull(current.getRegionViews());
        verifyNoInteractions(saver);
    }

    @Test
    void comparesAgainstResetValueEvenWhenResultEqualsDetachedValue() {
        currentCampaign.setRegion(Region.RUSSIA);
        assertTrue(writer.applyGeoViews(original, new VideoGeoViews(200, Map.of("RU", 20L))));
        assertEquals(20L, current.getRegionViews());
        verify(saver).save(current);
    }

    @Test
    void totalViewsUpdateDoesNotRestoreDetachedRegionViews() {
        assertTrue(writer.applyViews(original, 300L, Instant.now()));
        assertNull(current.getRegionViews());
        assertEquals(300L, current.getViews());
        verify(saver).save(current);
    }

    @Test
    void recalculationUsesCurrentLockedCampaign() {
        writer.recalculate(currentCampaign.getId());
        verify(accrual).recalculate(currentCampaign);
    }
}
