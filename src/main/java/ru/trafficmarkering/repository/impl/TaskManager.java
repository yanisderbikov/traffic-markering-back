package ru.trafficmarkering.repository.impl;

import lombok.RequiredArgsConstructor;
import lombok.extern.log4j.Log4j2;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Component;
import ru.trafficmarkering.model.task.Task;
import ru.trafficmarkering.model.task.TaskStatus;
import ru.trafficmarkering.model.task.TaskType;
import ru.trafficmarkering.repository.GetterTaskByStatus;
import ru.trafficmarkering.repository.SaverTask;

import java.util.List;

@Component
@RequiredArgsConstructor
@Log4j2
class TaskManager implements GetterTaskByStatus, SaverTask {

    private final TaskRepo taskRepo;

    @Override
    public List<Task> getByTaskTypeAndStatus(TaskType taskType, TaskStatus taskStatus, int batchSize) {
        try {
            return taskRepo.findByTypeAndStatusOrderByCreatedAtAsc(taskType, taskStatus, PageRequest.of(0, batchSize));
        } catch (Exception e) {
            log.error("getByTaskTypeAndStatus failed", e);
            throw new RuntimeException("Database exception", e);
        }
    }

    @Override
    public Task save(Task task) {
        try {
            return taskRepo.save(task);
        } catch (Exception e) {
            log.error("save task failed", e);
            throw new RuntimeException("Database exception", e);
        }
    }
}
