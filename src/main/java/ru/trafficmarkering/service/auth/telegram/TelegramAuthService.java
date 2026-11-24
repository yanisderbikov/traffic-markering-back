package ru.trafficmarkering.service.auth.telegram;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.http.HttpStatus;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.trafficmarkering.dto.auth.AuthResponseDTO;
import ru.trafficmarkering.dto.auth.TelegramAuthPollDTO;
import ru.trafficmarkering.dto.auth.TelegramAuthStartDTO;
import ru.trafficmarkering.dto.auth.TelegramWebAppDTO;
import ru.trafficmarkering.model.Role;
import ru.trafficmarkering.model.User;
import ru.trafficmarkering.model.auth.TelegramAuth;
import ru.trafficmarkering.model.auth.TelegramAuthOrigin;
import ru.trafficmarkering.model.auth.TelegramAuthStatus;
import ru.trafficmarkering.model.profile.CreatorProfile;
import ru.trafficmarkering.model.profile.CustomerProfile;
import ru.trafficmarkering.repository.GetterCreatorProfile;
import ru.trafficmarkering.repository.GetterCustomerProfile;
import ru.trafficmarkering.repository.SaverCreatorProfile;
import ru.trafficmarkering.repository.SaverCustomerProfile;
import ru.trafficmarkering.repository.TelegramAuthRepository;
import ru.trafficmarkering.repository.UserRepository;
import ru.trafficmarkering.service.auth.JwtTokenService;
import ru.trafficmarkering.service.partner.PartnerService;
import ru.trafficmarkering.service.studio.telegram.StudioBotInfo;
import ru.trafficmarkering.service.user.AccountProvisioningService;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.security.SecureRandom;
import java.time.Duration;
import java.time.Instant;
import java.util.Base64;
import java.util.HexFormat;
import java.util.Optional;

/**
 * Вход и регистрация через Telegram-бота — в обе стороны.
 * <ul>
 *   <li>С сайта: браузер получает ссылку t.me/бот?start=auth_&lt;id&gt; и секрет, бот спрашивает подтверждение,
 *   браузер по секрету забирает JWT.</li>
 *   <li>Из бота: /login или кнопка «Войти на сайт» дают одноразовую ссылку на сайт с секретом.</li>
 * </ul>
 * Если Telegram ни к чему не привязан, бот предлагает завести учётку (логин «tg:&lt;id&gt;», без почты)
 * или войти на сайте по почте — тогда Telegram привяжется к этой учётке, а дубля не появится.
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class TelegramAuthService {

    static final Duration TTL = Duration.ofMinutes(10);
    /** Сколько живёт ссылка «у меня есть аккаунт»: человеку нужно успеть получить код на почту */
    static final Duration EMAIL_TTL = Duration.ofMinutes(30);
    public static final String START_PREFIX = "auth_";
    private static final SecureRandom RANDOM = new SecureRandom();

    /** Что показать в боте после шага */
    public enum Step {
        /** Ссылка устарела или уже использована */
        EXPIRED,
        /** Кнопку нажал не тот Telegram, что открыл ссылку */
        FOREIGN,
        /** Telegram привязан: спросить «Войти как …?» */
        CONFIRM_LOGIN,
        /** Telegram не привязан: создать учётку или войти по почте */
        CHOOSE_ACCOUNT,
        /** Вход с сайта подтверждён — браузер войдёт сам */
        SITE_LOGGED_IN,
        /** Вход с сайта: учётки нет, человек войдёт по почте в том же браузере */
        SITE_USE_EMAIL,
        /** Ссылка из бота на сайт: сразу войти */
        LOGIN_LINK,
        /** Ссылка из бота на сайт: войти по почте, Telegram привяжется */
        EMAIL_LINK,
        CANCELLED
    }

    public record TgUser(long id, String username, String firstName, String lastName) {
        public String displayName() {
            String name = ((firstName == null ? "" : firstName) + " " + (lastName == null ? "" : lastName)).strip();
            if (!name.isEmpty()) {
                return name.length() > 255 ? name.substring(0, 255) : name;
            }
            return username != null && !username.isBlank() ? username : "Пользователь Telegram";
        }
    }

    public record BotStep(Step step, String authId, User user, Role presetRole, String url, String clientInfo) {
        static BotStep of(Step step) {
            return new BotStep(step, null, null, null, null, null);
        }
    }

    private final TelegramAuthRepository auths;
    private final UserRepository users;
    private final TelegramIdentity identity;
    private final JwtTokenService jwt;
    private final AccountProvisioningService provisioning;
    private final PartnerService partners;
    private final GetterCreatorProfile creatorProfiles;
    private final SaverCreatorProfile creatorProfileSaver;
    private final GetterCustomerProfile customerProfiles;
    private final SaverCustomerProfile customerProfileSaver;
    private final StudioBotInfo botInfo;
    private final ApplicationEventPublisher events;
    @Value("${studio.site-url}")
    private String siteUrl;

    // ───────────────────────── сайт ─────────────────────────

    @Transactional
    public TelegramAuthStartDTO start(Role role, String referralCode, String clientInfo) {
        String bot = botInfo.username();
        if (bot == null) {
            throw new ResponseStatusException(HttpStatus.SERVICE_UNAVAILABLE, "Вход через Telegram временно недоступен.");
        }
        if (role != null && !Role.selfRegistrable().contains(role)) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Роль должна быть CUSTOMER или CREATOR");
        }
        String secret = secret();
        TelegramAuth auth = auths.save(TelegramAuth.builder()
                .id(newId())
                .secretHash(hash(secret))
                .origin(TelegramAuthOrigin.SITE)
                .status(TelegramAuthStatus.PENDING)
                .role(role)
                .referralCode(blankToNull(referralCode))
                .clientInfo(trim(clientInfo, 255))
                .createdAt(Instant.now())
                .expiresAt(Instant.now().plus(TTL))
                .build());
        return new TelegramAuthStartDTO("https://t.me/" + bot + "?start=" + START_PREFIX + auth.getId(), secret,
                auth.getExpiresAt());
    }

    /** Браузер спрашивает результат; токен выдаётся ровно один раз */
    @Transactional
    public TelegramAuthPollDTO poll(String secret) {
        Optional<TelegramAuth> found = auths.lockBySecretHash(hash(secret));
        if (found.isEmpty()) {
            return TelegramAuthPollDTO.of("EXPIRED");
        }
        TelegramAuth auth = found.get();
        if (auth.getStatus() == TelegramAuthStatus.CANCELLED) {
            return TelegramAuthPollDTO.of("CANCELLED");
        }
        if (auth.getStatus() == TelegramAuthStatus.DONE || auth.expired()) {
            return TelegramAuthPollDTO.of("EXPIRED");
        }
        if (auth.getStatus() == TelegramAuthStatus.PENDING) {
            return TelegramAuthPollDTO.of("PENDING");
        }
        if (auth.getUserId() == null) {
            return new TelegramAuthPollDTO("NEED_EMAIL", null, telegramLabel(auth));
        }
        // Telegram могли отвязать, пока браузер ждал: входим только в ту учётку, к которой он привязан сейчас
        User user = identity.userOf(auth.getTelegramId())
                .filter(u -> u.getId().equals(auth.getUserId()))
                .orElse(null);
        auth.setStatus(TelegramAuthStatus.DONE);
        auths.save(auth);
        if (user == null) {
            return TelegramAuthPollDTO.of("EXPIRED");
        }
        return new TelegramAuthPollDTO("DONE", issue(user), telegramLabel(auth));
    }

    /** После входа по почте привязывает Telegram, подтверждённый в боте */
    @Transactional
    public String attach(String secret, User user) {
        TelegramAuth auth = auths.lockBySecretHash(hash(secret))
                .filter(a -> a.getStatus() == TelegramAuthStatus.CONFIRMED && a.getUserId() == null && !a.expired())
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.GONE,
                        "Ссылка из Telegram устарела — привяжите его в «Видео-студии»."));
        identity.attach(user, auth.getTelegramId(), auth.getTelegramUsername());
        auth.setUserId(user.getId());
        auth.setStatus(TelegramAuthStatus.DONE);
        auths.save(auth);
        events.publishEvent(new TelegramLinkedEvent(auth.getTelegramId(), user.getName(),
                TelegramIdentity.telegramOnly(user) ? null : user.getUsername()));
        return telegramLabel(auth);
    }

    // ───────────────────────── мини-приложение ─────────────────────────

    /**
     * Вход из мини-приложения: Telegram уже подтверждён подписью initData.
     * Привязан — сразу токен; нет — человек выбирает: новая учётка (cr/cu) или своя с почтой (have).
     */
    @Transactional
    public TelegramWebAppDTO webApp(TelegramInitData.Launch launch, String action, String referralCode) {
        TgUser tg = launch.user();
        String label = tg.username() != null ? "@" + tg.username() : tg.displayName();
        Optional<User> linked = identity.userOf(tg.id());
        if (linked.isPresent()) {
            return new TelegramWebAppDTO("DONE", issue(linked.get()), label, null, launch.startParam());
        }
        if ("cr".equals(action) || "cu".equals(action)) {
            User user = register(tg, "cu".equals(action) ? Role.CUSTOMER : Role.CREATOR, referralCode);
            return new TelegramWebAppDTO("DONE", issue(user), label, null, launch.startParam());
        }
        if ("have".equals(action)) {
            // Та же запись, что и при «У меня есть аккаунт» в боте: после входа по почте её привязывает /telegram/attach
            String secret = secret();
            TelegramAuth auth = TelegramAuth.builder()
                    .id(newId())
                    .secretHash(hash(secret))
                    .origin(TelegramAuthOrigin.SITE)
                    .status(TelegramAuthStatus.CONFIRMED)
                    .clientInfo("Мини-приложение Telegram")
                    .createdAt(Instant.now())
                    .expiresAt(Instant.now().plus(EMAIL_TTL))
                    .build();
            remember(auth, tg);
            auths.save(auth);
            return new TelegramWebAppDTO("NEED_EMAIL", null, label, secret, launch.startParam());
        }
        return new TelegramWebAppDTO("NEED_ACCOUNT", null, label, null, launch.startParam());
    }

    // ───────────────────────── бот ─────────────────────────

    /** /start auth_&lt;id&gt; — человек пришёл с сайта */
    @Transactional
    public BotStep openFromSite(String id, TgUser tg) {
        TelegramAuth auth = auths.lockById(id).orElse(null);
        if (auth == null || auth.getOrigin() != TelegramAuthOrigin.SITE || auth.expired()
                || auth.getStatus() != TelegramAuthStatus.PENDING) {
            return BotStep.of(Step.EXPIRED);
        }
        if (auth.getTelegramId() != null && auth.getTelegramId() != tg.id()) {
            return BotStep.of(Step.EXPIRED);
        }
        remember(auth, tg);
        auths.save(auth);
        return identity.userOf(tg.id())
                .map(user -> new BotStep(Step.CONFIRM_LOGIN, auth.getId(), user, null, null, auth.getClientInfo()))
                .orElseGet(() -> new BotStep(Step.CHOOSE_ACCOUNT, auth.getId(), null, auth.getRole(), null, null));
    }

    /** /login, /start без кода или кнопка «Войти на сайт» */
    @Transactional
    public BotStep openFromBot(TgUser tg) {
        Optional<User> user = identity.userOf(tg.id());
        TelegramAuth auth = TelegramAuth.builder()
                .id(newId())
                .secretHash(hash(secret()))
                .origin(TelegramAuthOrigin.BOT)
                .status(TelegramAuthStatus.PENDING)
                .createdAt(Instant.now())
                .expiresAt(Instant.now().plus(TTL))
                .build();
        remember(auth, tg);
        if (user.isPresent()) {
            return finish(auth, user.get());
        }
        auths.save(auth);
        return new BotStep(Step.CHOOSE_ACCOUNT, auth.getId(), null, null, null, null);
    }

    /**
     * Кнопка в боте: ok — войти, no — отмена, cr/cu — новая учётка криатора/рекламодателя,
     * have — у меня есть учётка, войду по почте.
     */
    @Transactional
    public BotStep decide(String id, String action, TgUser tg) {
        TelegramAuth auth = auths.lockById(id).orElse(null);
        if (auth == null || auth.expired() || auth.getStatus() != TelegramAuthStatus.PENDING) {
            return BotStep.of(Step.EXPIRED);
        }
        if (auth.getTelegramId() == null || auth.getTelegramId() != tg.id()) {
            return BotStep.of(Step.FOREIGN);
        }
        remember(auth, tg);
        Optional<User> linked = identity.userOf(tg.id());
        switch (action) {
            case "no" -> {
                auth.setStatus(TelegramAuthStatus.CANCELLED);
                auths.save(auth);
                return BotStep.of(Step.CANCELLED);
            }
            case "ok" -> {
                if (linked.isEmpty()) {
                    auths.save(auth);
                    return new BotStep(Step.CHOOSE_ACCOUNT, auth.getId(), null, auth.getRole(), null, null);
                }
                return finish(auth, linked.get());
            }
            case "cr", "cu" -> {
                // Telegram успели привязать (например, во второй вкладке) — входим в ту учётку, дубль не заводим
                User user = linked.orElseGet(() -> register(tg, "cu".equals(action) ? Role.CUSTOMER : Role.CREATOR,
                        auth.getReferralCode()));
                return finish(auth, user);
            }
            case "have" -> {
                if (linked.isPresent()) {
                    return finish(auth, linked.get());
                }
                auth.setStatus(TelegramAuthStatus.CONFIRMED);
                auth.setExpiresAt(Instant.now().plus(EMAIL_TTL));
                if (auth.getOrigin() == TelegramAuthOrigin.SITE) {
                    auths.save(auth);
                    return new BotStep(Step.SITE_USE_EMAIL, auth.getId(), null, null, null, null);
                }
                String secret = secret();
                auth.setSecretHash(hash(secret));
                auths.save(auth);
                return new BotStep(Step.EMAIL_LINK, auth.getId(), null, null, siteLink(secret), null);
            }
            default -> {
                return BotStep.of(Step.EXPIRED);
            }
        }
    }

    // ───────────────────────── внутреннее ─────────────────────────

    private BotStep finish(TelegramAuth auth, User user) {
        auth.setUserId(user.getId());
        auth.setStatus(TelegramAuthStatus.CONFIRMED);
        if (auth.getOrigin() == TelegramAuthOrigin.SITE) {
            auths.save(auth);
            return new BotStep(Step.SITE_LOGGED_IN, auth.getId(), user, null, null, null);
        }
        // Секрет ссылки из бота меняется на каждом шаге: в чате работает только последняя ссылка
        String secret = secret();
        auth.setSecretHash(hash(secret));
        auth.setExpiresAt(Instant.now().plus(TTL));
        auths.save(auth);
        return new BotStep(Step.LOGIN_LINK, auth.getId(), user, null, siteLink(secret), null);
    }

    /** Новая учётка без почты. Если «tg:&lt;id&gt;» уже есть (Telegram отвязывали) — возвращаем её же */
    private User register(TgUser tg, Role role, String referralCode) {
        String login = TelegramIdentity.login(tg.id());
        User user = users.findByUsername(login).orElse(null);
        if (user == null) {
            user = users.save(User.builder()
                    .username(login)
                    .name(tg.displayName())
                    .role(role)
                    .verifiedAt(Instant.now())
                    .referredBy(role == Role.CUSTOMER ? partners.findByCode(referralCode).orElse(null) : null)
                    .build());
            provisioning.provision(user);
            saveContact(user, tg.username());
            log.info("Учётка {} заведена через Telegram, роль {}", user.getId(), role);
        }
        identity.attach(user, tg.id(), tg.username());
        return user;
    }

    /** Ник Telegram сразу попадает в контакт профиля — его видит вторая сторона сделки */
    private void saveContact(User user, String username) {
        if (username == null || username.isBlank()) {
            return;
        }
        String contact = "@" + username;
        if (user.getRole() == Role.CREATOR) {
            creatorProfiles.getByUserId(user.getId()).filter(p -> p.getTelegram() == null).ifPresent(p -> {
                p.setTelegram(contact);
                creatorProfileSaver.save(p);
            });
        } else if (user.getRole() == Role.CUSTOMER) {
            customerProfiles.getByUserId(user.getId()).filter(p -> p.getTelegram() == null).ifPresent(p -> {
                p.setTelegram(contact);
                customerProfileSaver.save(p);
            });
        }
    }

    private AuthResponseDTO issue(User user) {
        if (user.getVerifiedAt() == null) {
            user.setVerifiedAt(Instant.now());
            user = users.save(user);
        }
        String token = jwt.createToken(user.getUsername(), user.getRole(), user.getName());
        return new AuthResponseDTO(token, user.getRole().name(),
                TelegramIdentity.telegramOnly(user) ? null : user.getUsername(), user.getName());
    }

    private static void remember(TelegramAuth auth, TgUser tg) {
        auth.setTelegramId(tg.id());
        auth.setTelegramUsername(trim(tg.username(), 64));
        auth.setTelegramName(tg.displayName());
    }

    private static String telegramLabel(TelegramAuth auth) {
        return auth.getTelegramUsername() != null ? "@" + auth.getTelegramUsername() : auth.getTelegramName();
    }

    private String siteLink(String secret) {
        // Секрет во фрагменте: он не уходит на сервер в запросе страницы и не попадает в Referer
        return (siteUrl == null ? "" : siteUrl.replaceAll("/+$", "")) + "/auth/telegram#" + secret;
    }

    @Scheduled(fixedDelay = 60 * 60 * 1000L, initialDelay = 5 * 60 * 1000L)
    @Transactional
    public void cleanup() {
        int removed = auths.deleteExpiredBefore(Instant.now().minus(Duration.ofDays(1)));
        if (removed > 0) {
            log.info("Вход через Telegram: удалено устаревших попыток {}", removed);
        }
    }

    static String hash(String secret) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256")
                    .digest(secret.getBytes(StandardCharsets.UTF_8)));
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    private static String newId() {
        byte[] raw = new byte[16];
        RANDOM.nextBytes(raw);
        return HexFormat.of().formatHex(raw);
    }

    private static String secret() {
        byte[] raw = new byte[32];
        RANDOM.nextBytes(raw);
        return Base64.getUrlEncoder().withoutPadding().encodeToString(raw);
    }

    private static String blankToNull(String value) {
        return value == null || value.isBlank() ? null : value.strip();
    }

    private static String trim(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() > max ? value.substring(0, max) : value;
    }
}
