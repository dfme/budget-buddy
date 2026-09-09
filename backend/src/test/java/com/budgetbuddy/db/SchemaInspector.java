package com.budgetbuddy.db;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Schema-Introspektion für die Flyway-Migrationstests (DB-05, ADR-12).
 *
 * <p>Ersetzt die {@code PRAGMA}-Aufrufe der SQLite-Zeit durch {@code information_schema} bzw. die
 * {@code pg_*}-Kataloge. Die Abfragen sind in allen Migrationstests identisch und stehen deshalb
 * hier statt mehrfach als private Helfer.
 *
 * <p>Alle Abfragen sind auf {@code current_schema()} eingeschränkt: die Testdatenbank enthält
 * neben {@code public} auch die Kataloge von Postgres selbst, und ein Tabellenname allein ist
 * darin nicht eindeutig.
 */
final class SchemaInspector {

    private final JdbcTemplate jdbcTemplate;

    SchemaInspector(JdbcTemplate jdbcTemplate) {
        this.jdbcTemplate = jdbcTemplate;
    }

    /**
     * Spaltenname → Postgres-Datentyp ({@code bigint}, {@code text}, {@code numeric}, …). Ohne
     * Präzisionsangabe; die prüft {@link #numericPrecisionAndScale} gezielt für Geldspalten.
     */
    Map<String, String> columnTypes(String table) {
        return jdbcTemplate.queryForList("""
                SELECT column_name, data_type
                FROM information_schema.columns
                WHERE table_schema = current_schema() AND table_name = ?
                """, table)
                .stream()
                .collect(Collectors.toMap(
                        c -> (String) c.get("column_name"),
                        c -> (String) c.get("data_type")));
    }

    /** Spaltenname → {@code true}, wenn die Spalte {@code NOT NULL} ist. */
    Map<String, Boolean> notNullFlags(String table) {
        return jdbcTemplate.queryForList("""
                SELECT column_name, is_nullable
                FROM information_schema.columns
                WHERE table_schema = current_schema() AND table_name = ?
                """, table)
                .stream()
                .collect(Collectors.toMap(
                        c -> (String) c.get("column_name"),
                        c -> "NO".equals(c.get("is_nullable"))));
    }

    /**
     * Präzision und Nachkommastellen einer {@code numeric}-Spalte als {@code "10,2"} — der
     * Nachweis für ADR-9 ({@code DECIMAL(10,2)}, niemals Gleitkomma).
     */
    String numericPrecisionAndScale(String table, String column) {
        return jdbcTemplate.queryForObject("""
                SELECT numeric_precision || ',' || numeric_scale
                FROM information_schema.columns
                WHERE table_schema = current_schema() AND table_name = ? AND column_name = ?
                """, String.class, table, column);
    }

    /** {@code true}, wenn die Spalte eine Identity-Spalte ist (Ersatz für AUTOINCREMENT). */
    boolean isIdentity(String table, String column) {
        return Boolean.TRUE.equals(jdbcTemplate.queryForObject("""
                SELECT is_identity = 'YES'
                FROM information_schema.columns
                WHERE table_schema = current_schema() AND table_name = ? AND column_name = ?
                """, Boolean.class, table, column));
    }

    /** Spalten des Primärschlüssels, in Definitionsreihenfolge. */
    List<String> primaryKeyColumns(String table) {
        return jdbcTemplate.queryForList("""
                SELECT kcu.column_name
                FROM information_schema.table_constraints tc
                JOIN information_schema.key_column_usage kcu
                  ON kcu.constraint_name = tc.constraint_name
                 AND kcu.table_schema = tc.table_schema
                WHERE tc.table_schema = current_schema()
                  AND tc.table_name = ?
                  AND tc.constraint_type = 'PRIMARY KEY'
                ORDER BY kcu.ordinal_position
                """, String.class, table);
    }

    /** {@code true}, wenn genau diese eine Spalte durch eine UNIQUE-Constraint abgedeckt ist. */
    boolean hasUniqueConstraintOn(String table, String column) {
        Integer matches = jdbcTemplate.queryForObject("""
                SELECT COUNT(*)
                FROM information_schema.table_constraints tc
                JOIN information_schema.key_column_usage kcu
                  ON kcu.constraint_name = tc.constraint_name
                 AND kcu.table_schema = tc.table_schema
                WHERE tc.table_schema = current_schema()
                  AND tc.table_name = ?
                  AND tc.constraint_type = 'UNIQUE'
                  AND kcu.column_name = ?
                """, Integer.class, table, column);

        return matches != null && matches > 0;
    }

    /**
     * Die Spaltenkombinationen aller UNIQUE-Constraints der Tabelle, je in Definitionsreihenfolge.
     *
     * <p>Ergänzt {@link #hasUniqueConstraintOn}, statt es zu ersetzen: Für eine zusammengesetzte
     * Constraint beantwortet die Einzelspalten-Variante die Frage <em>falsch positiv</em>. Sie
     * meldet {@code true} für {@code user_id} allein, obwohl die Eindeutigkeit erst mit der
     * zweiten Spalte gilt — und liesse damit eine versehentlich auf {@code user_id} reduzierte
     * Constraint («ein Nutzer, ein Abo») unbemerkt durch, obwohl die dem Zweck genau
     * widerspricht.
     */
    List<List<String>> uniqueConstraintColumns(String table) {
        return jdbcTemplate.queryForList("""
                SELECT tc.constraint_name,
                       string_agg(kcu.column_name, ',' ORDER BY kcu.ordinal_position) AS columns
                FROM information_schema.table_constraints tc
                JOIN information_schema.key_column_usage kcu
                  ON kcu.constraint_name = tc.constraint_name
                 AND kcu.table_schema = tc.table_schema
                WHERE tc.table_schema = current_schema()
                  AND tc.table_name = ?
                  AND tc.constraint_type = 'UNIQUE'
                GROUP BY tc.constraint_name
                """, table)
                .stream()
                .map(row -> List.of(((String) row.get("columns")).split(",")))
                .toList();
    }

    /**
     * Die Prüfausdrücke aller CHECK-Constraints der Tabelle, wie
     * {@code pg_get_constraintdef} sie ausgibt — z. B.
     * {@code "CHECK ((status = ANY (ARRAY['DETECTED'::text, 'DISMISSED'::text])))"}.
     *
     * <p>{@code contype = 'c'} liefert ausschliesslich echte CHECK-Constraints.
     * {@code NOT NULL} steht seit Postgres 17 als eigener {@code contype = 'n'} im Katalog
     * (Definition {@code "NOT NULL <spalte>"}) und fällt damit ohne weiteres Zutun heraus —
     * gegenprobiert an dieser Tabelle unter Postgres 18: sieben {@code n}-Zeilen, eine
     * {@code c}-Zeile.
     *
     * <p>Die Definition allein ist nur der halbe Nachweis — dass die Constraint auch
     * <em>greift</em>, zeigt erst ein abgewiesenes {@code INSERT}.
     */
    List<String> checkConstraintDefinitions(String table) {
        return jdbcTemplate.queryForList("""
                SELECT pg_get_constraintdef(con.oid) AS definition
                FROM pg_constraint con
                JOIN pg_class rel ON rel.oid = con.conrelid
                JOIN pg_namespace nsp ON nsp.oid = rel.relnamespace
                WHERE nsp.nspname = current_schema()
                  AND rel.relname = ?
                  AND con.contype = 'c'
                """, table)
                .stream()
                .map(row -> (String) row.get("definition"))
                .toList();
    }

    /**
     * Indexdefinition ({@code indexdef} aus {@code pg_indexes}, z. B. {@code "CREATE INDEX ...
     * USING btree (user_id, read_at)"}) für den Index mit diesem Namen auf der Tabelle, oder
     * {@code null}, wenn er nicht existiert.
     *
     * <p>Prüft absichtlich die volle Definition statt nur den Namen: Ein Index gleichen Namens,
     * aber auf weniger oder anderen Spalten, würde einen reinen Namensabgleich unbemerkt bestehen
     * lassen, obwohl die Abdeckung für die geplante Abfrage fehlt.
     */
    String indexDefinition(String table, String indexName) {
        return jdbcTemplate.query("""
                SELECT indexdef
                FROM pg_indexes
                WHERE schemaname = current_schema()
                  AND tablename = ?
                  AND indexname = ?
                """, rs -> rs.next() ? rs.getString("indexdef") : null, table, indexName);
    }

    /**
     * Fremdschlüssel der Tabelle als Zeilen mit den Schlüsseln {@code column},
     * {@code referenced_table} und {@code referenced_column}.
     */
    List<Map<String, Object>> foreignKeys(String table) {
        return jdbcTemplate.queryForList("""
                SELECT kcu.column_name        AS column,
                       ccu.table_name         AS referenced_table,
                       ccu.column_name        AS referenced_column
                FROM information_schema.table_constraints tc
                JOIN information_schema.key_column_usage kcu
                  ON kcu.constraint_name = tc.constraint_name
                 AND kcu.table_schema = tc.table_schema
                JOIN information_schema.constraint_column_usage ccu
                  ON ccu.constraint_name = tc.constraint_name
                 AND ccu.table_schema = tc.table_schema
                WHERE tc.table_schema = current_schema()
                  AND tc.table_name = ?
                  AND tc.constraint_type = 'FOREIGN KEY'
                """, table);
    }
}
