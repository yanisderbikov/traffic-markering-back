package ru.trafficmarkering.service.scrape;

/**
 * Ответ ScrapeCreators не получен или не успешен. status — HTTP-код ответа,
 * 0 — до ответа дело не дошло (сеть, таймаут, ключ не задан).
 * Неуспешные запросы ScrapeCreators не списывает с баланса.
 */
public class ScrapeCreatorsException extends RuntimeException {

    private final int status;

    public ScrapeCreatorsException(int status, String message) {
        super(message);
        this.status = status;
    }

    public ScrapeCreatorsException(int status, String message, Throwable cause) {
        super(message, cause);
        this.status = status;
    }

    public int status() {
        return status;
    }

    /** Площадка не отдала профиль или ролик. По документации это ещё не значит, что его удалили. */
    public boolean notFound() {
        return status == 404;
    }

    /**
     * Запрос не приняли из-за входных данных. Свой ScrapeCreators так отвечает на ссылки, которые не может прочитать
     * (ссылка на профиль вместо ролика, клип YouTube), и на ники не по правилам площадки — это ошибка криатора.
     */
    public boolean badInput() {
        return status == 400;
    }

    /** Ключ не принят или закончились кредиты: остальные запросы упадут так же, дальше ходить незачем. */
    public boolean fatal() {
        return status == 401 || status == 402;
    }
}
