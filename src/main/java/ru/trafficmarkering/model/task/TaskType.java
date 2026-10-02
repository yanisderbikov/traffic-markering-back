package ru.trafficmarkering.model.task;

import ru.trafficmarkering.dto.email.EmailTaskPayload;

public enum TaskType {
    EMAIL(EmailTaskPayload.class);

    private final Class<?> payloadClass;

    TaskType(Class<?> payloadClass) {
        this.payloadClass = payloadClass;
    }

    public Class<?> getPayloadClass() {
        return payloadClass;
    }

    public static TaskType fromObject(Object payload) {
        if (payload == null) {
            throw new IllegalArgumentException("Payload не может быть null");
        }
        Class<?> payloadClass = payload.getClass();
        for (TaskType taskType : values()) {
            if (taskType.payloadClass.isAssignableFrom(payloadClass)) {
                return taskType;
            }
        }
        throw new IllegalArgumentException("Неизвестный тип таски для класса: " + payloadClass.getName());
    }
}
