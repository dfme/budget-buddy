package com.budgetbuddy.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import io.jsonwebtoken.security.SignatureException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

/**
 * Reiner Unit-Test für {@link JwtService} (kein Spring-Kontext): Round-Trip sowie
 * Ablehnung von abgelaufenen, manipulierten und fremd-signierten Tokens.
 */
class JwtServiceTest {

    private static final String SECRET = "unit-test-secret-long-enough-for-hs256-0123456789";

    private JwtService jwtService;

    @BeforeEach
    void setUp() {
        jwtService = new JwtService(new JwtProperties(SECRET, Duration.ofHours(1)));
    }

    @Test
    void generatesAndValidatesToken() {
        String token = jwtService.generateToken(42L);

        JwtService.TokenClaims claims = jwtService.validate(token);
        assertThat(claims.userId()).isEqualTo(42L);
        assertThat(claims.tokenVersion()).isEqualTo(0L);
    }

    @Test
    void generatesAndValidatesTokenWithExplicitTokenVersion() {
        String token = jwtService.generateToken(42L, 3L);

        JwtService.TokenClaims claims = jwtService.validate(token);
        assertThat(claims.userId()).isEqualTo(42L);
        assertThat(claims.tokenVersion()).isEqualTo(3L);
    }

    @Test
    void rejectsExpiredToken() {
        // Negative Gültigkeitsdauer → Token ist sofort abgelaufen.
        JwtService shortLived = new JwtService(new JwtProperties(SECRET, Duration.ofSeconds(-1)));
        String expired = shortLived.generateToken(1L);

        assertThatThrownBy(() -> jwtService.validate(expired))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void rejectsTamperedToken() {
        String token = jwtService.generateToken(7L);
        // ERSTES Signatur-Zeichen ändern (high-order Bits) → dekodiert garantiert zu anderen
        // Bytes. Das letzte base64url-Zeichen hätte zu wenige signifikante Bits und könnte zur
        // identischen Signatur dekodieren.
        String[] parts = token.split("\\.");
        char first = parts[2].charAt(0);
        String tamperedSignature = (first == 'A' ? 'B' : 'A') + parts[2].substring(1);
        String tampered = parts[0] + "." + parts[1] + "." + tamperedSignature;

        assertThatThrownBy(() -> jwtService.validate(tampered))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsTokenSignedWithDifferentSecret() {
        JwtService other =
                new JwtService(new JwtProperties("a-completely-different-secret-0123456789", Duration.ofHours(1)));
        String foreignToken = other.generateToken(5L);

        assertThatThrownBy(() -> jwtService.validate(foreignToken))
                .isInstanceOf(SignatureException.class);
    }

    @Test
    void rejectsTokenWithoutTokenVersionClaim() {
        // Simuliert ein vor BE-AUTH-11 ausgestelltes JWT, das den tokenVersion-Claim noch nicht kennt.
        SecretKey key = Keys.hmacShaKeyFor(SECRET.getBytes(StandardCharsets.UTF_8));
        Instant now = Instant.now();
        String tokenWithoutClaim =
                Jwts.builder()
                        .subject("42")
                        .issuedAt(Date.from(now))
                        .expiration(Date.from(now.plus(Duration.ofHours(1))))
                        .signWith(key, Jwts.SIG.HS256)
                        .compact();

        assertThatThrownBy(() -> jwtService.validate(tokenWithoutClaim)).isInstanceOf(JwtException.class);
    }
}
