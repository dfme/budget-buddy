package com.budgetbuddy.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.budgetbuddy.support.PostgresTestDatabase;
import io.jsonwebtoken.Jwts;
import io.jsonwebtoken.security.Keys;
import jakarta.servlet.http.Cookie;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.util.Date;
import javax.crypto.SecretKey;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.core.Authentication;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * End-to-End-Test des JWT-Cookie-Filters über die echte SecurityFilterChain (Schritt 4):
 * gültiges Cookie → 200 + User-ID im SecurityContext, ungültiges/abgelaufenes/fehlendes Cookie →
 * 401. Deckt damit alle Acceptance Criteria von BE-AUTH-01 ab, plus die Erweiterung aus
 * BE-AUTH-11 (#201): der Filter braucht seit der {@code tokenVersion}-Prüfung eine echte
 * {@code users}-Tabelle und läuft deshalb — anders als vor #201 — mit aktivem Flyway.
 */
@SpringBootTest
@AutoConfigureMockMvc
@ActiveProfiles("test")
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
@Import(JwtCookieAuthenticationFilterTest.TestController.class)
class JwtCookieAuthenticationFilterTest {

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.register(registry, "jwt_cookie_filter");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JwtProperties jwtProperties;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long userId;

    @BeforeEach
    void insertUser() {
        jdbcTemplate.update("DELETE FROM users");
        jdbcTemplate.update(
                "INSERT INTO users (email, password_hash, onboarding_completed, token_version)"
                        + " VALUES (?, ?, ?, ?)",
                "filter-test@example.ch", "bcrypt-hash", false, 0);
        userId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE email = 'filter-test@example.ch'", Long.class);
    }

    @Test
    void validCookieGrantsAccessAndExposesUserId() throws Exception {
        String token = jwtService.generateToken(userId);

        mockMvc.perform(get("/api/test/me").cookie(new Cookie("jwt", token)))
                .andExpect(status().isOk())
                .andExpect(content().string(Long.toString(userId)));
    }

    @Test
    void invalidCookieReturns401() throws Exception {
        mockMvc.perform(get("/api/test/me").cookie(new Cookie("jwt", "not-a-valid-jwt")))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void expiredCookieReturns401() throws Exception {
        // Gleiches Secret wie der Kontext, aber sofort abgelaufen → gültige Signatur, exp in der
        // Vergangenheit → ExpiredJwtException im Filter → 401.
        JwtService expiredIssuer =
                new JwtService(new JwtProperties(jwtProperties.secret(), Duration.ofSeconds(-1)));
        String expired = expiredIssuer.generateToken(userId);

        mockMvc.perform(get("/api/test/me").cookie(new Cookie("jwt", expired)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void noCookieReturns401() throws Exception {
        mockMvc.perform(get("/api/test/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void outdatedTokenVersionReturns401() throws Exception {
        // Token trägt tokenVersion=0, die DB steht bereits auf 1 — simuliert ein Token, das vor
        // einer Passwort-Änderung ausgestellt wurde (BE-AUTH-11, #201).
        jdbcTemplate.update("UPDATE users SET token_version = 1 WHERE id = ?", userId);
        String staleToken = jwtService.generateToken(userId, 0);

        mockMvc.perform(get("/api/test/me").cookie(new Cookie("jwt", staleToken)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenForUnknownUserReturns401() throws Exception {
        String token = jwtService.generateToken(userId + 1_000_000);

        mockMvc.perform(get("/api/test/me").cookie(new Cookie("jwt", token)))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void tokenWithoutTokenVersionClaimReturns401() throws Exception {
        // Simuliert ein vor BE-AUTH-11 ausgestelltes JWT ohne tokenVersion-Claim — muss sauber
        // als 401 abgelehnt werden statt als 500 durchzuschlagen (siehe JwtServiceTest).
        SecretKey key = Keys.hmacShaKeyFor(jwtProperties.secret().getBytes(StandardCharsets.UTF_8));
        Instant now = Instant.now();
        String tokenWithoutClaim =
                Jwts.builder()
                        .subject(Long.toString(userId))
                        .issuedAt(Date.from(now))
                        .expiration(Date.from(now.plus(Duration.ofHours(1))))
                        .signWith(key, Jwts.SIG.HS256)
                        .compact();

        mockMvc.perform(get("/api/test/me").cookie(new Cookie("jwt", tokenWithoutClaim)))
                .andExpect(status().isUnauthorized());
    }

    @RestController
    static class TestController {

        @GetMapping("/api/test/me")
        String me(Authentication authentication) {
            return authentication.getPrincipal().toString();
        }
    }
}
