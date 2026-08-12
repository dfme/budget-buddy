# [FE-FC-03] Fixkosten-Liste

- **Issue:** [#26](https://github.com/dfme/budget-buddy/issues/26)
- **Task-ID:** `FE-FC-03`
- **Branch:** `feature/FE-FC-03-fixkosten-liste`
- **Story:** US-03 — Fixkosten erfassen (Onboarding-Wizard)
- **Sprint:** Backlog
- **Bestätigt am:** 2026-08-12

## Kontext

BE-FC-03 (#12) liefert seit Sprint 4 alle CRUD-Endpoints für Fixkosten, inklusive
`GET /fixed-costs` → `FixedCostSummaryResponse { fixedCosts, summeMonatlich, monthlyIncome,
exceedsIncome }`. Der Onboarding-Wizard (FE-FC-01/#24) kann nur anlegen; Liste, Bearbeiten,
Löschen und die Einkommens-Warnung fehlen — das ist genau der Scope dieses Issues, und sowohl
`fixed-cost-wizard.ts` als auch `onboarding.guard.ts` verweisen bereits explizit auf #26 als den
Ort, an dem das nachgeholt wird.

`docs/plans/BE-FC-03-fixed-costs-endpoints.md` hält fest, dass `POST`/`PUT` bewusst nur die
Einzelposition zurückgeben, nicht das aktualisierte Warn-Flag — der Re-Fetch von
`GET /fixed-costs` nach jedem Schreibzugriff ist explizit dieser Komponente zugewiesen.

**Routen-Falle:** Ein Angular-Pfad `/fixed-costs` würde mit dem API-Prefix kollidieren
(`SpaForwardController` verbietet `/**`-Wildcards über API-Prefixes, siehe dessen Javadoc). Die
neue Route heisst deshalb `/fixkosten`.

## Betroffene / neue Files

**Backend** (nur Routing-Registrierung, keine Domänenlogik):
- `backend/src/main/java/com/budgetbuddy/config/SpaForwardController.java` — `/fixkosten` zu
  `CLIENT_ROUTE_PATTERNS` und `@GetMapping` ergänzen. `SecurityConfig` leitet die `permitAll`-Liste
  daraus ab, `SpaRoutingTest` ist parametrisiert über dasselbe Array — keine weiteren Anpassungen
  nötig.

**Frontend:**
- `frontend/src/app/onboarding/fixed-cost.model.ts` — `FixedCostDetail` (= `FixedCost` +
  `monatsbetrag`) und `FixedCostSummary` ergänzen; veraltete «unbestätigter Contract»-Kommentare
  entfernen (Contract ist seit BE-FC-03 bestätigt)
- `frontend/src/app/onboarding/fixed-cost.validators.ts` *(neu)* — `nonBlank`, `maxTwoDecimals`,
  `MIN_BETRAG_CHF` aus `fixed-cost-wizard.ts` extrahiert, damit die Bearbeiten-Form dieselbe Regel
  nutzt statt sie zu duplizieren
- `frontend/src/app/onboarding/fixed-cost-wizard.ts` — importiert die Validatoren aus der neuen
  Datei (keine Verhaltensänderung)
- `frontend/src/app/onboarding/fixed-cost.service.ts` — `list()`, `update(id, request)`,
  `delete(id)` ergänzen
- `frontend/src/app/onboarding/fixed-cost-list.ts` + `.html` + `.scss` *(neu)* — die Listen-Komponente
- `frontend/src/app/app.routes.ts` — Route `/fixkosten` (`authGuard` + `onboardingGuard`)
- `frontend/src/app/core/layout/shell.ts` (+ Template) — Nav-Item, damit die Seite erreichbar ist
- `frontend/src/app/onboarding/fixed-cost.service.spec.ts` — um `list()`/`update()`/`delete()`
  erweitert
- `frontend/src/app/onboarding/fixed-cost-list.spec.ts` *(neu)*

## Implementierungsschritte

1. Validatoren aus `fixed-cost-wizard.ts` in `fixed-cost.validators.ts` extrahieren, Wizard auf
   den Import umstellen.
2. `fixed-cost.model.ts`: `FixedCostDetail`/`FixedCostSummary` ergänzen, stale Kommentare
   entfernen.
3. `fixed-cost.service.ts`: `list()` (`GET /fixed-costs`), `update()` (`PUT /fixed-costs/{id}`),
   `delete()` (`DELETE /fixed-costs/{id}`) ergänzen.
4. `FixedCostList`-Komponente (OnPush, Signals): lädt `list()` bei Init in ein `summary`-Signal;
   Tabelle (Bezeichnung, Betrag, Intervall, Monatsbetrag, Aktionen) in `app-card`, Stil analog
   `category-overview.html`; `app-notice variant="warning"` bei `exceedsIncome` mit dem Wortlaut
   aus US-03 («Deine Fixkosten übersteigen dein Einkommen — Safe-to-Spend kann nicht berechnet
   werden»); Bearbeiten klappt die Zeile in eine vorausgefüllte Reactive-Form auf (Speichern → PUT
   + Re-Fetch, Abbrechen → zuklappen ohne Call); Löschen öffnet `app-modal` zur Bestätigung
   (Confirm → DELETE + Re-Fetch, Cancel/Escape → kein Call); Empty-State mit Link auf `/onboarding`
   zum Anlegen neuer Positionen.
5. Route `/fixkosten` in `app.routes.ts` (`authGuard`, `onboardingGuard` — gleiches Muster wie
   `/dashboard`/`/categories`/`/import`).
6. Nav-Item in `shell.ts`/Template ergänzen.
7. `/fixkosten` in `SpaForwardController.CLIENT_ROUTE_PATTERNS` + `@GetMapping` ergänzen.
8. Tests schreiben (siehe unten), `ng build` und `mvn package` grün.

## Test-Strategie

- **`fixed-cost.service.spec.ts`** (erweitert): `list()`/`update()`/`delete()` gegen
  `HttpTestingController`, inkl. der bestehenden Prüfung, dass `betrag` als JSON-Zahl (nicht
  String) gesendet wird.
- **`fixed-cost-list.spec.ts`** (neu): rendert Zeilen aus `list()`; zeigt/verbirgt die Warnung nach
  `exceedsIncome`; Bearbeiten füllt das Formular vor und ruft `PUT`; Löschen fragt nach
  Bestätigung und ruft `DELETE` nur nach Confirm, nicht nach Cancel; Loading-/Error-/Empty-States.
- Kein neuer E2E-Test: für US-03 existiert noch keine Playwright-Spec (weder für FE-FC-01 noch
  FE-FC-02 wurde eine angelegt) — dieses Issue folgt demselben Präzedenzfall und deckt den Happy
  Path über die Vitest-Component-Spec ab (DoD: „Playwright oder JUnit"/Äquivalent).
- Backend: keine neuen Tests nötig — `SpaRoutingTest` ist parametrisiert über
  `CLIENT_ROUTE_PATTERNS` und deckt `/fixkosten` automatisch mit ab.

## Acceptance Criteria (aus Issue #26)

- [ ] Liste zeigt alle Fixkosten mit Bezeichnung, Betrag, Intervall
- [ ] Bearbeiten öffnet Formular mit vorausgefüllten Werten
- [ ] Löschen entfernt Eintrag nach Bestätigung
- [ ] Warnung „Fixkosten ≥ Einkommen" wird prominent angezeigt
