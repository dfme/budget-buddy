# [BE-NOTIF-01] NotificationService + REST-Endpoints

- **Issue:** [#246](https://github.com/dfme/budget-buddy/issues/246)
- **Task-ID:** `BE-NOTIF-01`
- **Branch:** `feature/BE-NOTIF-01-notification-service`
- **Story:** US-08 — Wiederkehrende Ausgaben (Abos) erkennen (Vorlauf-Task)
- **Sprint:** Sprint 6
- **Bestätigt am:** 2026-09-07

## Ausgangslage

Backend-Baustein des Notification-Fundaments (siehe
[docs/plans/us-08-09-12-breakdown.md](us-08-09-12-breakdown.md)): ein einfacher Port, den andere
Module zum Erzeugen von Benachrichtigungen aufrufen, plus die REST-Endpoints fürs Frontend. Die
Tabelle `notifications` liegt bereits auf `main` (`DB-08`, Flyway V10, #245/PR #272). Kein
konkreter Aufrufer existiert noch — die Abo-Erkennung (`BE-REC-01`, `RECURRING_EXPENSE_DETECTED`)
ist ein eigenes, nicht eingeplantes Folge-Issue.

## Entscheide

| Punkt | Entscheid | Begründung |
| --- | --- | --- |
| `type`-Parameter | `String`, kein Java-Enum | Kein konkreter Aufrufer existiert in diesem Issue; ein Enum jetzt hiesse, Werte für einen Consumer zu raten, der noch nicht gebaut ist (YAGNI, CLAUDE.md). Die Migration nutzt bewusst `TEXT` statt DB-Enum aus demselben Grund. |
| `GET /api/notifications` | Plain `List<NotificationResponse>`, unpaginiert | Analog `TransactionDirectionController.listUncertain` — eine kleine Inbox-Liste, keine Historie zum Blättern (US-08: „Lazy-Load beim Login, kein Polling"). |
| Sortierung „ungelesen zuerst" | Explizites JPQL mit `CASE WHEN read_at IS NULL THEN 0 ELSE 1 END`, dann `created_at DESC` | Portabler als eine abgeleitete Query-Methode, die sich auf DB-spezifisches NULL-Ordering verlassen müsste. |
| `POST /notifications/{id}/read` | 200 mit aktualisierter Ressource, idempotent (ursprünglicher `read_at`-Zeitpunkt bleibt bei Wiederholung erhalten) | Analog `PUT /transactions/{id}/direction` (Zustandsübergang, der die aktualisierte Ressource zurückgibt) und `completeOnboarding` (Idempotenz). |
| Erzeuger-Port | `NotificationPort.create(userId, type, referenceId, message)`, implementiert von `NotificationService` | Gleiche Bauart wie `UserIncomePort`/`CategorizationPort` — das Interface steht im liefernden Modul. |
| Cleanup-Port | Eigene `NotificationCleanupService` (nicht Teil von `NotificationService`) | Gleiche Aufteilung wie `FixedCostCleanupPort`/`Service` und `TransactionCleanupPort`/`Service`. |
| Validierung in `create()` | Einfache Guard Clauses (`IllegalArgumentException`), keine `InvalidXException`+Handler-Kette | `create()` wird nur modulintern von Code aufgerufen, nie direkt aus einem Request-Body — die 400-mit-Feldname-Konvention gilt für HTTP-Eingaben. |

## Betroffene Dateien

### Neu — `backend/src/main/java/com/budgetbuddy/notification/`

- `Notification.java` — JPA-Entity für `notifications` (V10)
- `NotificationRepository.java` — `findByUserIdOrderByUnreadFirstThenNewest`, `findByIdAndUserId`, `deleteAllByUserId`
- `NotificationPort.java` — Erzeuger-Port für andere Module
- `NotificationService.java` — implementiert `NotificationPort`; zusätzlich `list`/`markAsRead`
- `NotificationController.java` — `GET /api/notifications`, `POST /api/notifications/{id}/read`
- `NotificationNotFoundException.java`, `NotificationExceptionHandler.java`
- `NotificationCleanupPort.java`, `NotificationCleanupService.java`
- `dto/NotificationResponse.java`
- `package-info.java`

### Neu — Tests

- `NotificationServiceTest.java` (unit)
- `NotificationRepositoryIntegrationTest.java` (Postgres)
- `NotificationControllerIntegrationTest.java` (Postgres + MockMvc)
- `NotificationOpenApiTest.java`
- `NotificationCleanupServiceTest.java` (unit)

### Geändert

- `backend/src/main/java/com/budgetbuddy/auth/UserService.java` — `NotificationCleanupPort` einbinden, in `deleteUser` aufrufen
- `backend/src/test/java/com/budgetbuddy/auth/UserServiceTest.java` — `InOrder`-Verifikation um den neuen Port erweitern
- `backend/src/test/java/com/budgetbuddy/auth/UserDeletionIntegrationTest.java` — `notifications`-Zeile seeden und nach Löschung auf 0 prüfen (AC6, Review-Befund aus PR #272)

## Implementierungsschritte

1. Entity, Repository, DTO für `notifications` (Spalten-Mapping identisch zu V10)
2. `NotificationPort` + `NotificationService.create` (Guard Clauses, `Clock`-injizierte `createdAt`)
3. `NotificationService.list`/`markAsRead`, `NotificationController`, `NotificationExceptionHandler`
4. `NotificationCleanupPort` + `NotificationCleanupService`
5. `UserService.deleteUser` um den neuen Cleanup-Port erweitern
6. Tests je Ebene (unit, Repository-Integration, Controller-Integration, OpenAPI, Cleanup, UserDeletion-Integration)

## Test-Strategie

- Unit (`NotificationServiceTest`): Guard Clauses, Sortier-Mapping auf DTO, `markAsRead`-Idempotenz, 404 bei fremder/unbekannter ID
- Integration (`NotificationRepositoryIntegrationTest`): Spalten-Mapping, Sortierung ungelesen-zuerst, Mandantentrennung-Gegenprobe
- Integration (`NotificationControllerIntegrationTest`): Statuscodes, Mandantentrennung (fremde ID → 404), 401 ohne JWT, Wire-Format
- `NotificationOpenApiTest`: beide Endpoints im generierten OpenAPI-Dokument mit Summary + Schema
- `NotificationCleanupServiceTest`: `deleteAllForUser` ruft `deleteAllByUserId`
- `UserDeletionIntegrationTest`: `notifications`-Zeile wird bei Kontolöschung mitgelöscht

## Acceptance Criteria (aus #246)

- [ ] `NotificationService.create(userId, type, referenceId, message)` als aufrufbarer Service für andere Module
- [ ] `GET /api/notifications` liefert Benachrichtigungen des eingeloggten Nutzers, ungelesene zuerst
- [ ] `POST /api/notifications/{id}/read` markiert eine Benachrichtigung als gelesen
- [ ] Ein Nutzer kann nur eigene Benachrichtigungen lesen/als gelesen markieren (Mandantentrennung)
- [ ] Endpoints in Swagger UI sichtbar
- [ ] Kontolöschung (US-02, nDSG) räumt `notifications` mit ab
