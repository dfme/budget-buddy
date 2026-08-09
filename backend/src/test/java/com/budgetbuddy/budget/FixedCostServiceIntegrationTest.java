package com.budgetbuddy.budget;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.budgetbuddy.budget.dto.FixedCostRequest;
import com.budgetbuddy.budget.dto.FixedCostResponse;
import com.budgetbuddy.budget.dto.FixedCostSummaryResponse;
import java.io.IOException;
import java.io.UncheckedIOException;
import java.math.BigDecimal;
import java.nio.file.Files;
import java.nio.file.Path;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Integrationstest des {@link FixedCostService} gegen echte SQLite + Flyway (BE-FC-02).
 *
 * <p>Belegt drei Dinge, die der Unit-Test mit gemocktem Repository nicht belegen kann: die
 * <strong>Mandantentrennung</strong> aus Sicht eines fremden Users, die Skala der Beträge nach
 * einem echten DB-Round-Trip (#141) und die Verdrahtung des {@code UserIncomePort} über die
 * Modulgrenze hinweg.
 *
 * <p>Temp-File-DB statt {@code jdbc:sqlite::memory:} und {@code @DirtiesContext} analog zu
 * {@link FixedCostRepositoryIntegrationTest} (Begründung dort dokumentiert). Die Test-User werden
 * aus demselben Grund per {@link JdbcTemplate} eingefügt: der FK {@code fixed_costs.user_id →
 * users.id} braucht echte Zeilen, ein {@code UserRepository}-Zugriff wäre aber genau der
 * modulübergreifende Zugriff, den CLAUDE.md untersagt.
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class FixedCostServiceIntegrationTest {

    private static final Path DB_FILE = createTempDbFile();

    private static Path createTempDbFile() {
        try {
            Path file = Files.createTempFile("be-fc-02-service-it", ".db");
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

    @Autowired private FixedCostService service;
    @Autowired private JdbcTemplate jdbcTemplate;

    // --- Mandantentrennung: Gegenprobe aus Sicht eines fremden Users ---

    @Test
    void aForeignUserCanNeitherReadNorChangeNorDeleteAnEntry() {
        long lara = insertUser("lara-isolation@example.com", null);
        long marc = insertUser("marc-isolation@example.com", null);
        long entryId = service.create(lara, new FixedCostRequest("Miete",
                new BigDecimal("1200.00"), "monatlich")).id();

        assertThatThrownBy(() -> service.get(marc, entryId))
                .isInstanceOf(FixedCostNotFoundException.class);
        assertThatThrownBy(() -> service.update(marc, entryId,
                        new FixedCostRequest("Gekapert", new BigDecimal("1.00"), "monatlich")))
                .isInstanceOf(FixedCostNotFoundException.class);
        assertThatThrownBy(() -> service.delete(marc, entryId))
                .isInstanceOf(FixedCostNotFoundException.class);

        // Der Eintrag von Lara ist unverändert und existiert weiter — ein geworfener Fehler allein
        // beweist noch nicht, dass nichts geschrieben wurde.
        FixedCostResponse unveraendert = service.get(lara, entryId);
        assertThat(unveraendert.bezeichnung()).isEqualTo("Miete");
        assertThat(unveraendert.betrag()).isEqualByComparingTo("1200.00");
    }

    @Test
    void listShowsOnlyTheOwnEntries() {
        long lara = insertUser("lara-list@example.com", null);
        long marc = insertUser("marc-list@example.com", null);
        service.create(lara, new FixedCostRequest("Miete", new BigDecimal("1200.00"), "monatlich"));
        service.create(marc, new FixedCostRequest("Fitness", new BigDecimal("89.00"), "monatlich"));

        assertThat(service.list(lara).fixedCosts())
                .extracting(FixedCostResponse::bezeichnung)
                .containsExactly("Miete");
    }

    // --- Beträge nach echtem DB-Round-Trip ---

    @Test
    void amountsKeepScaleTwoAndTheSumStaysRappenExactAfterTheRoundTrip() {
        long userId = insertUser("rappen@example.com", null);
        service.create(userId, new FixedCostRequest("Miete", new BigDecimal("1200.00"),
                "monatlich"));
        service.create(userId, new FixedCostRequest("Handy", new BigDecimal("100.00"),
                "quartalsweise"));
        service.create(userId, new FixedCostRequest("Serafe", new BigDecimal("335.00"),
                "jaehrlich"));
        // 39.90 liegt als REAL 39.9 in der Datei und kommt mit Skala 1 zurück — der zweite Fall
        // aus #141. Ohne diese Position hätten alle Beträge hier Skala 0 und der Test liefe an
        // der halben Fehlerklasse vorbei, die er absichern soll.
        service.create(userId, new FixedCostRequest("Handy", new BigDecimal("39.90"),
                "monatlich"));

        FixedCostSummaryResponse summary = service.list(userId);

        // Aus SQLite kommt betrag mit Skala 0 oder 1 zurück (#141) — die Antwort normalisiert auf 2.
        assertThat(summary.fixedCosts()).allSatisfy(item -> {
            assertThat(item.betrag().scale()).isEqualTo(2);
            assertThat(item.monatsbetrag().scale()).isEqualTo(2);
        });
        assertThat(summary.fixedCosts()).extracting(FixedCostResponse::monatsbetrag)
                .containsExactly(
                        new BigDecimal("1200.00"), new BigDecimal("33.33"), new BigDecimal("27.92"),
                        new BigDecimal("39.90"));
        assertThat(summary.summeMonatlich()).isEqualByComparingTo("1301.15");
    }

    @Test
    void listOfAUserWithoutEntriesIsEmpty() {
        long userId = insertUser("leer@example.com", null);

        FixedCostSummaryResponse summary = service.list(userId);

        assertThat(summary.fixedCosts()).isEmpty();
        assertThat(summary.summeMonatlich()).isEqualByComparingTo("0.00");
        assertThat(summary.exceedsIncome()).isFalse();
    }

    // --- Einkommen über den UserIncomePort (echte Verdrahtung auth → budget) ---

    @Test
    void warningIsDerivedFromTheIncomeOfTheRequestingUser() {
        long knapp = insertUser("knapp@example.com", new BigDecimal("1200.00"));
        long komfortabel = insertUser("komfortabel@example.com", new BigDecimal("4200.00"));
        service.create(knapp, new FixedCostRequest("Miete", new BigDecimal("1200.00"),
                "monatlich"));
        service.create(komfortabel, new FixedCostRequest("Miete", new BigDecimal("1200.00"),
                "monatlich"));

        assertThat(service.list(knapp).exceedsIncome()).isTrue();
        assertThat(service.list(komfortabel).exceedsIncome()).isFalse();
    }

    @Test
    void withoutRecordedIncomeThereIsNoWarningAndNoIncomeInTheResponse() {
        long userId = insertUser("kein-einkommen@example.com", null);
        service.create(userId, new FixedCostRequest("Miete", new BigDecimal("1200.00"),
                "monatlich"));

        FixedCostSummaryResponse summary = service.list(userId);

        assertThat(summary.monthlyIncome()).isNull();
        assertThat(summary.exceedsIncome()).isFalse();
    }

    // --- CRUD gegen die echte DB ---

    @Test
    void updateAndDeletePersistThroughTheDatabase() {
        long userId = insertUser("crud@example.com", null);
        long entryId = service.create(userId, new FixedCostRequest("Miete",
                new BigDecimal("1200.00"), "monatlich")).id();

        service.update(userId, entryId,
                new FixedCostRequest("Serafe", new BigDecimal("335.00"), "jaehrlich"));

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT intervall FROM fixed_costs WHERE id = ?", String.class, entryId))
                .isEqualTo("jaehrlich");
        assertThat(service.get(userId, entryId).monatsbetrag()).isEqualByComparingTo("27.92");

        service.delete(userId, entryId);

        assertThat(jdbcTemplate.queryForObject(
                        "SELECT COUNT(*) FROM fixed_costs WHERE id = ?", Integer.class, entryId))
                .isZero();
    }

    /**
     * Legt einen User direkt per SQL an. Die ID wird über die eindeutige E-Mail zurückgelesen und
     * nicht über {@code last_insert_rowid()}: das ist ein Connection-lokaler Wert, und der
     * Hikari-Pool gibt für die zweite Query keine garantiert identische Connection heraus.
     */
    private long insertUser(String email, BigDecimal monthlyIncome) {
        jdbcTemplate.update(
                "INSERT INTO users (email, password_hash, monthly_income) VALUES (?, ?, ?)",
                email, "$2a$10$test.only.not.a.real.hash", monthlyIncome);
        return jdbcTemplate.queryForObject("SELECT id FROM users WHERE email = ?", Long.class, email);
    }
}
