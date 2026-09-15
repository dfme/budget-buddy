# [BE-REC-02] REST-Endpoints Abo-Übersicht

- **Issue:** [#254](https://github.com/dfme/budget-buddy/issues/254)
- **Task-ID:** `BE-REC-02`
- **Branch:** `feature/BE-REC-02-abo-uebersicht-endpoints`
- **Story:** US-08 — Wiederkehrende Ausgaben (Abos) erkennen
- **Sprint:** Sprint 6
- **Bestätigt am:** 2026-09-13

## Kontext

Vierter und letzter Backend-Task der US-08-Kette aus [us-08-09-12-breakdown.md](us-08-09-12-breakdown.md),
nach `DB-09` (#252), `BE-NOTIF-01` (#246) und `BE-REC-01` (#253, Erkennung + Notification beim
Import). Dieser Task legt nur die REST-Schnittstelle für die Abo-Übersicht im Frontend frei —
Auflisten und «Kein Abo» markieren. Die Erkennung selbst (`RecurringExpenseService.detect`) und
das automatische Ausschliessen von `DISMISSED`-Empfängern sind bereits mit BE-REC-01 gebaut, nicht
Teil dieses Tasks.

## Entscheide

Mit dem User bestätigt:

- **«Neu»-Flag aus der Notification abgeleitet**, kein neues Feld auf `RecurringExpense`. Die
  Erkennung (BE-REC-01) legt zu jeder neuen Zeile bereits eine `Notification`
  (`type=RECURRING_EXPENSE_DETECTED`, `referenceId=recurring_expense.id`) an; deren `readAt`
  trägt den Gelesen-Zustand bereits. Ein neuer schlanker Port `NotificationPort.unreadReferenceIds`
  liefert die Menge der `referenceId`s mit ungelesener Notification eines Typs — `recurring` ruft
  ihn einmal pro `GET` auf, kein N+1. `dismiss` braucht das Flag nur für eine Zeile und fragt
  gezielt über `NotificationPort.isUnread` (Review-Befund, `EXISTS` statt Liste).

  Verworfen: eigenes `seen_at`-Feld direkt auf `recurring_expenses` (neue Migration). Hätte den
  Gelesen-Zustand dupliziert, den `notification` bereits besitzt, und eine eigene Definition von
  «gesehen» gebraucht (z. B. beim ersten `GET` setzen) — mehr Fläche für denselben Zweck.

- **`GET` liefert nur `status=DETECTED`.** US-08 AC3 verlangt, dass ein per «Kein Abo» markierter
  Eintrag aus der Abo-Übersicht verschwindet — nicht nur, dass künftige Erkennung ihn ausschliesst
  (das leistet BE-REC-01 bereits). Neue Repository-Query `findByUserIdAndStatusOrderByPayeeKeyAsc`
  — alphabetisch nach Empfänger, damit die Übersicht zwischen zwei Aufrufen nicht springt
  (Review-Befund).

- **`dismiss` ist idempotent**, analog `Notification.markRead`: ein zweiter Aufruf auf einen
  bereits `DISMISSED`-Eintrag ist kein Fehler, sondern liefert den aktuellen Zustand.

- **`firstDetectedMonth` als `String` (`YYYY-MM`) in der DTO**, nicht als `YearMonth`. Kein
  bestehendes Response-DTO im Projekt gibt `YearMonth` direkt aus, und die Entity hält den Wert
  ohnehin als Text — Serialisierung ohne Abhängigkeit von der Jackson-JSR-310-Modulregistrierung.

## Betroffene / neue Files

### Neu — `backend/src/main/java/com/budgetbuddy/recurring/`

- `dto/RecurringExpenseResponse.java` — Record: `id, payeeKey, amount, status,
  firstDetectedMonth, createdAt, isNew`
- `RecurringExpenseController.java` — `GET /api/recurring-expenses`,
  `POST /api/recurring-expenses/{id}/dismiss`
- `RecurringExpenseNotFoundException.java` — analog `NotificationNotFoundException`
- `RecurringExpenseExceptionHandler.java` — `@RestControllerAdvice(assignableTypes =
  RecurringExpenseController.class)`, 404 ohne Body

### Geändert

- `recurring/RecurringExpense.java` — `dismiss()`-Methode (idempotent, setzt `status=DISMISSED`)
- `recurring/RecurringExpenseRepository.java` — `findByIdAndUserId`,
  `findByUserIdAndStatusOrderByPayeeKeyAsc`
- `recurring/RecurringExpenseService.java` — `list(long userId)`, `dismiss(long userId, long id)`
- `notification/NotificationPort.java` — `Set<Long> unreadReferenceIds(long userId, String type)`,
  `boolean isUnread(long userId, String type, long referenceId)`
- `notification/NotificationRepository.java` — `findByUserIdAndTypeAndReadAtIsNull`,
  `existsByUserIdAndTypeAndReferenceIdAndReadAtIsNull`
- `notification/NotificationService.java` — implementiert die neue Port-Methode
- `docs/plans/README.md` — neue Zeile

### Tests

- Neu `recurring/RecurringExpenseServiceTest` (Unit): `list` filtert `DISMISSED` heraus, mappt
  `isNew` korrekt (mit/ohne ungelesene Notification); `dismiss` setzt Status, ist idempotent bei
  zweitem Aufruf, wirft bei fremder/unbekannter ID
- Neu `recurring/RecurringExpenseControllerIntegrationTest` (Postgres, Testcontainers, analog
  `NotificationControllerIntegrationTest`): Happy Path GET (inkl. `isNew`), Happy Path dismiss,
  Mandantentrennung (User B bekommt 404 auf User As Eintrag / sieht ihn nicht in der eigenen
  Liste), 401 ohne JWT auf beiden Endpoints, JSON-Feld-Assertions
- Neu `recurring/RecurringExpenseOpenApiTest` (analog `NotificationOpenApiTest`): beide Pfade in
  `/v3/api-docs` mit Summary und Response-Schema
- Geändert `notification/NotificationServiceTest` — Unit-Test für `unreadReferenceIds`

## Implementierungsschritte

1. `NotificationPort`/`NotificationRepository`/`NotificationService`: neue Methode
   `unreadReferenceIds`
2. `RecurringExpense`: `dismiss()`-Methode; `RecurringExpenseRepository`: neue Queries
3. `RecurringExpenseService`: `list`, `dismiss`; DTO `RecurringExpenseResponse`
4. `RecurringExpenseController` + Exception + Handler
5. Tests je Ebene (Unit, Controller-Integration, OpenAPI), `mvn package`
6. Security-Review, lokaler Review, PR

## Acceptance Criteria (aus dem Issue)

- [ ] `GET /api/recurring-expenses` liefert erkannte wiederkehrende Ausgaben des eingeloggten
      Nutzers inkl. «Neu»-Flag
- [ ] `POST /api/recurring-expenses/{id}/dismiss` markiert einen Eintrag als «Kein Abo»
      (`status=DISMISSED`)
- [ ] Nach «Kein Abo» erkennt `BE-REC-01` den zugehörigen `payee_key` künftig nicht mehr
      automatisch (bereits durch BE-REC-01 erfüllt, hier nur per Inspektion bestätigt)
- [ ] Ein Nutzer kann nur eigene Einträge lesen/dismissen (Mandantentrennung)
- [ ] Endpoints in Swagger UI sichtbar
