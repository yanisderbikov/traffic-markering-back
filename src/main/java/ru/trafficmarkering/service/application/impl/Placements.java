package ru.trafficmarkering.service.application.impl;

import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;
import ru.trafficmarkering.controller.FileController;
import ru.trafficmarkering.model.application.Application;
import ru.trafficmarkering.model.application.Placement;
import ru.trafficmarkering.model.campaign.Campaign;

/** Правила выбора надбавок и доказательств, общие для взятия оффера и работы с поданной работой. */
final class Placements {

    private Placements() {
    }

    /**
     * Вариант участия по зафиксированным в отклике ставкам: надбавку можно взять, только если объявление
     * её предлагало, когда креатор брал оффер.
     */
    static void choose(Application application, Campaign campaign, boolean withLink, boolean withPromo) {
        if (withLink && !(application.rateParts().link() > 0 && campaign.getPromoUrl() != null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Объявление не платит надбавку за ссылку в профиле");
        }
        if (withPromo && !(application.rateParts().promo() > 0 && campaign.getPromoCode() != null)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Объявление не платит надбавку за промокод в закрепе");
        }
        application.setLinkPlacement(Placement.of(withLink));
        application.setPromoPlacement(Placement.of(withPromo));
    }

    /** Ключ доказательства — только из загрузки доказательств размещения. */
    static String proofKey(String value) {
        String key = trimToNull(value);
        if (key == null) {
            return null;
        }
        if (!key.startsWith(FileController.PLACEMENT_PROOF_PREFIX + "/") || key.contains("..")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Файл доказательства не из загрузки доказательств размещения: " + key);
        }
        return key;
    }

    /** Ссылка от креатора на комментарий или профиль: только http(s), чтобы её можно было открыть. */
    static String proofUrl(String value) {
        String url = trimToNull(value);
        if (url == null) {
            return null;
        }
        if (!(url.startsWith("https://") || url.startsWith("http://")) || url.length() > 1024) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Ссылка на комментарий должна начинаться с https://");
        }
        return url;
    }

    static String trimToNull(String value) {
        return value == null || value.isBlank() ? null : value.trim();
    }
}
