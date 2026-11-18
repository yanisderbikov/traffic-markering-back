package ru.trafficmarkering.repository;

import ru.trafficmarkering.model.task.Task;

public interface SaverTask {

    Task save(Task task);
}
