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
 * Erzeugt und validiert JWTs (HS256) für die Authentifizierung (BE-AUTH-01, ADR-7).
 *
 * <p>Der Signier-Key wird aus dem {@link JwtProperties#secret()} abgeleitet; HS256 wird bewusst
 * explizit gesetzt, da jjwt sonst aus der Key-Länge den stärksten Algorithmus (z.B. HS512) wählen
 * würde. Die User-ID wird im {@code subject}-Claim transportiert, die {@code tokenVersion} als
 * eigener Claim (BE-AUTH-11, #201) — der {@link JwtCookieAuthenticationFilter} vergleicht sie
 * gegen den aktuellen Stand in der DB, um Tokens nach einer Passwort-Änderung abzulehnen.
 */
@Service
public class JwtService {

    private static final String TOKEN_VERSION_CLAIM = "tokenVersion";

    private final SecretKey key;
    private final Duration expiration;

    public JwtService(JwtProperties properties) {
        this.key = Keys.hmacShaKeyFor(properties.secret().getBytes(StandardCharsets.UTF_8));
        this.expiration = properties.expiration();
    }

    /**
     * Erzeugt ein signiertes JWT mit der User-ID als {@code subject} und {@code tokenVersion=0}.
     *
     * <p>Convenience-Overload für Aufrufer, die keine Versionierung brauchen (Tests) — passt zum
     * Spalten-Default von {@code users.token_version} (Flyway V08) für frisch angelegte User.
     */
    public String generateToken(long userId) {
        return generateToken(userId, 0);
    }

    /** Erzeugt ein signiertes JWT mit der User-ID als {@code subject} und der {@code tokenVersion}. */
    public String generateToken(long userId, long tokenVersion) {
        Instant now = Instant.now();
        return Jwts.builder()
                .subject(Long.toString(userId))
                .claim(TOKEN_VERSION_CLAIM, tokenVersion)
                .issuedAt(Date.from(now))
                .expiration(Date.from(now.plus(expiration)))
                .signWith(key, Jwts.SIG.HS256)
                .compact();
    }

    /**
     * Validiert Signatur und Ablauf des Tokens und liefert User-ID und {@code tokenVersion}.
     *
     * @throws JwtException wenn das Token ungültig, abgelaufen, manipuliert oder das Subject keine
     *     gültige User-ID ist. Der Aufrufer (Filter) behandelt dies als „nicht authentifiziert".
     */
    public TokenClaims validate(String token) throws JwtException {
        var payload = Jwts.parser()
                .verifyWith(key)
                .build()
                .parseSignedClaims(token)
                .getPayload();
        String subject = payload.getSubject();
        try {
            long userId = Long.parseLong(subject);
            // Nicht payload.get(claim, Long.class): jjwt/Jackson deserialisiert kleine Zahlen als
            // Integer, ein direkter Cast auf Long würfe eine ClassCastException. Number.longValue()
            // funktioniert unabhängig vom konkreten deserialisierten Zahlentyp.
            long tokenVersion = ((Number) payload.get(TOKEN_VERSION_CLAIM)).longValue();
            return new TokenClaims(userId, tokenVersion);
        } catch (NumberFormatException e) {
            throw new JwtException("JWT-Subject ist keine gültige User-ID: " + subject, e);
        }
    }

    /** Aus dem JWT extrahierte Claims: User-ID ({@code subject}) und {@code tokenVersion}. */
    public record TokenClaims(long userId, long tokenVersion) {}
}
