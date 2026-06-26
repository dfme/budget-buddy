# BE-AUTH-01 — JWT HS256 Filter implementieren

**Issue:** #8 · **Task-ID:** BE-AUTH-01 · **Sprint 1 / Wave 2**
**Branch:** `feature/BE-AUTH-01-jwt-filter`
**Depends on:** #4 (DB-01 — users-Tabelle)

## Ziel

Spring-Security-Filter, der das JWT aus dem httpOnly-Cookie liest, per HS256 validiert
und den `SecurityContext` mit der User-ID befüllt. Secret ausschliesslich aus Umgebungsvariable
(ADR-7).

## Entscheide

- **HS256 explizit erzwingen** (`Jwts.SIG.HS256`). jjwt würde sonst aus der Key-Länge den
  stärksten Algorithmus ableiten — unser 512-bit-Secret ergäbe sonst HS512. AC verlangt HS256.
- **Secret nur aus `JWT_SECRET`-Env-Var**, gemappt via `app.jwt.secret`. Fail-fast beim Start
  über `@NotBlank` + `@Size(min=32)` in `JwtProperties` statt unsicherem Default.
- **`.env` lokal via spring-dotenv** laden (Dev-Convenience für gemischtes Team Mac/Windows).
  Echte System-Env-Vars (Render) haben Vorrang, fehlende `.env` wird ignoriert. `.env` bleibt
  gitignored; `.env.example` als committbare Team-Vorlage.
- **Stateless** (`SessionCreationPolicy.STATELESS`), kein `httpBasic`/`formLogin`. CSRF aus,
  da das JWT-Cookie `SameSite=Strict` nutzt (ADR-7).
- **401 ohne Login-Prompt:** `HttpStatusEntryPoint(UNAUTHORIZED)` statt Basic-Auth-Header.
- **Filter als Plain-Klasse** in `SecurityConfig` instanziiert (kein `@Component`), um
  Doppelregistrierung in der Servlet-Filterkette zu vermeiden.
- **Kein Login-Endpoint** in diesem Issue — Token-Ausstellung gehört zu BE-AUTH-02.
  `JwtService.generateToken` existiert als Vorbereitung + Test-Helper.
- **User-ID im `subject`-Claim** transportiert; Filter setzt sie als Principal (keine
  DB-Abfrage nötig → users-Tabelle wird vom Filter selbst nicht gelesen).

## Betroffene Files

**Neu:**
- `backend/src/main/java/com/budgetbuddy/auth/JwtProperties.java`
- `backend/src/main/java/com/budgetbuddy/auth/JwtService.java`
- `backend/src/main/java/com/budgetbuddy/auth/JwtCookieAuthenticationFilter.java`
- `backend/src/test/java/com/budgetbuddy/auth/JwtServiceTest.java`
- `backend/src/test/java/com/budgetbuddy/config/SecurityConfigTest.java`
- `backend/src/test/java/com/budgetbuddy/auth/JwtCookieAuthenticationFilterTest.java`
- `backend/.env.example` (Team-Vorlage)

**Geändert:**
- `backend/pom.xml` — `spring-dotenv`-Dependency + Surefire-Dummy-`JWT_SECRET` für Tests
- `backend/src/main/java/com/budgetbuddy/BudgetBuddyApplication.java` — `@ConfigurationPropertiesScan`
- `backend/src/main/java/com/budgetbuddy/config/SecurityConfig.java` — stateless/401 + Filter
- `backend/src/main/resources/application.properties` — `app.jwt.expiration=24h`, Kommentar Secret
- `.gitignore` — `!.env.example`-Ausnahme

**Lokal (nicht committet):**
- `backend/.env` — generiertes `JWT_SECRET` (`openssl rand -base64 48`)

## Implementierungsschritte (4 Commits)

1. **`JwtProperties` + `.env`-Setup** — `@ConfigurationProperties("app.jwt")` mit Fail-fast,
   `@ConfigurationPropertiesScan`, `app.jwt.expiration`, spring-dotenv, `.env`/`.env.example`,
   Surefire-Dummy-Secret.
2. **`JwtService` (HS256)** — `generateToken(userId)` + `validateAndGetUserId(token)`, Key aus
   Secret, HS256 erzwungen. Reiner Unit-Test.
3. **`SecurityConfig` stateless/401** — `STATELESS`, `httpBasic`/`formLogin` aus, CSRF aus,
   `HttpStatusEntryPoint(401)`. MockMvc-Test.
4. **`JwtCookieAuthenticationFilter`** — Cookie lesen, validieren, Principal setzen; vor
   `UsernamePasswordAuthenticationFilter` einhängen. Integrationstest über die echte FilterChain.

## Test-Strategie

- **`JwtServiceTest`** (reines JUnit, kein Kontext): Round-Trip, abgelaufenes, manipuliertes und
  fremd-signiertes Token werden abgelehnt. Tamper-Test ändert das **erste** Signatur-Zeichen
  (das letzte base64url-Zeichen einer 32-Byte-HS256-Signatur hat zu wenige signifikante Bits und
  kann zu identischen Bytes dekodieren).
- **`SecurityConfigTest`** (`@SpringBootTest` + MockMvc, test-Profil): geschützter Pfad → 401
  ohne `WWW-Authenticate`, `/actuator/health` → 200.
- **`JwtCookieAuthenticationFilterTest`** (`@SpringBootTest` + MockMvc, eigener Test-Controller
  `/test/me`): gültiges Cookie → 200 + User-ID, ungültiges/abgelaufenes/fehlendes Cookie → 401.
- Alle Test-Kontexte (test/prod/default) starten dank Surefire-Dummy-`JWT_SECRET` ohne `.env`
  (CI-tauglich).

## Acceptance Criteria (aus Issue #8)

- [x] Gültiger JWT → 200 OK, SecurityContext enthält User-ID
- [x] Ungültiger/abgelaufener JWT → 401 Unauthorized
- [x] Kein JWT → 401 Unauthorized
- [x] JWT-Secret wird ausschliesslich aus Umgebungsvariable gelesen
