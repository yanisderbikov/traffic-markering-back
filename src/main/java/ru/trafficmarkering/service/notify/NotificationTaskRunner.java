package ru.trafficmarkering.service.notify;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import ru.trafficmarkering.dto.notify.BroadcastPayload;
import ru.trafficmarkering.dto.notify.NotificationPayload;
import ru.trafficmarkering.model.task.Task;
import ru.trafficmarkering.model.task.TaskStatus;
import ru.trafficmarkering.model.task.TaskType;
import ru.trafficmarkering.repository.BroadcastRepository;
import ru.trafficmarkering.repository.GetterTaskByStatus;
import ru.trafficmarkering.repository.SaverTask;
import ru.trafficmarkering.service.studio.telegram.TelegramClient;

/**
 * Фоновая отправка уведомлений и рассылок. Чаще и крупнее, чем общие таски:
 * «вашу заявку одобрили» должно прийти за секунды, а рассылка на тысячи человек — за минуты.
 * Telegram пускает ~30 сообщений в секунду — пачка по умолчанию 25 раз в 2 секунды с запасом укладывается.
 */
@Slf4j
@Component
public class NotificationTaskRunner {

    private static final int MAX_COMMENT = 1000;

    private final GetterTaskByStatus taskQueue;
    private final SaverTask taskSaver;
    private final ObjectMapper json;
    private final NotificationSender sender;
    private final BroadcastService broadcastService;
    private final BroadcastRepository broadcasts;
    private final int batchSize;

    public NotificationTaskRunner(GetterTaskByStatus taskQueue, SaverTask taskSaver, ObjectMapper json,
                                  NotificationSender sender, BroadcastService broadcastService,
                                  BroadcastRepository broadcasts,
                                  @Value("${notifications.batch-size:25}") int batchSize) {
        this.taskQueue = taskQueue;
        this.taskSaver = taskSaver;
        this.json = json;
        this.sender = sender;
        this.broadcastService = broadcastService;
        this.broadcasts = broadcasts;
        this.batchSize = batchSize;
    }

    @Scheduled(fixedDelayString = "${notifications.delay-ms:2000}", initialDelayString = "${notifications.initial-delay-ms:15000}")
    public void run() {
        try {
            for (Task task : taskQueue.getByTaskTypeAndStatus(TaskType.BROADCAST, TaskStatus.NEW, 1)) {
                expand(task);
            }
            for (Task task : taskQueue.getByTaskTypeAndStatus(TaskType.NOTIFICATION, TaskStatus.NEW, batchSize)) {
                send(task);
            }
        } catch (RuntimeException e) {
            log.error("Уведомления: сбой очереди", e);
        }
    }

    private void expand(Task task) {
        try {
            BroadcastPayload payload = json.readValue(task.getPayload(), BroadcastPayload.class);
            broadcastService.expand(payload.getBroadcastId());
            finish(task, TaskStatus.DONE, null);
        } catch (Exception e) {
            log.error("Рассылка: не удалось разложить таску {}", task.getId(), e);
            finish(task, TaskStatus.FAILED, e.getMessage());
        }
    }

    void send(Task task) {
        NotificationPayload payload;
        try {
            payload = json.readValue(task.getPayload(), NotificationPayload.class);
        } catch (Exception e) {
            finish(task, TaskStatus.FAILED, "Некорректная таска: " + e.getMessage());
            return;
        }
        boolean ok;
        String error = null;
        try {
            sender.send(payload);
            ok = true;
        } catch (TelegramClient.TelegramException e) {
            if (e.getCode() == 429) {
                // Упёрлись в лимит Telegram — таска остаётся в очереди и уйдёт следующим проходом
                finish(task, TaskStatus.NEW, null);
                return;
            }
            ok = false;
            // 403 — человек заблокировал бота: повторять бессмысленно
            error = e.getCode() == 403 ? "Бот заблокирован пользователем" : e.getMessage();
        } catch (Exception e) {
            ok = false;
            error = e.getMessage();
        }
        finish(task, ok ? TaskStatus.DONE : TaskStatus.FAILED, error);
        if (payload.getBroadcastId() != null) {
            broadcasts.addResults(payload.getBroadcastId(), ok ? 1 : 0, ok ? 0 : 1);
            broadcasts.finishIfComplete(payload.getBroadcastId());
        }
        if (!ok) {
            log.info("Уведомление {} не доставлено: {}", task.getId(), error);
        }
    }

    private void finish(Task task, TaskStatus status, String comment) {
        task.setStatus(status);
        if (comment != null) {
            task.setComment(comment.length() > MAX_COMMENT ? comment.substring(0, MAX_COMMENT) : comment);
        }
        taskSaver.save(task);
    }
}
