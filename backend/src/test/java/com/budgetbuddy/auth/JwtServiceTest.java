package com.budgetbuddy.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.JwtException;
import io.jsonwebtoken.security.SignatureException;
import java.time.Duration;
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

        assertThat(jwtService.validateAndGetUserId(token)).isEqualTo(42L);
    }

    @Test
    void rejectsExpiredToken() {
        // Negative Gültigkeitsdauer → Token ist sofort abgelaufen.
        JwtService shortLived = new JwtService(new JwtProperties(SECRET, Duration.ofSeconds(-1)));
        String expired = shortLived.generateToken(1L);

        assertThatThrownBy(() -> jwtService.validateAndGetUserId(expired))
                .isInstanceOf(ExpiredJwtException.class);
    }

    @Test
    void rejectsTamperedToken() {
        String token = jwtService.generateToken(7L);
        // Letztes Signatur-Zeichen verändern → Signaturprüfung muss fehlschlagen.
        char last = token.charAt(token.length() - 1);
        String tampered = token.substring(0, token.length() - 1) + (last == 'a' ? 'b' : 'a');

        assertThatThrownBy(() -> jwtService.validateAndGetUserId(tampered))
                .isInstanceOf(JwtException.class);
    }

    @Test
    void rejectsTokenSignedWithDifferentSecret() {
        JwtService other =
                new JwtService(new JwtProperties("a-completely-different-secret-0123456789", Duration.ofHours(1)));
        String foreignToken = other.generateToken(5L);

        assertThatThrownBy(() -> jwtService.validateAndGetUserId(foreignToken))
                .isInstanceOf(SignatureException.class);
    }
}
