package ru.trafficmarkering.service.social;

import ru.trafficmarkering.dto.social.SocialAccountDTO;
import ru.trafficmarkering.dto.social.SocialVerificationDTO;

/**
 * Привязка аккаунта без OAuth (флаг social.scrape.enabled): криатор называет профиль,
 * получает код, ставит его в описание профиля, сервис читает описание через ScrapeCreators.
 */
public interface SocialVerificationService {

    /** Находит профиль и выдаёт код. Ничего не сохраняет. */
    SocialVerificationDTO start(String platformSlug, String account);

    /** Перечитывает профиль, ищет код в описании и привязывает аккаунт к криатору. */
    SocialAccountDTO confirm(String platformSlug, String account);
}
