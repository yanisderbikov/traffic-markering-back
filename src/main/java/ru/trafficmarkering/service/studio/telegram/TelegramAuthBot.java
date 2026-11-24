package ru.trafficmarkering.service.studio.telegram;

import com.fasterxml.jackson.databind.JsonNode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.server.ResponseStatusException;
import ru.trafficmarkering.model.Role;
import ru.trafficmarkering.model.User;
import ru.trafficmarkering.service.auth.telegram.TelegramAuthService;
import ru.trafficmarkering.service.auth.telegram.TelegramAuthService.BotStep;
import ru.trafficmarkering.service.auth.telegram.TelegramAuthService.TgUser;
import ru.trafficmarkering.service.auth.telegram.TelegramIdentity;
import ru.trafficmarkering.service.auth.telegram.TelegramLinkedEvent;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static ru.trafficmarkering.service.studio.telegram.TelegramClient.appButton;
import static ru.trafficmarkering.service.studio.telegram.TelegramClient.button;
import static ru.trafficmarkering.service.studio.telegram.TelegramClient.escape;
import static ru.trafficmarkering.service.studio.telegram.TelegramClient.keyboard;
import static ru.trafficmarkering.service.studio.telegram.TelegramClient.urlButton;

/**
 * Вход и регистрация на сайте через бота: экраны и кнопки. Логика — в {@link TelegramAuthService}.
 * Кнопки: «au:&lt;id&gt;:&lt;действие&gt;» и «al» — новая ссылка для входа на сайт.
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TelegramAuthBot {

    static final String CALLBACK_PREFIX = "au:";
    static final String LOGIN_CALLBACK = "al";

    private final TelegramClient client;
    private final TelegramAuthService auth;
    @Value("${studio.site-url:}")
    private String siteUrl;

    static TgUser tgUser(JsonNode from) {
        return new TgUser(from.path("id").asLong(), from.path("username").asText(null),
                from.path("first_name").asText(null), from.path("last_name").asText(null));
    }

    /** /start auth_&lt;id&gt; — человек нажал «Войти через Telegram» на сайте */
    void openFromSite(long chatId, JsonNode from, String argument) {
        String id = argument.substring(TelegramAuthService.START_PREFIX.length());
        render(chatId, null, auth.openFromSite(id, tgUser(from)), true);
    }

    /** /login, кнопка «Войти на сайт» или любое сообщение от непривязанного Telegram */
    void login(long chatId, JsonNode from, Long editMessageId) {
        render(chatId, editMessageId, auth.openFromBot(tgUser(from)), false);
    }

    /** @return true — кнопка входа, обработана */
    boolean onCallback(String callbackId, String data, JsonNode from, long chatId, Long messageId) {
        if (LOGIN_CALLBACK.equals(data)) {
            client.answerCallback(callbackId, null);
            login(chatId, from, null);
            return true;
        }
        if (!data.startsWith(CALLBACK_PREFIX)) {
            return false;
        }
        String[] parts = data.split(":");
        if (parts.length != 3) {
            client.answerCallback(callbackId, null);
            return true;
        }
        BotStep step;
        try {
            step = auth.decide(parts[1], parts[2], tgUser(from));
        } catch (ResponseStatusException e) {
            client.answerCallback(callbackId, null);
            send(chatId, messageId, escape(e.getReason()), null);
            return true;
        }
        if (step.step() == TelegramAuthService.Step.FOREIGN) {
            client.answerCallback(callbackId, "Эта кнопка для другого аккаунта Telegram");
            return true;
        }
        client.answerCallback(callbackId, null);
        render(chatId, messageId, step, true);
        return true;
    }

    /** После входа по почте на сайте: в чат приходит «готово», чтобы было понятно, что делать дальше */
    @Async
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void onLinked(TelegramLinkedEvent event) {
        send(event.telegramId(), null, "✅ <b>Готово — вы вошли на сайт</b>\n\n"
                + "Аккаунт: <b>" + escape(event.name()) + "</b>"
                + (event.email() != null ? " (" + escape(event.email()) + ")" : "") + "\n"
                + "Этот Telegram теперь привязан к нему. Дальше:\n"
                + "• «Открыть Offer» — кабинет прямо в Telegram, без входа;\n"
                + "• на сайте нажимайте «Войти через Telegram» — почта и код больше не нужны;\n"
                + "• или отправьте здесь /login — пришлю ссылку для входа;\n"
                + (event.creator() ? "• пришлите сюда ролик до 20 МБ — смонтирую видео."
                : "• сюда будут приходить уведомления о новых роликах по вашим кампаниям."),
                keyboard(List.of(List.of(appButton("📱 Открыть Offer", appUrl())),
                        List.of(button("🔐 Войти в браузере", LOGIN_CALLBACK)))));
    }

    private String appUrl() {
        return (siteUrl == null ? "" : siteUrl.replaceAll("/+$", "")) + "/app";
    }

    private void render(long chatId, Long messageId, BotStep step, boolean fromSite) {
        switch (step.step()) {
            case EXPIRED -> send(chatId, messageId, "Ссылка для входа устарела или уже использована.\n\n"
                    + "Нажмите «Войти через Telegram» на сайте ещё раз или отправьте /login — пришлю новую ссылку.", null);
            case CANCELLED -> send(chatId, messageId,
                    "Вход отменён. Если входили не вы — ничего делать не нужно, аккаунт в безопасности.", null);
            case CONFIRM_LOGIN -> send(chatId, messageId, "🔐 <b>Вход на сайт Offer</b>\n\n"
                    + "Аккаунт: <b>" + escape(step.user().getName()) + "</b>" + account(step.user()) + "\n"
                    + (step.clientInfo() != null ? "Устройство: " + escape(step.clientInfo()) + "\n" : "")
                    + "\nЕсли вы не нажимали «Войти через Telegram» на сайте — нажмите «Это не я».",
                    keyboard(List.of(List.of(button("✅ Войти", cb(step, "ok")), button("Это не я", cb(step, "no"))))));
            case CHOOSE_ACCOUNT -> send(chatId, messageId, chooseText(step), chooseMarkup(step, fromSite));
            case SITE_LOGGED_IN -> send(chatId, messageId, "✅ Готово — вы вошли на сайт как <b>"
                    + escape(step.user().getName()) + "</b>. Вернитесь во вкладку браузера.\n\n"
                    + (ru.trafficmarkering.service.studio.StudioService.allowed(step.user())
                    ? "Здесь же можно монтировать видео: пришлите ролик до 20 МБ."
                    : "Сюда будут приходить уведомления о роликах по вашим кампаниям."), null);
            case SITE_USE_EMAIL -> send(chatId, messageId, "Telegram подтверждён.\n\n"
                    + "Вернитесь на сайт и войдите по почте — после входа этот Telegram привяжется к вашему аккаунту, "
                    + "и дальше можно будет входить через него.", null);
            case LOGIN_LINK -> send(chatId, messageId, "🔐 Вход на сайт как <b>" + escape(step.user().getName()) + "</b>"
                    + account(step.user()) + ".\n\nСсылка одноразовая и действует 10 минут — никому её не пересылайте. "
                    + "Новая — по /login.",
                    keyboard(List.of(List.of(urlButton("Войти на сайт", step.url())))));
            case EMAIL_LINK -> send(chatId, messageId, "Откройте сайт и войдите по почте — Telegram привяжется "
                    + "к вашему аккаунту. Ссылка действует 30 минут.",
                    keyboard(List.of(List.of(urlButton("Войти по почте", step.url())))));
            default -> {
            }
        }
    }

    private static String chooseText(BotStep step) {
        if (step.presetRole() != null) {
            return "<b>Регистрация в Offer</b> — " + roleLabel(step.presetRole()) + ".\n\n"
                    + "Аккаунт будет привязан к этому Telegram, почта и пароль не нужны. "
                    + "Почту можно добавить потом в профиле.\n\n"
                    + "Уже есть аккаунт Offer с почтой? Нажмите «У меня есть аккаунт» — войдёте по почте, "
                    + "и Telegram привяжется к нему, второй аккаунт не появится.";
        }
        return "<b>Offer</b> — реклама у креаторов и монтаж видео.\n\n"
                + "Этот Telegram ещё не привязан к аккаунту.\n"
                + "• Уже есть аккаунт с почтой — нажмите «У меня есть аккаунт»: войдёте по почте, "
                + "и Telegram привяжется к нему.\n"
                + "• Впервые здесь — создайте аккаунт одной кнопкой, почта не нужна.";
    }

    private static Map<String, Object> chooseMarkup(BotStep step, boolean fromSite) {
        List<List<Map<String, Object>>> rows = new ArrayList<>();
        if (step.presetRole() != null) {
            rows.add(List.of(button("✅ Создать аккаунт", cb(step, step.presetRole() == Role.CUSTOMER ? "cu" : "cr"))));
        } else {
            rows.add(List.of(button("Я креатор — создать", cb(step, "cr"))));
            rows.add(List.of(button("Я рекламодатель — создать", cb(step, "cu"))));
        }
        rows.add(List.of(button("У меня есть аккаунт", cb(step, "have"))));
        if (fromSite) {
            rows.add(List.of(button("Отмена", cb(step, "no"))));
        }
        return keyboard(rows);
    }

    private static String account(User user) {
        return TelegramIdentity.telegramOnly(user) ? "" : " (" + escape(user.getUsername()) + ")";
    }

    private static String roleLabel(Role role) {
        return role == Role.CUSTOMER ? "рекламодатель" : "креатор";
    }

    private static String cb(BotStep step, String action) {
        return CALLBACK_PREFIX + step.authId() + ":" + action;
    }

    private void send(long chatId, Long messageId, String html, Object markup) {
        try {
            if (messageId != null) {
                client.editMessage(chatId, messageId, html, markup);
            } else {
                client.sendMessage(chatId, html, markup, null);
            }
        } catch (TelegramClient.TelegramException e) {
            log.warn("Вход через Telegram: сообщение в чат {} не отправлено: {}", chatId, e.getMessage());
        }
    }
}
