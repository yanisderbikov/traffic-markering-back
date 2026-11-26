package ru.trafficmarkering.service.moderation;

import org.junit.jupiter.api.Test;
import org.springframework.web.server.ResponseStatusException;
import ru.trafficmarkering.model.application.ApplicationStatus;
import ru.trafficmarkering.model.fraud.FraudStatus;
import ru.trafficmarkering.model.moderation.Moderator;

import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ModerationQueueRulesTest {

    @Test
    void windowWithinDay() {
        assertTrue(Moderator.inWindow(10, 10, 19));
        assertTrue(Moderator.inWindow(18, 10, 19));
        assertFalse(Moderator.inWindow(19, 10, 19));
        assertFalse(Moderator.inWindow(9, 10, 19));
    }

    @Test
    void windowOverMidnightAndAroundTheClock() {
        assertTrue(Moderator.inWindow(23, 22, 6));
        assertTrue(Moderator.inWindow(2, 22, 6));
        assertFalse(Moderator.inWindow(6, 22, 6));
        assertFalse(Moderator.inWindow(12, 22, 6));
        assertTrue(Moderator.inWindow(15, 0, 0));
    }

    @Test
    void worksAtUsesModeratorOffset() {
        Moderator moderator = Moderator.builder().username("ivan_moder").utcOffset("+03:00").workFrom(10).workTo(19).build();
        // 07:30 UTC = 10:30 по UTC+3
        assertTrue(moderator.worksAt(Instant.parse("2026-11-26T07:30:00Z")));
        // 16:00 UTC = 19:00 по UTC+3 — окно уже закрыто
        assertFalse(moderator.worksAt(Instant.parse("2026-11-26T16:00:00Z")));
        assertEquals("10:00–19:00 (UTC+3)", moderator.scheduleLabel());
    }

    @Test
    void unscheduledOrPausedModeratorGetsNothing() {
        Instant now = Instant.parse("2026-11-26T10:00:00Z");
        Moderator fresh = Moderator.builder().username("ivan_moder").chatId(1L).build();
        assertFalse(fresh.available(now));
        Moderator paused = Moderator.builder().username("ivan_moder").chatId(1L)
                .utcOffset("+00:00").workFrom(0).workTo(0).paused(true).build();
        assertFalse(paused.available(now));
        paused.setPaused(false);
        assertTrue(paused.available(now));
        assertEquals("круглосуточно (UTC)", paused.scheduleLabel());
    }

    @Test
    void normalizesUsernames() {
        assertEquals("ivan_petrov", ModeratorRoster.normalize("@Ivan_Petrov"));
        assertEquals("ivan_petrov", ModeratorRoster.normalize("https://t.me/ivan_petrov"));
        assertEquals("ivan_petrov", ModeratorRoster.normalize(" ivan_petrov "));
        assertNull(ModeratorRoster.normalize("@ab"));
        assertNull(ModeratorRoster.normalize("иван"));
    }

    @Test
    void parsesListFromTextarea() {
        assertEquals(List.of("ivan_moder", "maria_check", "oleg_qa"),
                ModeratorRoster.parse(List.of("@ivan_moder, @maria_check\n@oleg_qa", "@IVAN_MODER")));
        assertThrows(ResponseStatusException.class, () -> ModeratorRoster.parse(List.of("@ivan_moder что-то")));
    }

    @Test
    void captionFitsTelegramLimit() {
        ModerationCard.Data data = new ModerationCard.Data("A1B2C3D4", "Кампания ".repeat(20), "Требование ".repeat(300),
                "Криатор", "TikTok", "https://www.tiktok.com/@user/video/1234567890123456789", "Юмор", 15_000L,
                FraudStatus.SUSPICIOUS, 40);
        String text = ModerationCard.text(data, Instant.parse("2026-11-26T11:35:00Z"), ZoneOffset.ofHours(3), true);
        String visible = text.replaceAll("<[^>]+>", "").replace("&amp;", "&").replace("&lt;", "<").replace("&gt;", ">");
        assertTrue(visible.length() <= ModerationCard.CAPTION_LIMIT, "длина подписи " + visible.length());
        assertTrue(text.contains("14:35"), "срок показан во времени менеджера");
        assertTrue(text.contains("Подозрение на накрутку"));
    }

    @Test
    void closedCardDropsDeadlineAndShowsVerdict() {
        ModerationCard.Data data = new ModerationCard.Data("A1B2C3D4", "Кампания", null, "Криатор", "TikTok",
                "https://www.tiktok.com/@user/video/1", null, null, FraudStatus.CLEAN, 0);
        String text = ModerationCard.text(data, Instant.now(), ZoneOffset.UTC, true);
        String closed = ModerationCard.closed(text, ModerationCard.verdict(ApplicationStatus.REJECTED, "Нет <интеграции>"));
        assertFalse(closed.contains("Ответьте до"));
        assertTrue(closed.endsWith("❌ <b>Отклонено</b>: Нет &lt;интеграции&gt;"));
    }

    @Test
    @SuppressWarnings("unchecked")
    void toHoursGoAroundTheClock() {
        Map<String, Object> markup = ModerationBot.toHoursKeyboard(22);
        List<List<Map<String, Object>>> rows = (List<List<Map<String, Object>>>) markup.get("inline_keyboard");
        List<Map<String, Object>> buttons = rows.stream().flatMap(List::stream).toList();
        assertEquals(23, buttons.size());
        assertEquals("ht:22:23", buttons.get(0).get("callback_data"));
        assertEquals("ht:22:21", buttons.get(22).get("callback_data"));
    }

    @Test
    void inactiveAfterThreeMissedByDefault() {
        assertEquals(3, new ru.trafficmarkering.config.ModerationProperties().getPauseAfterMissed());
    }

    @Test
    void moderatorLabelOnVideo() {
        Moderator moderator = Moderator.builder().username("ivan_moder").displayName("Иван Петров").build();
        assertEquals("@ivan_moder (Иван Петров)", moderator.label());
        assertTrue(moderator.active());
        moderator.setPaused(true);
        assertFalse(moderator.active());
        assertEquals("@maria_check", Moderator.builder().username("maria_check").build().label());
    }

    @Test
    void minutesWord() {
        assertEquals("30 минут", ModerationBot.minutes(30));
        assertEquals("21 минута", ModerationBot.minutes(21));
        assertEquals("2 минуты", ModerationBot.minutes(2));
        assertEquals("12 минут", ModerationBot.minutes(12));
    }
}
