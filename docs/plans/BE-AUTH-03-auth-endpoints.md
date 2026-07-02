# [BE-AUTH-03] Register-, Login- und Logout-Endpoints

- **Issue:** #46
- **Task-ID:** BE-AUTH-03
- **Branch:** `feature/BE-AUTH-03-auth-endpoints`
- **Bezug:** US-01 (Konto erstellen und einloggen), ADR-7 (JWT httpOnly Cookie)

## Ansatz / Entscheide

- **Cookie via `ResponseCookie`** (nicht jakarta `Cookie`) — nur diese unterstuetzt `SameSite=Strict`.
  Cookie-Name `jwt`, `HttpOnly`, `Path=/`, `Max-Age` = JWT-Gueltigkeit. `Secure` konfigurierbar
  via `app.cookie.secure` (Default `false` dev, `true` in `application-prod.properties`).
  Der bestehende `JwtCookieAuthenticationFilter` liest das Cookie unveraendert per Name.
- **Passwort-Hashing:** `BCryptPasswordEncoder`-Bean in `SecurityConfig`. Passwoerter nur als
  bcrypt-Hash gespeichert, nie Klartext.
- **Response-Body:** Register + Login liefern `UserProfileResponse` (wiederverwendet). Logout: kein Body.
- **Falsche Credentials -> 401 ohne Auskunft**, ob die E-Mail existiert (unbekannte E-Mail und
  falsches Passwort werfen dieselbe `InvalidCredentialsException`).
- **Status-Codes:** register -> 201, login -> 200, logout -> 200, Duplikat-E-Mail -> 409.

## Betroffene / neue Files

Neu:
- `auth/AuthController.java`, `auth/AuthService.java`, `auth/JwtCookieFactory.java`
- `auth/EmailAlreadyExistsException.java` (409), `auth/InvalidCredentialsException.java` (401)
- `auth/dto/RegisterRequest.java`, `auth/dto/LoginRequest.java`
- Tests: `auth/AuthServiceTest.java`, `auth/AuthControllerTest.java`

Geaendert:
- `config/SecurityConfig.java` (`/auth/**` public, `PasswordEncoder`-Bean)
- `auth/UserRepository.java` (`findByEmail`, `existsByEmail`)
- `auth/User.java` (Konstruktor `User(email, passwordHash)`)
- `auth/UserExceptionHandler.java` (Handler fuer neue Exceptions)
- `resources/application.properties` + `application-prod.properties` (`app.cookie.secure`)

## Test-Strategie

- Unit (AuthServiceTest): register hasht & speichert; Duplikat -> Exception; login korrekt -> userId;
  login falsches Passwort -> Exception; login unbekannte E-Mail -> Exception.
- Integration (AuthControllerTest, echtes SQLite + Flyway): register -> 201 + `Set-Cookie: jwt; HttpOnly;
  SameSite=Strict`, bcrypt-Hash persistiert (!= Klartext); Duplikat -> 409; login korrekt -> 200 + Cookie;
  login falsch -> 401; logout -> `Max-Age=0`.

## Acceptance Criteria (aus Issue #46)

- [ ] POST /auth/register legt User an (bcrypt-Hash), Duplikat-E-Mail -> 409
- [ ] POST /auth/login korrekt -> 200 + `Set-Cookie: jwt=...; HttpOnly; SameSite=Strict`
- [ ] POST /auth/login falsch -> 401 (keine Auskunft, ob E-Mail existiert)
- [ ] POST /auth/logout setzt `Max-Age=0`
- [ ] Passwoerter nur als bcrypt-Hash
- [ ] `/auth/**` in SecurityConfig public
- [ ] Alle drei Endpoints in Swagger UI dokumentiert
