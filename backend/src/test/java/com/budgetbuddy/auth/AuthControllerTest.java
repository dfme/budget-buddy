package com.budgetbuddy.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.nio.file.Files;
import java.nio.file.Path;
import org.hamcrest.Matchers;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integrationstest der {@code /auth}-Endpoints (BE-AUTH-03) gegen echtes SQLite + Flyway.
 *
 * <p>Aufbau analog {@code UserControllerTest}: Temp-File-DB (kein {@code :memory:}, da Flyway die
 * Tabelle real anlegen muss) und {@code @DirtiesContext} zum Freigeben des File-Handles.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AuthControllerTest {

    private static final Path DB_FILE = createTempDbFile();

    private static Path createTempDbFile() {
        try {
            Path file = Files.createTempFile("be-auth-03-test", ".db");
            Files.deleteIfExists(file);
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
    private JdbcTemplate jdbcTemplate;

    @BeforeEach
    void clearUsers() {
        jdbcTemplate.update("DELETE FROM users");
    }

    private static final String LARA =
            "{\"email\": \"lara@example.ch\", \"password\": \"geheim123\"}";

    @Test
    void registerCreatesUserSetsCookieAndStoresBcryptHash() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LARA))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, Matchers.containsString("jwt=")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, Matchers.containsString("HttpOnly")))
                .andExpect(header().string(
                        HttpHeaders.SET_COOKIE, Matchers.containsString("SameSite=Strict")))
                .andExpect(jsonPath("$.email").value("lara@example.ch"))
                .andExpect(jsonPath("$.onboardingCompleted").value(false));

        String storedHash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM users WHERE email = 'lara@example.ch'", String.class);
        assertThat(storedHash).isNotEqualTo("geheim123");
        assertThat(storedHash).startsWith("$2"); // bcrypt-Prefix
    }

    @Test
    void registerWithDuplicateEmailReturns409() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(LARA))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(LARA))
                .andExpect(status().isConflict());
    }

    @Test
    void registerWithInvalidEmailReturns400() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"not-an-email\", \"password\": \"geheim123\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void loginWithCorrectCredentialsReturns200AndCookie() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(LARA))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON).content(LARA))
                .andExpect(status().isOk())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, Matchers.containsString("jwt=")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, Matchers.containsString("HttpOnly")))
                .andExpect(header().string(
                        HttpHeaders.SET_COOKIE, Matchers.containsString("SameSite=Strict")))
                .andExpect(jsonPath("$.email").value("lara@example.ch"));
    }

    @Test
    void loginWithWrongPasswordReturns401() throws Exception {
        mockMvc.perform(post("/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(LARA))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"lara@example.ch\", \"password\": \"falsch123\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginWithUnknownEmailReturns401() throws Exception {
        mockMvc.perform(post("/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"nobody@example.ch\", \"password\": \"geheim123\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutClearsCookieWithMaxAgeZero() throws Exception {
        mockMvc.perform(post("/auth/logout"))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, Matchers.containsString("jwt=")))
                .andExpect(header().string(
                        HttpHeaders.SET_COOKIE, Matchers.containsString("Max-Age=0")));
    }
}
