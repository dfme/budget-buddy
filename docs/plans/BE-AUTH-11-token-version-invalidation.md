# [BE-AUTH-11] JWT bleibt nach Passwort-Änderung gültig — Session-Invalidierung entscheiden

- **Issue:** [#201](https://github.com/dfme/budget-buddy/issues/201)
- **Task-ID:** `BE-AUTH-11`
- **Branch:** `feature/BE-AUTH-11-token-version-invalidation`
- **Story:** US-14 — Einstellungen
- **Sprint:** Sprint 6
- **Bestätigt am:** 2026-09-02

## Entscheid

Aufgefallen beim Review von #199 (BE-AUTH-09, PR für #176): der Passwort-Änderung-Endpoint setzt
kein neues Cookie und invalidiert das alte JWT nicht — bei stateless JWT (ADR-7) technisch
konsequent, aber ein Angreifer mit gestohlenem Token bleibt eingeloggt, obwohl der Nutzer genau
das mit der Passwort-Änderung beenden wollte.

Von drei im Issue skizzierten Richtungen gewählt: **Option 1 — Token-Version am User.** Eine
Spalte `token_version`, die bei jeder Passwort-Änderung hochgezählt und als Claim ins JWT
geschrieben wird; der `JwtCookieAuthenticationFilter` verwirft Tokens mit veralteter Version.
Invalidiert alle Sessions (inkl. eines Angreifers) und ist damit die einzige der drei Optionen,
die das im Issue beschriebene Szenario tatsächlich löst. Kostet einen DB-Read pro Request und hebt
die ADR-7-Kernannahme „kein Session-Lookup" für den Filter partiell auf — dieser Trade-off wird in
ADR-7 nachgetragen.

**Ergänzende Entscheide (im Rahmen dieses Plans getroffen):**
- **Kein Cookie-Reissue beim Passwortwechsel.** Der aufrufende Client wird durch das Hochzählen
  ebenfalls ausgeloggt und muss sich neu einloggen. Bewusst kein automatisches Set-Cookie in der
  Response von `PUT /api/users/me/password` — erzwungenes Re-Login bestätigt dem Nutzer aktiv,
  dass die Änderung wirksam war, und ändert den bestehenden Response-Vertrag (200, leerer Body)
  nicht.
- **ADR-7-Diskrepanz mitkorrigiert.** ADR-7 dokumentiert 1 Stunde JWT-Gültigkeit, konfiguriert
  ist tatsächlich `app.jwt.expiration=24h` (`application.properties:129`, Default in
  `JwtProperties`). Da ADR-7 für den Token-Version-Nachtrag ohnehin editiert wird, wird der
  Zahlendreher in derselben Änderung korrigiert (Scope-Erweiterung, im PR-Body deklariert).

## Betroffene Dateien

**Migration**
- neu: `backend/src/main/resources/db/migration/V08__add_token_version_to_users.sql`

**Backend-Code**
- `backend/src/main/java/com/budgetbuddy/auth/User.java` — Feld `tokenVersion`, Getter,
  Domänenmethode `invalidateTokenVersion()`
- `backend/src/main/java/com/budgetbuddy/auth/JwtService.java` — `generateToken(long userId, long
  tokenVersion)`, Convenience-Overload `generateToken(long userId)` (impliziert `tokenVersion=0`),
  `validateAndGetUserId` → `validate(String token)` liefert neuen Record `TokenClaims(long userId,
  long tokenVersion)`
- `backend/src/main/java/com/budgetbuddy/auth/JwtCookieAuthenticationFilter.java` — Konstruktor
  bekommt `UserRepository`; vergleicht `claims.tokenVersion()` gegen `user.getTokenVersion()` nach
  DB-Lookup, bei Mismatch/fehlendem User keine Authentifizierung
- `backend/src/main/java/com/budgetbuddy/config/SecurityConfig.java` — `filterChain(...)` bekommt
  `UserRepository`-Parameter, reicht ihn an den Filter weiter
- `backend/src/main/java/com/budgetbuddy/auth/AuthController.java` — `generateToken(user.getId(),
  user.getTokenVersion())`
- `backend/src/main/java/com/budgetbuddy/auth/UserService.java` — `changePassword` ruft
  zusätzlich `user.invalidateTokenVersion()` (gleiche Transaktion wie der Hash-Wechsel)
- `backend/src/main/java/com/budgetbuddy/auth/UserController.java` — OpenAPI-Beschreibung von
  `PUT /password` ergänzt: alle zuvor ausgestellten Tokens werden ungültig

**Doku**
- `docs/adr/ADR-7-jwt-authentication.md` — Nachtrag zur Token-Version-Invalidierung; Korrektur
  „1 Stunde" → „24 Stunden" (Zeilen 21, 49)

## Implementierungsschritte

1. Flyway-Migration `V08__add_token_version_to_users.sql` anlegen (`BIGINT NOT NULL DEFAULT 0`).
2. `User`: Feld, Getter, `invalidateTokenVersion()`.
3. `JwtService`: `TokenClaims`-Record, `generateToken(userId, tokenVersion)` +
   Convenience-Overload, `validate(token)` statt `validateAndGetUserId(token)`.
4. `JwtCookieAuthenticationFilter`: `UserRepository`-Abhängigkeit, Versionsvergleich, Javadoc
   anpassen (Filter ist ab hier nicht mehr rein zustandslos).
5. `SecurityConfig`: `UserRepository` durchreichen.
6. `AuthController`: aktuelle `tokenVersion` beim Cookie-Erstellen mitgeben.
7. `UserService.changePassword`: `invalidateTokenVersion()` aufrufen.
8. `UserController`: OpenAPI-Beschreibung nachziehen.
9. ADR-7 nachführen (Token-Version-Trade-off + 24h-Korrektur).

## Test-Strategie

- `JwtServiceTest`: bestehende Aufrufe auf neue Signatur umstellen; neuer Test für
  `TokenClaims.tokenVersion()`.
- `JwtCookieAuthenticationFilterTest`: Umstellung von `registerWithoutFlyway` auf volle
  Postgres+Flyway-DB mit echtem Test-User; neue Fälle: veraltete `token_version` → 401,
  nicht-existenter User → 401.
- `UserControllerTest`: neuer Test `changePasswordInvalidatesOldJwt` — altes Cookie vor dem
  Wechsel merken, Passwort ändern, mit altem Cookie `GET /api/users/me` → 401.
- `UserServiceTest`: neuer Test — `changePassword` erhöht `user.getTokenVersion()` um 1.

## Acceptance Criteria (aus dem Issue)

- [ ] Das Team hat entschieden, welche der Richtungen gilt, und der Entscheid ist begründet
      festgehalten — dieser Plan.
- [ ] Fällt der Entscheid auf eine Code-Änderung: ein Test belegt das gewählte Verhalten (altes
      Token nach Passwortwechsel abgelehnt) — `UserControllerTest.changePasswordInvalidatesOldJwt`.
- [ ] Berührt der Entscheid ADR-7, ist der ADR nachgeführt — nicht nur der Code.
