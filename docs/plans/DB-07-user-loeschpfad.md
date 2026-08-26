# [DB-07] Foreign Keys auf users ohne ON DELETE — Löschpfad für US-02

- **Issue:** [#142](https://github.com/dfme/budget-buddy/issues/142)
- **Task-ID:** `DB-07`
- **Branch:** `fix/DB-07-user-loeschpfad`
- **Story:** US-02 — Datenschutz-Consent + Konto löschen (nDSG)
- **Sprint:** Sprint 5
- **Bestätigt am:** 2026-08-24

## Entscheid

Expliziter Aufräumpfad im Code, kein `ON DELETE CASCADE`. Folgt der bereits in
`V05__create_import_jobs_table.sql` getroffenen Vorgabe («Bewusst KEIN ON DELETE CASCADE: Die
Löschung soll eine bewusste, testbare Operation im Code sein») — dieses Ticket formalisiert die
Entscheidung für alle drei abhängigen Tabellen und setzt sie um.

Umgesetzt über das bestehende Port-Pattern (analog `UserIncomePort`/`IncomeSuggestionPort`): das
Interface steht im *liefernden* Modul, `UserService` (auth) ruft es auf, ohne fremde Repositories
direkt zu berühren (Modulgrenze, CLAUDE.md).

## Scope-Erweiterung gegenüber der ursprünglichen AC

Die AC des Issues nennt nur `transactions` und `fixed_costs`. Eine breite Suche
(`grep -rn "REFERENCES users" backend/src/main/resources/db/migration/`) zeigt, dass
`import_jobs` (V05, BE-PDF-09) ebenfalls eine unqualifizierte FK auf `users` hat — die
Migration selbst dokumentiert bereits den Bedarf. Nach Rücksprache mit dem User: wird in diesem
PR mitgelöst, da V05 den Bedarf schon als Teil derselben Löschoperation beschreibt.

## Betroffene / neue Files

**Neu:**
- `backend/src/main/java/com/budgetbuddy/transaction/TransactionCleanupPort.java`
- `backend/src/main/java/com/budgetbuddy/transaction/TransactionCleanupService.java` — löscht
  `transactions` **und** `import_jobs` (beide leben im transaction-Modul)
- `backend/src/main/java/com/budgetbuddy/budget/FixedCostCleanupPort.java`
- `backend/src/main/java/com/budgetbuddy/budget/FixedCostCleanupService.java` — löscht `fixed_costs`
- `backend/src/test/java/com/budgetbuddy/transaction/TransactionCleanupServiceTest.java`
- `backend/src/test/java/com/budgetbuddy/budget/FixedCostCleanupServiceTest.java`
- `backend/src/test/java/com/budgetbuddy/auth/UserDeletionIntegrationTest.java` — Testcontainers
  Postgres, End-to-End-Nachweis

**Geändert:**
- `TransactionRepository.java`, `ImportJobRepository.java`, `FixedCostRepository.java` — je eine
  `@Modifying @Query("delete from … where userId = :userId")`-Methode. Bewusst `@Modifying` statt
  der abgeleiteten `deleteBy…`-Variante: Letztere lädt Entities und ruft `remove()` auf, was bis
  zum Flush aufgeschoben wird — bei drei Tabellen quer über Module ist die physische
  DELETE-Reihenfolge sonst nicht garantiert. `@Modifying` führt das DELETE sofort aus, bevor
  `UserService.deleteUser` den User selbst entfernt.
- `UserService.java` — neue Methode `deleteUser(long userId)`: lädt den User, ruft beide
  Cleanup-Ports auf, löscht dann den User — alles innerhalb einer `@Transactional`-Grenze.
- `UserServiceTest.java` — neue Mocks für beide Ports, Tests für Happy Path, Reihenfolge
  (Cleanup vor `userRepository.delete`) und `UserNotFoundException`.

**Nicht Teil dieses Tickets:** kein `DELETE /api/users/me`-Endpoint — der wird von US-02 auf
Basis von `UserService.deleteUser` gebaut.

## Implementierungsschritte

1. Ports + Cleanup-Services in `transaction` und `budget` anlegen
2. Bulk-Delete-Methoden (`@Modifying @Query`) in den drei betroffenen Repositories
3. `UserService.deleteUser` implementieren, Konstruktor um beide Ports erweitern
4. Unit-Tests für beide neuen Cleanup-Services + erweiterter `UserServiceTest`
5. Integrationstest gegen echtes Postgres (Testcontainers): User + Transaktion + Fixkosten-Position
   + ImportJob anlegen, `deleteUser` aufrufen, prüfen: keine Exception (FK-Reihenfolge korrekt),
   0 Zeilen in allen drei Tabellen, User selbst weg

## Test-Strategie

- **Unit (Mockito):** `TransactionCleanupServiceTest`, `FixedCostCleanupServiceTest`, erweiterter
  `UserServiceTest`
- **Integration (Testcontainers Postgres, `PostgresTestDatabase`-Pattern):** End-to-End-Löschung
  gegen echte FK-Constraints — der eigentliche Beweis für AC2
- **Migrationstest:** entfällt — AC3 ist bedingt auf «falls Migration», hier keine Schema-Änderung

## Acceptance Criteria (aus Issue #142)

- [ ] Entscheid zwischen `ON DELETE CASCADE` und explizitem Aufräumpfad ist im Ticket dokumentiert
      und umgesetzt — siehe „Entscheid" oben, umgesetzt über die Cleanup-Ports
- [ ] Ein Test belegt: nach dem Löschen eines Users existieren keine `transactions`- und
      `fixed_costs`-Zeilen (plus `import_jobs`, Scope-Erweiterung) mit dessen `user_id` mehr —
      `UserDeletionIntegrationTest`
- [ ] Falls Migration: sie ist durch einen Migrationstest abgedeckt — entfällt, keine Migration
- [ ] Bestehende Tests laufen weiter grün — insbesondere solche, die Testdaten in abhängiger
      Reihenfolge löschen
