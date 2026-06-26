package com.budgetbuddy.auth;

import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.springframework.stereotype.Service;

/**
 * Erzeugt und validiert JWTs (HS256) für die stateless Authentifizierung (BE-AUTH-01, ADR-7).
 *
 * <p>Der Signier-Key wird aus dem {@link JwtProperties#secret()} abgeleitet; HS256 wird bewusst
 * explizit gesetzt, da jjwt sonst aus der Key-Länge den stärksten Algorithmus (z.B. HS512) wählen
 * würde. Die User-ID wird im {@code subject}-Claim transportiert.
 */
@Service
public class JwtService {

    private final SecretKey key;
    private final Duration expiration;

    public JwtService(JwtProperties properties) {
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
        this.expiration = properties.expiration();
    }

    /** Erzeugt ein signiertes JWT mit der User-ID als {@code subject}. */
    public String generateToken(long userId) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(Long.toString(userId))
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiration)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Validiert Signatur und Ablauf des Tokens und liefert die User-ID.
     *
     * @throws JwtException wenn das Token ungültig, abgelaufen, manipuliert oder das Subject keine
     *     gültige User-ID ist. Der Aufrufer (Filter) behandelt dies als „nicht authentifiziert".
     */
    public long validateAndGetUserId(String token) throws JwtException {
        String subject = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload()
                .getSubject();
        try {
            return Long.parseLong(subject);
        } catch (NumberFormatException e) {
            throw new JwtException("JWT-Subject ist keine gültige User-ID: " + subject, e);
        }
    }
}
