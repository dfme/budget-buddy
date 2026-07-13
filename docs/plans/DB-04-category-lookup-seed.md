# [DB-04] Flyway V4: category_lookup-Tabelle mit Seed-Daten

- **Issue:** #7
- **Task-ID:** DB-04
- **Branch:** `feature/DB-04-category-lookup-seed`
- **User Story:** US-05 (Transaktionen kategorisieren)

## Entscheide

- **Case-insensitive Lookup** über `COLLATE NOCASE` auf dem PK-Feld `empfaenger_pattern`.
  In SQLite matcht damit `WHERE empfaenger_pattern = 'migros'` auch den Seed `'MIGROS'`.
- **Kategorien** ausschliesslich aus der fixen Liste in CLAUDE.md
  (Lebensmittel, Transport, Telekom, Versicherung, Freizeit, Restaurant, Shopping).
- Migrationstest gegen echte SQLite-Temp-File-DB, analog zu `FixedCostsMigrationTest`
  (kein `:memory:`, da Flyway- und Query-Connection sonst verschiedene DBs sähen).

## Betroffene / neue Files

- **Neu:** `backend/src/main/resources/db/migration/V04__create_category_lookup_table.sql`
- **Neu:** `backend/src/test/java/com/budgetbuddy/db/CategoryLookupMigrationTest.java`

## Schema

```sql
CREATE TABLE category_lookup (
    empfaenger_pattern VARCHAR COLLATE NOCASE PRIMARY KEY,
    category           VARCHAR NOT NULL
);
```

## Seed-Daten (~15 CH-Händler)

| Pattern | Kategorie |
|---|---|
| MIGROS, COOP, DENNER, ALDI, LIDL | Lebensmittel |
| SBB, SWISS PASS | Transport |
| SWISSCOM, SUNRISE, SALT | Telekom |
| CSS, HELSANA | Versicherung |
| DIGITEC, GALAXUS, ZALANDO | Shopping |
| NETFLIX, SPOTIFY | Freizeit |
| MCDONALD'S | Restaurant |

## Test-Strategie (JUnit)

1. Migrationen laufen fehlerfrei durch (>= 4 erfolgreiche Flyway-Einträge)
2. Tabelle hat genau Spalten `empfaenger_pattern`, `category`; `empfaenger_pattern` ist PK
3. >= 10 Seed-Zeilen vorhanden
4. Case-insensitive Lookup: Query mit kleingeschriebenem Pattern liefert Treffer
5. Alle Seed-Kategorien sind aus der erlaubten Kategorienliste

## Acceptance Criteria (aus Issue #7)

- [ ] Migration läuft fehlerfrei nach V3
- [ ] Tabelle enthält mind. 10 Seed-Einträge für CH-Händler
- [ ] PK ist empfaenger_pattern (case-insensitive Lookup möglich)
