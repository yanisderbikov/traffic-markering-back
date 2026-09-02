package ru.trafficmarkering.service.views.impl;

import lombok.extern.log4j.Log4j2;
import org.springframework.stereotype.Component;
import ru.trafficmarkering.model.application.Platform;
import ru.trafficmarkering.service.views.ViewCountProvider;

import java.util.Optional;

/**
 * Заглушка провайдера: сама платформа просмотры не считает.
 * <p>
 * Ни у одной из четырёх площадок нет публичного счётчика, который можно опросить по ссылке
 * без токенов и согласия автора ролика, поэтому здесь всегда Optional.empty() — «данных нет»,
 * а не ноль просмотров: ноль обнулил бы криатору уже начисленные деньги.
 * <p>
 * Реальные цифры приносит внешний сервис-анализатор: он ходит с ролью SERVICE
 * в {@code PATCH /api/tech/applications/{id}/views}, а начисления пересчитываются там же.
 * Когда появится счётчик для конкретной площадки — рядом встанет вторая реализация
 * {@link ViewCountProvider}, а эта останется фолбэком для остальных площадок.
 */
@Component
@Log4j2
class ManualViewCountProvider implements ViewCountProvider {

    @Override
    public Optional<Long> fetchViews(Platform platform, String videoUrl) {
        log.debug("Автосчётчик просмотров не подключён, {} пропускаем: {}", platform, videoUrl);
        return Optional.empty();
    }
}
