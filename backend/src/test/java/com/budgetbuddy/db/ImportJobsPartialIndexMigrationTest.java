package com.budgetbuddy.db;

import static org.assertj.core.api.Assertions.assertThat;

import com.budgetbuddy.support.PostgresTestDatabase;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.annotation.DirtiesContext;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.springframework.test.context.DynamicPropertySource;

/**
 * Verifiziert die Flyway-Migration V13 (Teilindex auf {@code import_jobs}, DB-10, #270) gegen
 * eine echte PostgreSQL-Datenbank.
 *
 * <p>Die beiden Indizes aus V05 führen beide mit {@code user_id}, das die Cleaner-Query in
 * {@code StaleImportJobCleaner.cleanUpStaleJobs} ({@code WHERE status = 'RUNNING' AND created_at
 * < ?}) nicht verwendet — Postgres fiel deshalb auf einen Sequential Scan zurück. Ob der neue
 * Index vom Planer tatsächlich statt eines Sequential Scans gewählt wird, ist per {@code EXPLAIN}
 * im PR dokumentiert; auf einer leeren Testcontainers-Tabelle kann der Planer trotz passendem
 * Index einen Seq Scan als günstiger einschätzen, ein solcher Test wäre brüchig.
 *
 * <p>V13 statt V12: {@code V12__create_user_category_lookup_table.sql} war zum Zeitpunkt dieser
 * Migration bereits durch einen anderen offenen PR (#323) belegt, der zwischenzeitlich gemergt
 * wurde.
 *
 * <p>Seit DB-05 (ADR-12) gegen Testcontainers-Postgres in derselben Major-Version wie Produktion,
 * mit einer eigenen Datenbank für diese Klasse (siehe {@link PostgresTestDatabase}).
 */
@SpringBootTest
@DirtiesContext(classMode = DirtiesContext.ClassMode.AFTER_CLASS)
class ImportJobsPartialIndexMigrationTest {

    private static final String TABLE = "import_jobs";

    @DynamicPropertySource
    static void datasourceProperties(DynamicPropertyRegistry registry) {
        PostgresTestDatabase.register(registry, "import_jobs_partial_index_migration");
    }

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private SchemaInspector schema() {
        return new SchemaInspector(jdbcTemplate);
    }

    @Test
    void migrationsRunSuccessfullyThroughV13() {
        // V01 (users) ... V13 (Teilindex auf import_jobs) müssen alle erfolgreich gelaufen sein.
        Integer successfulMigrations = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM flyway_schema_history WHERE success = true", Integer.class);

        assertThat(successfulMigrations).isGreaterThanOrEqualTo(13);
    }

    @Test
    void partialIndexCoversCreatedAtForRunningJobs() {
        // Geprüft wird die volle Indexdefinition, nicht nur der Name: Ein Index gleichen Namens
        // ohne die WHERE-Klausel oder auf der falschen Spalte bediente die Cleaner-Query nicht,
        // bliebe mit einem reinen Namensabgleich aber unbemerkt.
        assertThat(schema().indexDefinition(TABLE, "idx_import_jobs_running_created_at"))
                .isNotNull()
                .contains("(created_at)")
                .contains("WHERE (status = 'RUNNING'::text)");
    }
}
