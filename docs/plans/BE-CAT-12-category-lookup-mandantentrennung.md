# [BE-CAT-12] category_lookup überlebt die Kontolöschung — und wächst seit BE-CAT-11 ohne Zutun des Users

- **Issue:** [#319](https://github.com/dfme/budget-buddy/issues/319)
- **Task-ID:** `BE-CAT-12`
- **Branch:** `fix/BE-CAT-12-category-lookup-mandantentrennung` (abgezweigt von
  `feature/BE-CAT-11-lerneffekt-claude-erfolg`, PR #320 — dort entsteht die zweite Schreibquelle)
- **Story:** US-02 — Datenschutz-Consent
- **Sprint:** Sprint 7
- **Bestätigt am:** 2026-09-18

## Entscheide

1. **Zwischenform (AC 1, → ADR-15).** `category_lookup` (V04) bleibt die globale, kuratierte
   Basis: die Seeds und — auf Teamentscheid — die vor dieser Änderung gelernten Altzeilen, die
   sich keinem User mehr zuordnen lassen. Alles *neu* Gelernte (manuelle Korrektur BE-CAT-04 und
   Claude-Treffer BE-CAT-11) geht in eine neue Tabelle `user_category_lookup` mit `user_id` + FK
   auf `users` (ohne `ON DELETE`, wie V02/V03/V05/V10/V11).
2. **Matching:** eigene + globale Patterns, längstes gewinnt; bei gleicher Länge das eigene. Ein
   User kann `MIGROS` für sich umbiegen, ohne den Seed für alle zu ändern.
3. **Port-Signaturen tragen die User-ID:** `CategorizationPort.categorize(userId, text)` /
   `categorizeAll(userId, texts)` und `CategoryLearningPort.learn(userId, pattern, category)`.
   `ClaudeCategorizationService` bleibt hinter dem Port, ignoriert die ID und behält seine
   textbasierten Methoden als eigentliche Implementierung.
4. **Upsert** per nativem `INSERT … ON CONFLICT (user_id, empfaenger_pattern) DO UPDATE`
   (Postgres-only seit ADR-12).
5. **Basis-Branch:** gestapelt auf #320, weil die zweite Schreibquelle nur dort existiert und
   beide Quellen in einem Zug mandantengebunden werden.

## Neue Dateien

- `backend/src/main/resources/db/migration/V12__create_user_category_lookup_table.sql`
- `categorization/UserCategoryLookup.java`, `UserCategoryLookupRepository.java`
- `categorization/CategoryLookupCleanupPort.java`, `CategoryLookupCleanupService.java`
- Tests: `CategoryLookupCleanupServiceTest`, `db/UserCategoryLookupMigrationTest`
- `docs/adr/ADR-15-mandantengebundener-lerneffekt.md`

## Geänderte Dateien

- Backend main: `CategorizationPort`, `CategoryLearningPort`, `CategoryLearningService`,
  `LookupTableService`, `HybridCategorizationService`, `ClaudeCategorizationService`,
  `TransactionCategoryService`, `ImportJobRunner`, `UserService`, `UserController`
- Backend test: `UserServiceTest`, `UserDeletionIntegrationTest`, `UserOpenApiTest`,
  `CategoryLearningServiceIntegrationTest`, `LookupTableServiceIntegrationTest`/`-Test`,
  `HybridCategorizationServiceTest`, `CategorizationLogRedactionTest`, `ImportJobRunnerTest`/
  `-TimingTest`, `TransactionCategoryServiceTest`, `TransactionCategoryControllerIntegrationTest`,
  `PdfLookupCoverageIntegrationTest`, `PdfImport*IntegrationTest`
- Doku: ADR-6 (Step 4 + Consequences), `docs/adr/README.md`, `CLAUDE.md`,
  `docs/ARCHITECTURE.md`, `docs/plans/README.md`

## Implementierungsschritte

1. V12-Migration + Entity + Repository (`findMatching(userId, text)`, `upsert`,
   `deleteAllByUserId` `@Modifying`)
2. Port-Signaturen + `CategoryLearningService`/`LookupTableService` umbauen
3. `Hybrid`, `Claude`, `TransactionCategoryService`, `ImportJobRunner` nachziehen
4. `CategoryLookupCleanupPort/-Service`, in `UserService.deleteUser` einhängen; Lückentexte in
   `UserService`/`UserController`/`Hybrid` ersetzen
5. Tests anpassen und neue schreiben; `./mvnw verify`
6. ADR-15, ADR-6, README, CLAUDE.md, ARCHITECTURE.md

## Test-Strategie

- **Unit:** `CategoryLookupCleanupServiceTest` (Delegation), `UserServiceTest` (fünfter Port vor
  `userRepository.delete`; bei falschem Passwort nicht), `LookupTableServiceTest` (längstes
  gewinnt, Tie → eigenes), `HybridCategorizationServiceTest` (`learn` bekommt die User-ID)
- **Integration (Postgres):** `UserDeletionIntegrationTest` — nach `DELETE /api/users/me` zählt
  `SELECT COUNT(*) FROM user_category_lookup WHERE user_id = ?` 0, die Zeile eines zweiten Users
  bleibt; `CategoryLearningServiceIntegrationTest` — Gelerntes nur für den eigenen User sichtbar,
  Upsert aktualisiert dieselbe Zeile, Seed bleibt unverändert; Migrationstest V12
- **E2E:** keine Änderung — `user_category_lookup` steht nicht in `PRESERVED_TABLES` und wird mit
  `TRUNCATE … CASCADE` geleert

## Acceptance Criteria (aus dem Issue)

- [ ] Entscheid global / pro Nutzer / Zwischenform ist getroffen und als ADR oder ADR-Ergänzung
      festgehalten
- [ ] `category_lookup` lässt sich mandantenweise räumen (sofern der Entscheid das vorsieht)
- [ ] `UserService.deleteUser` räumt sie über einen Cleanup-Port, wie die übrigen vier Tabellen
- [ ] Test: nach `deleteUser` zählt eine Abfrage 0 zugehörige Zeilen — nicht die Annahme, dass ein
      Cascade greift
- [ ] Die Lückenbeschreibungen in `UserService.deleteUser` und im `@Operation`-Text von
      `UserController.deleteAccount` sind entfernt oder auf den neuen Stand gebracht
- [ ] ADR-6 (Consequences) und `CLAUDE.md` ziehen nach
