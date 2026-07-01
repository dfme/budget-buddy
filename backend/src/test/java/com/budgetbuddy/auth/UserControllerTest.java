package com.budgetbuddy.auth;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import jakarta.servlet.http.Cookie;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integrationstest der {@code /users/me}-Endpoints (BE-AUTH-02) gegen echtes SQLite + Flyway.
 *
 * <p>Bewusst Temp-File-DB statt {@code jdbc:sqlite::memory:} und Flyway aktiv (analog
 * {@code UsersMigrationTest}): die {@code users}-Tabelle muss real existieren, und In-Memory-SQLite
 * legt pro Connection eine eigene DB an. {@code @DirtiesContext} gibt das File-Handle nach der
 * Klasse frei (Windows).
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class UserControllerTest {

    private static final Path DB_FILE = createTempDbFile();

    private static Path createTempDbFile() {
        try {
            Path file = Files.createTempFile("be-auth-02-test", ".db");
            Files.deleteIfExists(file); // Flyway/SQLite legt die Datei selbst an
            file.toFile().deleteOnExit();
            return file;
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        registry.add("spring.datasource.url", () -> "jdbc:sqlite:" + DB_FILE);
        registry.add("spring.flyway.enabled", () -> "true");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private JdbcTemplate jdbcTemplate;

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
        mockMvc.perform(get("/users/me").cookie(jwtCookie()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value((int) userId))
                .andExpect(jsonPath("$.email").value("lara@example.ch"))
                .andExpect(jsonPath("$.monthlyIncome").value(4200.00))
                .andExpect(jsonPath("$.onboardingCompleted").value(true));
    }

    @Test
    void getCurrentUserWithoutJwtReturns401() throws Exception {
        mockMvc.perform(get("/users/me"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void updateIncomeWithPositiveAmountPersistsAndReturns200() throws Exception {
        mockMvc.perform(put("/users/me/income")
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
        mockMvc.perform(put("/users/me/income")
                        .cookie(jwtCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"betrag\": 0}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateIncomeWithNegativeReturns400() throws Exception {
        mockMvc.perform(put("/users/me/income")
                        .cookie(jwtCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"betrag\": -10}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateIncomeWithMissingBetragReturns400() throws Exception {
        mockMvc.perform(put("/users/me/income")
                        .cookie(jwtCookie())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void updateIncomeWithoutJwtReturns401() throws Exception {
        mockMvc.perform(put("/users/me/income")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"betrag\": 5000.00}"))
                .andExpect(status().isUnauthorized());
    }
}
