# [BE-AUTH-05] Vor- und Nachname im User-Model ergänzen

- **Issue:** [#114](https://github.com/dfme/budget-buddy/issues/114)
- **Task-ID:** `BE-AUTH-05`
- **Branch:** `feature/BE-AUTH-05-user-vor-nachname`
- **Story:** — (Querschnitt: berührt US-01 und US-14, keine der beiden fordert ein Namensfeld)
- **Sprint:** Sprint 5
- **Bestätigt am:** 2026-08-29

## Entscheide

Drei Punkte waren im Issue bewusst offen gelassen und wurden vor der Umsetzung geklärt:

1. **Bestandsdaten:** Spalten nullable, bestehender E-Mail-Fallback für Initialen bleibt erhalten
   (lässt sich nicht durch die Namens-Ableitung ersetzen, nur ergänzen).
2. **Pflichtfeld bei der Registrierung?** → **Optional.** Vermeidet eine zusätzliche Hürde beim
   Onboarding (Churn-Risiko #1, Laras "Aufschieberitis"). Konsequenz: Nachtragen in den
   Einstellungen ist **nicht** Teil der ACs von #114 und bleibt out of scope für diesen Task.
3. **Ein Feld oder zwei?** → **`firstName` + `lastName`** (zwei Felder), wie im Issue skizziert.
   Bessere Initialen als ein einzelnes `displayName`.

**Korrektur ggü. Issue-Text:** Die neue Migration heisst `V07__add_name_to_users.sql`, nicht
`V05__` — `V05`/`V06` sind seither für `import_jobs` (BE-PDF-09) und `buchungsdetails` (BE-PDF-07)
vergeben.

## Betroffene Dateien

**Backend**
- Neu: `backend/src/main/resources/db/migration/V07__add_name_to_users.sql`
- `backend/src/main/java/com/budgetbuddy/auth/User.java`
- `backend/src/main/java/com/budgetbuddy/auth/dto/RegisterRequest.java`
- `backend/src/main/java/com/budgetbuddy/auth/dto/UserProfileResponse.java`
- `backend/src/main/java/com/budgetbuddy/auth/AuthService.java`
- `backend/src/main/java/com/budgetbuddy/auth/AuthController.java`

**Backend-Tests**
- `backend/src/test/java/com/budgetbuddy/db/UsersMigrationTest.java`
- `backend/src/test/java/com/budgetbuddy/auth/AuthServiceTest.java`
- `backend/src/test/java/com/budgetbuddy/auth/AuthControllerTest.java`
- `backend/src/test/java/com/budgetbuddy/auth/UserControllerTest.java`

**Frontend**
- `frontend/src/app/auth/user.model.ts`
- `frontend/src/app/auth/auth.service.ts`
- `frontend/src/app/auth/register.ts`
- `frontend/src/app/auth/register.html`
- `frontend/src/app/core/layout/shell.ts`
- `frontend/src/app/core/layout/shell.html`

**Frontend-Tests**
- `frontend/src/app/auth/auth.service.spec.ts`
- `frontend/src/app/auth/register.spec.ts`
- `frontend/src/app/core/layout/shell.spec.ts`

## Implementierungsschritte

1. Migration `V07__add_name_to_users.sql`: `first_name`/`last_name` als nullable `TEXT`, kein
   Backfill.
2. `User.java`: Felder + Getter, zusätzlicher 4-Arg-Konstruktor (`email, passwordHash, firstName,
   lastName`); 2-Arg-Konstruktor bleibt für Bestandscode/Tests bestehen.
3. `RegisterRequest`: `firstName`/`lastName` als optionale `String`, keine `@NotBlank`.
4. `UserProfileResponse`: Felder + `from()`-Mapping.
5. `AuthService.register(...)`: zwei zusätzliche Parameter; leere/Blank-Strings werden vor dem
   Speichern zu `null` normalisiert.
6. `AuthController.register(...)`: reicht die Felder durch.
7. Frontend `user.model.ts`: `firstName`/`lastName: string | null`.
8. `auth.service.ts`: `register()` erhält zwei zusätzliche optionale Parameter, immer im Body.
9. `register.ts`/`register.html`: zwei optionale Formularfelder "Vorname"/"Nachname".
10. `shell.ts`: neue Helper-Funktion `initialsFromName`; Initialen aus Name, wenn vorhanden, sonst
    Fallback auf `initialsFromEmail`; neues `fullName`-Computed.
11. `shell.html`: Name im Konto-Block anzeigen (Sidebar + mobiles Popover), E-Mail bleibt sichtbar.

## Teststrategie

- **Backend Unit:** `AuthServiceTest` — Register mit Name, ohne Name, mit Blank-String-Normalisierung.
- **Backend Integration** (echtes Postgres + Flyway): `AuthControllerTest` (Register-Response inkl.
  Name, Register ohne Name weiterhin 201), `UserControllerTest` (`GET /users/me` inkl. Name),
  `UsersMigrationTest` (Spalten-Assertion erweitert).
- **Frontend:** Vitest/TestBed für `AuthService`, `Register`, `Shell` — Body-Assertions erweitert,
  neue Fälle mit/ohne Name, Initialen- und Namensanzeige inkl. E-Mail-Fallback.
- Kein E2E nötig — BE-AUTH-05 ist kein Must-Have, US-01/US-14-Kernpfad bereits E2E-abgedeckt.

## Acceptance Criteria (aus #114)

- [ ] Flyway-Migration ergänzt `first_name`/`last_name` in `users`; bestehende Zeilen bleiben gültig
- [ ] `RegisterRequest` und `UserProfileResponse` führen die Felder; `POST /auth/register` und
      `GET /users/me` geben sie zurück
- [ ] Die Registrierungs-Maske erfasst Vor- und Nachname (optional)
- [ ] Der Konto-Block der App-Shell zeigt den Namen; Initialen werden aus dem Namen abgeleitet
- [ ] User ohne hinterlegten Namen fallen sauber auf die bisherige E-Mail-Darstellung zurück —
      keine leeren Avatare, kein "undefined"
- [ ] Bestehende Tests sind angepasst, neue Felder sind test-abgedeckt (Backend und Frontend)
- [ ] Swagger UI zeigt die erweiterten DTOs
