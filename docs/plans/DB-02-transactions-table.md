# DB-02 — Flyway V2: transactions-Tabelle

- **Issue:** #5
- **Task-ID:** DB-02
- **Branch:** `feature/DB-02-transactions-table`
- **Depends on:** #4 (DB-01, gemergt) — V01 users-Tabelle

## Entscheidungen

- Dateiname `V02__create_transactions_table.sql` — zweistellige Version mit führender Null
  (CLAUDE.md-Konvention), trotz "Flyway V2" im Issue-Titel.
- Spaltentypen: `VARCHAR` für `buchungstext`/`category`/`pdf_sha256` (gemäss Issue-Wortlaut).
- Spalte `buchungstext` (statt `text`) — sprechender und kein SQL-naher Bezeichner.
- Nicht durch AC vorgegebene Nullability: `user_id`, `buchungsdatum`, `buchungstext`, `betrag` = NOT NULL;
  `is_income` NOT NULL DEFAULT 0; `category` nullable (wird erst durch US-05 gesetzt);
  `pdf_sha256` nullable (AC).
- `betrag` als `DECIMAL(10,2)` — nie FLOAT/REAL (ADR-9).

## Schema

```sql
CREATE TABLE transactions (
    id            INTEGER       PRIMARY KEY AUTOINCREMENT,
    user_id       INTEGER       NOT NULL,
    buchungsdatum DATE          NOT NULL,
    buchungstext  VARCHAR       NOT NULL,
    betrag        DECIMAL(10,2) NOT NULL,
    is_income     BOOLEAN       NOT NULL DEFAULT 0,
    category      VARCHAR,
    pdf_sha256    VARCHAR,
    FOREIGN KEY (user_id) REFERENCES users(id)
);
```

## Betroffene / neue Files

- Neu: `backend/src/main/resources/db/migration/V02__create_transactions_table.sql`
- Neu: `backend/src/test/java/com/budgetbuddy/db/TransactionsMigrationTest.java`

## Implementierungsschritte

1. Migration `V02__create_transactions_table.sql` schreiben
2. `TransactionsMigrationTest` (Integration, analog `UsersMigrationTest`) schreiben
3. `./mvnw test` ausführen
4. Lokaler Review (`git diff main`) → PR

## Test-Strategie

Integrationstest gegen echte SQLite-Datei (analog `UsersMigrationTest`):

- Migration läuft fehlerfrei nach V1 (≥ 2 erfolgreiche Migrationen in `flyway_schema_history`)
- Alle Spalten existieren mit korrektem Typ
- `betrag` ist `DECIMAL(10,2)`, kein float/real
- FK `user_id → users.id` via `PRAGMA foreign_key_list('transactions')` definiert
- `pdf_sha256` erlaubt NULL; `user_id`/`betrag` sind NOT NULL

## Acceptance Criteria (aus Issue #5)

- [ ] Migration läuft fehlerfrei nach V1
- [ ] Foreign Key user_id → users.id ist definiert
- [ ] betrag ist DECIMAL(10,2), pdf_sha256 erlaubt NULL
