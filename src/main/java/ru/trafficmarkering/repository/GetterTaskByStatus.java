package ru.trafficmarkering.repository;

import ru.trafficmarkering.model.task.Task;
import ru.trafficmarkering.model.task.TaskStatus;
import ru.trafficmarkering.model.task.TaskType;

import java.util.List;

public interface GetterTaskByStatus {

    List<Task> getByTaskTypeAndStatus(TaskType taskType, TaskStatus taskStatus, int batchSize);
}
