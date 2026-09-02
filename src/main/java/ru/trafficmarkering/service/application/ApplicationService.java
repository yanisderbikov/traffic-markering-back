package ru.trafficmarkering.service.application;

import ru.trafficmarkering.dto.application.ApplicationCreateRequestDTO;
import ru.trafficmarkering.dto.application.ApplicationDTO;
import ru.trafficmarkering.dto.application.ApplicationStatusUpdateRequestDTO;
import ru.trafficmarkering.dto.application.ViewsUpdateRequestDTO;

import java.util.List;
import java.util.UUID;

public interface ApplicationService {

    /**
     * Взять объявление в работу: криатор прикладывает ссылку на ролик.
     * Откликнуться можно только на ACTIVE-объявление и только один раз.
     */
    ApplicationDTO apply(ApplicationCreateRequestDTO request);

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

    /** Отозвать свой отклик; после решения заказчика отзывать уже поздно. */
    void delete(UUID id);

    /**
     * Просмотры от внешнего анализатора (техническая ручка): проставляет views,
     * отметку времени синхронизации и пересчитывает начисления по объявлению.
     */
    ApplicationDTO updateViews(UUID id, ViewsUpdateRequestDTO request);
}
