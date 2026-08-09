package com.budgetbuddy.db;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.jdbc.core.JdbcTemplate;

/**
 * Schema-Introspektion für die Flyway-Migrationstests (DB-05, ADR-12).
 *
 * <p>Ersetzt die {@code PRAGMA}-Aufrufe der SQLite-Zeit durch {@code information_schema} bzw. die
 * {@code pg_*}-Kataloge. Die Abfragen sind in vier Migrationstests identisch und stehen deshalb
 * hier statt viermal als private Helfer.
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
