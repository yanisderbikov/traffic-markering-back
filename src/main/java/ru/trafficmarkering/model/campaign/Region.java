package ru.trafficmarkering.model.campaign;

import java.util.Set;

/**
 * Регион, по которому считаются оплачиваемые просмотры оффера: заказчик выбирает его
 * при создании объявления, а криатор видит на карточке до того, как снимет ролик.
 * В начисление идут только просмотры из стран региона (см. RegionViewsCalculator);
 * если гео ролика ещё не подтверждено — начисление по нему не идёт, а не считается,
 * будто он «весь мир».
 *
 * Код страны — ISO 3166-1 alpha-2, как в {@link ru.trafficmarkering.service.geo.VideoGeoViews}.
 */
public enum Region {

    RUSSIA("Только РФ", Set.of("RU")),

    /** СНГ включает Россию — это более широкий регион, а не отдельный от неё список стран. */
    CIS("СНГ", Set.of("RU", "BY", "KZ", "KG", "TJ", "UZ", "AM", "AZ", "MD", "TM")),

    WORLDWIDE("Весь мир", null);

    private final String description;
    private final Set<String> countryCodes;

    Region(String description, Set<String> countryCodes) {
        this.description = description;
        this.countryCodes = countryCodes;
    }

    public String getDescription() {
        return description;
    }

    /** true — просмотр из этой страны засчитывается в регион; для WORLDWIDE — всегда true. */
    public boolean includesCountry(String countryCode) {
        return countryCodes == null || (countryCode != null && countryCodes.contains(countryCode.toUpperCase()));
    }
}
