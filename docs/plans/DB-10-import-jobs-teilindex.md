# [DB-10] Cleaner-Query auf import_jobs läuft als Sequential Scan — Teilindex fehlt

- **Issue:** [#270](https://github.com/dfme/budget-buddy/issues/270)
- **Task-ID:** `DB-10`
- **Branch:** `fix/DB-10-import-jobs-teilindex`
- **Story:** US-04 — PDF-Upload
- **Sprint:** Sprint 7
- **Bestätigt am:** 2026-09-19

## Ausgangslage

`StaleImportJobCleaner.cleanUpStaleJobs()` fragt `import_jobs` mit
`WHERE status = 'RUNNING' AND created_at < ?`. Die beiden bestehenden Indizes aus V05
(`idx_import_jobs_user_id`, `idx_import_jobs_user_hash_status`) führen beide mit `user_id`, das in
dieser Query gar nicht vorkommt — Postgres fällt auf einen Sequential Scan zurück (per `EXPLAIN`
gegen den laufenden Container bestätigt, siehe Issue).

## Versionsnummer

`V12` ist bereits durch den offenen PR #323 (`fix/BE-CAT-12-category-lookup-mandantentrennung`,
`V12__create_user_category_lookup_table.sql`) belegt. Alle offenen Remote-Branches wurden nach
`db/migration/V(12|13|...)__`-Dateien durchsucht — ausser diesem einen Treffer keine weiteren.
Diese Migration verwendet deshalb **`V13`**.

## Implementierungsschritte

1. `backend/src/main/resources/db/migration/V13__add_partial_index_import_jobs_running.sql`:
   ```sql
   CREATE INDEX idx_import_jobs_running_created_at
       ON import_jobs (created_at)
       WHERE status = 'RUNNING';
   ```
   Teilindex, weil nur `RUNNING`-Zeilen je gesucht werden (Cleaner-Query, Duplikatcheck-Pfad nicht
   betroffen — der nutzt `idx_import_jobs_user_hash_status`).
2. Kommentar in `StaleImportJobCleaner.java` (Javadoc von `onApplicationReady()`, beschreibt aktuell
   den Sequential Scan und verweist auf DB-10 als offen) auf den neuen Zustand nachziehen: Query
   nutzt jetzt `idx_import_jobs_running_created_at` (V13).
3. Kommentar in `application.properties` (bei
   `budgetbuddy.import.stale-job-scan-interval`, beschreibt denselben Sequential Scan) ebenso
   nachziehen.
4. Neuer Migrationstest `backend/src/test/java/com/budgetbuddy/db/ImportJobsMigrationTest.java`
   nach dem Muster von `NotificationsMigrationTest` (via `SchemaInspector`, echtes
   Testcontainers-Postgres): prüft, dass der Index mit korrekter Spalte **und** `WHERE`-Klausel
   existiert.
5. `EXPLAIN` der Cleaner-Query manuell gegen den laufenden Container erheben und das Ergebnis
   (kein Seq Scan mehr) im PR-Body dokumentieren — das AC verlangt eine dokumentierte Erhebung,
   kein automatisiertes `EXPLAIN`-Assertion (der Postgres-Planer kann auf einer kleinen
   Testcontainers-Tabelle trotz Index einen Seq Scan wählen; ein Test darauf wäre brüchig).

## Betroffene Dateien

- Neu: `backend/src/main/resources/db/migration/V13__add_partial_index_import_jobs_running.sql`
- Neu: `backend/src/test/java/com/budgetbuddy/db/ImportJobsMigrationTest.java`
- Geändert: `backend/src/main/java/com/budgetbuddy/transaction/StaleImportJobCleaner.java`
- Geändert: `backend/src/main/resources/application.properties`

## Test-Strategie

- Neuer Migrationstest gegen echtes Postgres (Testcontainers): Index existiert mit
  `(created_at)` und `WHERE (status = 'RUNNING'::text)` in der Definition.
- Manuelles `EXPLAIN` gegen den laufenden Container, Ergebnis im PR-Body dokumentiert (AC3).
- Bestehende Tests (`StaleImportJobCleanerTest`, `StaleImportJobCleanerIntegrationTest`) laufen
  unverändert weiter — die Migration ändert nur den Zugriffspfad, nicht das Verhalten.

## Acceptance Criteria (aus dem Issue)

- [ ] Flyway-Migration legt einen Teilindex an, der die Cleaner-Query bedient
- [ ] Die Versionsnummer der Migration kollidiert mit keinem offenen Branch
- [ ] `EXPLAIN` der Cleaner-Query zeigt danach keinen Sequential Scan mehr — im PR dokumentiert
- [ ] Kommentare in `application.properties` und `StaleImportJobCleaner`, die den Sequential Scan
      beschreiben, sind auf den neuen Ist-Zustand nachgezogen
