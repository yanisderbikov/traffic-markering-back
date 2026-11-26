package ru.trafficmarkering.util;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class PromoLinksTest {

    private static final String CAMPAIGN = "https://example.com/app?utm_source=offer";

    @Test
    void matches_sameAddressWithCosmeticDifferences() {
        assertTrue(PromoLinks.matches(CAMPAIGN, "https://example.com/app?utm_source=offer"));
        assertTrue(PromoLinks.matches(CAMPAIGN, "example.com/app/?utm_source=offer"));
        assertTrue(PromoLinks.matches(CAMPAIGN, "https://WWW.Example.com/app?utm_source=offer&ref=tiktok"));
        assertTrue(PromoLinks.matches(CAMPAIGN, "http://example.com/app?utm_source=offer"));
    }

    @Test
    void matches_rejectsLookalikes() {
        assertFalse(PromoLinks.matches(CAMPAIGN, "https://example.com.evil/app?utm_source=offer"));
        assertFalse(PromoLinks.matches(CAMPAIGN, "https://evil.com/example.com/app?utm_source=offer"));
        assertFalse(PromoLinks.matches(CAMPAIGN, "https://example.com/apps?utm_source=offer"));
        assertFalse(PromoLinks.matches(CAMPAIGN, "https://example.com/app"));
        assertFalse(PromoLinks.matches(CAMPAIGN, "https://example.com/app?utm_source=other"));
        assertFalse(PromoLinks.matches(CAMPAIGN, "VPN example.com"));
    }

    @Test
    void matches_unwrapsPlatformRedirects() {
        assertTrue(PromoLinks.matches(CAMPAIGN,
                "https://l.instagram.com/?u=https%3A%2F%2Fexample.com%2Fapp%3Futm_source%3Doffer&e=AT0"));
        assertTrue(PromoLinks.matches(CAMPAIGN,
                "https://www.youtube.com/redirect?q=https%3A%2F%2Fexample.com%2Fapp%3Futm_source%3Doffer"));
        assertTrue(PromoLinks.containsMatch(CAMPAIGN, List.of("https://t.me/me", "example.com/app?utm_source=offer")));
        assertFalse(PromoLinks.containsMatch(CAMPAIGN, List.of()));
        assertFalse(PromoLinks.containsMatch(CAMPAIGN, null));
    }

    @Test
    void validateCampaignUrl() {
        assertTrue(PromoLinks.validateCampaignUrl("https://example.com/app?x=1").isEmpty());
        assertTrue(PromoLinks.validateCampaignUrl("http://example.com").isPresent());
        assertTrue(PromoLinks.validateCampaignUrl("https://user:pass@example.com").isPresent());
        assertTrue(PromoLinks.validateCampaignUrl("https://127.0.0.1/admin").isPresent());
        assertTrue(PromoLinks.validateCampaignUrl("https://localhost:8080").isPresent());
        assertTrue(PromoLinks.validateCampaignUrl("https://service.internal/x").isPresent());
        assertTrue(PromoLinks.validateCampaignUrl("javascript:alert(1)").isPresent());
        assertTrue(PromoLinks.validateCampaignUrl(" ").isPresent());
    }

    @Test
    void validatePromoCode() {
        assertTrue(PromoLinks.validatePromoCode("DEMO20").isEmpty());
        assertTrue(PromoLinks.validatePromoCode("скидка-20").isEmpty());
        assertTrue(PromoLinks.validatePromoCode("DEMO 20").isPresent());
        assertTrue(PromoLinks.validatePromoCode("D").isPresent());
    }
}
