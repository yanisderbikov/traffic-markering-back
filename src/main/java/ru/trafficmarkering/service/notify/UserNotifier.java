package ru.trafficmarkering.service.notify;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import ru.trafficmarkering.dto.notify.NotificationPayload;
import ru.trafficmarkering.model.User;
import ru.trafficmarkering.repository.StudioProfileRepository;
import ru.trafficmarkering.service.task.TaskAdder;

/**
 * Уведомление человеку о событии на площадке. Привязан Telegram — пишет бот, нет — письмо на почту.
 * Отправка — таской NOTIFICATION: ставится в той же транзакции, что и само событие, и уходит в фоне.
 */
@Service
@RequiredArgsConstructor
public class UserNotifier {

    private final StudioProfileRepository profiles;
    private final TaskAdder tasks;

    public void notify(User user, String title, String text, String buttonText, String path) {
        if (user == null || user.getId() == null) {
            return;
        }
        Long chatId = profiles.findById(user.getId()).map(p -> p.getTelegramId()).orElse(null);
        String email = BroadcastService.emailOf(user.getUsername());
        if (chatId == null && email == null) {
            return;
        }
        tasks.addTask(NotificationPayload.builder()
                .chatId(chatId)
                .email(chatId == null ? email : null)
                .title(title)
                .text(text)
                .buttonText(buttonText)
                .buttonUrl(path)
                .build());
    }
}
