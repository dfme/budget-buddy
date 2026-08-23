# [BE-AUTH-09] Passwort-Änderung-Endpoint

- **Issue:** [#176](https://github.com/dfme/budget-buddy/issues/176)
- **Task-ID:** `BE-AUTH-09`
- **Branch:** `feature/BE-AUTH-09-password-change`
- **Story:** US-14 — Passwort, Einkommen und Erscheinungsbild ändern
- **Sprint:** Sprint 5
- **Bestätigt am:** 2026-08-23

## Beschreibung

Endpoint zum Ändern des eigenen Passworts: `PUT /users/me/password`. Der Request trägt das
aktuelle und das neue Passwort; nur wer das aktuelle kennt, darf es ersetzen. Grundlage für das
Formular in FE-SET-02. Gehört zu `UserController` (`/users/me`), wo bereits `GET /users/me`,
`PUT /users/me/income` und `POST /users/me/onboarding-complete` liegen. Die Prüfung des aktuellen
Passworts läuft über denselben `PasswordEncoder` wie der Login (BE-AUTH-01) — bcrypt, nie
Klartextvergleich.

## Betroffene Files

Neu:
- `backend/src/main/java/com/budgetbuddy/auth/dto/ChangePasswordRequest.java`
- `backend/src/main/java/com/budgetbuddy/auth/dto/PasswordErrorResponse.java`
- `backend/src/main/java/com/budgetbuddy/auth/InvalidCurrentPasswordException.java`

Geändert:
- `backend/src/main/java/com/budgetbuddy/auth/User.java`
- `backend/src/main/java/com/budgetbuddy/auth/UserService.java`
- `backend/src/main/java/com/budgetbuddy/auth/UserController.java`
- `backend/src/main/java/com/budgetbuddy/auth/UserExceptionHandler.java`
- `backend/src/test/java/com/budgetbuddy/auth/UserServiceTest.java`
- `backend/src/test/java/com/budgetbuddy/auth/UserControllerTest.java`

## Implementierungsschritte

1. `ChangePasswordRequest` (`aktuellesPasswort` `@NotBlank`, `neuesPasswort` `@NotBlank @Size(min = 8)`
   mit derselben Meldung wie `RegisterRequest`) und `PasswordErrorResponse(String message)`.
2. `InvalidCurrentPasswordException` — Meldung „Aktuelles Passwort falsch".
3. `User.changePasswordHash(String)` als Domänenmethode (analog `completeOnboarding()`, kein
   generischer Setter).
4. `UserService`: `PasswordEncoder` als zusätzliche Konstruktor-Abhängigkeit; `changePassword(userId,
   currentPassword, newPassword)` prüft via `passwordEncoder.matches` gegen den gespeicherten Hash,
   wirft bei Fehlschlag `InvalidCurrentPasswordException`, sonst `passwordEncoder.encode` +
   `changePasswordHash`.
5. `UserController`: `PUT /api/users/me/password`, `@AuthenticationPrincipal Long userId` (nie aus
   dem Body), `@Valid @RequestBody`, void-Rückgabe (200 ohne Body — nichts Sensibles im Body, das
   versehentlich landen könnte), OpenAPI-Annotationen.
6. `UserExceptionHandler`: Handler für `InvalidCurrentPasswordException` → 400 +
   `PasswordErrorResponse`.
7. Tests (siehe Test-Strategie).

Kein `SecurityConfig`-Change nötig — `PUT /api/**` ist bereits durch
`.requestMatchers("/api/**").authenticated()` abgedeckt.

## Test-Strategie

- `UserServiceTest` (Unit): korrektes aktuelles Passwort → Hash über den Encoder aktualisiert;
  falsches aktuelles Passwort → `InvalidCurrentPasswordException`, Hash unverändert; unbekannter
  User → `UserNotFoundException`.
- `UserControllerTest` (Integration, echtes PostgreSQL): User mit echtem bcrypt-Hash eingefügt
  (`PasswordEncoder` autowired); Happy Path → 200, danach `POST /api/auth/login` mit altem
  Passwort → 401, mit neuem → 200 (belegt die AC direkt, nicht nur den Statuscode); falsches
  aktuelles Passwort → 400 + Message-Body, Hash in der DB unverändert; `neuesPasswort` < 8 Zeichen
  → 400; fehlendes JWT-Cookie → 401; weder altes noch neues Passwort erscheint in einer Response.

## Acceptance Criteria (aus dem Issue)

- [ ] `PUT /users/me/password` nimmt `{ aktuellesPasswort, neuesPasswort }` entgegen und antwortet
      bei Erfolg mit `200`
- [ ] Das neue Passwort wird bcrypt-gehasht gespeichert; ein anschliessender Login mit dem alten
      Passwort schlägt fehl, mit dem neuen gelingt er
- [ ] Ist `aktuellesPasswort` falsch, antwortet der Endpoint mit `400` und der Meldung „Aktuelles
      Passwort falsch" — die Änderung findet nicht statt
- [ ] `neuesPasswort` unter 8 Zeichen wird mit `400` abgelehnt (Bean Validation, gleiche
      Mindestlänge wie bei der Registrierung)
- [ ] Ohne gültiges JWT-Cookie antwortet der Endpoint mit `401`; das Passwort eines fremden Users
      ist nicht änderbar (User-ID kommt ausschliesslich aus `@AuthenticationPrincipal`, nie aus dem
      Request-Body)
- [ ] Weder das alte noch das neue Passwort erscheint in einer Response oder im Log
- [ ] OpenAPI-Annotation vorhanden, Endpoint in der Swagger UI sichtbar
