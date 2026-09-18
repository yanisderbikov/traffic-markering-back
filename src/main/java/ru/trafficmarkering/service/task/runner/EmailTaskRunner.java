package ru.trafficmarkering.service.task.runner;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import ru.trafficmarkering.dto.email.EmailTaskPayload;
import ru.trafficmarkering.model.task.Task;
import ru.trafficmarkering.model.task.TaskStatus;
import ru.trafficmarkering.model.task.TaskType;
import ru.trafficmarkering.repository.GetterTaskByStatus;
import ru.trafficmarkering.repository.SaverTask;
import ru.trafficmarkering.service.email.EmailService;

import java.util.List;

@Component
class EmailTaskRunner extends AbstractRunnableTask {

    private final GetterTaskByStatus getterTaskByStatus;
    private final EmailService emailService;
    private final ObjectMapper objectMapper;

    EmailTaskRunner(GetterTaskByStatus getterTaskByStatus,
                    EmailService emailService,
                    ObjectMapper objectMapper,
                    SaverTask saverTask) {
        super(saverTask);
        this.getterTaskByStatus = getterTaskByStatus;
        this.emailService = emailService;
        this.objectMapper = objectMapper;
    }

    @Override
    protected List<Task> fetchBatch(int batchSize) {
        return getterTaskByStatus.getByTaskTypeAndStatus(TaskType.EMAIL, TaskStatus.NEW, batchSize);
    }

    @Override
    protected void process(Task task) throws Exception {
        EmailTaskPayload payload = objectMapper.readValue(task.getPayload(), EmailTaskPayload.class);
        emailService.sendEmail(payload.getTo(), payload.getSubject(), payload.getHtml());
    }
}
