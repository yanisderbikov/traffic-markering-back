package ru.trafficmarkering.service.social.impl;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;
import ru.trafficmarkering.model.application.Platform;
import ru.trafficmarkering.service.social.OAuthStateService;

import javax.crypto.SecretKey;
import java.nio.charset.StandardCharsets;
import java.util.Date;
import java.util.UUID;

@Service
class OAuthStateServiceImpl implements OAuthStateService {

    private static final String TYPE_CLAIM = "typ";
    private static final String TYPE_VALUE = "social_state";
    private static final String PLATFORM_CLAIM = "platform";
    private static final long TTL_MS = 10 * 60 * 1000L;

    private final SecretKey key;

    OAuthStateServiceImpl(@Value("${jwt.secret}") String secret) {
        this.key = Keys.hmacShaKeyFor(secret.getBytes(StandardCharsets.UTF_8));
    }

    @Override
    public String issue(Long userId, Platform platform) {
        Date now = new Date();
        return Jwts.builder()
                .subject(String.valueOf(userId))
                .claim(TYPE_CLAIM, TYPE_VALUE)
                .claim(PLATFORM_CLAIM, platform.name())
                .id(UUID.randomUUID().toString())
                .issuedAt(now)
                .expiration(new Date(now.getTime() + TTL_MS))
                .signWith(key)
                .compact();
    }

    @Override
    public Long verify(String state, Platform platform) {
        if (state == null || state.isBlank()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Не передан параметр state");
        }
        Claims claims;
        try {
            claims = Jwts.parser().verifyWith(key).build().parseSignedClaims(state).getPayload();
        } catch (JwtException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST,
                    "Ссылка подключения устарела, начните привязку заново");
        }
        if (!TYPE_VALUE.equals(claims.get(TYPE_CLAIM, String.class))
                || !platform.name().equals(claims.get(PLATFORM_CLAIM, String.class))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Некорректный параметр state");
        }
        try {
            return Long.valueOf(claims.getSubject());
        } catch (NumberFormatException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Некорректный параметр state");
        }
    }
}
