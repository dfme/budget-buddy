# [FE-AUTH-06] Register-Formular zeigt die Backend-Fehlermeldung bei 400 nicht an

- **Issue:** [#264](https://github.com/dfme/budget-buddy/issues/264)
- **Task-ID:** `FE-AUTH-06`
- **Branch:** `fix/FE-AUTH-06-register-400-message`
- **Story:** — (kein `us-*`-Label)
- **Sprint:** Sprint 6
- **Bestätigt am:** 2026-09-16

## Entscheide

### Backend-Meldung bei 400 durchreichen, analog `settings.ts`

`register.ts` mappte bisher jeden Fehler ausser `409` pauschal auf eine generische Meldung.
Mit BE-AUTH-10 (#200, PR #262) kam `@MaxBcryptBytes` dazu — die erste Backend-Passwortregel
ohne clientseitiges Gegenstück, sodass ein `400` erstmals aus der Register-UI erreichbar ist und
seine Meldung verschluckt wurde. `settings.ts` reicht bei `400` bereits `err.error.message`
durch (`submitPassword`, `submitIncome`) — `register.ts` übernimmt dasselbe Muster.

Kein clientseitiges Pendant zu `@MaxBcryptBytes` eingeführt: das Issue verlangt ausdrücklich nur
die Durchreichung der Backend-Meldung, keine neue Validierung.

## Betroffene Dateien

- `frontend/src/app/auth/register.ts` — Fehlerbehandlung in `submit()`
- `frontend/src/app/auth/register.spec.ts` — Tests für den 400-Fall

## Implementierungsschritte

1. In `register.ts` die Fehlerlogik im `error`-Handler von `submit()` erweitern: bei
   `err.status === 400` mit vorhandenem `err.error?.message` diese Meldung anzeigen; sonst
   (400 ohne `message`, andere Codes ausser 409) bleibt die bisherige generische Meldung. Der
   409-Fall bleibt unverändert bei der festen Meldung "E-Mail bereits vergeben".

## Test-Strategie

Unit-Tests (Vitest), analog zur bestehenden Suite in `register.spec.ts`:

- 400 mit `err.error.message` zeigt genau diese Meldung an, keine Weiterleitung,
  `submitting()` wieder `false`.
- 400 ohne `message` im Body fällt auf die generische Meldung zurück.
- Bestehender 409-Test bleibt unverändert grün (Regression).

## Acceptance Criteria

- Ein `400` von `/api/auth/register` mit `err.error.message` zeigt diese Meldung im Formular an,
  statt der generischen Meldung "Registrierung fehlgeschlagen. Bitte versuche es später erneut."
- Der `409`-Fall ("E-Mail bereits vergeben") bleibt unverändert.
