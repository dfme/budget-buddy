package com.budgetbuddy.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import com.budgetbuddy.budget.FixedCost;
import com.budgetbuddy.budget.FixedCostRepository;
import com.budgetbuddy.budget.Intervall;
import com.budgetbuddy.categorization.Category;
import com.budgetbuddy.categorization.CategoryLearningPort;
import com.budgetbuddy.notification.Notification;
import com.budgetbuddy.notification.NotificationRepository;
import com.budgetbuddy.recurring.RecurringExpense;
import com.budgetbuddy.recurring.RecurringExpenseRepository;
import com.budgetbuddy.support.PostgresTestDatabase;
import com.budgetbuddy.transaction.ImportJob;
import com.budgetbuddy.transaction.ImportJobRepository;
import com.budgetbuddy.transaction.Transaction;
import com.budgetbuddy.transaction.TransactionRepository;
import jakarta.servlet.http.Cookie;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
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
 * Integrationstest der Kontolöschung (US-02, DB-07/nDSG) gegen echtes PostgreSQL: belegt, dass
 * {@code transactions}, {@code import_jobs}, {@code fixed_costs}, {@code notifications},
 * {@code recurring_expenses} und {@code user_category_lookup} vor dem User selbst gelöscht werden. Seit BE-AUTH-14 (#290) läuft
 * der Test über {@code DELETE /api/users/me} statt direkt über den Service — so belegt er den
 * ganzen Pfad, den ein User tatsächlich auslösen kann, inklusive Passwortbestätigung. Ohne diese
 * Reihenfolge schlägt die letzte Löschung an der Fremdschlüssel-Constraint fehl (siehe
 * {@code V02}/{@code V03}/{@code V05}/{@code V10}/{@code V11}) — ein
 * Mock-Repository wie in {@code UserServiceTest} könnte das nicht belegen, da die Constraint nur
 * in einer echten Datenbank existiert. Die {@code notifications}-Zeile deckt AC6 von #246
 * (BE-NOTIF-01) ab — der Review-Befund aus PR #272, der ohne sie eine
 * {@code DataIntegrityViolationException} verursacht hätte, sobald diese Tabelle die erste Zeile
 * enthält. Die {@code recurring_expenses}-Zeile deckt AC 6 von #253 (BE-REC-01) ab — dieselbe
 * Verpflichtung, in DB-09 (#252) für die Erkennung vorgemerkt. Die {@code user_category_lookup}-
 * Zeilen decken AC 4 von #319 (BE-CAT-12) ab — gezählt wird nach der Löschung, nicht angenommen,
 * dass ein Cascade greift; und die Zeile eines zweiten Users bleibt stehen, sonst wäre nur
 * belegt, dass die Tabelle leer ist, nicht dass sie mandantenweise geräumt wurde.
 */
@SpringBootTest
@AutoConfigureMockMvc
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class UserDeletionIntegrationTest {

    private static final String PASSWORD = "laraPasswort1";

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.register(registry, "user_deletion");
    }

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private PasswordEncoder passwordEncoder;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private ImportJobRepository importJobRepository;

    @Autowired
    private FixedCostRepository fixedCostRepository;

    @Autowired
    private NotificationRepository notificationRepository;

    @Autowired
    private RecurringExpenseRepository recurringExpenseRepository;

    @Autowired
    private CategoryLearningPort categoryLearningPort;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long userId;
    private long otherUserId;

    @BeforeEach
    void seed() {
        transactionRepository.deleteAll();
        // Vor den Usern: import_jobs.user_id ist ein Fremdschlüssel auf users (Flyway V05).
        importJobRepository.deleteAll();
        fixedCostRepository.deleteAll();
        notificationRepository.deleteAll();
        recurringExpenseRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM user_category_lookup");
        jdbcTemplate.update("DELETE FROM users");

        User user = userRepository.save(
                new User("lara@example.ch", passwordEncoder.encode(PASSWORD)));
        userId = user.getId();
        otherUserId = userRepository.save(
                new User("marc@example.ch", passwordEncoder.encode(PASSWORD))).getId();

        transactionRepository.save(new Transaction(
                userId, LocalDate.of(2026, 8, 1), "MIGROS BERN", null, new BigDecimal("42.50"),
                false, "Lebensmittel", "abc123"));
        importJobRepository.save(new ImportJob(userId, "abc123", 1, Instant.now()));
        fixedCostRepository.save(new FixedCost(
                userId, "Miete", new BigDecimal("1200.00"), Intervall.MONATLICH));
        notificationRepository.save(new Notification(
                userId, "RECURRING_EXPENSE_DETECTED", null, "Netflix wurde als Abo erkannt",
                Instant.now()));
        recurringExpenseRepository.save(new RecurringExpense(
                userId, "NETFLIX INTERNATIONAL BV", new BigDecimal("20.90"),
                YearMonth.of(2026, 7), Instant.now()));
        // Über den Port statt per INSERT: derselbe Weg, den manuelle Korrektur und Claude-Stufe
        // produktiv nehmen (BE-CAT-04, BE-CAT-11).
        categoryLearningPort.learn(userId, "BAECKEREI MUELLER", Category.LEBENSMITTEL);
        categoryLearningPort.learn(otherUserId, "BAECKEREI MUELLER", Category.RESTAURANT);
    }

    @Test
    void deleteEndpointRemovesUserAndAllDependentRows() throws Exception {
        mockMvc.perform(delete("/api/users/me")
                        .cookie(new Cookie("jwt", jwtService.generateToken(userId)))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"passwort\": \"" + PASSWORD + "\"}"))
                .andExpect(status().isNoContent());

        assertThat(userRepository.findById(userId)).isEmpty();
        assertThat(countRows("SELECT COUNT(*) FROM transactions WHERE user_id = ?")).isZero();
        assertThat(countRows("SELECT COUNT(*) FROM import_jobs WHERE user_id = ?")).isZero();
        assertThat(countRows("SELECT COUNT(*) FROM fixed_costs WHERE user_id = ?")).isZero();
        assertThat(countRows("SELECT COUNT(*) FROM notifications WHERE user_id = ?")).isZero();
        assertThat(countRows("SELECT COUNT(*) FROM recurring_expenses WHERE user_id = ?")).isZero();
        assertThat(countRows("SELECT COUNT(*) FROM user_category_lookup WHERE user_id = ?")).isZero();

        // Gegenprobe: Marcs gelerntes Pattern überlebt Laras Kontolöschung — geräumt wurde
        // mandantenweise, nicht die ganze Tabelle.
        Integer marcsRows = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM user_category_lookup WHERE user_id = ?", Integer.class,
                otherUserId);
        assertThat(marcsRows).isEqualTo(1);
    }

    private int countRows(String sql) {
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, userId);
        return count == null ? 0 : count;
    }
}
