# [BE-FC-03] REST-Endpoints für Fixkosten

- **Issue:** [#12](https://github.com/dfme/budget-buddy/issues/12)
- **Task-ID:** `BE-FC-03`
- **Branch:** `feature/BE-FC-03-fixed-costs-endpoints`
- **Story:** US-03 — Fixkosten erfassen (Onboarding-Wizard)
- **Sprint:** Sprint 4
- **Bestätigt am:** 2026-08-12

## Ausgangslage

#11 (BE-FC-02) ist gemergt; der `FixedCostService` liegt auf `main` und liefert `list`, `get`,
`create`, `update`, `delete`. Dieser Task legt ausschliesslich die HTTP-Schicht darüber.

FE-FC-01 (#24) ist ebenfalls gemergt: `frontend/src/app/onboarding/fixed-cost.service.ts` ruft
bereits `POST /fixed-costs` auf und erwartet `{id, bezeichnung, betrag, intervall}`. Der Contract
ist damit kein Entwurf mehr, sondern hat einen Konsumenten in `main`.

## Contract-Gegenprüfung

Aus dem Kommentar von danielwagner990 an #12 (Review-Erkenntnis aus #146). Beide Punkte treffen zu
und sind durch die DTOs aus #11 bereits erfüllt — dieser Task belegt sie mit Tests, statt sie
anzunehmen:

| Anforderung | Stand |
| ----------- | ----- |
| `intervall` als ASCII-Label (`monatlich`/`quartalsweise`/`jaehrlich`), nicht als Enum-Name | `FixedCostRequest.intervall` und `FixedCostResponse.intervall` sind `String`; die Response wird aus `Intervall.getLabel()` gefüllt, der Request über `Intervall.fromLabel()` geparst. Kein `@JsonValue` nötig. |
| `betrag` als JSON-Number, nicht als String | Typ ist `BigDecimal`; `grep -rn "jackson" backend/src/main/resources/application*.properties` findet nichts — keine Konfiguration, die auf String-Serialisierung umstellt. |

Beides ist ohne Test eine Behauptung: Jackson serialisiert ein Enum per Default als Konstantennamen,
und eine spätere Jackson-Property könnte `BigDecimal` auf String umstellen. Der
Integrationstest nagelt deshalb das Wire-Format fest.

## Entscheide

### Warnung «Fixkosten ≥ Einkommen»: Einzelressource plus Re-Fetch

US-03 verlangt die Warnung «wenn ich speichere **oder** das Dashboard öffne». `create` und `update`
liefern aber `FixedCostResponse` ohne das Flag; nur `list()` liefert `FixedCostSummaryResponse`.

`POST`/`PUT` antworten trotzdem mit der **Einzelressource**, und die Warnung kommt über
`GET /fixed-costs`. Grund ist der bereits gemergte Konsument: `fixed-cost-wizard.ts:166` liest
`created.bezeichnung` aus der POST-Antwort. Eine Summary-Antwort lieferte dort zur Laufzeit
`undefined` — ein Bruch an einer Stelle, die kein Frontend-Test fängt, weil die Spec ihre eigene
Antwort mockt. Dazu käme pro Schreibvorgang eine zusätzliche Listen- und Einkommens-Query.

Das Nachladen gehört damit ins Frontend (#26). Ein Custom-Header (`X-…-Exceed-Income`) wurde
verworfen: Fachlogik in einem Header ist im Projekt nirgends etabliert und in Swagger kaum sichtbar.

### 400 mit Feldname im Body

`InvalidFixedCostException` trägt seit #11 einen Feldnamen, weil US-03 eine **feldspezifische**
Fehlermeldung verlangt. Die Mehrheitskonvention im Projekt ist body-los (`UserExceptionHandler`,
`TransactionExceptionHandler`), aber `PdfImportExceptionHandler` setzt den Präzedenzfall für einen
Body, wenn der Status allein nicht reicht — genau die Lage hier: drei Felder, ein Status.

Ohne Body wäre `getField()` tote Information. Die 400er tragen deshalb
`FixedCostErrorResponse { field, message }`. Der Wizard zeigt heute nur eine generische
400-Meldung (`fixed-cost-wizard.ts:174`) und validiert die drei Felder client-seitig selbst; der
Body ist die Absicherung dahinter und für #26 nutzbar.

404 bleibt body-los — gleiche Begründung wie bei `TransactionNotFoundException`: keine Auskunft
darüber, ob eine fremde ID existiert.

### Statuscodes

| Methode | Pfad | Erfolg | Fehler |
| ------- | ---- | ------ | ------ |
| `GET` | `/fixed-costs` | 200 `FixedCostSummaryResponse` | 401 |
| `GET` | `/fixed-costs/{id}` | 200 `FixedCostResponse` | 401, 404 |
| `POST` | `/fixed-costs` | 201 `FixedCostResponse` | 400, 401 |
| `PUT` | `/fixed-costs/{id}` | 200 `FixedCostResponse` | 400, 401, 404 |
| `DELETE` | `/fixed-costs/{id}` | 204 ohne Body | 401, 404 |
| `POST` | `/users/me/onboarding-complete` | 200 `UserProfileResponse` | 401 |

`GET /fixed-costs` liefert bewusst die Summary und nicht die nackte Liste: Summe und Warn-Flag sind
genau das, was Wizard und Dashboard aus US-03 brauchen, und sie aus derselben Antwort zu lesen hält
Liste und Summe konsistent.

### `onboarding-complete` liegt im auth-Modul

Der Endpoint hängt an `/users/me` und schreibt `users.onboarding_completed` — beides gehört dem
`auth`-Modul. `User` bekommt dafür eine Domänenmethode `completeOnboarding()` statt eines nackten
Setters: das Flag kennt genau einen Übergang (`false → true`), und ein `setOnboardingCompleted(false)`
wäre ein Rückschritt, den die Domäne nicht vorsieht.

### `SecurityConfig` bleibt unangetastet

`/fixed-costs` steht nicht in `SpaForwardController.CLIENT_ROUTE_PATTERNS` und fällt damit unter
`anyRequest().authenticated()` (`SecurityConfig.java:95`). AC 4 ist ohne Freigabe erfüllt. Eine
Freigabe wäre hier das Gegenteil einer Verbesserung — Fixkosten sind Nutzerdaten (Risiko #2).

## Betroffene Files

### Neu

| Datei | Inhalt |
| ----- | ------ |
| `backend/src/main/java/com/budgetbuddy/budget/FixedCostController.java` | Die fünf CRUD-Endpoints, OpenAPI-annotiert |
| `backend/src/main/java/com/budgetbuddy/budget/FixedCostExceptionHandler.java` | `InvalidFixedCostException` → 400 + Body, `FixedCostNotFoundException` → 404 body-los |
| `backend/src/main/java/com/budgetbuddy/budget/dto/FixedCostErrorResponse.java` | `{ field, message }` |

### Geändert

| Datei | Änderung |
| ----- | -------- |
| `backend/src/main/java/com/budgetbuddy/auth/UserController.java` | `POST /users/me/onboarding-complete` |
| `backend/src/main/java/com/budgetbuddy/auth/UserService.java` | `completeOnboarding(long userId)` |
| `backend/src/main/java/com/budgetbuddy/auth/User.java` | `completeOnboarding()` als Domänenmethode |
| `backend/src/main/java/com/budgetbuddy/budget/package-info.java` | Controller erwähnen |

## Implementierungsschritte

1. `FixedCostErrorResponse` anlegen.
2. `FixedCostController` mit den fünf Endpoints; User-ID über `@AuthenticationPrincipal Long userId`
   wie in `UserController` und `TransactionCategoryController`.
3. `FixedCostExceptionHandler` als `@RestControllerAdvice(assignableTypes = FixedCostController.class)`.
4. `User.completeOnboarding()`, `UserService.completeOnboarding(...)`, Endpoint im `UserController`.
5. `package-info.java` nachziehen.

## Test-Strategie

### `FixedCostControllerIntegrationTest` — `@SpringBootTest` + `MockMvc`, Testcontainers PostgreSQL

Muster von `TransactionCategoryControllerIntegrationTest`.

- Happy Path aller sechs Endpoints mit Statuscode-Assertion (201 bei POST, 204 bei DELETE)
- **Wire-Format:** `intervall` ist `"monatlich"` und nicht `"MONATLICH"`; `betrag` ist eine
  JSON-Number und kein String. Das ist der Contract-Punkt aus dem Kommentar an #12.
- **Mandantentrennung:** User B auf einer Position von User A → 404 bei `GET`, `PUT` und `DELETE`,
  und die Position von User A ist danach unverändert
- **401** ohne Cookie auf allen sechs Pfaden (AC 4)
- **400 mit Feldname** je einmal für `bezeichnung`, `betrag` und `intervall`
- `GET /fixed-costs` liefert Summe und `exceedsIncome`

### `UserControllerTest` — Ergänzung

- `POST /users/me/onboarding-complete` setzt `onboardingCompleted` auf `true` und liefert das Profil
- Zweiter Aufruf bleibt idempotent (bereits abgeschlossenes Onboarding ist kein Fehler)

### OpenAPI

Test gegen `/v3/api-docs`, der die sechs Pfade und ihre Request-/Response-Schemata nachweist.
AC 3 verlangt Sichtbarkeit in der Swagger UI — ein Blick ins UI ist kein automatisierter Nachweis,
das generierte Dokument schon.

## Acceptance Criteria (aus #12)

- [ ] CRUD-Endpoints antworten mit korrekten HTTP-Status-Codes
- [ ] POST /users/me/onboarding-complete setzt onboarding_completed=true
- [ ] Alle Endpoints in Swagger UI sichtbar mit Request/Response-Schema
- [ ] Unauthentifizierte Anfragen → 401
