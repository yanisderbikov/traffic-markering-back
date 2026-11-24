package ru.trafficmarkering.service.studio.telegram;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import ru.trafficmarkering.config.StudioProperties;
import ru.trafficmarkering.model.studio.StudioJob;
import ru.trafficmarkering.model.studio.StudioJobOrigin;
import ru.trafficmarkering.model.studio.StudioJobStatus;
import ru.trafficmarkering.model.studio.StudioProfile;
import ru.trafficmarkering.repository.StudioProfileRepository;
import ru.trafficmarkering.service.storage.FileStorage;
import ru.trafficmarkering.service.studio.StudioService;
import ru.trafficmarkering.service.studio.StudioSettings;
import ru.trafficmarkering.service.studio.StudioStorage;

import java.util.Optional;
import java.util.UUID;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class StudioNotifierTest {

    private final TelegramClient client = mock(TelegramClient.class);
    private final FileStorage fileStorage = mock(FileStorage.class);
    private final StudioService studio = mock(StudioService.class);
    private final StudioProfileRepository profiles = mock(StudioProfileRepository.class);
    private final StudioNotifier notifier = new StudioNotifier(client, new StudioProperties(), fileStorage,
            mock(StudioStorage.class), studio, profiles);

    private StudioJob webJob() {
        return StudioJob.builder().id(UUID.randomUUID()).userId(7L).origin(StudioJobOrigin.WEB)
                .status(StudioJobStatus.DONE).filename("clip.mp4").resultKey("r/x.mp4").resultSize(10L).build();
    }

    @Test
    void webVideoGoesToLinkedTelegram() {
        when(client.enabled()).thenReturn(true);
        when(studio.settingsOf(any())).thenReturn(new StudioSettings());
        when(fileStorage.presignedUrl(anyString(), anyString(), anyBoolean())).thenReturn("https://s/x");
        StudioProfile profile = new StudioProfile();
        profile.setTelegramId(555L);
        when(profiles.findById(7L)).thenReturn(Optional.of(profile));

        notifier.finished(webJob(), null);

        verify(client).sendMessage(eq(555L), contains("clip.mp4"), any(), isNull());
    }

    @Test
    void webVideoWithoutTelegramStaysOnSite() {
        when(client.enabled()).thenReturn(true);
        when(profiles.findById(7L)).thenReturn(Optional.empty());

        notifier.finished(webJob(), null);

        verify(client, never()).sendMessage(anyLong(), anyString(), any(), any());
    }

    @Test
    void progressMessageThenFileAsNewMessage() throws Exception {
        when(client.enabled()).thenReturn(true);
        when(studio.settingsOf(any())).thenReturn(new StudioSettings());
        when(fileStorage.presignedUrl(anyString(), anyString(), anyBoolean())).thenReturn("https://s/x");
        StudioProfile profile = new StudioProfile();
        profile.setTelegramId(555L);
        when(profiles.findById(7L)).thenReturn(Optional.of(profile));
        when(client.sendMessage(eq(555L), contains("Собираю"), any(), isNull()))
                .thenReturn(new ObjectMapper().readTree("{\"message_id\": 42}"));

        StudioJob job = webJob();
        StudioNotifier.Progress progress = notifier.started(job);
        assertThat(progress).isNotNull();
        progress.update(50); // раньше 3 секунд — не правим, бережём лимит Telegram
        verify(client, never()).tryEditMessage(anyLong(), anyLong(), anyString());

        notifier.finished(job, null, progress);
        verify(client).tryEditMessage(eq(555L), eq(42L), contains("Собрано"));
        verify(client).sendMessage(eq(555L), contains("Готово"), any(), isNull());
        assertThat(StudioNotifier.progressText("a.mp4", 40)).contains("▓▓▓▓░░░░░░ 40%");
    }
}
