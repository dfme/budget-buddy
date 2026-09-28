package com.budgetbuddy.recurring;

import static org.assertj.core.api.Assertions.assertThat;
import static org.hamcrest.Matchers.everyItem;
import static org.hamcrest.Matchers.is;
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
    void listReturnsOnlyTheOwnEntries() throws Exception {
        createRecurringExpense(lara, "NETFLIX");
        createRecurringExpense(marc, "SPOTIFY");

        mockMvc.perform(get("/api/recurring-expenses").cookie(jwtCookie(lara)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].payeeKey").value("NETFLIX"));
    }

    @Test
    void listIsSortedAlphabeticallyByPayeeKey() throws Exception {
        // Bewusst nicht in Zielreihenfolge angelegt: ohne ORDER BY käme die Einfügereihenfolge.
        createRecurringExpense(lara, "SPOTIFY");
        createRecurringExpense(lara, "NETFLIX");
        createRecurringExpense(lara, "SWISSCOM");

        mockMvc.perform(get("/api/recurring-expenses").cookie(jwtCookie(lara)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].payeeKey").value("NETFLIX"))
                .andExpect(jsonPath("$[1].payeeKey").value("SPOTIFY"))
                .andExpect(jsonPath("$[2].payeeKey").value("SWISSCOM"));
    }

    /**
     * FE-NOTIF-03 (#333): ein verneinter Eintrag bleibt in der Liste, erkennbar an
     * {@code status=DISMISSED} — die Übersicht zeigt ihn im Abschnitt «Kein Abo». Vor #333
     * antwortete der Endpoint hier mit {@code []}.
     */
    @Test
    void dismissedEntriesStayInTheListWithStatusDismissed() throws Exception {
        long id = createRecurringExpense(lara, "NETFLIX");

        mockMvc.perform(post("/api/recurring-expenses/" + id + "/dismiss").cookie(jwtCookie(lara)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/recurring-expenses").cookie(jwtCookie(lara)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(id))
                .andExpect(jsonPath("$[0].status").value("DISMISSED"))
                .andExpect(jsonPath("$[0].isNew").value(false));
    }

    @Test
    void listReturnsEmptyArrayForAUserWithoutRecurringExpenses() throws Exception {
        mockMvc.perform(get("/api/recurring-expenses").cookie(jwtCookie(lara)))
                .andExpect(status().isOk())
                .andExpect(content().json("[]"));
    }

    @Test
    void listMarksEntryAsNewWhileItsBundleNotificationIsUnread() throws Exception {
        Notification bundle = createBundleNotification(lara);
        createRecurringExpense(lara, "NETFLIX", bundle.getId());

        mockMvc.perform(get("/api/recurring-expenses").cookie(jwtCookie(lara)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].isNew").value(true));
    }

    @Test
    void listDoesNotMarkEntryAsNewOnceItsBundleNotificationIsRead() throws Exception {
        Notification bundle = createBundleNotification(lara);
        createRecurringExpense(lara, "NETFLIX", bundle.getId());
        bundle.markRead(Instant.now());
        notificationRepository.save(bundle);

        mockMvc.perform(get("/api/recurring-expenses").cookie(jwtCookie(lara)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$[0].isNew").value(false));
    }

    /**
     * FE-NOTIF-04 (#336): Eine Kenntnisnahme für das ganze Bündel — nach {@code read-all} in der
     * Glocke sind alle Einträge des Imports nicht mehr «Neu», ohne N-mal zu klicken.
     */
    @Test
    void markingAllNotificationsAsReadClearsNewOnEveryEntryOfTheBundle() throws Exception {
        Notification bundle = createBundleNotification(lara);
        createRecurringExpense(lara, "NETFLIX", bundle.getId());
        createRecurringExpense(lara, "SPOTIFY", bundle.getId());
        createRecurringExpense(lara, "SWISSCOM", bundle.getId());

        mockMvc.perform(post("/api/notifications/read-all").cookie(jwtCookie(lara)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/recurring-expenses").cookie(jwtCookie(lara)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(3))
                .andExpect(jsonPath("$[*].isNew").value(everyItem(is(false))));
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
    void dismissAnswersNotNewEvenWhenItsBundleNotificationIsUnread() throws Exception {
        Notification bundle = createBundleNotification(lara);
        long id = createRecurringExpense(lara, "NETFLIX", bundle.getId());
        createRecurringExpense(lara, "SPOTIFY", bundle.getId());

        // Das Bündel bleibt wegen Spotify ungelesen — der verneinte Eintrag ist trotzdem nicht neu.
        mockMvc.perform(post("/api/recurring-expenses/" + id + "/dismiss").cookie(jwtCookie(lara)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.isNew").value(false));
    }

    // --- BE-REC-03: Dismiss markiert die Bündel-Benachrichtigung als gelesen (FE-NOTIF-04) ---

    @Test
    void dismissingTheOnlyEntryOfABundleMarksItsNotificationAsRead() throws Exception {
        Notification bundle = createBundleNotification(lara);
        long id = createRecurringExpense(lara, "NETFLIX", bundle.getId());

        mockMvc.perform(post("/api/recurring-expenses/" + id + "/dismiss").cookie(jwtCookie(lara)))
                .andExpect(status().isOk());

        assertThat(notificationRepository.findByIdAndUserId(bundle.getId(), lara))
                .get()
                .extracting(Notification::isRead)
                .isEqualTo(true);
    }

    @Test
    void dismissingOneEntryOfABundleLeavesItsNotificationUnreadWhileOthersAreOpen() throws Exception {
        Notification bundle = createBundleNotification(lara);
        long netflix = createRecurringExpense(lara, "NETFLIX", bundle.getId());
        createRecurringExpense(lara, "SPOTIFY", bundle.getId());

        mockMvc.perform(post("/api/recurring-expenses/" + netflix + "/dismiss")
                        .cookie(jwtCookie(lara)))
                .andExpect(status().isOk());

        assertThat(notificationRepository.findByIdAndUserId(bundle.getId(), lara))
                .get()
                .extracting(Notification::isRead)
                .isEqualTo(false);
        // Spotify ist weiterhin «neu» in der Übersicht; Netflix steht als DISMISSED davor
        // (alphabetisch).
        mockMvc.perform(get("/api/recurring-expenses").cookie(jwtCookie(lara)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(2))
                .andExpect(jsonPath("$[0].payeeKey").value("NETFLIX"))
                .andExpect(jsonPath("$[0].status").value("DISMISSED"))
                .andExpect(jsonPath("$[1].payeeKey").value("SPOTIFY"))
                .andExpect(jsonPath("$[1].status").value("DETECTED"))
                .andExpect(jsonPath("$[1].isNew").value(true));
    }

    @Test
    void dismissingTheLastOpenEntryOfABundleMarksItsNotificationAsRead() throws Exception {
        Notification bundle = createBundleNotification(lara);
        long netflix = createRecurringExpense(lara, "NETFLIX", bundle.getId());
        long spotify = createRecurringExpense(lara, "SPOTIFY", bundle.getId());

        mockMvc.perform(post("/api/recurring-expenses/" + netflix + "/dismiss").cookie(jwtCookie(lara)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/recurring-expenses/" + spotify + "/dismiss").cookie(jwtCookie(lara)))
                .andExpect(status().isOk());

        assertThat(notificationRepository.findByIdAndUserId(bundle.getId(), lara))
                .get()
                .extracting(Notification::isRead)
                .isEqualTo(true);
    }

    @Test
    void dismissWithoutANotificationAnswersWith200() throws Exception {
        long id = createRecurringExpense(lara, "NETFLIX");
        // Keine Benachrichtigung angelegt — der Eintrag stammt etwa aus einer Zeit vor der
        // Glocke oder die Benachrichtigung wurde bereits aufgeräumt.

        mockMvc.perform(post("/api/recurring-expenses/" + id + "/dismiss").cookie(jwtCookie(lara)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DISMISSED"))
                .andExpect(jsonPath("$.isNew").value(false));
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

    // --- BE-REC-05: POST /api/recurring-expenses/{id}/reactivate ---

    @Test
    void reactivateSetsStatusToDetectedAndAnswersWith200() throws Exception {
        long id = createRecurringExpense(lara, "NETFLIX");
        mockMvc.perform(post("/api/recurring-expenses/" + id + "/dismiss").cookie(jwtCookie(lara)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/recurring-expenses/" + id + "/reactivate").cookie(jwtCookie(lara)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value(id))
                .andExpect(jsonPath("$.status").value("DETECTED"))
                .andExpect(jsonPath("$.isNew").value(false));

        assertThat(recurringExpenseRepository.findByIdAndUserId(id, lara))
                .get()
                .extracting(RecurringExpense::getStatus)
                .isEqualTo(RecurringExpenseStatus.DETECTED);
    }

    @Test
    void reactivateIsIdempotent() throws Exception {
        long id = createRecurringExpense(lara, "NETFLIX");
        mockMvc.perform(post("/api/recurring-expenses/" + id + "/dismiss").cookie(jwtCookie(lara)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/recurring-expenses/" + id + "/reactivate").cookie(jwtCookie(lara)))
                .andExpect(status().isOk());
        mockMvc.perform(post("/api/recurring-expenses/" + id + "/reactivate").cookie(jwtCookie(lara)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("DETECTED"));
    }

    /**
     * AC4: Reaktivierte Zeile erscheint wieder in der Abo-Liste und verschwindet aus «Kein Abo».
     */
    @Test
    void reactivatedEntryAppearsInTheListAgainWithStatusDetected() throws Exception {
        long id = createRecurringExpense(lara, "NETFLIX");
        mockMvc.perform(post("/api/recurring-expenses/" + id + "/dismiss").cookie(jwtCookie(lara)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/recurring-expenses/" + id + "/reactivate").cookie(jwtCookie(lara)))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/recurring-expenses").cookie(jwtCookie(lara)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(id))
                .andExpect(jsonPath("$[0].status").value("DETECTED"));
    }

    @Test
    void aForeignEntryCannotBeReactivated() throws Exception {
        long id = createRecurringExpense(lara, "NETFLIX");
        mockMvc.perform(post("/api/recurring-expenses/" + id + "/dismiss").cookie(jwtCookie(lara)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/recurring-expenses/" + id + "/reactivate").cookie(jwtCookie(marc)))
                .andExpect(status().isNotFound())
                .andExpect(content().string(""));

        assertThat(recurringExpenseRepository.findByIdAndUserId(id, lara))
                .get()
                .extracting(RecurringExpense::getStatus)
                .isEqualTo(RecurringExpenseStatus.DISMISSED);
    }

    @Test
    void unknownIdReturns404ForReactivate() throws Exception {
        mockMvc.perform(post("/api/recurring-expenses/999999/reactivate").cookie(jwtCookie(lara)))
                .andExpect(status().isNotFound());
    }

    // --- 401 ohne JWT ---

    @Test
    void withoutJwtEveryEndpointReturns401() throws Exception {
        mockMvc.perform(get("/api/recurring-expenses")).andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/recurring-expenses/1/dismiss"))
                .andExpect(status().isUnauthorized());
        mockMvc.perform(post("/api/recurring-expenses/1/reactivate"))
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

    /**
     * BE-REC-04: {@code ENDED} ist der dritte Vertragswert — das Frontend-Model erwartet exakt
     * {@code 'ENDED'} und sortiert den Eintrag danach in den Abschnitt «Beendet».
     */
    @Test
    void wireFormatCarriesStatusEndedForAnExpiredRecurringExpense() throws Exception {
        long id = createRecurringExpense(lara, "NETFLIX");
        RecurringExpense entry = recurringExpenseRepository.findById(id).orElseThrow();
        entry.markEnded();
        recurringExpenseRepository.save(entry);

        mockMvc.perform(get("/api/recurring-expenses").cookie(jwtCookie(lara)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.length()").value(1))
                .andExpect(jsonPath("$[0].id").value(id))
                .andExpect(jsonPath("$[0].status").value("ENDED"));
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

    /** Ein Eintrag ohne Bündel-Benachrichtigung — Bestandsdaten, die V14 nicht zuordnen konnte. */
    private long createRecurringExpense(long userId, String payeeKey) {
        return createRecurringExpense(userId, payeeKey, null);
    }

    private long createRecurringExpense(long userId, String payeeKey, Long notificationId) {
        return recurringExpenseRepository
                .save(new RecurringExpense(userId, payeeKey, new BigDecimal("20.90"),
                        YearMonth.of(2026, 6), Instant.now(), notificationId))
                .getId();
    }

    /** Die ungelesene Bündel-Benachrichtigung eines Erkennungslaufs (FE-NOTIF-04). */
    private Notification createBundleNotification(long userId) {
        return notificationRepository.save(new Notification(
                userId, "RECURRING_EXPENSE_DETECTED", null, "Abos erkannt", Instant.now()));
    }
}
