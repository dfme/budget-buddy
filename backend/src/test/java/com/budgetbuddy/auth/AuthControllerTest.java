package com.budgetbuddy.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.containsString;
import static org.hamcrest.Matchers.not;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.budgetbuddy.support.PostgresTestDatabase;
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
 * Integrationstest der {@code /auth}-Endpoints (BE-AUTH-03) gegen echtes PostgreSQL + Flyway.
 *
 * <p>Aufbau analog {@code UserControllerTest}: eigene Datenbank auf dem gemeinsamen Testcontainer
 * (Flyway muss die Tabelle real anlegen) und {@code @DirtiesContext} zum Schliessen des Pools.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class AuthControllerTest {

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.register(registry, "auth_controller");
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
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LARA))
                .andExpect(status().isCreated())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, Matchers.containsString("jwt=")))
                .andExpect(header().string(HttpHeaders.SET_COOKIE, Matchers.containsString("HttpOnly")))
                .andExpect(header().string(
                        HttpHeaders.SET_COOKIE, Matchers.containsString("SameSite=Strict")))
                .andExpect(jsonPath("$.email").value("lara@example.ch"))
                .andExpect(jsonPath("$.onboardingCompleted").value(false))
                // Review-Befund #230: doesNotExist() lässt einen vorhandenen null-Wert
                // ununterscheidbar von einem fehlenden Feld durch — value(nullValue()) prüft den
                // tatsächlichen Vertrag (Feld ist da, Wert ist null).
                .andExpect(jsonPath("$.firstName").value(Matchers.nullValue()))
                .andExpect(jsonPath("$.lastName").value(Matchers.nullValue()));

        String storedHash = jdbcTemplate.queryForObject(
                "SELECT password_hash FROM users WHERE email = 'lara@example.ch'", String.class);
        assertThat(storedHash).isNotEqualTo("geheim123");
        assertThat(storedHash).startsWith("$2"); // bcrypt-Prefix
    }

    @Test
    void registerWithNameStoresAndReturnsFirstNameAndLastName() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"lara@example.ch\", \"password\": \"geheim123\", "
                                + "\"firstName\": \"Lara\", \"lastName\": \"Meier\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.firstName").value("Lara"))
                .andExpect(jsonPath("$.lastName").value("Meier"));

        String firstName = jdbcTemplate.queryForObject(
                "SELECT first_name FROM users WHERE email = 'lara@example.ch'", String.class);
        String lastName = jdbcTemplate.queryForObject(
                "SELECT last_name FROM users WHERE email = 'lara@example.ch'", String.class);
        assertThat(firstName).isEqualTo("Lara");
        assertThat(lastName).isEqualTo("Meier");
    }

    @Test
    void registerWithDuplicateEmailReturns409() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(LARA))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(LARA))
                .andExpect(status().isConflict());
    }

    @Test
    void registerWithInvalidEmailReturns400() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"not-an-email\", \"password\": \"geheim123\"}"))
                .andExpect(status().isBadRequest());
    }

    @Test
    void registerWithPasswordOver72BytesReturns400InsteadOf500() throws Exception {
        // BE-AUTH-10 (#200): 73 ASCII-Bytes reissen die bcrypt-Grenze knapp — der Grenzfall.
        String tooLongPassword = "a".repeat(73);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"lara@example.ch\", \"password\": \""
                                + tooLongPassword + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Passwort ist zu lang (maximal 72 Bytes)."))
                .andExpect(content().string(not(containsString(tooLongPassword))));
    }

    @Test
    void registerWithEmojiPasswordOver72BytesReturns400() throws Exception {
        // 40 Emoji sind nur 40 Codepoints, aber 160 UTF-8-Bytes — belegt die Byte- statt
        // Zeichen-Zählung (AC aus #200).
        String emojiPassword = "😀".repeat(40);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"lara@example.ch\", \"password\": \""
                                + emojiPassword + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Passwort ist zu lang (maximal 72 Bytes)."))
                .andExpect(content().string(not(containsString(emojiPassword))));
    }

    @Test
    void loginWithCorrectCredentialsReturns200AndCookie() throws Exception {
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(LARA))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/login")
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
        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON).content(LARA))
                .andExpect(status().isCreated());

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"lara@example.ch\", \"password\": \"falsch123\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void loginWithUnknownEmailReturns401() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"nobody@example.ch\", \"password\": \"geheim123\"}"))
                .andExpect(status().isUnauthorized());
    }

    @Test
    void logoutClearsCookieWithMaxAgeZero() throws Exception {
        mockMvc.perform(post("/api/auth/logout"))
                .andExpect(status().isNoContent())
                .andExpect(header().string(HttpHeaders.SET_COOKIE, Matchers.containsString("jwt=")))
                .andExpect(header().string(
                        HttpHeaders.SET_COOKIE, Matchers.containsString("Max-Age=0")));
    }
}
