package com.budgetbuddy.recurring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.budgetbuddy.auth.JwtService;
import com.budgetbuddy.notification.Notification;
import com.budgetbuddy.notification.NotificationRepository;
import com.budgetbuddy.support.PostgresTestDatabase;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.YearMonth;
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
 * Integrationstest der Abo-Übersicht-Endpoints (BE-REC-02) gegen echtes PostgreSQL + Flyway.
 * Deckt die Acceptance Criteria von #254 ab: Statuscodes, Mandantentrennung, 401 ohne JWT, das
 * Wire-Format inkl. Neu-Flag.
 *
 * <p>Eigene Datenbank auf dem gemeinsamen Testcontainer und {@code @DirtiesContext} analog zu
 * {@code NotificationControllerIntegrationTest}.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RecurringExpenseControllerIntegrationTest {

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.register(registry, "recurring_expense_controller");
    }

    @Autowired private MockMvc mockMvc;
    @Autowired private JwtService jwtService;
    @Autowired private JdbcTemplate jdbcTemplate;
    @Autowired private RecurringExpenseRepository recurringExpenseRepository;
    @Autowired private NotificationRepository notificationRepository;

    private long lara;
    private long marc;

    @BeforeEach
    void seed() {
        recurringExpenseRepository.deleteAll();
        notificationRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM users");

        lara = insertUser("lara@example.ch");
        marc = insertUser("marc@example.ch");
    }

    // --- AC1: GET /api/recurring-expenses ---

    @Test
    void listReturnsOnlyTheOwnDetectedEntries() throws Exception {
        createRecurringExpense(lara, "NETFLIX");
        createRecurringExpense(marc, "SPOTIFY");

        mockMvc.perform(get("/api/recurring-expenses").cookie(jwtCookie(lara)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].payeeKey").value("NETFLIX"));
    }

    @Test
    void dismissedEntriesDoNotAppearInTheList() throws Exception {
        long id = createRecurringExpense(lara, "NETFLIX");

        mockMvc.perform(post("/api/recurring-expenses/" + id + "/dismiss").cookie(jwtCookie(lara)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/recurring-expenses").cookie(jwtCookie(lara)))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void listReturnsEmptyArrayForAUserWithoutRecurringExpenses() throws Exception {
        mockMvc.perform(get("/api/recurring-expenses").cookie(jwtCookie(lara)))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void listMarksEntryAsNewWhileItsNotificationIsUnread() throws Exception {
        long id = createRecurringExpense(lara, "NETFLIX");
        notificationRepository.save(new Notification(
                lara, "RECURRING_EXPENSE_DETECTED", id, "Netflix erkannt", Instant.now()));

        mockMvc.perform(get("/api/recurring-expenses").cookie(jwtCookie(lara)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].isNew").value(true));
    }

    @Test
    void listDoesNotMarkEntryAsNewOnceItsNotificationIsRead() throws Exception {
        long id = createRecurringExpense(lara, "NETFLIX");
        Notification notification = notificationRepository.save(new Notification(
                lara, "RECURRING_EXPENSE_DETECTED", id, "Netflix erkannt", Instant.now()));
        notification.markRead(Instant.now());
        notificationRepository.save(notification);

        mockMvc.perform(get("/api/recurring-expenses").cookie(jwtCookie(lara)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].isNew").value(false));
    }

    // --- AC2: POST /api/recurring-expenses/{id}/dismiss ---

    @Test
    void dismissSetsStatusToDismissedAndAnswersWith200() throws Exception {
        long id = createRecurringExpense(lara, "NETFLIX");

        mockMvc.perform(post("/api/recurring-expenses/" + id + "/dismiss").cookie(jwtCookie(lara)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.status").value("DISMISSED"));

        assertThat(recurringExpenseRepository.findByIdAndUserId(id, lara))
                .get()
                .extracting(RecurringExpense::getStatus)
                .isEqualTo(RecurringExpenseStatus.DISMISSED);
    }

    @Test
    void dismissIsIdempotent() throws Exception {
        long id = createRecurringExpense(lara, "NETFLIX");

        mockMvc.perform(post("/api/recurring-expenses/" + id + "/dismiss").cookie(jwtCookie(lara)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/recurring-expenses/" + id + "/dismiss").cookie(jwtCookie(lara)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISMISSED"));
    }

    // --- AC4: Mandantentrennung ---

    @Test
    void aForeignEntryCannotBeDismissed() throws Exception {
        long id = createRecurringExpense(lara, "NETFLIX");

        mockMvc.perform(post("/api/recurring-expenses/" + id + "/dismiss").cookie(jwtCookie(marc)))
                .andExpect(status().isNotFound())
                .andExpect(content().string(""));

        assertThat(recurringExpenseRepository.findByIdAndUserId(id, lara))
                .get()
                .extracting(RecurringExpense::getStatus)
                .isEqualTo(RecurringExpenseStatus.DETECTED);
    }

    @Test
    void unknownIdReturns404() throws Exception {
        mockMvc.perform(post("/api/recurring-expenses/999999/dismiss").cookie(jwtCookie(lara)))
                .andExpect(status().isNotFound());
    }

    // --- 401 ohne JWT ---

    @Test
    void withoutJwtEveryEndpointReturns401() throws Exception {
        mockMvc.perform(get("/api/recurring-expenses")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/recurring-expenses/1/dismiss"))
                .andExpect(status().isUnauthorized());
    }

    // --- Wire-Format ---

    @Test
    void wireFormatCarriesAllExpectedFields() throws Exception {
        createRecurringExpense(lara, "NETFLIX");

        mockMvc.perform(get("/api/recurring-expenses").cookie(jwtCookie(lara)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].id").isNumber())
                .andExpect(jsonPath("$[0].payeeKey").value("NETFLIX"))
                .andExpect(jsonPath("$[0].amount").value(20.90))
                .andExpect(jsonPath("$[0].status").value("DETECTED"))
                .andExpect(jsonPath("$[0].firstDetectedMonth").value("2026-06"))
                .andExpect(jsonPath("$[0].createdAt").exists())
                .andExpect(jsonPath("$[0].isNew").exists());
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

    private long createRecurringExpense(long userId, String payeeKey) {
        return recurringExpenseRepository
                .save(new RecurringExpense(userId, payeeKey, new BigDecimal("20.90"),
                        YearMonth.of(2026, 6), Instant.now()))
                .getId();
    }
}
