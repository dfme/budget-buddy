package com.budgetbuddy.auth;

import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.budgetbuddy.support.PostgresTestDatabase;
import jakarta.servlet.http.Cookie;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integrationstest der {@code /users/me}-Endpoints (BE-AUTH-02) gegen echtes PostgreSQL + Flyway.
 *
 * <p>Flyway ist aktiv (analog {@code UsersMigrationTest}): die {@code users}-Tabelle muss real
 * existieren. Die Datenbank gehört dieser Klasse allein (siehe {@code PostgresTestDatabase});
 * {@code @DirtiesContext} schliesst den Hikari-Pool nach der Klasse.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class UserControllerTest {

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.register(registry, "user_controller");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private PasswordEncoder passwordEncoder;

    private long userId;

    @BeforeEach
    void insertUser() {
        jdbcTemplate.update("DELETE FROM users");
        jdbcTemplate.update(
                "INSERT INTO users (email, password_hash, monthly_income, onboarding_completed)"
                        + " VALUES (?, ?, ?, ?)",
                "lara@example.ch", "bcrypt-hash", new java.math.BigDecimal("4200.00"), true);
        userId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE email = 'lara@example.ch'", Long.class);
    }

    private Cookie jwtCookie() {
        return new Cookie("jwt", jwtService.generateToken(userId));
    }

    @Test
    void getCurrentUserWithValidJwtReturnsProfile() throws Exception {
        mockMvc.perform(get("/api/users/me").cookie(jwtCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value((int) userId))
                .andExpect(jsonPath("$.email").value("lara@example.ch"))
                .andExpect(jsonPath("$.monthlyIncome").value(4200.00))
                .andExpect(jsonPath("$.onboardingCompleted").value(true));
    }

    @Test
    void getCurrentUserWithoutJwtReturns401() throws Exception {
        mockMvc.perform(get("/api/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateIncomeWithPositiveAmountPersistsAndReturns200() throws Exception {
        mockMvc.perform(put("/api/users/me/income")
                        .cookie(jwtCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"betrag\": 5000.50}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.monthlyIncome").value(5000.50));

        java.math.BigDecimal persisted = jdbcTemplate.queryForObject(
                "SELECT monthly_income FROM users WHERE id = ?",
                java.math.BigDecimal.class, userId);
        org.assertj.core.api.Assertions.assertThat(persisted).isEqualByComparingTo("5000.50");
    }

    @Test
    void updateIncomeWithZeroReturns400() throws Exception {
        mockMvc.perform(put("/api/users/me/income")
                        .cookie(jwtCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"betrag\": 0}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateIncomeWithNegativeReturns400() throws Exception {
        mockMvc.perform(put("/api/users/me/income")
                        .cookie(jwtCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"betrag\": -10}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateIncomeWithMissingBetragReturns400() throws Exception {
        mockMvc.perform(put("/api/users/me/income")
                        .cookie(jwtCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateIncomeWithoutJwtReturns401() throws Exception {
        mockMvc.perform(put("/api/users/me/income")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"betrag\": 5000.00}"))
                .andExpect(status().isUnauthorized());
    }

    // --- POST /users/me/onboarding-complete (BE-FC-03, US-03) ---

    @Test
    void onboardingCompleteSetsTheFlagInTheDatabase() throws Exception {
        jdbcTemplate.update("UPDATE users SET onboarding_completed = false WHERE id = ?", userId);

        mockMvc.perform(post("/api/users/me/onboarding-complete").cookie(jwtCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onboardingCompleted").value(true));

        // Gegen die Datenbank geprüft, nicht nur gegen die Antwort: die Antwort käme auch aus einer
        // Entity, die nie geflusht wurde.
        org.assertj.core.api.Assertions.assertThat(onboardingCompleted()).isTrue();
    }

    @Test
    void onboardingCompleteIsIdempotent() throws Exception {
        jdbcTemplate.update("UPDATE users SET onboarding_completed = false WHERE id = ?", userId);

        mockMvc.perform(post("/api/users/me/onboarding-complete").cookie(jwtCookie()))
                .andExpect(status().isOk());
        // Zweiter Aufruf ist kein Fehler — der Client muss den Zustand nicht vorher prüfen.
        mockMvc.perform(post("/api/users/me/onboarding-complete").cookie(jwtCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.onboardingCompleted").value(true));

        org.assertj.core.api.Assertions.assertThat(onboardingCompleted()).isTrue();
    }

    @Test
    void onboardingCompleteWithoutJwtReturns401() throws Exception {
        mockMvc.perform(post("/api/users/me/onboarding-complete"))
                .andExpect(status().isUnauthorized());
    }

    private boolean onboardingCompleted() {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject(
                "SELECT onboarding_completed FROM users WHERE id = ?", Boolean.class, userId));
    }

    // --- PUT /users/me/password (BE-AUTH-09) ---

    private void setPasswordHash(String rawPassword) {
        jdbcTemplate.update("UPDATE users SET password_hash = ? WHERE id = ?",
                passwordEncoder.encode(rawPassword), userId);
    }

    private String currentPasswordHash() {
        return jdbcTemplate.queryForObject(
                "SELECT password_hash FROM users WHERE id = ?", String.class, userId);
    }

    @Test
    void changePasswordWithCorrectCurrentPasswordReturns200AndAllowsLoginWithNewPasswordOnly()
            throws Exception {
        setPasswordHash("altesPasswort1");

        mockMvc.perform(put("/api/users/me/password")
                        .cookie(jwtCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"aktuellesPasswort\": \"altesPasswort1\", "
                                + "\"neuesPasswort\": \"neuesPasswort2\"}"))
                .andExpect(status().isOk())
                .andExpect(content().string(""));

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"lara@example.ch\", \"password\": \"altesPasswort1\"}"))
                .andExpect(status().isUnauthorized());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"lara@example.ch\", \"password\": \"neuesPasswort2\"}"))
                .andExpect(status().isOk());
    }

    @Test
    void changePasswordWithWrongCurrentPasswordReturns400WithMessageAndLeavesHashUnchanged()
            throws Exception {
        setPasswordHash("altesPasswort1");
        String hashBefore = currentPasswordHash();

        mockMvc.perform(put("/api/users/me/password")
                        .cookie(jwtCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"aktuellesPasswort\": \"falschesPasswort\", "
                                + "\"neuesPasswort\": \"neuesPasswort2\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Aktuelles Passwort falsch"))
                // Weder das falsche noch das neue Passwort dürfen in der Response auftauchen.
                .andExpect(content().string(not(containsString("falschesPasswort"))))
                .andExpect(content().string(not(containsString("neuesPasswort2"))));

        org.assertj.core.api.Assertions.assertThat(currentPasswordHash()).isEqualTo(hashBefore);
    }

    @Test
    void changePasswordWithShortNewPasswordReturns400WithoutLeakingIt() throws Exception {
        setPasswordHash("altesPasswort1");

        mockMvc.perform(put("/api/users/me/password")
                        .cookie(jwtCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"aktuellesPasswort\": \"altesPasswort1\", "
                                + "\"neuesPasswort\": \"kurz\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Passwort muss mindestens 8 Zeichen lang sein."))
                // Bean Validation darf den abgelehnten Wert nicht in die Response spiegeln.
                .andExpect(content().string(not(containsString("kurz"))))
                .andExpect(content().string(not(containsString("altesPasswort1"))));
    }

    @Test
    void changePasswordWithBlankCurrentPasswordReturns400WithGermanMessage() throws Exception {
        setPasswordHash("altesPasswort1");

        mockMvc.perform(put("/api/users/me/password")
                        .cookie(jwtCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"aktuellesPasswort\": \"\", \"neuesPasswort\": \"neuesPasswort2\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Aktuelles Passwort ist erforderlich."));
    }

    @Test
    void changePasswordWithoutJwtReturns401() throws Exception {
        mockMvc.perform(put("/api/users/me/password")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"aktuellesPasswort\": \"altesPasswort1\", "
                                + "\"neuesPasswort\": \"neuesPasswort2\"}"))
                .andExpect(status().isUnauthorized());
    }
}
