package ru.trafficmarkering.service.views;

public interface ViewsSyncService {

    /**
     * Обходит отклики в работе и спрашивает у {@link ViewCountProvider} свежие просмотры.
     * Объявления, где просмотры изменились, пересчитываются по деньгам.
     *
     * @return сколько откликов реально обновили
     */
    int syncApproved();
}
