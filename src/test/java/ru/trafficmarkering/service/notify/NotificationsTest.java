package ru.trafficmarkering.service.notify;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import ru.trafficmarkering.dto.notify.NotificationPayload;
import ru.trafficmarkering.model.Role;
import ru.trafficmarkering.model.User;
import ru.trafficmarkering.model.application.Application;
import ru.trafficmarkering.model.application.ApplicationStatus;
import ru.trafficmarkering.model.campaign.Campaign;
import ru.trafficmarkering.model.notify.BroadcastChannel;
import ru.trafficmarkering.model.studio.StudioProfile;
import ru.trafficmarkering.model.task.Task;
import ru.trafficmarkering.model.task.TaskStatus;
import ru.trafficmarkering.repository.BroadcastRepository;
import ru.trafficmarkering.repository.GetterTaskByStatus;
import ru.trafficmarkering.repository.SaverTask;
import ru.trafficmarkering.repository.StudioProfileRepository;
import ru.trafficmarkering.service.email.EmailService;
import ru.trafficmarkering.service.studio.telegram.TelegramClient;
import ru.trafficmarkering.service.task.TaskAdder;

import java.util.Map;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationsTest {

    private final TelegramClient telegram = mock(TelegramClient.class);
    private final EmailService email = mock(EmailService.class);
    private final NotificationSender sender = new NotificationSender(telegram, email, "https://site.example/");

    @Test
    void broadcastPrefersTelegramAndFallsBackToEmail() {
        var tg = BroadcastService.message(BroadcastChannel.ANY, 5L, "a@x.ru", "T", "B", null, null, 1L);
        assertThat(tg.getChatId()).isEqualTo(5L);
        assertThat(tg.getEmail()).isNull();
        var mail = BroadcastService.message(BroadcastChannel.ANY, null, "a@x.ru", "T", "B", null, null, 1L);
        assertThat(mail.getEmail()).isEqualTo("a@x.ru");
        assertThat(BroadcastService.message(BroadcastChannel.TELEGRAM, null, "a@x.ru", "T", "B", null, null, 1L)).isNull();
        assertThat(BroadcastService.message(BroadcastChannel.EMAIL, 5L, null, "T", "B", null, null, 1L)).isNull();
        assertThat(BroadcastService.message(BroadcastChannel.EMAIL, 5L, "a@x.ru", "T", "B", null, null, 1L).getEmail())
                .isEqualTo("a@x.ru");
        assertThat(BroadcastService.emailOf("tg:5")).isNull();
        assertThat(BroadcastService.emailOf("admin")).isNull();
    }

    @Test
    @SuppressWarnings("unchecked")
    void siteLinksOpenAsMiniAppInTelegram() {
        sender.send(NotificationPayload.builder().chatId(7L).title("Привет <b>").text("Текст").buttonUrl("/app/board").build());
        ArgumentCaptor<Object> markup = ArgumentCaptor.forClass(Object.class);
        verify(telegram).sendMessage(eq(7L), contains("Привет &lt;b&gt;"), markup.capture(), isNull());
        assertThat(markup.getValue().toString()).contains("web_app").contains("https://site.example/app/board");

        sender.send(NotificationPayload.builder().email("a@x.ru").title("Тема").text("Строка").build());
        verify(email).sendEmail(eq("a@x.ru"), eq("Тема"), contains("https://site.example/app"));
    }

    @Test
    void runnerCountsBroadcastDeliveriesAndBlockedBot() throws Exception {
        GetterTaskByStatus queue = mock(GetterTaskByStatus.class);
        SaverTask saver = mock(SaverTask.class);
        BroadcastRepository broadcasts = mock(BroadcastRepository.class);
        ObjectMapper json = new ObjectMapper();
        NotificationSender failing = mock(NotificationSender.class);
        doThrow(new TelegramClient.TelegramException(403, "blocked")).when(failing).send(any());
        var runner = new NotificationTaskRunner(queue, saver, json, failing, mock(BroadcastService.class), broadcasts, 25);

        Task task = Task.builder().status(TaskStatus.NEW)
                .payload(json.writeValueAsString(NotificationPayload.builder().chatId(1L).title("t").text("x")
                        .broadcastId(9L).build()))
                .build();
        runner.send(task);
        assertThat(task.getStatus()).isEqualTo(TaskStatus.FAILED);
        assertThat(task.getComment()).contains("заблокирован");
        verify(broadcasts).addResults(9L, 0, 1);
        verify(broadcasts).finishIfComplete(9L);

        doThrow(new TelegramClient.TelegramException(429, "slow down")).when(failing).send(any());
        Task limited = Task.builder().status(TaskStatus.NEW)
                .payload(json.writeValueAsString(NotificationPayload.builder().chatId(1L).broadcastId(10L).build()))
                .build();
        runner.send(limited);
        assertThat(limited.getStatus()).isEqualTo(TaskStatus.NEW);
        verify(broadcasts, never()).addResults(eq(10L), any(Integer.class), any(Integer.class));
    }

    @Test
    void approvedApplicationNotifiesCreatorInTelegram() {
        StudioProfileRepository profiles = mock(StudioProfileRepository.class);
        TaskAdder tasks = mock(TaskAdder.class);
        User creator = User.builder().id(2L).username("tg:77").name("Аня").role(Role.CREATOR).build();
        User customer = User.builder().id(1L).username("boss@x.ru").name("Босс").role(Role.CUSTOMER).build();
        StudioProfile profile = new StudioProfile();
        profile.setTelegramId(77L);
        when(profiles.findById(2L)).thenReturn(Optional.of(profile));
        when(profiles.findById(1L)).thenReturn(Optional.empty());
        var notifications = new ApplicationNotifications(new UserNotifier(profiles, tasks));
        Campaign campaign = Campaign.builder().title("Кроссовки").customer(customer).build();
        Application application = Application.builder().campaign(campaign).creator(creator)
                .status(ApplicationStatus.APPROVED).build();

        notifications.decided(application);
        ArgumentCaptor<NotificationPayload> sent = ArgumentCaptor.forClass(NotificationPayload.class);
        verify(tasks).addTask(sent.capture());
        assertThat(sent.getValue().getChatId()).isEqualTo(77L);
        assertThat(sent.getValue().getText()).contains("Кроссовки");

        notifications.submitted(application);
        verify(tasks, org.mockito.Mockito.times(2)).addTask(sent.capture());
        assertThat(sent.getValue().getEmail()).isEqualTo("boss@x.ru");
        assertThat(sent.getValue().getChatId()).isNull();
    }
}
