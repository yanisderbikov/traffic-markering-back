package ru.trafficmarkering.service.application;

import ru.trafficmarkering.dto.application.ApplicationDTO;
import ru.trafficmarkering.dto.placement.PlacementChoiceRequestDTO;
import ru.trafficmarkering.dto.placement.PlacementDecisionRequestDTO;
import ru.trafficmarkering.dto.placement.PlacementProofRequestDTO;
import ru.trafficmarkering.dto.placement.SnapshotQuoteDTO;
import ru.trafficmarkering.dto.placement.SnapshotQuoteRequestDTO;
import ru.trafficmarkering.dto.placement.SnapshotSubmitRequestDTO;
import ru.trafficmarkering.model.application.PlacementKind;

import java.util.UUID;

/**
 * Работы по объявлениям «ссылка и промокод»: вариант участия, расчёт по снимку просмотров,
 * подача, исправление и решения по надбавкам, конец срока размещения.
 */
public interface PlacementService {

    /** Криатор меняет вариант, пока работа не подана. */
    ApplicationDTO choose(UUID applicationId, PlacementChoiceRequestDTO request);

    /** Ролик к работе и свежий снимок просмотров: предварительный расчёт, который потом подтверждается подачей. */
    SnapshotQuoteDTO quote(UUID applicationId, SnapshotQuoteRequestDTO request);

    /** Подача: фиксирует просмотры и сумму по подтверждённому расчёту. */
    ApplicationDTO submit(UUID applicationId, SnapshotSubmitRequestDTO request);

    /** Новое доказательство по надбавке после отказа или вместо прежнего. */
    ApplicationDTO resubmitProof(UUID applicationId, PlacementKind kind, PlacementProofRequestDTO request);

    /** Криатор отказывается от надбавки и продолжает по меньшей ставке. */
    ApplicationDTO decline(UUID applicationId, PlacementKind kind);

    /** Модератор или заказчик подтверждает или отклоняет надбавку. */
    ApplicationDTO decide(UUID applicationId, PlacementKind kind, PlacementDecisionRequestDTO request);

    /** Повторная автопроверка ссылки в профиле. */
    ApplicationDTO recheckLink(UUID applicationId);

    /**
     * Конец срока размещения: подтверждённые надбавки засчитываются, ссылка перед этим проверяется ещё раз.
     *
     * @return сколько работ обработано
     */
    int finalizeDue();
}
