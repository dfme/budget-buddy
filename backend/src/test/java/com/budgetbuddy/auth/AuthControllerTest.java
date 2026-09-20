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

    /**
     * Baut eine formal gültige E-Mail-Adresse exakt der gewünschten Gesamtlänge — lokaler Teil auf
     * die RFC-5321-Grenze von 64 Zeichen gedeckelt, der Rest über mehrere Domain-Labels (je
     * höchstens 63 Zeichen, RFC 1035) aufgefüllt. Ein einzelnes überlanges Label würde von
     * {@code @Email} unabhängig von {@code @Size} schon als Formatfehler abgelehnt (BE-AUTH-12,
     * #231) — der Test soll aber gezielt die Längengrenze prüfen, nicht das Format.
     */
    private static String emailOfLength(int totalLength) {
        String local = "a".repeat(64);
        String tld = ".ch";
        int remaining = totalLength - local.length() - 1 - tld.length();
        StringBuilder domain = new StringBuilder();
        while (remaining > 0) {
            int labelLength = Math.min(63, remaining);
            domain.append("a".repeat(labelLength));
            remaining -= labelLength;
            if (remaining > 0) {
                domain.append('.');
                remaining--;
            }
        }
        domain.append(tld);
        return local + "@" + domain;
    }

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
    void registerWithUmlautPasswordOver72BytesReturns400() throws Exception {
        // 40 Umlaute sind 40 char (Java zählt UTF-16-Codeeinheiten), aber 80 UTF-8-Bytes: ein
        // zeichenbasiertes @Size(max = 72) liesse das durch, die Byte-Prüfung nicht (AC aus #200).
        // "😀".repeat(40) hätte das nicht belegt — als Surrogatpaar sind das bereits 80 char.
        String tooManyBytesPassword = "ä".repeat(40);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"lara@example.ch\", \"password\": \""
                                + tooManyBytesPassword + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Passwort ist zu lang (maximal 72 Bytes)."))
                .andExpect(content().string(not(containsString(tooManyBytesPassword))));
    }

    @Test
    void registerWithEmailOver254CharsReturns400() throws Exception {
        // BE-AUTH-12 (#231): 255 Zeichen — reisst die RFC-5321-Grenze knapp.
        String tooLongEmail = emailOfLength(255);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"" + tooLongEmail + "\", \"password\": \"geheim123\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("E-Mail darf höchstens 254 Zeichen lang sein."))
                .andExpect(content().string(not(containsString(tooLongEmail))));
    }

    @Test
    void registerWithEmailAtMaxLengthIsAccepted() throws Exception {
        // 254 Zeichen — der Grenzfall, gerade noch gültig.
        String maxLengthEmail = emailOfLength(254);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"" + maxLengthEmail + "\", \"password\": \"geheim123\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.email").value(maxLengthEmail));
    }

    @Test
    void registerWithFirstNameOver50CharsReturns400() throws Exception {
        String tooLongName = "a".repeat(51);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"lara@example.ch\", \"password\": \"geheim123\", "
                                + "\"firstName\": \"" + tooLongName + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Vorname darf höchstens 50 Zeichen lang sein."))
                .andExpect(content().string(not(containsString(tooLongName))));
    }

    @Test
    void registerWithFirstNameAtMaxLengthIsAccepted() throws Exception {
        String maxLengthName = "a".repeat(50);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"lara@example.ch\", \"password\": \"geheim123\", "
                                + "\"firstName\": \"" + maxLengthName + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.firstName").value(maxLengthName));
    }

    @Test
    void registerWithLastNameOver50CharsReturns400() throws Exception {
        String tooLongName = "a".repeat(51);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"lara@example.ch\", \"password\": \"geheim123\", "
                                + "\"lastName\": \"" + tooLongName + "\"}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Nachname darf höchstens 50 Zeichen lang sein."))
                .andExpect(content().string(not(containsString(tooLongName))));
    }

    @Test
    void registerWithLastNameAtMaxLengthIsAccepted() throws Exception {
        String maxLengthName = "a".repeat(50);

        mockMvc.perform(post("/api/auth/register")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"email\": \"lara@example.ch\", \"password\": \"geheim123\", "
                                + "\"lastName\": \"" + maxLengthName + "\"}"))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.lastName").value(maxLengthName));
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
