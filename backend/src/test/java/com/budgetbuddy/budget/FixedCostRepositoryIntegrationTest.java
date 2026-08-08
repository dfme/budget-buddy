package com.budgetbuddy.budget;

import static org.assertj.core.api.Assertions.assertThat;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Integrationstest von {@link FixedCost} und {@link FixedCostRepository} gegen echte SQLite +
 * Flyway (BE-FC-01). Belegt die drei Acceptance Criteria von #10: Spalten-Mapping,
 * {@link BigDecimal}-Betrag und user-gebundene Queries — letztere mit Gegenprobe aus Sicht eines
 * fremden Users, weil ein grüner Happy Path die Mandantentrennung nicht beweist.
 *
 * <p>Temp-File-DB statt {@code jdbc:sqlite::memory:} und {@code @DirtiesContext} analog zu
 * {@link com.budgetbuddy.db.FixedCostsMigrationTest} (Begründung dort dokumentiert).
 *
 * <p>Die Test-User werden per {@link JdbcTemplate} eingefügt statt über das {@code UserRepository}:
 * der FK {@code fixed_costs.user_id → users.id} braucht echte Zeilen, ein Repository-Zugriff über
 * die Modulgrenze hinweg wäre aber genau das, was CLAUDE.md untersagt.
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class FixedCostRepositoryIntegrationTest {

    private static final Path DB_FILE = createTempDbFile();

    private static Path createTempDbFile() {
        try {
            Path file = Files.createTempFile("be-fc-01-repository-it", ".db");
            Files.deleteIfExists(file); // SQLite/Flyway legt die Datei selbst frisch an
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

    @Autowired private FixedCostRepository repository;
    @Autowired private JdbcTemplate jdbcTemplate;

    // --- AC1: Entity mappt korrekt auf fixed_costs ---

    @Test
    void entityIsPersistedIntoTheCorrectColumns() {
        Long userId = insertUser("mapping@example.com");

        FixedCost saved = repository.save(
                new FixedCost(userId, "Krankenkasse", new BigDecimal("312.45"),
                        Intervall.QUARTALSWEISE));

        Map<String, Object> row = jdbcTemplate.queryForMap(
                "SELECT user_id, bezeichnung, betrag, intervall FROM fixed_costs WHERE id = ?",
                saved.getId());

        assertThat(((Number) row.get("user_id")).longValue()).isEqualTo(userId);
        assertThat(row.get("bezeichnung")).isEqualTo("Krankenkasse");
        assertThat(row.get("intervall")).isEqualTo("quartalsweise");
        assertThat(new BigDecimal(row.get("betrag").toString()))
                .isEqualByComparingTo("312.45");
    }

    @Test
    void intervallIsStoredAsLowercaseLabelAndReadBackAsEnum() {
        Long userId = insertUser("intervall@example.com");
        FixedCost saved = repository.save(
                new FixedCost(userId, "Serafe", new BigDecimal("335.00"), Intervall.JAEHRLICH));

        // In der DB steht das ASCII-Label aus V03 — nicht der Enum-Konstantenname.
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT intervall FROM fixed_costs WHERE id = ?", String.class,
                        saved.getId()))
                .isEqualTo("jaehrlich");

        assertThat(repository.findByIdAndUserId(saved.getId(), userId))
                .get()
                .extracting(FixedCost::getIntervall)
                .isEqualTo(Intervall.JAEHRLICH);
    }

    // --- AC2: betrag ist BigDecimal ---

    @Test
    void betragRoundTripsRappenExactAsBigDecimal() {
        Long userId = insertUser("betrag@example.com");
        FixedCost saved = repository.save(
                new FixedCost(userId, "Miete", new BigDecimal("1234.56"), Intervall.MONATLICH));

        BigDecimal readBack = repository.findByIdAndUserId(saved.getId(), userId)
                .orElseThrow()
                .getBetrag();

        assertThat(readBack).isEqualByComparingTo("1234.56");
    }

    @Test
    void betragKeepsValuesThatBinaryFloatingPointCannotRepresent() {
        Long userId = insertUser("rappen@example.com");
        // 0.07 und 0.10 sind als double nicht exakt darstellbar; die Summe muss trotzdem
        // rappengenau 0.17 ergeben (ADR-9).
        repository.save(new FixedCost(userId, "Klein A", new BigDecimal("0.07"),
                Intervall.MONATLICH));
        repository.save(new FixedCost(userId, "Klein B", new BigDecimal("0.10"),
                Intervall.MONATLICH));

        BigDecimal sum = repository.findByUserIdOrderByIdAsc(userId).stream()
                .map(FixedCost::getBetrag)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(sum).isEqualByComparingTo("0.17");
    }

    // --- AC3: Repository-Queries filtern nach user_id ---

    @Test
    void findByUserIdReturnsOnlyOwnEntriesInInsertionOrder() {
        Long lara = insertUser("lara-list@example.com");
        Long marc = insertUser("marc-list@example.com");
        repository.save(new FixedCost(lara, "Miete", new BigDecimal("1200.00"),
                Intervall.MONATLICH));
        repository.save(new FixedCost(marc, "Fitness", new BigDecimal("89.00"),
                Intervall.MONATLICH));
        repository.save(new FixedCost(lara, "Handy", new BigDecimal("39.90"),
                Intervall.MONATLICH));

        List<FixedCost> laraEntries = repository.findByUserIdOrderByIdAsc(lara);

        assertThat(laraEntries)
                .extracting(FixedCost::getBezeichnung)
                .containsExactly("Miete", "Handy");
        assertThat(laraEntries).allSatisfy(fc -> assertThat(fc.getUserId()).isEqualTo(lara));
    }

    @Test
    void findByIdAndUserIdDoesNotLeakAForeignEntry() {
        Long lara = insertUser("lara-read@example.com");
        Long marc = insertUser("marc-read@example.com");
        FixedCost larasEntry = repository.save(
                new FixedCost(lara, "Miete", new BigDecimal("1200.00"), Intervall.MONATLICH));

        assertThat(repository.findByIdAndUserId(larasEntry.getId(), lara)).isPresent();
        assertThat(repository.findByIdAndUserId(larasEntry.getId(), marc)).isEmpty();
    }

    @Test
    void deleteByIdAndUserIdDoesNotDeleteAForeignEntry() {
        Long lara = insertUser("lara-delete@example.com");
        Long marc = insertUser("marc-delete@example.com");
        FixedCost larasEntry = repository.save(
                new FixedCost(lara, "Miete", new BigDecimal("1200.00"), Intervall.MONATLICH));

        assertThat(repository.deleteByIdAndUserId(larasEntry.getId(), marc)).isZero();
        assertThat(repository.findByIdAndUserId(larasEntry.getId(), lara)).isPresent();

        assertThat(repository.deleteByIdAndUserId(larasEntry.getId(), lara)).isEqualTo(1);
        assertThat(repository.findByIdAndUserId(larasEntry.getId(), lara)).isEmpty();
    }

    @Test
    void existsByUserIdIsScopedToTheUser() {
        Long withEntry = insertUser("with-entry@example.com");
        Long withoutEntry = insertUser("without-entry@example.com");
        repository.save(new FixedCost(withEntry, "Miete", new BigDecimal("1200.00"),
                Intervall.MONATLICH));

        assertThat(repository.existsByUserId(withEntry)).isTrue();
        assertThat(repository.existsByUserId(withoutEntry)).isFalse();
    }

    /**
     * Legt einen User direkt per SQL an — der FK {@code fixed_costs.user_id} braucht eine echte
     * Zeile in {@code users}.
     *
     * <p>Die ID wird über die eindeutige E-Mail zurückgelesen und nicht über
     * {@code last_insert_rowid()}: das ist ein Connection-lokaler Wert, und der Hikari-Pool gibt
     * für die zweite Query keine garantiert identische Connection heraus.
     */
    private Long insertUser(String email) {
        jdbcTemplate.update(
                "INSERT INTO users (email, password_hash) VALUES (?, ?)",
                email, "$2a$10$test.only.not.a.real.hash");
        return jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE email = ?", Long.class, email);
    }
}
