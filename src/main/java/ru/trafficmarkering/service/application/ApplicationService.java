package ru.trafficmarkering.service.application;

import ru.trafficmarkering.dto.application.ApplicationCreateRequestDTO;
import ru.trafficmarkering.dto.application.ApplicationDTO;
import ru.trafficmarkering.dto.application.ApplicationStatusUpdateRequestDTO;
import ru.trafficmarkering.dto.application.ApplicationVideoRequestDTO;
import ru.trafficmarkering.dto.application.ViewSnapshotDTO;
import ru.trafficmarkering.dto.application.ViewsUpdateRequestDTO;

import java.util.List;
import java.util.UUID;

public interface ApplicationService {

    /**
     * Взять объявление в работу. Без ссылки отклик встаёт в IN_PROGRESS и ждёт ролика,
     * со ссылкой сразу уходит заказчику на рассмотрение.
     * Откликнуться можно только на ACTIVE-объявление в период его действия.
     */
    ApplicationDTO apply(ApplicationCreateRequestDTO request);

    /** Приложить ролик к взятому в работу офферу: отклик уходит заказчику на рассмотрение. */
    ApplicationDTO attachVideo(UUID id, ApplicationVideoRequestDTO request);

    /** Отклики текущего криатора, новые сверху. */
    List<ApplicationDTO> getMyApplications();

    /**
     * Отклики по объявлению — для его владельца.
     *
     * @param ownerId id заказчика, которому объявление должно принадлежать;
     *                null — владельца проверил вызывающий (например, для ADMIN)
     */
    List<ApplicationDTO> getByCampaignId(UUID campaignId, Long ownerId);

    /**
     * Решение заказчика по отклику: APPROVED, REJECTED или COMPLETED.
     * Начисления по объявлению пересчитываются целиком — статус меняет, кому идут деньги.
     */
    ApplicationDTO updateStatus(UUID id, ApplicationStatusUpdateRequestDTO request);

    /** Отозвать свой отклик или отказаться от работы; после решения заказчика отзывать уже поздно. */
    void delete(UUID id);

    /**
     * Просмотры от внешнего анализатора (техническая ручка): проставляет views,
     * отметку времени синхронизации и пересчитывает начисления по объявлению.
     */
    ApplicationDTO updateViews(UUID id, ViewsUpdateRequestDTO request);

    /** История замеров просмотров ролика, свежие сверху: по ней видно динамику. */
    List<ViewSnapshotDTO> viewHistory(UUID id);
}
