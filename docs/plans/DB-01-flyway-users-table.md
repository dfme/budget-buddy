# DB-01 — Flyway V1: users-Tabelle

**Issue:** #4 · **Task-ID:** DB-01 · **Sprint 1 / Wave 1**
**Branch:** `feature/DB-01-flyway-users-table`

## Entscheide

- **Flyway aktivieren** und via Spring-Boot-Start ausführen (kein `flyway-maven-plugin`).
- **Flyway-Version von Spring Boot gemanaged lassen** (11.7.2). Ein separates SQLite-Modul
  ist nicht nötig: `flyway-database-sqlite` existiert nicht, und Flyway 11 unterstützt SQLite
  direkt über den bereits vorhandenen JDBC-Treiber `org.xerial:sqlite-jdbc` (verifiziert durch
  grünen Integrationstest). Damit entfällt der ursprünglich angedachte 10.24.0-Pin.
- **Schema:** nur AC-Spalten.
- **Migrations-Naming:** zweistellige Version mit führender Null (`V01__…`) für stabile
  Sortierung bei vielen Files.
- **Kein** User-JPA-Entity (gehört zu BE-AUTH).

## Betroffene Files

**Neu:**
- `backend/src/main/resources/db/migration/V01__create_users_table.sql`
- `backend/src/test/java/com/budgetbuddy/db/UsersMigrationTest.java`

**Geändert:**
- `backend/pom.xml` — nur Kommentar an `flyway-core` aktualisiert (keine Versionsänderung,
  keine neue Dependency)
- `backend/src/main/resources/application.properties` — `spring.flyway.enabled=true`
- `backend/src/main/resources/db/migration/.gitkeep` — entfernen

## Migration (V01)

```sql
CREATE TABLE users (
    id                   INTEGER       PRIMARY KEY AUTOINCREMENT,
    email                TEXT          NOT NULL UNIQUE,
    password_hash        TEXT          NOT NULL,
    monthly_income       DECIMAL(10,2),
    onboarding_completed BOOLEAN       NOT NULL DEFAULT 0
);
```

- `monthly_income` als `DECIMAL(10,2)` → SQLite NUMERIC-Affinität, **kein FLOAT/REAL**
  (AC #3 + ADR-9).
- `onboarding_completed BOOLEAN` → NUMERIC-Affinität, gespeichert als 0/1.

## Implementierungsschritte

1. `pom.xml`: Kommentar an `flyway-core` aktualisieren (Version bleibt Spring-Boot-gemanaged,
   keine zusätzliche Dependency).
2. `application.properties`: `spring.flyway.enabled=true`.
3. `V01__create_users_table.sql` anlegen, `.gitkeep` löschen.
4. Integrationstest schreiben.

## Test-Strategie (JUnit Integration)

`UsersMigrationTest` als `@SpringBootTest` mit `@DynamicPropertySource` → SQLite
**Temp-File-DB** (`@TempDir`) + `spring.flyway.enabled=true`. Grund: `jdbc:sqlite::memory:`
legt pro Connection eine eigene DB an → Flyway-Connection und Query-Connection würden sich
nicht sehen; Temp-File umgeht das zuverlässig.

Assertions via `JdbcTemplate`:
- **Happy Path:** Migration läuft fehlerfrei; `flyway_schema_history` enthält einen
  erfolgreichen Eintrag (`success = 1`). Kein exakter Versionsstring, da Flyway führende
  Nullen normalisiert (`01` → `1`).
- Alle 5 Spalten existieren (`PRAGMA table_info(users)`).
- `monthly_income`-Typ ist `DECIMAL(10,2)` und **nicht** FLOAT/REAL (AC #3).
- `email` ist NOT NULL + UNIQUE-Index vorhanden.

Der bestehende `contextLoads`-Test (test-Profil) bleibt unverändert.

## Acceptance Criteria (aus Issue #4)

- [ ] Migration läuft fehlerfrei mit `mvn flyway:migrate` (hier: Spring-Boot-Startup-Migration
      + JUnit-Verifikation)
- [ ] Tabelle enthält alle definierten Spalten mit korrekten Typen
- [ ] monthly_income ist DECIMAL(10,2), kein FLOAT
