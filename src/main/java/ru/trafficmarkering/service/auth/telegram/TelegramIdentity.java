package ru.trafficmarkering.service.auth.telegram;

import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;
import ru.trafficmarkering.model.User;
import ru.trafficmarkering.model.studio.StudioProfile;
import ru.trafficmarkering.repository.StudioProfileRepository;
import ru.trafficmarkering.repository.UserRepository;

import java.time.Instant;
import java.util.Optional;

/**
 * Связка «Telegram ↔ учётка сайта». Один Telegram — ровно одна учётка.
 * Учётка, заведённая через бота, живёт без почты: логин у неё «tg:&lt;id&gt;», и Telegram — её единственный вход,
 * поэтому такую связку нельзя ни снять, ни перехватить, пока к учётке не добавлена почта.
 */
@Service
@RequiredArgsConstructor
public class TelegramIdentity {

    public static final String LOGIN_PREFIX = "tg:";

    private final StudioProfileRepository profiles;
    private final UserRepository users;

    public static String login(long telegramId) {
        return LOGIN_PREFIX + telegramId;
    }

    /** Учётка без почты: войти в неё можно только через Telegram */
    public static boolean telegramOnly(User user) {
        return user.getUsername() != null && user.getUsername().startsWith(LOGIN_PREFIX);
    }

    @Transactional(readOnly = true)
    public Optional<User> userOf(long telegramId) {
        return profiles.findByTelegramId(telegramId).flatMap(p -> users.findById(p.getUserId()));
    }

    @Transactional(readOnly = true)
    public Optional<StudioProfile> linkOf(Long userId) {
        return profiles.findById(userId).filter(p -> p.getTelegramId() != null);
    }

    /**
     * Привязывает Telegram к учётке. Если он был привязан к другой учётке с почтой — переезжает;
     * если к учётке без почты — отказ, иначе та учётка потеряет единственный вход.
     */
    @Transactional
    public void attach(User user, long telegramId, String telegramUsername) {
        StudioProfile profile = profiles.findById(user.getId())
                .orElseGet(() -> StudioProfile.builder().userId(user.getId()).build());
        if (profile.getTelegramId() != null && profile.getTelegramId() == telegramId) {
            profile.setTelegramUsername(telegramUsername);
            profiles.save(profile);
            return;
        }
        if (telegramOnly(user) && profile.getTelegramId() != null) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "В эту учётку входят через другой Telegram. Сначала добавьте к ней почту.");
        }
        profiles.findByTelegramId(telegramId)
                .filter(other -> !other.getUserId().equals(user.getId()))
                .ifPresent(other -> {
                    User otherUser = users.findById(other.getUserId()).orElse(null);
                    if (otherUser != null && telegramOnly(otherUser)) {
                        throw new ResponseStatusException(HttpStatus.CONFLICT,
                                "Этот Telegram — вход в другую учётку Offer («" + otherUser.getName() + "»). "
                                        + "Войдите в неё через Telegram или сначала добавьте к ней почту.");
                    }
                    other.setTelegramId(null);
                    other.setTelegramUsername(null);
                    other.setTelegramLinkedAt(null);
                    profiles.saveAndFlush(other);
                });
        profile.setTelegramId(telegramId);
        profile.setTelegramUsername(telegramUsername);
        profile.setTelegramLinkedAt(Instant.now());
        profile.setLinkCode(null);
        profile.setLinkCodeExpiresAt(null);
        profiles.save(profile);
    }

    @Transactional
    public void detach(User user) {
        if (telegramOnly(user)) {
            throw new ResponseStatusException(HttpStatus.CONFLICT,
                    "Telegram — единственный вход в эту учётку. Сначала добавьте почту, потом отвязывайте.");
        }
        profiles.findById(user.getId()).ifPresent(profile -> {
            profile.setTelegramId(null);
            profile.setTelegramUsername(null);
            profile.setTelegramLinkedAt(null);
            profiles.save(profile);
        });
    }
}
