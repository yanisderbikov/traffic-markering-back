package ru.trafficmarkering.service.studio;

import org.springframework.http.HttpStatus;

/** Ошибка студии с текстом для пользователя (сайт и бот показывают его как есть). */
public class StudioException extends RuntimeException {

    private final HttpStatus status;

    public StudioException(String message) {
        this(HttpStatus.BAD_REQUEST, message);
    }

    public StudioException(HttpStatus status, String message) {
        super(message);
        this.status = status;
    }

    public HttpStatus getStatus() {
        return status;
    }
}
