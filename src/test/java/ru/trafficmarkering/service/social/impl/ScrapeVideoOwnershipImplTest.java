package ru.trafficmarkering.service.social.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import ru.trafficmarkering.model.Role;
import ru.trafficmarkering.model.User;
import ru.trafficmarkering.model.application.Platform;
import ru.trafficmarkering.model.social.SocialAccount;
import ru.trafficmarkering.repository.GetterSocialAccount;
import ru.trafficmarkering.service.scrape.ScrapeCreatorsApi;
import ru.trafficmarkering.service.scrape.ScrapeCreatorsException;
import ru.trafficmarkering.service.scrape.ScrapeVideo;
import ru.trafficmarkering.service.social.SocialIntegration;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class ScrapeVideoOwnershipImplTest {

    private static final String URL = "https://www.instagram.com/reel/DLDXI0fylTC/";

    private final ScrapeCreatorsApi api = mock(ScrapeCreatorsApi.class);
    private final GetterSocialAccount getterSocialAccount = mock(GetterSocialAccount.class);
    private final User creator = User.builder().id(2L).role(Role.CREATOR).build();

    private ScrapeVideoOwnershipImpl ownership(boolean scrape) {
        return new ScrapeVideoOwnershipImpl(new SocialIntegration(scrape), api, getterSocialAccount);
    }

    @BeforeEach
    void setUp() {
        when(api.isConfigured()).thenReturn(true);
        when(getterSocialAccount.getAllActiveByUserIdAndPlatform(2L, Platform.INSTAGRAM)).thenReturn(List.of(
                SocialAccount.builder().externalId("111").username("first").build(),
                SocialAccount.builder().externalId("222").username("second").build()));
    }

    @Test
    void acceptsVideoFromAnyLinkedAccount() {
        when(api.video(Platform.INSTAGRAM, URL))
                .thenReturn(new ScrapeVideo("222", "second", 10L, null, null, null, null, null));

        assertThatCode(() -> ownership(true).requireOwnedBy(creator, Platform.INSTAGRAM, URL)).doesNotThrowAnyException();
    }

    @Test
    void rejectsSomeoneElsesVideo() {
        when(api.video(Platform.INSTAGRAM, URL))
                .thenReturn(new ScrapeVideo("999", "famous", 10_000_000L, null, null, null, null, null));

        assertThatThrownBy(() -> ownership(true).requireOwnedBy(creator, Platform.INSTAGRAM, URL))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.getReason()).contains("@famous");
                });
    }

    @Test
    void unavailableVideoIsRejectedAndOutageIsBadGateway() {
        when(api.video(Platform.INSTAGRAM, URL)).thenThrow(new ScrapeCreatorsException(404, "not_found"));
        assertThatThrownBy(() -> ownership(true).requireOwnedBy(creator, Platform.INSTAGRAM, URL))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));

        doThrow(new ScrapeCreatorsException(0, "timeout")).when(api).video(Platform.INSTAGRAM, URL);
        assertThatThrownBy(() -> ownership(true).requireOwnedBy(creator, Platform.INSTAGRAM, URL))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_GATEWAY));
    }

    /** Свой ScrapeCreators отвечает 400 на ссылку, которую не может прочитать: это ошибка креатора, а не сбой. */
    @Test
    void unreadableLinkIsRejected() {
        when(api.video(Platform.INSTAGRAM, URL)).thenThrow(new ScrapeCreatorsException(400, "not a link to a post"));

        assertThatThrownBy(() -> ownership(true).requireOwnedBy(creator, Platform.INSTAGRAM, URL))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.getReason()).contains("ссылку на сам ролик");
                });
    }

    @Test
    void oauthModeSkipsCheck() {
        ownership(false).requireOwnedBy(creator, Platform.INSTAGRAM, URL);

        verify(api, never()).video(any(), any());
    }
}
