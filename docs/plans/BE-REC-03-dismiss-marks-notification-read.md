# [BE-REC-03] Dismiss einer Abo-Erkennung markiert die zugehörige Benachrichtigung als gelesen

- **Issue:** [#324](https://github.com/dfme/budget-buddy/issues/324)
- **Task-ID:** `BE-REC-03`
- **Branch:** `fix/BE-REC-03-dismiss-marks-notification-read`
- **Story:** US-08 — Abo-Erkennung
- **Sprint:** Sprint 7
- **Bestätigt am:** 2026-09-18

## Befund

`RecurringExpenseService.dismiss()` setzt nur den Status auf `DISMISSED` und *liest* den
Gelesen-Zustand der zugehörigen `RECURRING_EXPENSE_DETECTED`-Benachrichtigung per
`NotificationPort.isUnread(...)` — schreibt ihn aber nie. Die Glocke zeigt die Benachrichtigung
danach weiter als ungelesen; ein Klick führt nach `/abos`, wo der Eintrag nicht mehr auftaucht.

Das Notification-Modul hat keinen Weg, per `referenceId` als gelesen zu markieren:
`NotificationService.markAsRead` arbeitet mit der Notification-ID, die das Recurring-Modul nicht
kennt. Recurring darf nur über `NotificationPort` gehen (CONVENTIONS) — die neue Fähigkeit gehört
an den Port.

## Entscheide

- **Neue Port-Methode `markReadByReference(userId, type, referenceId)`** statt eines
  Rückgriffs auf `markAsRead(notificationId)`: das aufrufende Modul kennt nur seine eigene Row-ID.
- **`isUnread` wird entfernt** (Option a). Sobald `dismiss()` die Benachrichtigung als gelesen
  markiert, ist `isUnread` danach strukturell immer `false`. Die Methode existierte laut eigener
  Javadoc nur für `dismiss`; eine Query, deren Antwort vor dem Aufruf feststeht, ist irreführend.
  Mit ihr fallen `NotificationRepository.existsByUserIdAndTypeAndReferenceIdAndReadAtIsNull`
  und die beiden zugehörigen Unit-Tests. `dismiss()` liefert `isNew=false` fix.
  Scope-Erweiterung wird im PR-Body deklariert.
- **Kein Frontend-Anpassungsbedarf:** die Abo-Liste ignoriert den Response-Body des Dismiss
  (`recurring-expense-list.ts`), die Glocke lädt bei jeder Navigation neu
  (`notification-bell.ts`, `NavigationEnd`).
- **Eine Transaktion:** Status-Wechsel und Gelesen-Marke stehen zusammen in der DB oder gar
  nicht — dieselbe Klammer wie bei `detect()` (Zeile + Notification).

## Betroffene Dateien

Ändern:
- `backend/src/main/java/com/budgetbuddy/notification/NotificationPort.java`
- `backend/src/main/java/com/budgetbuddy/notification/NotificationRepository.java`
- `backend/src/main/java/com/budgetbuddy/notification/NotificationService.java`
- `backend/src/main/java/com/budgetbuddy/recurring/RecurringExpenseService.java`
- `backend/src/test/java/com/budgetbuddy/notification/NotificationServiceTest.java`
- `backend/src/test/java/com/budgetbuddy/notification/NotificationRepositoryIntegrationTest.java`
- `backend/src/test/java/com/budgetbuddy/recurring/RecurringExpenseServiceTest.java`
- `backend/src/test/java/com/budgetbuddy/recurring/RecurringExpenseControllerIntegrationTest.java`

Neu: keine.

## Implementierungsschritte

1. `NotificationPort`: `markReadByReference(long userId, String type, long referenceId)` —
   markiert alle ungelesenen Benachrichtigungen des Users zu Typ + Referenz als gelesen; ohne
   Treffer No-op, wirft nie. `isUnread` entfernen.
2. `NotificationRepository`: abgeleitete Query
   `findByUserIdAndTypeAndReferenceIdAndReadAtIsNull(Long, String, Long)`; `existsBy…` entfernen.
3. `NotificationService`: Implementierung — laden, je Zeile `markRead(clock.instant())`
   (idempotent über die Entity, gleicher Pfad wie `markAsRead`).
4. `RecurringExpenseService.dismiss()`: nach `expense.dismiss()` den neuen Port-Aufruf;
   `toResponse(expense, false)`; Javadoc nachziehen.

## Test-Strategie

- `NotificationServiceTest` (Unit): `markReadByReference` setzt `readAt` aus der Clock auf jeder
  Treffer-Zeile; No-op bei leerer Liste; `isUnread`-Tests entfernt.
- `NotificationRepositoryIntegrationTest` (Postgres): die neue Query liefert keine fremde Zeile
  mit gleicher `referenceId` — `referenceId` ist eine globale Row-ID, der Filter muss über
  `userId` greifen.
- `RecurringExpenseServiceTest` (Unit): `dismiss` ruft `markReadByReference(USER_ID, TYPE, id)`
  und liefert `isNew=false`; bestehender `isNew=true`-Test ersetzt.
- `RecurringExpenseControllerIntegrationTest` (Postgres): nach `POST …/dismiss` ist `read_at`
  der zugehörigen Notification gesetzt (AC1), die Notification eines anderen Eintrags bleibt
  ungelesen, Dismiss ohne Notification antwortet 200 (AC3). Bestehender `isNew`-IT-Test ersetzt.

## Acceptance Criteria (aus dem Issue)

- [ ] Nach `POST /api/recurring-expenses/{id}/dismiss` ist die zugehörige
      `RECURRING_EXPENSE_DETECTED`-Benachrichtigung als gelesen markiert
- [ ] Test deckt: Dismiss markiert die Benachrichtigung als gelesen
- [ ] Test deckt: Dismiss eines Eintrags ohne zugehörige Benachrichtigung wirft nicht
