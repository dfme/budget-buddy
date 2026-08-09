package com.budgetbuddy.budget;

import static org.assertj.core.api.Assertions.assertThat;

import com.budgetbuddy.support.PostgresTestDatabase;
import java.math.BigDecimal;
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
 * Integrationstest von {@link FixedCost} und {@link FixedCostRepository} gegen echtes PostgreSQL +
 * Flyway (BE-FC-01). Belegt die drei Acceptance Criteria von #10: Spalten-Mapping,
 * {@link BigDecimal}-Betrag und user-gebundene Queries — letztere mit Gegenprobe aus Sicht eines
 * fremden Users, weil ein grüner Happy Path die Mandantentrennung nicht beweist.
 *
 * <p>Eigene Datenbank auf dem gemeinsamen Testcontainer und {@code @DirtiesContext} analog zu
 * {@link com.budgetbuddy.db.FixedCostsMigrationTest} (Begründung in {@code PostgresTestDatabase}).
 *
 * <p>Die Test-User werden per {@link JdbcTemplate} eingefügt statt über das {@code UserRepository}:
 * der FK {@code fixed_costs.user_id → users.id} braucht echte Zeilen, ein Repository-Zugriff über
 * die Modulgrenze hinweg wäre aber genau das, was CLAUDE.md untersagt.
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class FixedCostRepositoryIntegrationTest {

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.register(registry, "fixed_cost_repository");
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
    void betragIsStoredAsExactDecimalNotFloatingPoint() {
        Long userId = insertUser("rappen@example.com");
        FixedCost kleinA = repository.save(
                new FixedCost(userId, "Klein A", new BigDecimal("0.07"), Intervall.MONATLICH));
        repository.save(new FixedCost(userId, "Klein B", new BigDecimal("0.10"),
                Intervall.MONATLICH));

        // Unter SQLite war DECIMAL(10,2) bloss eine *Affinität*: der Wert lag physisch als REAL in
        // der Datei und lief damit sehr wohl durch Binär-Fliesskomma (#141). Seit DB-05 (ADR-12)
        // ist der Spaltentyp echtes numeric — ADR-9 gilt jetzt in der Datenbank, nicht nur in Java.
        // Bricht das weg, wird diese Assertion rot statt der Rundungsfehler still zurückzukommen.
        assertThat(jdbcTemplate.queryForObject(
                        "SELECT pg_typeof(betrag)::text FROM fixed_costs WHERE id = ?",
                        String.class, kleinA.getId()))
                .isEqualTo("numeric");

        BigDecimal sum = repository.findByUserIdOrderByIdAsc(userId).stream()
                .map(FixedCost::getBetrag)
                .reduce(BigDecimal.ZERO, BigDecimal::add);

        assertThat(sum).isEqualByComparingTo("0.17");
    }

    @Test
    void betragScaleIsPreservedByTheDatabase() {
        Long userId = insertUser("skala@example.com");
        FixedCost ganzzahlig = repository.save(
                new FixedCost(userId, "Serafe", new BigDecimal("335.00"), Intervall.JAEHRLICH));
        FixedCost nachkomma = repository.save(
                new FixedCost(userId, "Klein", new BigDecimal("0.10"), Intervall.MONATLICH));

        // Verhaltensänderung durch DB-05 (ADR-12), bewusst festgehalten: unter SQLite ging die
        // Skala beim Round-Trip verloren (335.00 kam als 335 zurück, 0.10 als 0.1), weil die
        // Spalte nur eine Affinität war. numeric(10,2) in PostgreSQL rundet dagegen auf genau zwei
        // Nachkommastellen und liefert sie auch so zurück.
        //
        // Für #11/#12 heisst das: das JSON zeigt jetzt von sich aus 335.00, und ein DTO-equals
        // gegen new BigDecimal("335.00") schlägt nicht mehr unerwartet fehl. Ein setScale(2) an
        // der DTO-Grenze bleibt trotzdem sinnvoll — es macht die Zusage unabhängig davon, welche
        // Datenbank darunter liegt.
        assertThat(repository.findByIdAndUserId(ganzzahlig.getId(), userId).orElseThrow()
                        .getBetrag().scale())
                .isEqualTo(2);
        assertThat(repository.findByIdAndUserId(nachkomma.getId(), userId).orElseThrow()
                        .getBetrag().scale())
                .isEqualTo(2);
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
