# BE-AUTH-02 — GET /users/me und PUT /users/me/income

- **Issue:** #9 ([BE-AUTH-02])
- **Branch:** `feature/BE-AUTH-02-users-me`
- **Basis-Branch:** `feature/BE-AUTH-01-jwt-filter` (nicht `main` — #9 baut auf dem noch nicht
  gemergten #8 auf → Stacked PR; PR-Base = `feature/BE-AUTH-01-jwt-filter`)

## Kontext

`GET /users/me` liefert das Profil (inkl. `onboarding_completed` und `monthly_income`),
`PUT /users/me/income` aktualisiert das monatliche Einkommen. Beide Endpoints erfordern ein
gültiges JWT (httpOnly-Cookie, BE-AUTH-01 / ADR-7). Der `JwtCookieAuthenticationFilter` setzt
die `Long userId` als Principal in den SecurityContext.

Login/Register (das das Cookie erst setzt) ist bewusst **nicht** Teil von #9, sondern als
separates Issue **#46 [BE-AUTH-03]** getrackt. Für #9 wird das JWT in Tests direkt über
`JwtService.generateToken(userId)` erzeugt.

## Entscheidungen

1. **API-JSON in camelCase** (`monthlyIncome`, `onboardingCompleted`) — snake_case im Issue sind
   DB-Spalten; das Angular-Frontend erwartet camelCase. Default-Jackson.
2. **PUT-Request-Feld `betrag`** (`{ "betrag": 1234.56 }`) — direkt nach AC-Wortlaut.
3. **Validierung `betrag > 0`** via `@NotNull` + `@Positive` auf `BigDecimal` (ADR-9, kein
   double/float).
4. **Kein neues Flyway-Migration** — `users`-Tabelle (V01) deckt alle Felder ab.
5. **Principal** = `Long userId` → Controller-Zugriff via `@AuthenticationPrincipal Long userId`.
6. **Keine Änderung an `SecurityConfig`** — Endpoints sind durch `anyRequest().authenticated()`
   automatisch geschützt.

## Neue Files (alle in `com.budgetbuddy.auth`)

- `User.java` — JPA-Entity auf Tabelle `users` (`id`, `email`, `passwordHash`,
  `monthlyIncome: BigDecimal`, `onboardingCompleted`)
- `UserRepository.java` — `JpaRepository<User, Long>`
- `UserService.java` — `getProfile(userId)`, `updateIncome(userId, betrag)`
- `UserController.java` — `GET /users/me`, `PUT /users/me/income` + OpenAPI-Annotationen
- `dto/UserProfileResponse.java` — record (`id`, `email`, `monthlyIncome`, `onboardingCompleted`)
- `dto/UpdateIncomeRequest.java` — record (`@NotNull @Positive BigDecimal betrag`)
- ggf. `UserNotFoundException` + Handling → 404

## Implementierungsschritte

1. `User`-Entity + `UserRepository`.
2. `UserService` (Profil laden + Einkommen aktualisieren).
3. DTOs (records) inkl. Bean-Validation.
4. `UserController` mit beiden Endpoints + Swagger-Annotationen, `@Valid` auf PUT-Body.
5. `@ControllerAdvice` für `MethodArgumentNotValidException` → 400 (nur falls noch keins existiert).
6. `mvn test` lokal grün.

## Test-Strategie

**`UserControllerTest`** (`@SpringBootTest` + `@AutoConfigureMockMvc`, Temp-File-SQLite +
Flyway aktiv — analog `UsersMigrationTest`, da `test`-Profil mit `::memory:` keine Tabelle hat):

- `GET /users/me` mit gültigem JWT-Cookie → 200 + korrekter Body (AC1)
- `GET /users/me` ohne Cookie → 401 (AC4)
- `PUT /users/me/income` mit `betrag > 0` → 200, Wert persistiert (AC2)
- `PUT /users/me/income` mit `betrag <= 0` und `null` → 400 (AC2)
- `PUT /users/me/income` ohne Cookie → 401 (AC4)

**`UserServiceTest`** (JUnit5 + Mockito + AssertJ): Profil-Mapping + Income-Update inkl.
„User nicht gefunden".

Swagger-Sichtbarkeit (AC3) via OpenAPI-Annotationen, im lokalen Review verifiziert.

## Acceptance Criteria (aus Issue #9)

- [ ] `GET /users/me` gibt 200 mit korrektem JSON-Body zurück
- [ ] `PUT /users/me/income` validiert `betrag > 0` (BigDecimal)
- [ ] Beide Endpoints sind in Swagger UI dokumentiert
- [ ] Unauthentifizierter Aufruf → 401
