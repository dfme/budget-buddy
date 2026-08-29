# [FE-SET-03] Einkommen manuell erfassen und ändern

- **Issue:** [#179](https://github.com/dfme/budget-buddy/issues/179)
- **Task-ID:** `FE-SET-03`
- **Branch:** `feature/FE-SET-03-einkommen-aendern`
- **Story:** US-14 — Passwort, Einkommen und Erscheinungsbild ändern
- **Sprint:** Sprint 5
- **Bestätigt am:** 2026-08-26

## Kontext

Die "Einkommen"-Card im Einstellungs-Screen (FE-SET-01, `frontend/src/app/settings/settings.html`)
ist bislang leer. Backend-seitig existiert `PUT /api/users/me/income` bereits (BE-AUTH-08,
`UserController.updateIncome`) und `AuthService.updateIncome()` (`frontend/src/app/auth/auth.service.ts:86`)
kapselt den Call bereits inkl. State-Update — beides ungenutzt ausserhalb des
"Übernehmen"-Buttons im Dashboard (FE-STS-03), der nur erscheint, wenn `BE-STS-02` einen
Vorschlag findet. Ohne diesen Task gibt es keinen Weg, das Einkommen manuell zu erfassen, wenn
die Heuristik keinen Vorschlag liefert.

Branch basiert auf `main`, nicht auf dem noch offenen `feature/FE-SET-02-passwort-aendern`
(PR #212): #179 ist nur durch #177 (FE-SET-01, gemerged) blockiert. FE-SET-02 ist ein
Schwester-Task auf demselben Gerüst — ein Merge-Konflikt auf `settings.*` beim Zusammenführen
ist erwartet und wird beim Merge aufgelöst, analog zum bereits etablierten Muster der drei
Settings-Unter-Tasks.

## Entscheidungen

- **Kein Cross-Import der Validatoren aus `onboarding/fixed-cost.validators.ts`.** Die
  Zwei-Nachkommastellen-Regel wird lokal in `settings.ts` dupliziert, um die Feature-Ordner
  unabhängig zu halten (Konvention: Feature-Struktur nach Domäne).
- **"Übernehmen" speichert sofort**, statt nur das Feld zu befüllen — identisches Verhalten wie
  der bestehende "Übernehmen"-Button im Dashboard (FE-STS-03), kein zweiter Persistenz-Pfad für
  denselben Wert.
- **Kein zusätzlicher Code für AC5** (Dashboard zeigt neuen Betrag ohne Reload): `AuthService.updateIncome`
  schreibt bereits in den geteilten `currentUser`-State, und `Dashboard` lädt `GET
  /api/budget/safe-to-spend` bei jeder Routenaktivierung frisch (kein `RouteReuseStrategy`
  konfiguriert). Ein Routing-Test belegt das Verhalten, statt es erneut zu implementieren.

## Betroffene Files

Alle bestehend, keine neuen Dateien:

- `frontend/src/app/settings/settings.ts`
- `frontend/src/app/settings/settings.html`
- `frontend/src/app/settings/settings.scss`
- `frontend/src/app/settings/settings.spec.ts`

## Implementierungsschritte

1. `settings.ts`: reaktives `incomeForm` mit einem optionalen `betrag`-Control —
   `Validators.min(0.01)` + lokaler `maxTwoDecimals`-Validator. Kein `required` (AC1: Feld bleibt
   optional, leer = automatische Schätzung).
2. Vorbelegung im Constructor aus `authService.currentUser()?.monthlyIncome` — bereits vom
   `authGuard` (`ensureCurrentUser()`) geladen, bevor die Route aktiviert (AC2).
3. Vorschlag: `safeToSpendService.getSafeToSpend()` im Constructor aufrufen, `incomeSuggestion`
   als Signal halten. Ist er gesetzt (Backend liefert ihn nur bei `noIncome`), erscheint ein
   `app-notice` unter dem Feld ("Regelmässige Gutschrift von X CHF erkannt — als Monatseinkommen
   übernehmen?") mit "Übernehmen"-Button, der den Formwert setzt und denselben Save-Pfad auslöst
   (AC3).
4. `submitIncome()`: früher Ausstieg bei `incomeForm.invalid` oder leerem (`null`) Wert — kein
   Request. Sonst `authService.updateIncome(betrag)`; bei Erfolg Bestätigung "Einkommen
   gespeichert." (analog FE-SET-02), Wert bleibt im Feld stehen. Bei Fehler `err.error?.message`
   aus `IncomeErrorResponse` direkt anzeigen (laut Backend-Doku zur direkten Anzeige gedacht),
   generische Meldung als Fallback für Nicht-400-Fehler (AC4, Teil von AC2).
5. `settings.html`/`settings.scss`: Formular in der "Einkommen"-Card (`app-field` + `appInput
   type="number"`, `app-notice`, `appButton`), Styling analog zur Passwort-Card aus FE-SET-02.

## Test-Strategie

Vitest/Angular TestBed, Erweiterung von `settings.spec.ts`:

- Vorbelegung aus `currentUser().monthlyIncome`, wenn gesetzt.
- Vorschlags-Notice erscheint nur bei `noIncome` + `incomeSuggestion`; "Übernehmen" befüllt und
  speichert.
- Speichern mit gültigem Betrag ruft `PUT /api/users/me/income` auf und zeigt die Bestätigung.
- Beträge `<= 0` werden clientseitig abgefangen, ohne HTTP-Call.
- Eine 400-Antwort zeigt die `message` aus dem Backend-Body an.
- Routing-Test (Erweiterung von `describe('Route /einstellungen', …)`): Einkommen in den
  Einstellungen speichern, zu `/dashboard` navigieren, frischer `GET
  /api/budget/safe-to-spend` zeigt den neuen Wert — belegt AC5 ohne Reload.

## Acceptance Criteria (aus Issue #179)

- [ ] Betragsfeld für das Monatseinkommen, als optional gekennzeichnet; leer lassen ist erlaubt
      und heisst "automatische Schätzung verwenden" (US-06)
- [ ] Ist ein Einkommen erfasst, steht der aktuelle Wert im Feld
- [ ] Ist keines erfasst und liefert `GET /budget/safe-to-spend` ein `incomeSuggestion`,
      erscheint der Schätzwert als Vorschlag am Feld
- [ ] Speichern ruft `PUT /users/me/income` auf; Beträge `<= 0` werden vor dem Request
      abgefangen (das Backend antwortet sonst mit `400`)
- [ ] Nach dem Speichern zeigt das Dashboard den Safe-to-Spend-Betrag mit dem neuen Einkommen —
      ohne Reload der App
- [ ] Tests decken ab: Speichern mit gültigem Betrag, Ablehnung von `0`/negativ, Vorbelegung
      aus dem Vorschlag
