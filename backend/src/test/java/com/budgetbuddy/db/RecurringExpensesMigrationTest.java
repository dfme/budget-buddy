package com.budgetbuddy.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.budgetbuddy.support.PostgresTestDatabase;
import java.math.BigDecimal;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Verifiziert die Flyway-Migration V11 (recurring_expenses-Tabelle, DB-09) gegen eine echte
 * PostgreSQL-Datenbank.
 *
 * <p>V11 statt V09 wie im Issue-Titel: V09 ({@code add_token_version_to_users}, BE-AUTH-11) und
 * V10 ({@code create_notifications_table}, DB-08) sind auf main vergeben. Der Migrations-Guard
 * (INFRA-29, {@code scripts/check-migrations.sh}) verhindert genau diese Kollision.
 *
 * <p>Seit DB-05 (ADR-12) gegen Testcontainers-Postgres in derselben Major-Version wie Produktion,
 * mit einer eigenen Datenbank für diese Klasse (siehe {@link PostgresTestDatabase}).
 *
 * <p>Zwei Fälle gehen über reine Schema-Introspektion hinaus und setzen echte {@code INSERT}s ab
 * ({@link #statusAcceptsOnlyDetectedAndDismissed}, {@link #payeeKeyIsUniquePerUser}): Eine
 * Constraint, deren Wirkung niemand ausgelöst hat, ist unbelegt — der Katalogeintrag allein sagt
 * nur, dass sie <em>dasteht</em>.
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class RecurringExpensesMigrationTest {

    private static final String TABLE = "recurring_expenses";

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.register(registry, "recurring_expenses_migration");
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** Der FK auf {@code users(id)} verlangt für jeden Insert-Fall einen echten User. */
    private long userId;

    @BeforeEach
    void insertUser() {
        jdbcTemplate.update("DELETE FROM " + TABLE);
        jdbcTemplate.update("DELETE FROM users");
        jdbcTemplate.update(
                "INSERT INTO users (email, password_hash, monthly_income, onboarding_completed)"
                        + " VALUES (?, ?, ?, ?)",
                "lara@example.ch", "bcrypt-hash", new BigDecimal("4200.00"), true);
        userId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE email = 'lara@example.ch'", Long.class);
    }

    private SchemaInspector schema() {
        return new SchemaInspector(jdbcTemplate);
    }

    @Test
    void migrationsRunSuccessfullyThroughV11() {
        // V01 (users) ... V11 (recurring_expenses) müssen alle erfolgreich gelaufen sein.
        Integer successfulMigrations = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = true", Integer.class);

        assertThat(successfulMigrations).isGreaterThanOrEqualTo(11);
    }

    @Test
    void recurringExpensesTableHasAllColumnsWithCorrectTypes() {
        Map<String, String> typeByColumn = schema().columnTypes(TABLE);

        // containsOnlyKeys und nicht containsKeys: Eine zusätzliche Spalte wäre im Schema
        // genauso ein Befund wie eine fehlende — der AC zählt die sieben abschliessend auf.
        assertThat(typeByColumn).containsOnlyKeys(
                "id", "user_id", "payee_key", "amount", "status", "first_detected_month",
                "created_at");

        assertThat(typeByColumn.get("id")).isEqualTo("bigint");
        assertThat(typeByColumn.get("user_id")).isEqualTo("bigint");
        assertThat(typeByColumn.get("payee_key")).isEqualTo("text");
        assertThat(typeByColumn.get("amount")).isEqualTo("numeric");
        assertThat(typeByColumn.get("status")).isEqualTo("text");
        // TEXT und nicht DATE: der Wert ist ein Monat (YYYY-MM), kein Datum — siehe Migration.
        assertThat(typeByColumn.get("first_detected_month")).isEqualTo("text");
        assertThat(typeByColumn.get("created_at")).isEqualTo("timestamp with time zone");
    }

    @Test
    void amountIsDecimalWithTwoFractionDigits() {
        // ADR-9: Geldbeträge als DECIMAL(10,2), niemals Gleitkomma.
        assertThat(schema().numericPrecisionAndScale(TABLE, "amount")).isEqualTo("10,2");
    }

    @Test
    void idIsIdentityPrimaryKey() {
        assertThat(schema().primaryKeyColumns(TABLE)).containsExactly("id");
        assertThat(schema().isIdentity(TABLE, "id")).isTrue();
    }

    @Test
    void foreignKeyToUsersIsDefined() {
        assertThat(schema().foreignKeys(TABLE)).anySatisfy(fk -> {
            assertThat(fk.get("column")).isEqualTo("user_id");
            assertThat(fk.get("referenced_table")).isEqualTo("users");
            assertThat(fk.get("referenced_column")).isEqualTo("id");
        });
    }

    @Test
    void allColumnsAreNotNull() {
        // Keine Spalte trägt zum Insert-Zeitpunkt eine sinnvolle Leere. Das unterscheidet die
        // Tabelle von notifications, wo reference_id und read_at bewusst nullable sind.
        Map<String, Boolean> notNullByColumn = schema().notNullFlags(TABLE);

        assertThat(notNullByColumn).containsOnlyKeys(
                "id", "user_id", "payee_key", "amount", "status", "first_detected_month",
                "created_at");
        assertThat(notNullByColumn.values()).containsOnly(true);
    }

    @Test
    void uniqueConstraintCoversUserIdAndPayeeKey() {
        // Geprüft wird die Spaltenkombination, nicht bloss die Existenz irgendeiner UNIQUE: Eine
        // versehentlich auf user_id reduzierte Constraint hiesse "ein Nutzer, ein Abo" und
        // widerspräche dem Zweck — mit einem Einzelspalten-Check bliebe sie unbemerkt.
        assertThat(schema().uniqueConstraintColumns(TABLE))
                .containsExactly(List.of("user_id", "payee_key"));
    }

    @Test
    void payeeKeyIsUniquePerUser() {
        // Die strukturelle Absicherung für das dauerhafte "Kein Abo" aus US-08: Neben einer als
        // DISMISSED abgelegten Gruppe darf keine zweite DETECTED-Zeile für denselben Empfänger
        // entstehen, sonst wäre der Ausschluss still umgangen.
        insertRecurringExpense("SPOTIFY", "12.95", "DISMISSED", "2026-07");

        assertThatThrownBy(() -> insertRecurringExpense("SPOTIFY", "12.95", "DETECTED", "2026-09"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void differentUsersMayShareTheSamePayeeKey() {
        // Die Gegenprobe zum Fall oben: Eindeutig ist das Paar, nicht payee_key allein — sonst
        // könnte ein zweiter Nutzer dasselbe Abo nie bekommen.
        jdbcTemplate.update(
                "INSERT INTO users (email, password_hash, onboarding_completed) VALUES (?, ?, ?)",
                "marc@example.ch", "bcrypt-hash", true);
        Long otherUserId = jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE email = 'marc@example.ch'", Long.class);

        insertRecurringExpense(userId, "SPOTIFY", "12.95", "DETECTED", "2026-07");
        insertRecurringExpense(otherUserId, "SPOTIFY", "12.95", "DETECTED", "2026-08");

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + TABLE + " WHERE payee_key = 'SPOTIFY'", Integer.class))
                .isEqualTo(2);
    }

    @Test
    void statusAcceptsOnlyDetectedAndDismissed() {
        // Erst die Definition, damit ein Fehlschlag unten nicht mit einem anderen Constraint-
        // Verstoss verwechselt wird. singleElement statt anySatisfy: Die Tabelle soll genau
        // diese eine CHECK-Constraint tragen, eine zweite wäre selbst ein Befund.
        assertThat(schema().checkConstraintDefinitions(TABLE))
                .singleElement()
                .asString()
                .contains("status")
                .contains("DETECTED")
                .contains("DISMISSED");

        // ... dann die Wirkung. Beide erlaubten Werte gehen durch, alles andere wird abgewiesen —
        // ohne diesen Teil wäre nur belegt, dass die Constraint dasteht, nicht dass sie greift.
        insertRecurringExpense("SPOTIFY", "12.95", "DETECTED", "2026-07");
        insertRecurringExpense("NETFLIX", "19.90", "DISMISSED", "2026-07");

        assertThatThrownBy(() -> insertRecurringExpense("SALT", "39.95", "NONSENSE", "2026-07"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void createdAtDefaultsToNow() {
        // Der Insert oben setzt created_at nirgends — DEFAULT now() füllt es.
        insertRecurringExpense("SPOTIFY", "12.95", "DETECTED", "2026-07");

        assertThat(jdbcTemplate.queryForObject(
                "SELECT created_at IS NOT NULL FROM " + TABLE, Boolean.class)).isTrue();
    }

    @Test
    void upperCaseComparisonMatchesRegardlessOfStoredCase() {
        // AC 3: Gespeichert wird in Grossschreibung, verglichen über upper() auf beiden Seiten —
        // dieselbe Mechanik wie CategoryLookupRepository.findMatching für V04. Der Beleg, dass
        // die Suche eines gemischt geschriebenen Empfängers den normalisierten Eintrag findet,
        // ohne dass Postgres eine COLLATE NOCASE bräuchte.
        insertRecurringExpense("SPOTIFY AB", "12.95", "DETECTED", "2026-07");

        Integer matches = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + TABLE + " WHERE upper(payee_key) = upper(?)",
                Integer.class, "Spotify AB");

        assertThat(matches).isEqualTo(1);
    }

    private void insertRecurringExpense(
            String payeeKey, String amount, String status, String firstDetectedMonth) {
        insertRecurringExpense(userId, payeeKey, amount, status, firstDetectedMonth);
    }

    private void insertRecurringExpense(
            Long owner, String payeeKey, String amount, String status, String firstDetectedMonth) {
        jdbcTemplate.update(
                "INSERT INTO " + TABLE
                        + " (user_id, payee_key, amount, status, first_detected_month)"
                        + " VALUES (?, ?, ?, ?, ?)",
                owner, payeeKey, new BigDecimal(amount), status, firstDetectedMonth);
    }
}
