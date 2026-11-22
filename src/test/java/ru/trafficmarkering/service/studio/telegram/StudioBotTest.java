package ru.trafficmarkering.service.studio.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import ru.trafficmarkering.config.StudioProperties;
import ru.trafficmarkering.model.Role;
import ru.trafficmarkering.model.User;
import ru.trafficmarkering.model.studio.StudioChatDraft;
import ru.trafficmarkering.model.studio.StudioProfile;
import ru.trafficmarkering.repository.StudioChatDraftRepository;
import ru.trafficmarkering.repository.StudioJobRepository;
import ru.trafficmarkering.repository.StudioProfileRepository;
import ru.trafficmarkering.repository.UserRepository;
import ru.trafficmarkering.service.studio.StudioService;
import ru.trafficmarkering.service.studio.StudioStorage;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class StudioBotTest {

    private final ObjectMapper json = new ObjectMapper();

    @Mock private TelegramClient client;
    @Mock private StudioService studio;
    @Mock private StudioNotifier notifier;
    @Mock private StudioStorage storage;
    @Mock private StudioProfileRepository profiles;
    @Mock private StudioChatDraftRepository drafts;
    @Mock private StudioJobRepository jobs;
    @Mock private UserRepository users;

    private StudioBot bot;
    private final User user = User.builder().id(7L).role(Role.CREATOR).name("Аня").username("anya@example.com").build();

    @BeforeEach
    void setUp() {
        bot = new StudioBot(client, studio, notifier, storage, profiles, drafts, jobs, users, new StudioProperties(),
                "https://dev.getoffer.now");
        when(users.findById(7L)).thenReturn(Optional.of(user));
        when(drafts.save(any())).thenAnswer(inv -> inv.getArgument(0));
    }

    private JsonNode message(String extra) throws Exception {
        return json.readTree("{\"update_id\":1,\"message\":{\"message_id\":10,\"chat\":{\"id\":555,\"type\":\"private\"},"
                + "\"from\":{\"id\":555,\"username\":\"anya\"}," + extra + "}}");
    }

    @Test
    void startWithLinkCodeConfirmsLink() throws Exception {
        when(studio.confirmTelegramLink("link_abc", 555L, "anya")).thenReturn(7L);
        bot.handle(message("\"text\":\"/start link_abc\""));
        verify(client).sendMessage(eq(555L), contains("привязан"), any(), isNull());
    }

    @Test
    void unlinkedUserGetsSiteButton() throws Exception {
        when(profiles.findByTelegramId(555L)).thenReturn(Optional.empty());
        bot.handle(message("\"text\":\"/jobs\""));
        ArgumentCaptor<Object> markup = ArgumentCaptor.forClass(Object.class);
        verify(client).sendMessage(eq(555L), contains("привяжите Telegram"), markup.capture(), isNull());
        assertThat(markup.getValue().toString()).contains("https://dev.getoffer.now/app/studio");
    }

    @Test
    void videoWithoutAutoTemplateOffersModes() throws Exception {
        when(profiles.findByTelegramId(555L)).thenReturn(Optional.of(StudioProfile.builder().userId(7L).telegramId(555L).build()));
        when(studio.profile(7L)).thenReturn(StudioProfile.builder().userId(7L).build());
        when(studio.ownTemplates(7L)).thenReturn(List.of());
        bot.handle(message("\"video\":{\"file_id\":\"F1\",\"file_size\":1000,\"file_name\":\"clip.mp4\"}"));
        ArgumentCaptor<Object> markup = ArgumentCaptor.forClass(Object.class);
        verify(client).sendMessage(eq(555L), contains("Как смонтировать"), markup.capture(), eq(10L));
        String buttons = markup.getValue().toString();
        assertThat(buttons).contains("9:16 · видео в круге").contains(":q:circle");
        verify(drafts).save(any(StudioChatDraft.class));
    }

    @Test
    void oversizedVideoIsRejectedBeforeDownload() throws Exception {
        when(profiles.findByTelegramId(555L)).thenReturn(Optional.of(StudioProfile.builder().userId(7L).telegramId(555L).build()));
        bot.handle(message("\"video\":{\"file_id\":\"F1\",\"file_size\":30000000}"));
        verify(client).sendMessage(eq(555L), contains("до 20 МБ"), any(), eq(10L));
        verify(client, never()).download(any(), any(), anyLong());
    }

    @Test
    void groupChatsAreIgnored() throws Exception {
        bot.handle(json.readTree("{\"update_id\":2,\"message\":{\"message_id\":1,\"chat\":{\"id\":-1,\"type\":\"group\"},"
                + "\"from\":{\"id\":555},\"text\":\"/start\"}}"));
        verify(client, never()).sendMessage(anyLong(), any(), any(), any());
    }

    @Test
    void quickModesMatchOriginalBot() {
        assertThat(StudioBot.QUICK_MODES.keySet()).containsExactly("small", "compact", "vertical", "circle");
        assertThat(StudioBot.QUICK_MODES.get("compact").settings().getSizeLimit()).isEqualTo(25);
        assertThat(StudioBot.QUICK_MODES.get("circle").settings().getShape()).isEqualTo("circle");
        assertThat(("d:" + java.util.UUID.randomUUID() + ":t:" + StudioBot.shortId(java.util.UUID.randomUUID())).length())
                .isLessThanOrEqualTo(64);
        assertThat(Map.of()).isEmpty();
    }
}
