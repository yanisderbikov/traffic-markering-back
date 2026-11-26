package ru.trafficmarkering.service.scrape;

import java.time.Instant;

/**
 * Публичные счётчики ролика и его автор. views может быть null: Instagram не отдаёт
 * просмотры у фото и иногда у роликов — тогда счётчик просто не обновляется.
 */
public record ScrapeVideo(
        String authorId,
        String authorUsername,
        Long views,
        Long likes,
        Long comments,
        Long shares,
        Long saves,
        Instant publishedAt
) {

    /** Ролик выложен с этого аккаунта: сверяем по id площадки, а если его нет в ответе — по нику. */
    public boolean publishedBy(String externalId, String username) {
        if (authorId != null) {
            return authorId.equals(externalId);
        }
        return authorUsername != null && username != null && authorUsername.equalsIgnoreCase(username);
    }

    public String authorLabel() {
        if (authorUsername != null) {
            return "@" + authorUsername;
        }
        return authorId != null ? authorId : "неизвестного автора";
    }
}
