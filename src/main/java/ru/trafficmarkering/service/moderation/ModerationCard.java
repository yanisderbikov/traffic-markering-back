package ru.trafficmarkering.service.moderation;

import ru.trafficmarkering.model.application.Application;
import ru.trafficmarkering.model.application.ApplicationStatus;
import ru.trafficmarkering.model.campaign.Campaign;
import ru.trafficmarkering.model.fraud.FraudStatus;
import ru.trafficmarkering.util.MoneyUtil;

import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static ru.trafficmarkering.service.studio.telegram.TelegramClient.button;
import static ru.trafficmarkering.service.studio.telegram.TelegramClient.escape;
import static ru.trafficmarkering.service.studio.telegram.TelegramClient.keyboard;

/**
 * Карточка ролика в боте модерации. Подпись к видео в Telegram — не больше 1024 символов,
 * поэтому требования кампании обрезаются.
 */
final class ModerationCard {

    static final int CAPTION_LIMIT = 1024;
    private static final int REQUIREMENTS_LIMIT = 380;
    private static final DateTimeFormatter TIME = DateTimeFormatter.ofPattern("HH:mm");

    /** Причины отказа одной кнопкой; криатор видит текст причины */
    static final List<String> REASONS = List.of(
            "Нет рекламной интеграции",
            "Не соответствует требованиям кампании",
            "Ролик недоступен или удалён",
            "Ролик выложен не с аккаунта криатора",
            "Низкое качество ролика",
            "Запрещённый контент");

    /** Всё, что нужно для подписи, — снимается в транзакции, дальше работаем без JPA */
    record Data(String publicId, String campaignTitle, String requirements, String creatorName, String platform,
                String videoUrl, String contentTopic, Long rateKopecks, FraudStatus fraudStatus, int fraudScore) {

        static Data of(Application application) {
            Campaign campaign = application.getCampaign();
            return new Data(application.getPublicId(), campaign.getTitle(), campaign.getDescription(),
                    application.getCreator().getName(),
                    application.getPlatform() != null ? application.getPlatform().getDescription() : null,
                    application.getVideoUrl(), application.getContentTopic(),
                    application.rateFor(campaign), application.fraudStatus(), application.fraudScoreValue());
        }
    }

    private ModerationCard() {
    }

    static String text(Data data, Instant expiresAt, ZoneOffset offset, boolean videoAttached) {
        StringBuilder head = new StringBuilder();
        head.append("🎬 <b>Ролик на модерацию</b> · <code>").append(escape(data.publicId())).append("</code>\n");
        head.append("Кампания: <b>").append(escape(data.campaignTitle())).append("</b>\n");
        head.append("Криатор: ").append(escape(data.creatorName()));
        if (data.platform() != null) {
            head.append(" · ").append(escape(data.platform()));
        }
        head.append('\n');
        if (data.contentTopic() != null || (data.rateKopecks() != null && data.rateKopecks() > 0)) {
            List<String> parts = new ArrayList<>();
            if (data.contentTopic() != null) {
                parts.add("Тематика: " + escape(data.contentTopic()));
            }
            if (data.rateKopecks() != null && data.rateKopecks() > 0) {
                parts.add(MoneyUtil.formatRubles(data.rateKopecks()) + " за 1000");
            }
            head.append(String.join(" · ", parts)).append('\n');
        }
        if (data.fraudStatus() != null && data.fraudStatus() != FraudStatus.CLEAN && data.fraudStatus() != FraudStatus.VERIFIED) {
            head.append("⚠️ Антифрод: ").append(escape(data.fraudStatus().getDescription()))
                    .append(" (").append(data.fraudScore()).append(")\n");
        }
        if (data.videoUrl() != null) {
            head.append("<a href=\"").append(escape(data.videoUrl()).replace("\"", "&quot;")).append("\">")
                    .append(videoAttached ? "Ролик на площадке" : "▶️ Открыть ролик").append("</a>");
            if (!videoAttached) {
                head.append(" — видео не удалось загрузить в Telegram");
            }
            head.append('\n');
        }

        String footer = "\n⏳ Ответьте до " + TIME.format(expiresAt.atOffset(offset))
                + " — потом заявка уйдёт другому менеджеру.";
        String requirements = requirements(data.requirements(), head.length() + footer.length());
        return head + requirements + footer;
    }

    /** Требования влезают в остаток лимита подписи; считаем по сырому тексту — сущности HTML не в счёт */
    private static String requirements(String raw, int used) {
        if (raw == null || raw.isBlank()) {
            return "";
        }
        int room = Math.min(REQUIREMENTS_LIMIT, CAPTION_LIMIT - used - 40);
        if (room < 60) {
            return "";
        }
        String text = raw.strip().replaceAll("\\s+", " ");
        if (text.length() > room) {
            text = text.substring(0, room).strip() + "…";
        }
        return "\nТребования: <i>" + escape(text) + "</i>\n";
    }

    /** Итог под карточкой после решения или снятия */
    static String closed(String text, String verdict) {
        String body = text;
        int deadline = body.lastIndexOf("\n⏳ ");
        if (deadline >= 0) {
            body = body.substring(0, deadline);
        }
        return body + "\n\n" + verdict;
    }

    static String verdict(ApplicationStatus decision, String reason) {
        if (decision == ApplicationStatus.APPROVED) {
            return "✅ <b>Принято</b>";
        }
        return "❌ <b>Отклонено</b>" + (reason != null ? ": " + escape(reason) : "");
    }

    static Map<String, Object> decisionKeyboard(long assignmentId) {
        return keyboard(List.of(List.of(
                button("✅ Принять", "ok:" + assignmentId),
                button("❌ Отклонить", "no:" + assignmentId))));
    }

    static Map<String, Object> reasonsKeyboard(long assignmentId) {
        List<List<Map<String, Object>>> rows = new ArrayList<>();
        for (int i = 0; i < REASONS.size(); i++) {
            rows.add(List.of(button(REASONS.get(i), "rj:" + assignmentId + ":" + i)));
        }
        rows.add(List.of(button("✍️ Своя причина", "rt:" + assignmentId)));
        rows.add(List.of(button("↩️ Назад", "bk:" + assignmentId)));
        return keyboard(rows);
    }
}
