package ru.trafficmarkering.service.task.runner;

import lombok.extern.log4j.Log4j2;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Scheduled;
import ru.trafficmarkering.model.task.Task;
import ru.trafficmarkering.model.task.TaskStatus;
import ru.trafficmarkering.repository.SaverTask;

import java.util.List;

@Log4j2
public abstract class AbstractRunnableTask {

    private static final int MAX_COMMENT_LENGTH = 1000;

    private final SaverTask saverTask;

    @Value("${tasks.batch-size}")
    private int batchSize;

    protected AbstractRunnableTask(SaverTask saverTask) {
        this.saverTask = saverTask;
    }

    protected abstract List<Task> fetchBatch(int batchSize);

    protected abstract void process(Task task) throws Exception;

    @Scheduled(fixedRateString = "${tasks.fixed-rate-ms}", initialDelayString = "${tasks.initial-delay-ms}")
    public void runBatch() {
        List<Task> tasks;
        try {
            tasks = fetchBatch(batchSize);
        } catch (Exception e) {
            log.error("Не удалось забрать пачку тасок", e);
            return;
        }
        for (Task task : tasks) {
            runOne(task);
        }
    }

    private void runOne(Task task) {
        try {
            task.setStatus(TaskStatus.RUNNING);
            saverTask.save(task);

            process(task);

            task.setStatus(TaskStatus.DONE);
            saverTask.save(task);
        } catch (Exception e) {
            log.error("Ошибка во время исполнения таски {}", task.getId(), e);
            task.setStatus(TaskStatus.FAILED);
            task.setComment(crop(e.getMessage()));
            saverTask.save(task);
        }
    }

    private static String crop(String message) {
        if (message == null) {
            return null;
        }
        return message.length() > MAX_COMMENT_LENGTH ? message.substring(0, MAX_COMMENT_LENGTH) : message;
    }
}
