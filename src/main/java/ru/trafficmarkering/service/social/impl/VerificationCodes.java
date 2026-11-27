package ru.trafficmarkering.service.social.impl;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;
import ru.trafficmarkering.model.application.Platform;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Locale;

/**
 * Проверочный код для описания профиля. Код не хранится: это HMAC от креатора, площадки
 * и id аккаунта, поэтому он одинаковый при каждом запросе и не подходит к чужому аккаунту.
 * Сравнение терпимо к регистру, пробелам и дефису — креаторы перепечатывают код руками.
 */
@Component
class VerificationCodes {

    static final String PREFIX = "OFFER";
    private static final String ALPHABET = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789";
    private static final int LENGTH = 6;

    private final SecretKeySpec key;

    VerificationCodes(@Value("${social.token.secret}") String secret) {
        this.key = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
    }

    String codeFor(Long userId, Platform platform, String externalId) {
        byte[] digest = hmac("social-verify:" + userId + ":" + platform.name() + ":" + externalId);
        StringBuilder code = new StringBuilder(PREFIX).append('-');
        for (int i = 0; i < LENGTH; i++) {
            code.append(ALPHABET.charAt((digest[i] & 0xFF) % ALPHABET.length()));
        }
        return code.toString();
    }

    static boolean present(String bio, String code) {
        if (bio == null || code == null) {
            return false;
        }
        return normalize(bio).contains(normalize(code));
    }

    private static String normalize(String text) {
        return text.toUpperCase(Locale.ROOT).replaceAll("[^A-Z0-9]", "");
    }

    private byte[] hmac(String payload) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            mac.init(key);
            return mac.doFinal(payload.getBytes(StandardCharsets.UTF_8));
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 недоступен", e);
        }
    }
}
