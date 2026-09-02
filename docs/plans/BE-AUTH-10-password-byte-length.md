# [BE-AUTH-10] Passwort über 72 Bytes führt zu HTTP 500 statt 400

- **Issue:** [#200](https://github.com/dfme/budget-buddy/issues/200)
- **Task-ID:** `BE-AUTH-10`
- **Branch:** `fix/BE-AUTH-10-password-byte-length`
- **Story:** US-14 — Passwort, Einkommen und Erscheinungsbild ändern
- **Sprint:** Sprint 6
- **Bestätigt am:** 2026-09-02

## Beschreibung

`BCryptPasswordEncoder.encode()` wirft seit Spring Security 6.3 eine `IllegalArgumentException`
für Passwörter über 72 Bytes; kein `@RestControllerAdvice` fängt sie, also läuft der Request in
einen 500. Betroffen sind `POST /api/auth/register` und `PUT /api/users/me/password` (BE-AUTH-09,
#176) — beide rufen `passwordEncoder.encode(...)` auf. Die Grenze ist die **UTF-8-Byte-Länge**,
nicht die Zeichenzahl: ein `@Size(max = 72)` allein würde ein 72 Zeichen langes Passwort aus
Umlauten oder Emoji durchlassen, das in UTF-8 weit über 72 Bytes liegt.

## Betroffene Files

Neu:
- `backend/src/main/java/com/budgetbuddy/auth/dto/MaxBcryptBytes.java`
- `backend/src/main/java/com/budgetbuddy/auth/dto/MaxBcryptBytesValidator.java`

Geändert:
- `backend/src/main/java/com/budgetbuddy/auth/dto/RegisterRequest.java`
- `backend/src/main/java/com/budgetbuddy/auth/dto/ChangePasswordRequest.java`
- `backend/src/test/java/com/budgetbuddy/auth/AuthControllerTest.java`
- `backend/src/test/java/com/budgetbuddy/auth/UserControllerTest.java`

## Implementierungsschritte

1. `@MaxBcryptBytes` — Bean-Validation-Constraint-Annotation, `String`-Feld, Standardmeldung
   „Passwort ist zu lang (maximal 72 Bytes)." (nennt die Regel, nicht die Eingabe).
2. `MaxBcryptBytesValidator implements ConstraintValidator<MaxBcryptBytes, String>` — prüft
   `value.getBytes(StandardCharsets.UTF_8).length <= 72`; `null` gilt als gültig (Aufgabe von
   `@NotBlank` auf demselben Feld, dieselbe Komposition wie bei den Standard-Constraints).
   `MAX_BYTES = 72` ist die einzige Definition der Grenze — von beiden DTOs über die Annotation
   genutzt, kein dupliziertes Limit.
3. `RegisterRequest.password` und `ChangePasswordRequest.neuesPasswort` bekommen zusätzlich
   `@MaxBcryptBytes`. Kein Änderungsbedarf an `UserExceptionHandler`: `MethodArgumentNotValidException`
   aus einer verletzten Bean-Validation-Regel läuft bereits durch dessen bestehenden Handler und
   liefert `400` + `AuthErrorResponse` — exakt das, was für beide Endpoints gebraucht wird.
4. Tests (siehe Test-Strategie).

## Test-Strategie

- `AuthControllerTest`: `POST /auth/register` mit einem 73-Byte-ASCII-Passwort (Grenzfall) → `400`
  mit der festen Meldung, das Passwort erscheint nicht in der Response.
- `UserControllerTest`: `PUT /users/me/password` mit einem 73-Byte-ASCII-`neuesPasswort`
  (Grenzfall) → `400`, Hash in der DB unverändert, Passwort nicht in der Response; zusätzlich ein
  Test mit 40 Emoji als `neuesPasswort` (160 UTF-8-Bytes, aber nur 40 Codepoints) → `400`, belegt
  explizit die Byte- statt Zeichen-Zählung.
- Kein neuer Unit-Test in `AuthServiceTest`/`UserServiceTest` nötig: die Prüfung sitzt in der
  Bean Validation vor dem Controller-Aufruf, die Services sehen den Fall nie.

## Acceptance Criteria (aus dem Issue)

- [ ] Ein Passwort über der bcrypt-Grenze wird bei `POST /api/auth/register` mit `400` abgelehnt,
      nicht mit `500`
- [ ] Dasselbe gilt für `neuesPasswort` bei `PUT /api/users/me/password`
- [ ] Die Prüfung greift auf der UTF-8-Byte-Länge, nicht auf der Zeichenzahl — ein Passwort aus 40
      Emoji wird korrekt abgelehnt statt in einen `500` zu laufen
- [ ] Die Regel ist an einer Stelle definiert und von beiden Requests genutzt (kein dupliziertes
      Limit in zwei DTOs)
- [ ] Je ein Test pro Endpoint belegt den `400` an der Grenze; kein Test-Passwort erscheint in der
      Response
- [ ] Die Fehlermeldung nennt die Regel, nicht die Eingabe des Users
