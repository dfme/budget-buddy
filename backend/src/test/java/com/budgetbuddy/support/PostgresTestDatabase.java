package com.budgetbuddy.support;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Locale;
import java.util.concurrent.atomic.AtomicInteger;
import org.springframework.test.context.DynamicPropertyRegistry;
import org.testcontainers.containers.PostgreSQLContainer;

/**
 * Gemeinsame Testdatenbank für alle Integrationstests (DB-05, ADR-12).
 *
 * <p>Getestet wird gegen dieselbe PostgreSQL-Major-Version wie in Produktion (Neon). Vorher lief
 * die Suite gegen SQLite und damit gegen eine andere Engine als Produktion — genau der
 * Dialekt-Mismatch, vor dem {@code CLAUDE.md} unter <em>What NOT to Use</em> warnt.
 *
 * <h2>Ein Container, eine Datenbank pro Testklasse</h2>
 *
 * <p>Der Container wird als Singleton beim ersten Zugriff gestartet und für die gesamte JVM
 * wiederverwendet; er wird bewusst nie gestoppt. Das Ryuk-Sidecar von Testcontainers räumt ihn
 * nach dem Ende der JVM ab. Ein Container pro Testklasse (über {@code @Testcontainers}) würde bei
 * über zwanzig Integrationstestklassen entsprechend viele Container starten und den Testlauf um
 * ein Vielfaches verlängern.
 *
 * <p>Isoliert wird stattdessen über eine eigene Datenbank je Testklasse — dasselbe Prinzip wie die
 * frühere Temp-Datei pro Klasse unter SQLite: kein Test sieht die Zeilen eines anderen, und
 * Reihenfolge-Abhängigkeiten können gar nicht erst entstehen.
 *
 * <h2>Ein kleiner HikariCP-Pool pro Kontext</h2>
 *
 * <p>Dieser Container läuft mit {@code max_connections=100} über alle Testklassen. Jede Klasse
 * bekommt via {@code @DynamicPropertySource} einen eigenen {@code ApplicationContext}
 * (unterschiedliche Methodenreferenz = unterschiedlicher Cache-Key), und Spring hält bis zu 32
 * Kontexte gleichzeitig im Cache (Default {@code spring.test.context.cache.maxSize}) — jeder mit
 * einem eigenen HikariCP-Pool. Mit dem HikariCP-Default ({@code maximumPoolSize=10}) reichen
 * schon rund zehn gleichzeitig gecachte Kontexte, um die 100 Verbindungen des Containers
 * auszuschöpfen; ab dann hängt der nächste Kontextaufbau im HikariCP-{@code connectionTimeout}
 * fest, und der volle Lauf wirkt wie eingefroren, bis Spring den Kontext nach dem ersten
 * Fehlschlag als dauerhaft gescheitert markiert ({@code "ApplicationContext failure threshold
 * exceeded"}). {@link #register} setzt die Pool-Grösse deshalb auf 2 — das reicht für
 * sequenzielle Tests bequem und hält den Bedarf selbst bei allen 32 gecachten Kontexten unter
 * dem Limit. Die Grenze sitzt hier statt in {@code application-test.properties}, weil sie für
 * jede Klasse gelten muss, die sich diesen Container teilt — nicht nur für die mit
 * {@code @ActiveProfiles("test")}.
 */
public final class PostgresTestDatabase {

    /**
     * Muss zur Version in {@code docker-compose.yml}, im CI-Service-Container
     * ({@code .github/workflows/build.yml}) und im Neon-Projekt passen.
     */
    private static final String IMAGE = "postgres:18-alpine";

    /** Macht jeden Datenbanknamen eindeutig — siehe {@link #createFreshDatabase}. */
    private static final AtomicInteger SEQUENCE = new AtomicInteger();

    private PostgresTestDatabase() {
        // Utility-Klasse
    }

    // Holder-Idiom: der Container startet erst beim ersten register()-Aufruf, nicht schon beim
    // Laden der Klasse. Unit-Tests, die diese Klasse nie anfassen, brauchen so kein Docker.
    private static final class Holder {
        private static final PostgreSQLContainer<?> CONTAINER = start();

        private static PostgreSQLContainer<?> start() {
            PostgreSQLContainer<?> container = new PostgreSQLContainer<>(IMAGE);
            container.start();
            return container;
        }
    }

    /**
     * Registriert eine frische, exklusive Datenbank für die aufrufende Testklasse und aktiviert
     * Flyway darauf. Aufruf aus einer mit {@code @DynamicPropertySource} annotierten Methode:
     *
     * <pre>{@code
     * @DynamicPropertySource
     * static void datasourceProperties(DynamicPropertyRegistry registry) {
     *     PostgresTestDatabase.register(registry, "users_migration");
     * }
     * }</pre>
     *
     * @param registry     Registry aus {@code @DynamicPropertySource}.
     * @param databaseName sprechender Name, pro Testklasse eindeutig zu wählen.
     */
    public static void register(DynamicPropertyRegistry registry, String databaseName) {
        register(registry, databaseName, true);
    }

    /**
     * Wie {@link #register}, lässt Flyway aber aus. Für Tests, die nur eine startfähige
     * DataSource brauchen und kein Schema anfassen (Security-, Routing-, Actuator-Tests).
     */
    public static void registerWithoutFlyway(DynamicPropertyRegistry registry,
            String databaseName) {
        register(registry, databaseName, false);
    }

    private static void register(DynamicPropertyRegistry registry, String databaseName,
            boolean flywayEnabled) {
        PostgreSQLContainer<?> container = Holder.CONTAINER;
        String database = createFreshDatabase(databaseName);

        registry.add("spring.datasource.url", () -> "jdbc:postgresql://%s:%d/%s"
                .formatted(container.getHost(), container.getFirstMappedPort(), database));
        registry.add("spring.datasource.username", container::getUsername);
        registry.add("spring.datasource.password", container::getPassword);
        registry.add("spring.flyway.enabled", () -> String.valueOf(flywayEnabled));
        registry.add("spring.datasource.hikari.maximum-pool-size", () -> "2");
    }

    /**
     * Legt eine frische Datenbank an. Der übergebene Name bekommt eine laufende Nummer angehängt,
     * damit jeder Aufruf garantiert eine eigene Datenbank erhält.
     *
     * <p>Die Nummer ist nicht bloss Kosmetik: Spring baut für eine Testklasse mehrere Kontexte auf,
     * wenn sie {@code @Nested}-Klassen mit unterschiedlicher Konfiguration enthält
     * ({@code AnthropicConfigTest}). Bei einem festen Namen liefe der zweite Aufbau in
     * {@code database "…" is being accessed by other users} — der Pool des ersten Kontexts hält
     * seine Verbindungen noch. Mit laufender Nummer entfällt das Problem, ohne dass ein Test von
     * den Daten eines anderen etwas sieht.
     */
    private static String createFreshDatabase(String databaseName) {
        String database = sanitize(databaseName) + "_" + SEQUENCE.incrementAndGet();

        try (Connection connection = Holder.CONTAINER.createConnection("");
                Statement statement = connection.createStatement()) {
            // Bezeichner lassen sich nicht als Prepared-Statement-Parameter binden; sanitize()
            // oben beschränkt sie deshalb auf [a-z0-9_].
            statement.execute("CREATE DATABASE " + database);
        } catch (SQLException e) {
            throw new IllegalStateException(
                    "Test-Datenbank '" + database + "' konnte nicht angelegt werden", e);
        }

        return database;
    }

    private static String sanitize(String databaseName) {
        String sanitized = databaseName.toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_]", "_");

        if (sanitized.isBlank() || !Character.isLetter(sanitized.charAt(0))) {
            throw new IllegalArgumentException(
                    "Datenbankname muss mit einem Buchstaben beginnen: " + databaseName);
        }

        return sanitized;
    }
}
