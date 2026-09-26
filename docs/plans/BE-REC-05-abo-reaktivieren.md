# [BE-REC-05] Verneintes Abo aus «Kein Abo» wieder aktivieren können

- **Issue:** [#368](https://github.com/dfme/budget-buddy/issues/368)
- **Task-ID:** `BE-REC-05`
- **Branch:** `feature/BE-REC-05-abo-reaktivieren`
- **Story:** US-08 — Wiederkehrende Ausgaben (Abos) erkennen
- **Sprint:** — (im Board ohne Sprint-Zuordnung geplant)
- **Bestätigt am:** 2026-09-26

## Ausgangslage

Wird eine automatisch erkannte Abo-Zeile per «Kein Abo» verneint, landet sie dauerhaft im
Abschnitt «Kein Abo» (`RecurringExpenseStatus.DISMISSED`) — ein Rückweg fehlt. Es gibt weder
einen Reaktivieren-Endpoint noch eine Aktion in der Liste. Klickt eine Person versehentlich
«Kein Abo», sitzt sie fest — auch ein erneuter PDF-Import bringt den Empfänger nie zurück nach
`DETECTED`, weil `RecurringExpenseService.detect()` bereits bekannte Empfänger («in beiden
Status») überspringt.

## Entscheide

- Reaktivieren ist eine reine Nutzeraktion, keine neue Erkennung: die Antwort trägt `isNew=false`,
  unabhängig davon, ob das ursprüngliche Bündel noch andere offene Mitglieder hat — analog zur
  Begründung bei `dismiss()`.
- `RecurringExpense.reactivate()` wirkt nur auf `DISMISSED` (Guard wie bei `markActive()`/
  `markEnded()`); ein `ENDED`-Eintrag bleibt unangetastet — dafür ist `markActive()` zuständig,
  das läuft ausschliesslich aus `detect()`.

## Betroffene Dateien

**Backend:**
- `backend/src/main/java/com/budgetbuddy/recurring/RecurringExpense.java` — neue Methode `reactivate()`
- `backend/src/main/java/com/budgetbuddy/recurring/RecurringExpenseService.java` — neue Methode `reactivate(userId, id)`
- `backend/src/main/java/com/budgetbuddy/recurring/RecurringExpenseController.java` — neuer Endpoint `POST /{id}/reactivate`
- `backend/src/test/java/com/budgetbuddy/recurring/RecurringExpenseServiceTest.java`
- `backend/src/test/java/com/budgetbuddy/recurring/RecurringExpenseControllerIntegrationTest.java`
- `backend/src/test/java/com/budgetbuddy/recurring/RecurringExpenseOpenApiTest.java`

**Frontend:**
- `frontend/src/app/recurring/recurring-expense.service.ts` — neue Methode `reactivate(id)`
- `frontend/src/app/recurring/recurring-expense-list.ts` — `reactivatingId`/`reactivateErrorMessage` Signals, `reactivate(expense)`
- `frontend/src/app/recurring/recurring-expense-list.html` — Reaktivieren-Button im Abschnitt «Kein Abo»
- `frontend/src/app/recurring/recurring-expense-list.scss`
- `frontend/src/app/recurring/recurring-expense.service.spec.ts`
- `frontend/src/app/recurring/recurring-expense-list.spec.ts`

**E2E:**
- `e2e/tests/recurring-expenses.spec.ts` — neuer Test: Dismiss → Reactivate

## Implementierungsschritte

1. `RecurringExpense.reactivate()`: `DISMISSED → DETECTED`, sonst No-Op — idempotent.
2. `RecurringExpenseService.reactivate(userId, id)`: `findByIdAndUserId` (gleiche Mandantenprüfung
   wie `dismiss`), `RecurringExpenseNotFoundException` bei Fehlen/fremdem User,
   `toResponse(expense, false)`.
3. `RecurringExpenseController`: `POST /api/recurring-expenses/{id}/reactivate`, Swagger-Doku
   analog `dismiss` (200/401/404).
4. Frontend-Service: `reactivate(id)` postet und ersetzt den State-Eintrag wie `dismiss`.
5. Frontend-Component/Template: Button (Undo-Icon) pro Zeile im Abschnitt «Kein Abo», gesperrt
   während eines laufenden Requests, mit `aria-label`/`title`; veralteten Kommentar («ein
   Rückgängig gibt es (noch) nicht») entfernen.

## Test-Strategie

- **Unit** (`RecurringExpenseServiceTest`): Statuswechsel, Idempotenz, No-Op auf `ENDED`, 404 bei
  fremdem/fehlendem Eintrag.
- **Integration** (`RecurringExpenseControllerIntegrationTest`): 200 + Statuswechsel, Idempotenz,
  Mandantentrennung (404 für fremden User), 404 unbekannte ID, 401 ohne JWT, Eintrag erscheint
  nach Reaktivierung wieder in der Hauptliste.
- **OpenAPI**: neuer Pfad in `RecurringExpenseOpenApiTest` sichtbar mit Summary + Response-Schema.
- **Frontend Unit** (Vitest): Service-Test für Erfolg/Fehlerfall, Component-Test für
  Button-Rendering, Klick-Verhalten, Ladezustand, Verschieben zwischen Abschnitten.
- **E2E** (Playwright): voller Zyklus Dismiss → Reactivate.

## Acceptance Criteria (aus Issue)

- [ ] Neuer Endpoint (`POST /api/recurring-expenses/{id}/reactivate`) setzt eine
      `DISMISSED`-Zeile zurück auf `DETECTED`, mit derselben Mandantenprüfung wie `dismiss()`
- [ ] Endpoint ist idempotent, analog zu `dismiss()`
- [ ] Der Abschnitt «Kein Abo» im Frontend bietet pro Zeile eine Aktion zum Reaktivieren
- [ ] Reaktivierte Zeile erscheint wieder in der Abo-Liste und verschwindet aus «Kein Abo»
- [ ] Test deckt: Reaktivieren einer `DISMISSED`-Zeile
- [ ] Test deckt: Reaktivieren respektiert Mandantentrennung (fremder User erhält 404/403)
