# [DB-08] Flyway V09: notifications-Tabelle

- **Issue:** [#245](https://github.com/dfme/budget-buddy/issues/245)
- **Task-ID:** `DB-08`
- **Branch:** `feature/DB-08-notifications-table`
- **Story:** US-08 — Wiederkehrende Ausgaben (Abos) erkennen (Vorlauf-Task, kein direkter AC-Bezug)
- **Sprint:** Sprint 6
- **Bestätigt am:** 2026-09-05

## Nachtrag (nach automatischem PR-Review, #272)

- **V08 → V09:** Während dieser Branch offen war, wurde `V08__add_direction_uncertain_to_transactions.sql`
  (BE-PDF-10, #193) auf `main` gemergt und belegte damit die Version V08 zuerst. Der
  Migrations-Guard (INFRA-29) verhindert genau diese Kollision; die Migration dieses Plans läuft
  deshalb als `V09__create_notifications_table.sql`. Der Task-ID `DB-08` bleibt unverändert — er
  bezeichnet das Issue, nicht die Flyway-Version.
- **Testabdeckung nachgezogen:** Die ursprüngliche Annahme unten ("kein automatisierter Test,
  analog zu V01, V03, V07") war falsch — V01 und V03 haben dedizierte Migrationstests
  (`UsersMigrationTest`, `FixedCostsMigrationTest`); nur reine Spaltenzusätze (V06/V07) verzichten
  darauf. `NotificationsMigrationTest` deckt die neue Tabelle jetzt nach demselben Muster ab
  (Spalten/Typen, PK-Identity, FK auf `users`, Index-Vorhandensein).

## Kontext

Vorlauf-Task für das Notification-Fundament aus
[docs/plans/us-08-09-12-breakdown.md](us-08-09-12-breakdown.md). Es existiert im Projekt noch
keine Notification-Infrastruktur — weder Backend noch Frontend (`shared/notice/` ist eine
inline Banner-Komponente, kein Inbox-/Toast-System). Diese Migration schafft nur die
Datengrundlage; `NotificationService` + REST-Endpoints folgen in `BE-NOTIF-01`.

## Entscheide

- **`reference_id` ohne Foreign-Key-Constraint** — polymorpher Verweis, dessen Zieltabelle vom
  `type`-Wert abhängt (z. B. `recurring_expenses.id` bei `RECURRING_EXPENSE_DETECTED`, künftig
  auch andere Quellen wie `MONTHLY_REPORT_READY`). Ein FK auf eine einzelne Tabelle würde die
  Notification-Tabelle an eine einzige Quelle binden. Mit dem User bestätigt.
- **`user_id`-FK ohne `ON DELETE CASCADE`**, analog zu `import_jobs` (V05): Die Kontolöschung
  (US-02, nDSG) bleibt eine bewusste, testbare Operation im Code statt einer stillen
  Fremdschlüssel-Nebenwirkung. `notifications` reiht sich damit in die Liste der Tabellen ein,
  die eine künftige Konto-Löschung mit abräumen muss.
- **`type` als `TEXT`, kein DB-Enum** (laut AC) — eine neue Ausprägung ist damit eine
  Code-Änderung, keine Migration.
- **`read_at` als `TIMESTAMPTZ`, nullable** — NULL heisst ungelesen, ein gesetzter Zeitstempel
  heisst gelesen. Deckt das geplante `POST /api/notifications/{id}/read` aus `BE-NOTIF-01` ab,
  ohne ein zusätzliches Boolean-Feld.
- **Index auf `(user_id, read_at)`** — der geplante `GET /api/notifications`-Endpoint listet
  ungelesene zuerst pro Nutzer.

## Betroffene Dateien

- Neu: `backend/src/main/resources/db/migration/V09__create_notifications_table.sql`

## Implementierungsschritte

1. Migration `V09__create_notifications_table.sql` anlegen mit Spalten
   `id, user_id, type, reference_id, message, read_at, created_at`.
2. FK `user_id → users(id)`, kein CASCADE.
3. Index auf `user_id` (bzw. `(user_id, read_at)`) für die künftige Ungelesen-Abfrage.
4. Lokal verifizieren: `docker compose up -d` + Anwendungsstart, Flyway wendet V09 fehlerfrei an.

## Test-Strategie

`NotificationsMigrationTest` (siehe Nachtrag oben) nach dem Muster von `FixedCostsMigrationTest`:
Spalten/Typen, PK-Identity, FK auf `users`, Nullable-Flags, Index-Vorhandensein — gegen eine echte
Testcontainers-Postgres-Instanz. Zusätzlich Nachweis für AC5: manueller Lauf von
`docker compose up -d` + Backend-Start, Flyway-Log zeigt V09 als erfolgreich angewendet.

## Acceptance Criteria (aus Issue #245)

- [ ] Migration `V09__create_notifications_table.sql` liegt unter
      `backend/src/main/resources/db/migration/`
- [ ] Tabelle `notifications` mit Spalten `id, user_id, type, reference_id, message, read_at, created_at`
- [ ] `user_id` als Foreign Key auf `users(id)`
- [ ] `type` ist ein String-Feld (kein DB-Enum), das künftige Quellen abgrenzt (z. B. `RECURRING_EXPENSE_DETECTED`)
- [ ] Migration läuft lokal via `docker compose up -d` + Anwendungsstart fehlerfrei durch
