## Prompt

Du bist ein erfahrener Scrum Master.
Du kennst unsere User Stories US-1 aus dem @CLAUDE.md.
Unser C2 Container Diagram kennst du auch aus dem @CLAUDE.md.

Zerlege in Tasks (max 4h je Task).
Schätze Aufwand. Markiere Risiken.

## Antwort

### US-01 — Task Breakdown

**Story:** Als Lara möchte ich ein Konto erstellen und mich einloggen können.  
**Stack:** Spring Boot 3.5 · JWT HS256 httpOnly Cookie · Angular 21 · SQLite + Flyway

---

### Backend

| ID | Task | Aufwand | Abhängigkeit |
|----|------|---------|-------------|
| B1 | User-Entity + Flyway-Migration (`users`-Tabelle: id, email, password_hash, created_at) | **2h** | — |
| B2 | `JwtService` (Token generieren / validieren / Email extrahieren, JJWT 0.12.x) | **3h** | — |
| B3 | Spring Security Config (`SecurityFilterChain`, `JwtAuthFilter` liest Cookie, CORS für localhost:4200) | **4h** | B2 |
| B4 | `AuthController` + `AuthService` (`POST /api/auth/register`, `login`, `logout`) | **3h** | B1, B3 |
| B5 | Integration Tests (`@SpringBootTest`, `jdbc:sqlite::memory:` — kein H2) | **3h** | B4 |

**Backend-Total: 15h**

---

### Frontend

| ID | Task | Aufwand | Abhängigkeit |
|----|------|---------|-------------|
| F1 | Angular Routing + `AuthGuard` (canActivate) + `AuthService` mit `isAuthenticated` Signal | **2h** | — |
| F2 | `RegisterComponent` (ReactiveForm: email, password, Confirm; minLength 8; API-Call `withCredentials`) | **3h** | F1 |
| F3 | `LoginComponent` (ReactiveForm; Redirect → Dashboard on success; Fehlermeldung) | **2h** | F1 |
| F4 | Logout-Button im Nav + `POST /api/auth/logout` + Redirect zu `/login` | **1h** | F3 |

**Frontend-Total: 8h**

---

### Risiken

| Symbol | Schwere |
|--------|---------|
| 🔴 | Blockiert andere Tasks wenn falsch |
| ⚠️ | Zeitfresser / bekannte Falle |

| Risiko | Betrifft | Einschätzung |
|--------|----------|-------------|
| 🔴 **Security Config** (httpOnly Cookie + `SameSite=Strict` + CORS für `localhost:4200`) — eine falsche Header-Einstellung blockiert sämtliche Auth-Calls in allen anderen Tasks | B3 | ~1h Einrichtungszeit einplanen; sofort mit `curl -v` verifizieren |
| ⚠️ **JJWT 0.12.x API** — CLAUDE.md markiert dies als MEDIUM-Konfidenz; Beispiele im Web sind oft für 0.11.x (anderer Builder) | B2 | Offizielle Migration Guide lesen, bevor Code geschrieben wird |
| ⚠️ **SQLite-Dialect** — `hibernate-community-dialects` muss explizit als Dependency rein; Spring Boot auto-configures es nicht | B1 | pom.xml vor dem ersten `./mvnw spring-boot:run` prüfen |
| ⚠️ **H2-Falle in Tests** — Spring Boot Auto-Config zieht H2 wenn es im Classpath ist; `jdbc:sqlite::memory:` explizit in `application-test.properties` setzen | B5 | Dialect-Mismatch bricht sonst alle Tests lautlos |
| ⚠️ **`withCredentials: true`** — muss global am `HttpClient` (via `provideHttpClient(withXsrfConfiguration(...), withCredentials())`) gesetzt sein, nicht per-Request | F2 | Vergessen → Cookie wird nie mitgeschickt → alle API-Calls nach Login scheitern |

---

### Sprint-Empfehlung

B1 + B2 + F1 laufen **parallel** (keine gegenseitige Abhängigkeit).  
B3 ist der **kritische Pfad** — erst danach kann sinnvoll gegen das Backend getestet werden.

**Gesamtaufwand: ~23h** (≈ 3 Personentage zu zweit)
