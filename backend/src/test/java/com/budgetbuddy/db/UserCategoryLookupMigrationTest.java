package com.budgetbuddy.db;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

import com.budgetbuddy.support.PostgresTestDatabase;
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
 * Verifiziert die Flyway-Migration V12 ({@code user_category_lookup}-Tabelle, BE-CAT-12 / ADR-15)
 * gegen eine echte PostgreSQL-Datenbank.
 *
 * <p>Neben der Schema-Introspektion setzen zwei Fälle echte {@code INSERT}s ab
 * ({@link #patternIsUniquePerUser}, {@link #differentUsersMayLearnTheSamePattern}): Die
 * Unique-Constraint trägt den Upsert des Lerneffekts — eine Constraint, deren Wirkung niemand
 * ausgelöst hat, ist unbelegt. Und {@link #globalSeedsAreLeftUntouched} hält fest, dass V12 die
 * bestehende {@code category_lookup} nicht anfasst (Teamentscheid «Altdaten» in ADR-15).
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class UserCategoryLookupMigrationTest {

    private static final String TABLE = "user_category_lookup";

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.register(registry, "user_category_lookup_migration");
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    /** Der FK auf {@code users(id)} verlangt für jeden Insert-Fall einen echten User. */
    private long userId;

    @BeforeEach
    void insertUser() {
        jdbcTemplate.update("DELETE FROM " + TABLE);
        jdbcTemplate.update("DELETE FROM users");
        userId = insertUser("lara@example.ch");
    }

    private SchemaInspector schema() {
        return new SchemaInspector(jdbcTemplate);
    }

    @Test
    void migrationsRunSuccessfullyThroughV12() {
        Integer successfulMigrations = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = true", Integer.class);

        assertThat(successfulMigrations).isGreaterThanOrEqualTo(12);
    }

    @Test
    void tableHasAllColumnsWithCorrectTypes() {
        Map<String, String> typeByColumn = schema().columnTypes(TABLE);

        assertThat(typeByColumn).containsOnlyKeys("id", "user_id", "empfaenger_pattern", "category");
        assertThat(typeByColumn.get("id")).isEqualTo("bigint");
        assertThat(typeByColumn.get("user_id")).isEqualTo("bigint");
        assertThat(typeByColumn.get("empfaenger_pattern")).isEqualTo("text");
        assertThat(typeByColumn.get("category")).isEqualTo("text");
    }

    @Test
    void allColumnsAreNotNull() {
        assertThat(schema().notNullFlags(TABLE).values()).containsOnly(true);
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
    void deletingAUserWithLearnedPatternsIsRefusedByTheDatabase() {
        // Bewusst kein ON DELETE CASCADE (DB-07): Vergisst ein Löschpfad diese Tabelle, soll die
        // Datenbank laut werden statt still zu räumen — genau die Lücke, die #319 beschreibt.
        insertPattern(userId, "BAECKEREI MUELLER", "Lebensmittel");

        assertThatThrownBy(() -> jdbcTemplate.update("DELETE FROM users WHERE id = ?", userId))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void uniqueConstraintCoversUserIdAndPattern() {
        assertThat(schema().uniqueConstraintColumns(TABLE))
                .containsExactly(List.of("user_id", "empfaenger_pattern"));
    }

    @Test
    void patternIsUniquePerUser() {
        insertPattern(userId, "BAECKEREI MUELLER", "Lebensmittel");

        assertThatThrownBy(() -> insertPattern(userId, "BAECKEREI MUELLER", "Restaurant"))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    void differentUsersMayLearnTheSamePattern() {
        // Eindeutig ist das Paar, nicht das Pattern allein — sonst könnte nur ein User einen
        // Händler lernen, und der Lerneffekt wäre wieder global.
        long otherUserId = insertUser("marc@example.ch");

        insertPattern(userId, "BAECKEREI MUELLER", "Lebensmittel");
        insertPattern(otherUserId, "BAECKEREI MUELLER", "Restaurant");

        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + TABLE + " WHERE empfaenger_pattern = 'BAECKEREI MUELLER'",
                Integer.class))
                .isEqualTo(2);
    }

    @Test
    void globalSeedsAreLeftUntouched() {
        // V12 legt nur die neue Tabelle an; category_lookup (V04) bleibt mit allen Zeilen stehen —
        // die 18 Seeds aus V04 sind die Untergrenze, kommt ein Seed dazu, darf der Test nicht reissen.
        assertThat(jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM category_lookup", Integer.class))
                .isGreaterThanOrEqualTo(18);
        assertThat(schema().columnTypes("category_lookup"))
                .containsOnlyKeys("empfaenger_pattern", "category");
    }

    private long insertUser(String email) {
        jdbcTemplate.update(
                "INSERT INTO users (email, password_hash, onboarding_completed) VALUES (?, ?, ?)",
                email, "bcrypt-hash", true);
        return jdbcTemplate.queryForObject(
                "SELECT id FROM users WHERE email = ?", Long.class, email);
    }

    private void insertPattern(long forUser, String pattern, String category) {
        jdbcTemplate.update(
                "INSERT INTO " + TABLE + " (user_id, empfaenger_pattern, category) VALUES (?, ?, ?)",
                forUser, pattern, category);
    }
}
