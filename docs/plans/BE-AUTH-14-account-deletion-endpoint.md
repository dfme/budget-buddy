# [BE-AUTH-14] Konto löschen: Endpoint auf /api/users/me

- **Issue:** [#290](https://github.com/dfme/budget-buddy/issues/290)
- **Task-ID:** `BE-AUTH-14`
- **Branch:** `feature/BE-AUTH-14-account-deletion-endpoint`
- **Story:** US-02 — Datenschutz-Consent + Konto löschen (nDSG)
- **Sprint:** Sprint 6
- **Bestätigt am:** 2026-09-12

## Ausgangslage

`UserService.deleteUser` räumt `transactions`, `import_jobs`, `fixed_costs` und `notifications`
und ist getestet (`UserDeletionIntegrationTest`, `UserServiceTest`) — aber kein Endpoint ruft es
auf. Das Recht auf Löschung aus US-02 ist damit technisch nicht ausübbar. Dieser Task liefert die
API; die Aktion in den Einstellungen ist FE-SET-05.

## Entscheide

| Punkt | Entscheid | Begründung |
|---|---|---|
| HTTP-Form | `DELETE /api/users/me` mit JSON-Body `{"passwort": "…"}` | Empfehlung des Issues; passt zu `PUT /users/me/password`. Angular `HttpClient.delete(url, {body})`, MockMvc und Playwright unterstützen DELETE mit Body; zwischen Render und Spring steht kein Body-strippender Proxy. |
| Passwortprüfung | im Service, nicht im Controller | `UserService.deleteUser(long, String)` prüft via `PasswordEncoder.matches` und wirft `InvalidCurrentPasswordException` — gleiche Stelle wie bei `changePassword`; ein Nicht-HTTP-Aufrufer kann die Prüfung nicht umgehen. Die Signatur `deleteUser(long)` verschwindet (kein Produktiv-Caller). |
| Fehler-Mapping | bestehender `UserExceptionHandler` | `InvalidCurrentPasswordException` → 400 mit `AuthErrorResponse`, `@NotBlank` → 400 — beides schon verdrahtet. |
| Antwort | `204 No Content` + `Set-Cookie: jwt=; Max-Age=0` | `JwtCookieFactory.clear()`, wie beim Logout. Nach dem Löschen findet der `JwtCookieAuthenticationFilter` keinen User mehr — jedes alte Token liefert ohnehin 401. |
| Auth | keine Änderung an `SecurityConfig` | `/api/**` ist bereits `authenticated()`. |

## Betroffene Dateien

**Neu**
- `backend/src/main/java/com/budgetbuddy/auth/dto/DeleteAccountRequest.java`

**Ändern**
- `auth/UserController.java` — `@DeleteMapping`, `JwtCookieFactory` injizieren, `@Operation`
  mit «endgültig» und den zwei offenen Lücken
- `auth/UserService.java` — `deleteUser(long userId, String currentPassword)`, Javadoc mit den
  zwei Lücken
- `auth/InvalidCurrentPasswordException.java`, `auth/dto/AuthErrorResponse.java`,
  `auth/UserExceptionHandler.java` — Javadoc um den neuen Endpoint erweitern

## Implementierungsschritte

1. `DeleteAccountRequest` anlegen (`passwort`, `@NotBlank`)
2. `UserService.deleteUser` um die Passwortprüfung erweitern (vor dem ersten Cleanup-Port)
3. `UserController.deleteAccount` mit 204 + Clear-Cookie
4. Javadocs nachziehen
5. Tests, `mvn verify` grün

## Test-Strategie

- **`UserServiceTest`** (Unit): bestehende `deleteUser`-Tests auf die neue Signatur; neu:
  falsches Passwort → `InvalidCurrentPasswordException`, kein Cleanup-Port berührt
- **`UserDeletionIntegrationTest`** (Postgres): geht über den Endpoint (MockMvc, echter
  bcrypt-Hash) — belegt «nach dem Aufruf keine Zeile in `users`, `transactions`, `import_jobs`,
  `fixed_costs`, `notifications`»
- **`UserControllerTest`** (MockMvc + Postgres): 204 + Clear-Cookie; Login mit alten
  Credentials → 401; altes Cookie → 401; falsches Passwort → 400, Zeile bleibt, Passwort nicht in
  der Response; leeres Passwort → 400; ohne JWT → 401
- **`UserOpenApiTest`**: `paths['/api/users/me'].delete` mit `summary`, `description` enthält
  «endgültig», `category_lookup` und `recurring_expenses`; Request-Schema
  `DeleteAccountRequest`, 400 → `AuthErrorResponse`

## Nicht in diesem PR

- Frontend-Aktion (FE-SET-05)
- `category_lookup` überlebt die Löschung (Tabelle ohne `user_id`) — eigenes Issue
- `recurring_expenses` nicht in der Cleanup-Kette — AC an BE-REC-01 (#253)

## Acceptance Criteria (aus dem Issue)

- [ ] Endpoint auf `/api/users/me` löscht das Konto des authentifizierten Users über das
      vorhandene `UserService.deleteUser`
- [ ] Die Löschung verlangt eine Bestätigung durch das aktuelle Passwort; falsches Passwort → 400,
      kein Löschvorgang
- [ ] Ohne gültiges JWT → 401
- [ ] Die Antwort löscht das JWT-Cookie (`JwtCookieFactory.clear()`)
- [ ] Ein erneuter Login mit denselben Zugangsdaten schlägt fehl (US-02, AC 3)
- [ ] Integrationstest belegt: nach dem Aufruf existiert keine Zeile mehr in `users`,
      `transactions`, `import_jobs`, `fixed_costs`, `notifications` für diese User-ID
- [ ] Der Endpoint ist in der Swagger UI sichtbar und dokumentiert, dass die Löschung endgültig ist
- [ ] Javadoc oder Endpoint-Beschreibung benennt die zwei offenen Lücken oben, damit sie nicht als
      erledigt gelten
