package com.budgetbuddy.auth;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

import com.budgetbuddy.budget.FixedCost;
import com.budgetbuddy.budget.FixedCostRepository;
import com.budgetbuddy.budget.Intervall;
import com.budgetbuddy.support.PostgresTestDatabase;
import com.budgetbuddy.transaction.ImportJob;
import com.budgetbuddy.transaction.ImportJobRepository;
import com.budgetbuddy.transaction.Transaction;
import com.budgetbuddy.transaction.TransactionRepository;
import java.math.BigDecimal;
import java.time.Instant;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Integrationstest der Kontolöschung (US-02, DB-07) gegen echtes PostgreSQL: belegt, dass
 * {@code transactions}, {@code import_jobs} und {@code fixed_costs} vor dem User selbst gelöscht
 * werden. Ohne diese Reihenfolge schlägt die letzte Löschung an der Fremdschlüssel-Constraint
 * fehl (siehe {@code V02}/{@code V03}/{@code V05}) — ein Mock-Repository wie in
 * {@code UserServiceTest} könnte das nicht belegen, da die Constraint nur in einer echten
 * Datenbank existiert.
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class UserDeletionIntegrationTest {

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.register(registry, "user_deletion");
    }

    @Autowired
    private UserService userService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private TransactionRepository transactionRepository;

    @Autowired
    private ImportJobRepository importJobRepository;

    @Autowired
    private FixedCostRepository fixedCostRepository;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private long userId;

    @BeforeEach
    void seed() {
        transactionRepository.deleteAll();
        // Vor den Usern: import_jobs.user_id ist ein Fremdschlüssel auf users (Flyway V05).
        importJobRepository.deleteAll();
        fixedCostRepository.deleteAll();
        jdbcTemplate.update("DELETE FROM users");

        User user = userRepository.save(new User("lara@example.ch", "bcrypt-hash"));
        userId = user.getId();

        transactionRepository.save(new Transaction(
                userId, LocalDate.of(2026, 8, 1), "MIGROS BERN", null, new BigDecimal("42.50"),
                false, "Lebensmittel", "abc123"));
        importJobRepository.save(new ImportJob(userId, "abc123", 1, Instant.now()));
        fixedCostRepository.save(new FixedCost(
                userId, "Miete", new BigDecimal("1200.00"), Intervall.MONATLICH));
    }

    @Test
    void deleteUserRemovesUserAndAllDependentRows() {
        assertThatCode(() -> userService.deleteUser(userId)).doesNotThrowAnyException();

        assertThat(userRepository.findById(userId)).isEmpty();
        assertThat(countRows("SELECT COUNT(*) FROM transactions WHERE user_id = ?")).isZero();
        assertThat(countRows("SELECT COUNT(*) FROM import_jobs WHERE user_id = ?")).isZero();
        assertThat(countRows("SELECT COUNT(*) FROM fixed_costs WHERE user_id = ?")).isZero();
    }

    private int countRows(String sql) {
        Integer count = jdbcTemplate.queryForObject(sql, Integer.class, userId);
        return count == null ? 0 : count;
    }
}
