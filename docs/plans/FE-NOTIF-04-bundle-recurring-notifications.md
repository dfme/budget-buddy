# [FE-NOTIF-04] Viele Abo-Benachrichtigungen auf einmal: Kenntnisnahme nur per Einzelklick

- **Issue:** [#336](https://github.com/dfme/budget-buddy/issues/336)
- **Task-ID:** `FE-NOTIF-04`
- **Branch:** `feature/FE-NOTIF-04-bundle-recurring-notifications`
- **Story:** US-08 — Wiederkehrende Ausgaben (Abos) erkennen
- **Sprint:** Sprint 7
- **Bestätigt am:** 2026-09-20

## Produktentscheid

Das Issue liess den Lösungsweg offen. Gewählt wurde **Bündeln bei der Erzeugung** plus
**«Alle als gelesen» in der Glocke** — das Bündeln löst den Fall «ein Import, 27
Benachrichtigungen», der Button den Fall «drei Importe, drei Bündel».

## Entscheide

1. **Eine Notification pro Erkennungslauf** (= pro Import), nicht pro Zeile. Ein Lauf ohne
   Treffer erzeugt keine Notification.
2. **Neue Quelle für `isNew`:** Der Verweis dreht sich um. Statt
   `notifications.reference_id → recurring_expenses.id` (1:1) bekommt `recurring_expenses` eine
   FK-lose Spalte `notification_id` (N:1). `isNew` = «die Bündel-Notification dieser Zeile ist
   ungelesen». Der Gelesen-Zustand bleibt einzige Wahrheit im Notification-Modul.
3. **Bestandsdaten:** V14 backfillt `notification_id` aus den vorhandenen
   `reference_id`-Verweisen — alte Einzel-Notifications werden «Bündel der Grösse 1».
4. **`dismiss` und das Bündel:** BE-REC-03 bleibt sinngemäss — die Bündel-Notification wird
   gelesen, sobald keine `DETECTED`-Zeile des Bündels mehr übrig ist.
5. **Message:** `«3 neue Abos erkannt: A, B, C»` · `«1 neues Abo erkannt: A»` · ab 4:
   `«7 neue Abos erkannt: A, B, C und 4 weitere»`.
6. **«Alle als gelesen»:** `POST /api/notifications/read-all` markiert alle ungelesenen
   Notifications des Users, liefert die aktualisierte Liste, idempotent. Im Bell-Dropdown ein
   Button über der Liste, nur bei `unreadCount() > 0`; Dropdown bleibt offen; Fehler still wie
   bei `select`. Typ-unabhängig. Nimmt als Nebeneffekt alle «Neu»-Labels — dokumentiert in
   US-08 AC2.
7. **Einzelklick** (`select`) bleibt unverändert.

## Betroffene Dateien

**Neu**
- `backend/src/main/resources/db/migration/V14__add_notification_id_to_recurring_expenses.sql`
- `e2e/fixtures/pdf/kontoauszug-abo-buendel.pdf`

**Backend**
- `notification/NotificationPort.java` — `create` gibt ID zurück; `unreadReferenceIds` →
  `unreadIds(userId, type)`; `markReadByReference` → `markRead(userId, notificationId)`
- `notification/NotificationService.java` — Umsetzung + `markAllAsRead(userId)`
- `notification/NotificationRepository.java` — `findByUserIdAndReadAtIsNull`
- `notification/NotificationController.java` — `POST /read-all`
- `recurring/RecurringExpense.java` — Feld `notificationId`
- `recurring/RecurringExpenseRepository.java` — `findByUserIdAndNotificationId`
- `recurring/RecurringExpenseService.java` — `detect`/`list`/`dismiss` + Message-Builder
- `recurring/package-info.java`

**Frontend**
- `notifications/notification.service.ts` — `markAllAsRead()`
- `notifications/notification-bell.ts/.html/.scss` — Button
- `notifications/notification.model.ts` — Doku

**Doku**
- `docs/requirements/US-08-wiederkehrende-ausgaben.md` — AC2 präzisieren
- `docs/plans/README.md`

## Implementierungsschritte

1. Plan ablegen, Branch erstellen
2. V14 Migration (Spalte + Backfill)
3. Notification-Modul: Port/Service/Repository umbauen, `read-all`-Endpoint
4. `RecurringExpense` + Repository erweitern
5. `RecurringExpenseService.detect/list/dismiss` umbauen
6. Backend-Tests anpassen/erweitern
7. Frontend: Service + Bell-Button, Vitest
8. PDF-Fixture bauen, Playwright ergänzen
9. US-08 AC2, FE-Kommentare
10. `mvn verify`, `ng test`, `ng build`, Playwright lokal

## Test-Strategie

| Ebene | Was |
|---|---|
| JUnit unit `NotificationServiceTest` | `create` liefert ID; `unreadIds`; `markRead` idempotent/No-op fremd; `markAllAsRead` setzt nur ungelesene |
| JUnit integration `NotificationControllerIntegrationTest` | `POST /read-all`: 200 mit Liste; Mandantentrennung; 401 |
| `NotificationOpenApiTest` | neuer Endpoint in der Spec |
| JUnit unit `RecurringExpenseServiceTest` | N Treffer → eine Notification; 0 → keine; Message 1/3/7; `list` isNew über Bündel; `dismiss` letzter → `markRead` |
| JUnit integration `RecurringExpenseDetectionIntegrationTest` | genau 1 Notification; `markAsRead` → alle `isNew=false`; Backfill |
| Vitest | Button nur bei ungelesenen; Klick → `POST /read-all`, Badge 0 |
| Playwright `recurring-expenses.spec.ts` | Import mit 2 Abos → Badge 1 → Klick → Badge weg; zwei Importe → «Alle als gelesen» → 0 |

## Acceptance Criteria (aus dem Issue)

- [ ] Nach einem Import mit N erkannten Abos lässt sich das Badge mit höchstens einer Aktion auf
      0 bringen, ohne N-mal zu navigieren
- [ ] Der Einzelklick auf eine Benachrichtigung funktioniert unverändert (FE-NOTIF-01, US-08 AC2)
- [ ] Das Verhalten des «Neu»-Labels auf `/abos` ist für den gewählten Weg dokumentiert
      (US-08 AC2 ggf. präzisieren)
- [ ] Test deckt den gewählten Weg ab (JUnit/Vitest/Playwright, je nach Lösung)
