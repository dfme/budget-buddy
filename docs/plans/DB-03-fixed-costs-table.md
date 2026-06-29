# DB-03 — Flyway V3: fixed_costs-Tabelle

- **Issue:** #6
- **Task-ID:** DB-03
- **Branch:** `feature/DB-03-fixed-costs-table`
- **Depends on:** #4 (DB-01, gemergt), baut auf V02 (#48, gemergt) auf

## Entscheidungen

- Dateiname `V03__create_fixed_costs_table.sql` — zweistellige Version mit führender Null.
- Spaltentypen: `VARCHAR` für `bezeichnung`/`intervall` (konsistent mit DB-02).
- Alle Spalten `NOT NULL`: eine Fixkosten-Position hat immer Bezeichnung, Betrag und Intervall.
- `betrag` als `DECIMAL(10,2)` — nie FLOAT/REAL (ADR-9).

## Schema

```sql
CREATE TABLE fixed_costs (
    id          INTEGER       PRIMARY KEY AUTOINCREMENT,
    user_id     INTEGER       NOT NULL,
    bezeichnung VARCHAR       NOT NULL,
    betrag      DECIMAL(10,2) NOT NULL,
    intervall   VARCHAR       NOT NULL,
    FOREIGN KEY (user_id) REFERENCES users(id)
);
```

## Betroffene / neue Files

- Neu: `backend/src/main/resources/db/migration/V03__create_fixed_costs_table.sql`
- Neu: `backend/src/test/java/com/budgetbuddy/db/FixedCostsMigrationTest.java`

## Implementierungsschritte

1. Migration `V03__create_fixed_costs_table.sql` schreiben
2. `FixedCostsMigrationTest` (Integration, analog `TransactionsMigrationTest`) schreiben
3. `./mvnw test` (volle Suite) ausführen
4. Lokaler Review (`git diff main`) → PR

## Test-Strategie

Integrationstest gegen echte SQLite-Datei (analog `TransactionsMigrationTest`):

- Migration läuft fehlerfrei nach V2 (≥ 3 erfolgreiche Migrationen in `flyway_schema_history`)
- Alle Spalten existieren mit korrektem Typ
- `betrag` ist `DECIMAL(10,2)`, kein float/real
- FK `user_id → users.id` via `PRAGMA foreign_key_list('fixed_costs')` definiert
- `user_id`/`bezeichnung`/`betrag`/`intervall` sind NOT NULL

## Acceptance Criteria (aus Issue #6)

- [ ] Migration läuft fehlerfrei nach V2
- [ ] Foreign Key user_id → users.id ist definiert
- [ ] betrag ist DECIMAL(10,2)
