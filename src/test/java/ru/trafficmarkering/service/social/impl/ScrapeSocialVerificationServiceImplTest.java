package ru.trafficmarkering.service.social.impl;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import ru.trafficmarkering.dto.social.SocialAccountDTO;
import ru.trafficmarkering.dto.social.SocialVerificationDTO;
import ru.trafficmarkering.model.Role;
import ru.trafficmarkering.model.User;
import ru.trafficmarkering.model.application.Platform;
import ru.trafficmarkering.model.social.SocialAccount;
import ru.trafficmarkering.model.social.SocialAccountStatus;
import ru.trafficmarkering.model.social.SocialLinkMethod;
import ru.trafficmarkering.repository.GetterSocialAccount;
import ru.trafficmarkering.repository.SaverSocialAccount;
import ru.trafficmarkering.service.auth.CurrentUserService;
import ru.trafficmarkering.service.scrape.ProfileRef;
import ru.trafficmarkering.service.scrape.ScrapeCreatorsApi;
import ru.trafficmarkering.service.scrape.ScrapeCreatorsException;
import ru.trafficmarkering.service.scrape.ScrapeProfile;
import ru.trafficmarkering.service.social.SocialIntegration;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static ru.trafficmarkering.service.scrape.ScrapeProfile.Privacy.PRIVATE;
import static ru.trafficmarkering.service.scrape.ScrapeProfile.Privacy.PUBLIC;
import static ru.trafficmarkering.service.scrape.ScrapeProfile.Privacy.UNKNOWN;

class ScrapeSocialVerificationServiceImplTest {

    private static final ProfileRef REF = new ProfileRef(ProfileRef.Kind.HANDLE, "creator");

    private final ScrapeCreatorsApi api = mock(ScrapeCreatorsApi.class);
    private final CurrentUserService currentUserService = mock(CurrentUserService.class);
    private final GetterSocialAccount getterSocialAccount = mock(GetterSocialAccount.class);
    private final SaverSocialAccount saverSocialAccount = mock(SaverSocialAccount.class);
    private final VerificationCodes codes = new VerificationCodes("test-secret-test-secret-test-secret");

    private final User creator = User.builder().id(2L).name("Криатор").role(Role.CREATOR).build();
    private final String code = codes.codeFor(2L, Platform.TIKTOK, "tt-1");

    private ScrapeSocialVerificationServiceImpl service(boolean scrape) {
        return new ScrapeSocialVerificationServiceImpl(new SocialIntegration(scrape), api, currentUserService,
                getterSocialAccount, saverSocialAccount, codes);
    }

    private static ScrapeProfile profile(String bio, ScrapeProfile.Privacy privacy) {
        return new ScrapeProfile("tt-1", "creator", "Creator", "https://cdn/a.jpg", 1500L, bio, privacy);
    }

    @BeforeEach
    void setUp() {
        when(api.isConfigured()).thenReturn(true);
        when(currentUserService.require(Role.CREATOR)).thenReturn(creator);
        when(getterSocialAccount.getByPlatformAndExternalId(Platform.TIKTOK, "tt-1")).thenReturn(Optional.empty());
        when(saverSocialAccount.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    @Test
    void start_returnsStableCodeForProfile() {
        when(api.profile(Platform.TIKTOK, REF)).thenReturn(profile("просто описание", PUBLIC));

        SocialVerificationDTO first = service(true).start("tiktok", "@creator");
        SocialVerificationDTO second = service(true).start("tiktok", "https://www.tiktok.com/@creator");

        assertThat(first.code()).startsWith("OFFER-").hasSize(12).isEqualTo(second.code());
        assertThat(first.codeFound()).isFalse();
        assertThat(first.followers()).isEqualTo(1500L);
        assertThat(codes.codeFor(3L, Platform.TIKTOK, "tt-1")).isNotEqualTo(first.code());
    }

    @Test
    void confirm_linksAccountWhenCodeIsInBio() {
        String typedByHand = code.toLowerCase().replace("-", " ");
        when(api.profile(Platform.TIKTOK, REF)).thenReturn(profile("Блогер 🎬 " + typedByHand, PUBLIC));

        SocialAccountDTO linked = service(true).confirm("tiktok", "creator");

        assertThat(linked.linkMethod()).isEqualTo("SCRAPE");
        assertThat(linked.status()).isEqualTo("ACTIVE");
        assertThat(linked.externalId()).isEqualTo("tt-1");
        verify(saverSocialAccount).save(any(SocialAccount.class));
    }

    @Test
    void confirm_withoutCodeIsRejected() {
        when(api.profile(Platform.TIKTOK, REF)).thenReturn(profile("нет кода", PUBLIC));

        assertThatThrownBy(() -> service(true).confirm("tiktok", "creator"))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.getReason()).contains(code);
                });
        verify(saverSocialAccount, never()).save(any());
    }

    @Test
    void confirm_rejectsPrivateProfileAndForeignLink() {
        when(api.profile(Platform.TIKTOK, REF)).thenReturn(profile(code, PRIVATE));
        assertThatThrownBy(() -> service(true).confirm("tiktok", "creator"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));

        when(api.profile(Platform.TIKTOK, REF)).thenReturn(profile(code, PUBLIC));
        User other = User.builder().id(99L).role(Role.CREATOR).build();
        when(getterSocialAccount.getByPlatformAndExternalId(Platform.TIKTOK, "tt-1")).thenReturn(Optional.of(
                SocialAccount.builder().user(other).platform(Platform.TIKTOK).externalId("tt-1")
                        .linkMethod(SocialLinkMethod.SCRAPE).status(SocialAccountStatus.ACTIVE).build()));
        assertThatThrownBy(() -> service(true).confirm("tiktok", "creator"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.CONFLICT));
        verify(saverSocialAccount, never()).save(any());
    }

    /** Свой ScrapeCreators не знает, закрыт ли Instagram без embed: код не выдаём и не принимаем, подсказываем. */
    @Test
    void unknownPrivacyIsRejectedWithHint() {
        when(api.profile(Platform.INSTAGRAM, REF)).thenReturn(profile(code, UNKNOWN));

        assertThatThrownBy(() -> service(true).start("instagram", "creator"))
                .isInstanceOfSatisfying(ResponseStatusException.class, e -> {
                    assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST);
                    assertThat(e.getReason()).contains("@creator", "встраивание");
                });
        assertThatThrownBy(() -> service(true).confirm("instagram", "creator"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.BAD_REQUEST));
        verify(saverSocialAccount, never()).save(any());
    }

    @Test
    void unknownProfileIs404() {
        when(api.profile(Platform.TIKTOK, REF)).thenThrow(new ScrapeCreatorsException(404, "not_found"));

        assertThatThrownBy(() -> service(true).start("tiktok", "creator"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    /** Свой ScrapeCreators отвечает 400 на ник не по правилам площадки — такого аккаунта нет, а не сбой. */
    @Test
    void rejectedHandleIs404() {
        when(api.profile(Platform.TIKTOK, REF)).thenThrow(new ScrapeCreatorsException(400, "not a TikTok username"));

        assertThatThrownBy(() -> service(true).start("tiktok", "creator"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
    }

    @Test
    void disabledFlagClosesVerification() {
        assertThatThrownBy(() -> service(false).start("tiktok", "creator"))
                .isInstanceOfSatisfying(ResponseStatusException.class,
                        e -> assertThat(e.getStatusCode()).isEqualTo(HttpStatus.NOT_FOUND));
        verify(api, never()).profile(any(), any());
    }
}
