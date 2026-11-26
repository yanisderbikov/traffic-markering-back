package ru.trafficmarkering.service.social;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.trafficmarkering.model.social.SocialLinkMethod;

/**
 * Фича-флаг интеграции с соцсетями. Выключен — работает OAuth площадок, как раньше.
 * Включён — привязка по коду в описании профиля и просмотры через ScrapeCreators;
 * аккаунты, привязанные другим способом, для сервиса как будто не существуют.
 */
@Component
public class SocialIntegration {

    private final boolean scrape;

    public SocialIntegration(@Value("${social.scrape.enabled:false}") boolean scrape) {
        this.scrape = scrape;
    }

    public boolean scrape() {
        return scrape;
    }

    public SocialLinkMethod linkMethod() {
        return scrape ? SocialLinkMethod.SCRAPE : SocialLinkMethod.OAUTH;
    }
}
