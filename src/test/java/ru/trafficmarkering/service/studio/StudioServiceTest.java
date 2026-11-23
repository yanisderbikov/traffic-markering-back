package ru.trafficmarkering.service.studio;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import ru.trafficmarkering.config.StudioProperties;
import ru.trafficmarkering.dto.studio.StudioJobRequestDTO;
import ru.trafficmarkering.model.Role;
import ru.trafficmarkering.model.User;
import ru.trafficmarkering.model.studio.StudioJob;
import ru.trafficmarkering.model.studio.StudioJobStatus;
import ru.trafficmarkering.model.studio.StudioProfile;
import ru.trafficmarkering.repository.StudioAssetRepository;
import ru.trafficmarkering.repository.StudioJobRepository;
import ru.trafficmarkering.repository.StudioProfileRepository;
import ru.trafficmarkering.repository.StudioTemplateRepository;
import ru.trafficmarkering.service.storage.FileStorage;
import ru.trafficmarkering.service.studio.telegram.StudioBotInfo;
import software.amazon.awssdk.services.s3.model.HeadObjectResponse;

import java.time.Instant;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StudioServiceTest {

    private static final String SOURCE = "studio/uploads/7/3f1c2d4e-5a6b-4c7d-8e9f-0a1b2c3d4e5f.mp4";

    @Mock private StudioJobRepository jobs;
    @Mock private StudioAssetRepository assets;
    @Mock private StudioTemplateRepository templates;
    @Mock private StudioProfileRepository profiles;
    @Mock private StudioStorage storage;
    @Mock private FileStorage fileStorage;
    @Mock private StudioBotInfo botInfo;
    @Mock private StudioRendererHealth health;
    @Mock private ru.trafficmarkering.repository.UserRepository users;

    private StudioService service;
    private User user;
    private StudioProfile profile;

    @BeforeEach
    void setUp() {
        StudioProperties properties = new StudioProperties();
        service = new StudioService(jobs, assets, templates, profiles, storage, fileStorage, properties, botInfo, health,
                new ObjectMapper(), "https://back.example", users, new ru.trafficmarkering.service.auth.telegram.TelegramIdentity(profiles, users));
        user = User.builder().id(7L).role(Role.CREATOR).name("Аня").username("anya@example.com").build();
        profile = StudioProfile.builder().userId(7L).build();
        when(users.findById(7L)).thenReturn(Optional.of(user));
        when(profiles.findById(7L)).thenReturn(Optional.of(profile));
        when(profiles.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(jobs.save(any())).thenAnswer(inv -> inv.getArgument(0));
        when(storage.head(SOURCE)).thenReturn(Optional.of(HeadObjectResponse.builder().contentLength(1000L).build()));
    }

    private StudioJobRequestDTO request(StudioSettings settings) {
        return new StudioJobRequestDTO(SOURCE, "clip.mp4", settings, null, null, null, null, null);
    }

    private static StudioSettings vertical() {
        StudioSettings s = new StudioSettings();
        s.setFormat("vertical");
        return s;
    }

    @Test
    void createsQueuedJobFromOwnUpload() {
        var created = service.createFromWeb(user, request(vertical()));
        assertThat(created).hasSize(1);
        assertThat(created.get(0).status()).isEqualTo("QUEUED");
        assertThat(created.get(0).settings().getFormat()).isEqualTo("vertical");
    }

    @Test
    void rejectsForeignUploadKeys() {
        StudioJobRequestDTO foreign = new StudioJobRequestDTO("studio/uploads/8/3f1c2d4e-5a6b-4c7d-8e9f-0a1b2c3d4e5f.mp4",
                "clip.mp4", vertical(), null, null, null, null, null);
        assertThatThrownBy(() -> service.createFromWeb(user, foreign)).isInstanceOf(StudioException.class);
        assertThat(StudioStorage.ownedUpload("studio/uploads/7/../8/x.mp4", 7L)).isFalse();
    }

    @Test
    void enforcesDailyLimitAndActiveSlots() {
        when(jobs.countUsedSince(eq(7L), any())).thenReturn(5L);
        assertThatThrownBy(() -> service.createFromWeb(user, request(vertical()))).hasMessageContaining("Дневной лимит");

        when(jobs.countUsedSince(eq(7L), any())).thenReturn(0L);
        when(jobs.countByUserIdAndStatusIn(eq(7L), any())).thenReturn(2L);
        assertThatThrownBy(() -> service.createFromWeb(user, request(vertical()))).hasMessageContaining("Одновременно");
    }

    @Test
    void adminsHaveNoLimits() {
        user.setRole(Role.ADMIN);
        when(jobs.countUsedSince(eq(7L), any())).thenReturn(500L);
        when(jobs.countByUserIdAndStatusIn(eq(7L), any())).thenReturn(50L);
        assertThat(service.createFromWeb(user, request(vertical()))).hasSize(1);
    }

    @Test
    void verticalSquareProfileCreatesBatchOfTwo() {
        StudioSettings s = vertical();
        s.setExportProfile("vertical_square");
        var created = service.createFromWeb(user, request(s));
        assertThat(created).extracting(j -> j.settings().getFormat()).containsExactly("vertical", "square");
        assertThat(created.get(0).batchId()).isNotNull().isEqualTo(created.get(1).batchId());
    }

    @Test
    void bannerModeNeedsBannerFile() {
        StudioSettings s = vertical();
        s.setMode("overlay");
        assertThatThrownBy(() -> service.createFromWeb(user, request(s))).hasMessageContaining("баннера");
    }

    @Test
    void randomPresetRotatesWithoutRepeats() {
        Set<String> seen = new HashSet<>();
        for (int i = 0; i < StudioSettings.PRESETS.size(); i++) {
            seen.add(service.nextPreset(profile));
        }
        assertThat(seen).containsExactlyInAnyOrderElementsOf(StudioSettings.PRESETS);
        assertThat(profile.getPresetQueue()).isEmpty();
    }

    @Test
    void retryRequiresKeptSources() {
        UUID id = UUID.randomUUID();
        StudioJob job = StudioJob.builder().id(id).userId(7L).status(StudioJobStatus.DONE)
                .origin(ru.trafficmarkering.model.studio.StudioJobOrigin.WEB).filename("clip.mp4")
                .settings("{\"format\":\"vertical\"}").sourceKey(SOURCE).createdAt(Instant.now()).build();
        when(jobs.findByIdAndUserId(id, 7L)).thenReturn(Optional.of(job));
        assertThatThrownBy(() -> service.retry(user, id, null)).hasMessageContaining("удалены");

        job.setInputsUntil(Instant.now().plusSeconds(3600));
        var retried = service.retry(user, id, "720");
        assertThat(retried.settings().getQuality()).isEqualTo("720");
        assertThat(job.getInputsUntil()).isNull();
    }

    @Test
    void cancelReleasesTemporaryInputs() {
        UUID id = UUID.randomUUID();
        StudioJob job = StudioJob.builder().id(id).userId(7L).status(StudioJobStatus.QUEUED)
                .origin(ru.trafficmarkering.model.studio.StudioJobOrigin.WEB).filename("clip.mp4")
                .settings("{}").sourceKey(SOURCE).bannerKey("studio/assets/7/banner.png").createdAt(Instant.now()).build();
        when(jobs.findByIdAndUserId(id, 7L)).thenReturn(Optional.of(job));
        assertThat(service.cancel(user, id).status()).isEqualTo("CANCELLED");
        verify(storage).delete(SOURCE);
        verify(storage, never()).delete("studio/assets/7/banner.png");
    }

    @Test
    void telegramLinkCodeIsOneTimeAndMovesTelegramBetweenAccounts() {
        when(botInfo.username()).thenReturn("offer_studio_bot");
        var link = service.createTelegramLink(user);
        assertThat(link.url()).startsWith("https://t.me/offer_studio_bot?start=link_");
        String code = link.url().substring(link.url().indexOf("start=") + 6);

        StudioProfile other = StudioProfile.builder().userId(9L).telegramId(555L).build();
        when(profiles.findByLinkCode(code)).thenReturn(Optional.of(profile));
        when(profiles.findByTelegramId(555L)).thenReturn(Optional.of(other));
        assertThat(service.confirmTelegramLink(code, 555L, "anya")).isEqualTo(7L);
        assertThat(profile.getTelegramId()).isEqualTo(555L);
        assertThat(profile.getLinkCode()).isNull();
        assertThat(other.getTelegramId()).isNull();

        when(profiles.findByLinkCode("link_expired")).thenReturn(Optional.of(StudioProfile.builder()
                .userId(7L).linkCode("link_expired").linkCodeExpiresAt(Instant.now().minusSeconds(1)).build()));
        assertThatThrownBy(() -> service.confirmTelegramLink("link_expired", 1L, null)).hasMessageContaining("устарела");
    }

    @Test
    void downloadNameKeepsOriginalBase() {
        StudioJob job = StudioJob.builder().filename("Мой ролик.MOV").build();
        StudioSettings s = vertical();
        assertThat(StudioService.downloadName(job, s)).isEqualTo("Мой ролик-offer-vertical.mp4");
        assertThat(StudioService.trimName("C:\\fakepath\\clip.mp4")).isEqualTo("clip.mp4");
    }
}
