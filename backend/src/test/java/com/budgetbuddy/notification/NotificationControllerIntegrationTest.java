package com.budgetbuddy.notification;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.budgetbuddy.auth.JwtService;
import com.budgetbuddy.support.PostgresTestDatabase;
import jakarta.servlet.http.Cookie;
import java.time.Instant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;
import org.springframework.test.web.servlet.MockMvc;

/**
 * Integrationstest der Benachrichtigungs-Endpoints (BE-NOTIF-01) gegen echtes PostgreSQL + Flyway.
 * Deckt die Acceptance Criteria von #246 ab: Statuscodes, Mandantentrennung, 401 ohne JWT und das
 * Wire-Format.
 *
 * <p>Eigene Datenbank auf dem gemeinsamen Testcontainer und {@code @DirtiesContext} analog zu
 * {@code FixedCostControllerIntegrationTest}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class NotificationControllerIntegrationTest {

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.register(registry, "notification_controller");
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private NotificationRepository notificationRepository;

    private long lara;
    private long marc;

    @BeforeEach
    void seed() {
        notificationRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM users");

        lara = insertUser("lara@example.ch");
        marc = insertUser("marc@example.ch");
    }

    // --- AC2: GET /api/notifications ---

    @Test
    void listReturnsOnlyTheOwnNotifications() throws Exception {
        notificationRepository.save(
                new Notification(lara, "A", null, "Laras Meldung", Instant.now()));
        notificationRepository.save(
                new Notification(marc, "A", null, "Marcs Meldung", Instant.now()));

        mockMvc.perform(get("/api/notifications").cookie(jwtCookie(lara)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].message").value("Laras Meldung"));
    }

    @Test
    void listShowsUnreadNotificationsFirst() throws Exception {
        Notification read = notificationRepository.save(
                new Notification(lara, "A", null, "Gelesen", Instant.now().minusSeconds(60)));
        read.markRead(Instant.now());
        notificationRepository.save(read);
        notificationRepository.save(
                new Notification(lara, "B", null, "Ungelesen", Instant.now().minusSeconds(120)));

        mockMvc.perform(get("/api/notifications").cookie(jwtCookie(lara)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].message").value("Ungelesen"))
                .andExpect(jsonPath("$[0].read").value(false))
                .andExpect(jsonPath("$[1].message").value("Gelesen"))
                .andExpect(jsonPath("$[1].read").value(true));
    }

    @Test
    void listReturnsEmptyArrayForAUserWithoutNotifications() throws Exception {
        mockMvc.perform(get("/api/notifications").cookie(jwtCookie(lara)))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    // --- AC3: POST /api/notifications/{id}/read ---

    @Test
    void markAsReadSetsReadToTrueAndAnswersWith200() throws Exception {
        long id = createNotification(lara, "Netflix erkannt");

        mockMvc.perform(post("/api/notifications/" + id + "/read").cookie(jwtCookie(lara)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.read").value(true));

        assertThat(notificationRepository.findByIdAndUserId(id, lara))
                .get()
                .extracting(Notification::isRead)
                .isEqualTo(true);
    }

    @Test
    void markAsReadIsIdempotent() throws Exception {
        long id = createNotification(lara, "Netflix erkannt");

        mockMvc.perform(post("/api/notifications/" + id + "/read").cookie(jwtCookie(lara)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/notifications/" + id + "/read").cookie(jwtCookie(lara)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.read").value(true));
    }

    // --- AC4: Mandantentrennung ---

    @Test
    void aForeignNotificationCannotBeMarkedAsRead() throws Exception {
        long id = createNotification(lara, "Netflix erkannt");

        mockMvc.perform(post("/api/notifications/" + id + "/read").cookie(jwtCookie(marc)))
                .andExpect(status().isNotFound())
                .andExpect(content().string(""));

        assertThat(notificationRepository.findByIdAndUserId(id, lara))
                .get()
                .extracting(Notification::isRead)
                .isEqualTo(false);
    }

    @Test
    void unknownIdReturns404() throws Exception {
        mockMvc.perform(post("/api/notifications/999999/read").cookie(jwtCookie(lara)))
                .andExpect(status().isNotFound());
    }

    // --- 401 ohne JWT ---

    @Test
    void withoutJwtEveryEndpointReturns401() throws Exception {
        mockMvc.perform(get("/api/notifications")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/notifications/1/read")).andExpect(status().isUnauthorized());
    }

    // --- Wire-Format ---

    @Test
    void wireFormatCarriesAllExpectedFields() throws Exception {
        notificationRepository.save(
                new Notification(lara, "RECURRING_EXPENSE_DETECTED", 55L, "Netflix erkannt",
                        Instant.now()));

        mockMvc.perform(get("/api/notifications").cookie(jwtCookie(lara)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").isNumber())
                .andExpect(jsonPath("$[0].type").value("RECURRING_EXPENSE_DETECTED"))
                .andExpect(jsonPath("$[0].referenceId").value(55))
                .andExpect(jsonPath("$[0].message").value("Netflix erkannt"))
                .andExpect(jsonPath("$[0].read").value(false))
                .andExpect(jsonPath("$[0].createdAt").exists());
    }

    // --- Helfer ---

    private long insertUser(String email) {
        jdbcTemplate.update(
                "INSERT INTO users (email, password_hash) VALUES (?, ?)",
                email, "bcrypt-hash");
        return jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE email = ?", Long.class, email);
    }

    private Cookie jwtCookie(long uid) {
        return new Cookie("jwt", jwtService.generateToken(uid));
    }

    private long createNotification(long userId, String message) {
        return notificationRepository
                .save(new Notification(userId, "A", null, message, Instant.now()))
                .getId();
    }
}
