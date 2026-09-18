package ru.trafficmarkering.service.task.impl;

import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Service;
import ru.trafficmarkering.model.task.Task;
import ru.trafficmarkering.model.task.TaskStatus;
import ru.trafficmarkering.model.task.TaskType;
import ru.trafficmarkering.repository.SaverTask;
import ru.trafficmarkering.service.task.TaskAdder;

@Service
@RequiredArgsConstructor
@Log4j2
class TaskAdderService implements TaskAdder {

    private final SaverTask saverTask;
    private final ObjectMapper objectMapper;

    @Override
    public void addTask(Object payload) {
        try {
            Task task = Task.builder()
                    .type(TaskType.fromObject(payload))
                    .payload(objectMapper.writeValueAsString(payload))
                    .status(TaskStatus.NEW)
                    .build();
            saverTask.save(task);
        } catch (Exception e) {
            log.error("Ошибка добавления таски", e);
        }
    }
}
