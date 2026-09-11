# [BE-REC-01] RecurringExpenseService: Erkennung

- **Issue:** [#253](https://github.com/dfme/budget-buddy/issues/253)
- **Task-ID:** `BE-REC-01`
- **Branch:** `feature/BE-REC-01-recurring-expense-detection`
- **Story:** US-08 — Wiederkehrende Ausgaben (Abos) erkennen
- **Sprint:** Sprint 6
- **Bestätigt am:** 2026-09-11

## Kontext

Dritter Task der US-08-Kette aus [us-08-09-12-breakdown.md](us-08-09-12-breakdown.md), nach
`DB-09` (#252, Tabelle `recurring_expenses`, Flyway V11) und `BE-NOTIF-01` (#246,
`NotificationPort`). Erkennt wiederkehrende Ausgaben am Ende des bestehenden Async-Import-Flows
(`ImportJobRunner`, ADR-14) — einmal pro erfolgreichem Upload, auf dem Pool-Thread, nach dem
Persistieren. Kein Scheduler: wer nichts importiert, löst auch keine Erkennung aus.

Der Hinweis «analog zu `FixedCostDebitMatcher`» im Issue ist lose gemeint: Der Matcher ist reine
Lesepfad-Rechenlogik im budget-Modul und wird nicht aus dem Import aufgerufen. Übernommen wird
daraus nur die Haltung — Vergleich über auf Rappen normalisierte `BigDecimal`-Beträge, keine
Gleitkommazahlen (ADR-9).

## Entscheide

Mit dem User bestätigt:

- **Neues Modul `recurring/`**, analog `notification/`: Entity, Repository, Service,
  `RecurringExpenseDetectionPort` (für `ImportJobRunner`) und `RecurringExpenseCleanupPort`
  (für `UserService.deleteUser`). Transaktionen liest das Modul über einen neuen, schmalen
  Lese-Port `ExpenseHistoryPort` im transaction-Modul — dieselbe Bauart wie `MonthlyExpensePort`
  für budget. Der Zyklus transaction ↔ recurring läuft ausschliesslich über Interfaces, wie heute
  schon auth ↔ budget (`FixedCostCleanupPort` / `UserIncomePort`).

  Verworfen: alles in `transaction/`. Kein neuer Port, aber das Modul wächst weiter, BE-REC-02
  legte seinen Controller ebenfalls dort ab, und der vom AC geforderte
  `RecurringExpenseCleanupPort` stünde neben `TransactionCleanupPort` im selben Modul — dessen
  Javadoc «löscht alle Daten eines Users, die im transaction-Modul liegen» wäre dann falsch.

- **`first_detected_month` = frühester Monat des ersten qualifizierenden Monatspaars** in den
  Daten («seit wann läuft dieses Abo»). Nicht der Monat des Erkennungslaufs — den hält
  `created_at` bereits fest, ein zweites Feld dafür wäre redundant.

Eigene Entscheide, im Plan vorgelegt:

- **`payee_key`-Normalisierung** liegt im transaction-Modul (`ExpenseHistoryService`): nur dort
  ist bekannt, dass der Empfänger in der ersten Zeile von `buchungsdetails` steht (BE-PDF-07) und
  bei UBS/Raiffeisen im Buchungstext. Quelle wie `IncomeSuggestionService.groupingKey`: erste
  Detailzeile, Rückfall Buchungstext. Ziffernhaltige Tokens entfernt (`COOP-1234 BERN` →
  `COOP BERN` — Filialnummern und Referenzen wechseln), Whitespace kollabiert,
  **Grossschreibung** (V11-Vertrag, wie `CategoryLearningService` für V04).
- **Toleranz ±2 %:** Belastungen in Monat *m* und *m+1* gehören zusammen, wenn
  `|b − a| ≤ a × 0.02`, Basis ist der frühere Betrag. Gespeichert wird der Betrag des jüngsten
  qualifizierenden Paars.
- **Zeitfenster:** gesamte Ausgaben-Historie des Users, kein 12-Monats-Fenster ab «heute» wie in
  `IncomeSuggestionService` — sonst bliebe ein rückwirkend importierter Jahresauszug 2025
  unerkannt.
- **Bestehende Zeilen:** `DISMISSED` → übersprungen (AC 5). `DETECTED` → unverändert, keine
  zweite Notification. Nur neue Gruppen schreiben und benachrichtigen.
- **Fehlerisolation:** `detect()` läuft nach dem Persist und vor `finishSuccessfully`, in
  `try/catch RuntimeException`. Ein Fehler in der Erkennung setzt den Import nicht auf `FAILED`
  (CLAUDE.md: ein Einzelausfall blockiert nie den Import-Flow). Log-Zeile ohne Empfängernamen.
- **Notification:** `type = "RECURRING_EXPENSE_DETECTED"`, `referenceId = recurring_expenses.id`,
  Text «Wiederkehrende Ausgabe erkannt: NETFLIX INTERNATIONAL BV — CHF 20.90 pro Monat».
- **Bekannte Grenze:** `UNIQUE (user_id, payee_key)` (DB-09) erlaubt eine Zeile pro Empfänger.
  Zwei Abos beim selben Anbieter (Swisscom Mobile + Internet) ergeben einen Eintrag. Wird im
  PR-Body und Javadoc benannt, hier nicht gelöst.
- **Doku-Delta (vorbestehend, im selben PR):** `docs/CONVENTIONS.md` kennt im Package-Baum
  `notification/` nicht (Lücke seit BE-NOTIF-01). `notification/` und `recurring/` ergänzen, die
  Erkennung in der Import-Flow-Tabelle als Teil des `@Async`-Teils nennen.

## Betroffene / neue Files

### Neu — `backend/src/main/java/com/budgetbuddy/recurring/`

- `RecurringExpense.java` — Entity (V11), Status als `@Enumerated(STRING)`
- `RecurringExpenseStatus.java` — `DETECTED`, `DISMISSED`
- `RecurringExpenseRepository.java` — `findByUserId`, `@Modifying deleteAllByUserId`
- `RecurringExpenseDetectionPort.java` — `void detect(long userId)`
- `RecurringExpenseService.java` — implementiert den Port
- `RecurringExpenseCleanupPort.java`, `RecurringExpenseCleanupService.java`
- `package-info.java`

### Neu — `backend/src/main/java/com/budgetbuddy/transaction/`

- `ExpenseHistoryPort.java` — `List<ExpenseEntry> expenseHistory(long userId)`,
  Record `ExpenseEntry(String payeeKey, BigDecimal amount, YearMonth month)`
- `ExpenseHistoryService.java` — implementiert den Port, `payee_key`-Normalisierung

### Geändert

- `transaction/ImportJobRunner.java` — Port injizieren, `detect()` nach Persist (guarded)
- `transaction/TransactionRepository.java` — `findByUserIdAndIncomeFalse`
- `transaction/package-info.java` — neuen Port erwähnen
- `auth/UserService.java` — `RecurringExpenseCleanupPort` in `deleteUser`, Javadoc
- `docs/CONVENTIONS.md`, `docs/plans/README.md`

### Tests

- Neu `recurring/RecurringExpenseServiceTest` (Unit): Paar in Folgemonaten → DETECTED +
  Notification; Jan/März → nichts; ausserhalb ±2 % → nichts; DISMISSED übersprungen;
  bestehendes DETECTED → keine zweite Zeile/Notification; `payee_key` gross
- Neu `recurring/RecurringExpenseDetectionIntegrationTest` (Postgres): Fixture
  `Post_Kontoauszug_2025_240_Buchungen.pdf` durch Parser → persistiert → `detect()` → die
  bekannten Abos in `recurring_expenses`; Mandantentrennung: User B sieht nichts
- Neu `recurring/RecurringExpenseCleanupServiceTest` (Unit)
- Neu `transaction/ExpenseHistoryServiceTest` (Unit): Normalisierung
- Geändert `ImportJobRunnerTest` / `ImportJobRunnerTimingTest`: Konstruktor; Erkennung nach
  `saveAll`; Exception aus `detect()` lässt Job `SUCCESS`
- Geändert `UserServiceTest` (InOrder), `UserDeletionIntegrationTest` (Zeilenzahl 0, AC 6)

## Implementierungsschritte

1. Plan ablegen, Branch erstellen
2. `ExpenseHistoryPort` + Service + Repository-Query im transaction-Modul
3. `recurring/`-Modul: Entity, Status, Repository, Detection-Port, Service
4. Cleanup-Port + Service, `UserService.deleteUser` erweitern
5. `ImportJobRunner` anbinden
6. Tests je Ebene, `mvn package`
7. Doku, Security-Review, lokaler Review, PR

## Acceptance Criteria (aus dem Issue)

- [ ] Gruppierung nach `payee_key` + Betrag mit ±2% Toleranz über mindestens 2 aufeinanderfolgende Monate
- [ ] Aufruf am Ende von `ImportJobRunner`, nach der Kategorisierung
- [ ] Neu erkannte Gruppe: Eintrag mit `status=DETECTED` in `recurring_expenses`
- [ ] Neu erkannte Gruppe löst `NotificationService.create(..., RECURRING_EXPENSE_DETECTED)` aus
- [ ] Bereits als `DISMISSED` markierte `payee_key` werden von künftiger Erkennung ausgeschlossen
- [ ] `UserService.deleteUser` räumt `recurring_expenses` mit ab — über einen
      `RecurringExpenseCleanupPort`, mit Nachweis in `UserDeletionIntegrationTest`
