# [FE-FC-01] Fixkosten-Wizard Component

- **Issue:** [#24](https://github.com/dfme/budget-buddy/issues/24)
- **Task-ID:** `FE-FC-01`
- **Branch:** `feature/FE-FC-01-fixkosten-wizard`
- **Story:** US-03 — Fixkosten erfassen (Onboarding-Wizard)
- **Sprint:** Sprint 4
- **Bestätigt am:** 2026-08-09

## Ziel

Reactive Form zum Erfassen einer Fixkosten-Position: `Bezeichnung`, `Betrag` (CHF > 0) und
`Intervall` aus `{monatlich, quartalsweise, jaehrlich}`, mit feldspezifischer Validierung
und Erfolgs-Feedback nach dem Absenden.

## Acceptance Criteria (aus Issue)

- [ ] Formular zeigt Validierungsfehler inline an
- [ ] Betrag-Feld akzeptiert nur positive Zahlen
- [ ] Intervall-Dropdown enthält monatlich, quartalsweise, jährlich
- [ ] Submit sendet POST /fixed-costs und zeigt Erfolgs-Feedback

## Entscheide vor der Umsetzung

| Punkt | Entscheid | Begründung |
| ----- | --------- | ---------- |
| Fehlender Endpoint | **Nur Frontend**, Contract als deklarierte Annahme | `POST /fixed-costs` existiert nicht: `backend/src/main/java/com/budgetbuddy/budget/` enthält nur Entity, Repository, `Intervall` und Converter. #12 (BE-FC-03) ist offen und hängt an #11 (BE-FC-02), ebenfalls offen. Die Alternativen — #11+#12 mitziehen (5 → 12 SP, Backend+Frontend in einem PR) oder bis #12 zurückstellen (blockiert #25 weiter) — wurden verworfen. |
| Scope | Nur das **Formular**, nicht die Wizard-Mechanik | Zwang bis `onboarding_completed` und „Keine Fixkosten" liegen in #25 (FE-FC-02), Liste/Bearbeiten/Löschen/Warnung in #26 (FE-FC-03). Der Titel „Wizard" benennt die Komponente, nicht den Ablauf. |
| Intervall-Dropdown | Natives `<select>` in `app-field`, kein neuer Shared-`Select` | `frontend/src/app/shared/` hat keine Select-Komponente, und `field.ts:22-25` dokumentiert ausdrücklich, dass `Field` auch `<select>` und `<textarea>` trägt — das Steuerelement wird generisch gesucht statt über `contentChild(Input)`. |
| Intervall-Werte | ASCII-Labels aus `Intervall.getLabel()`, Anzeige mit Umlaut nur im Template | `Intervall.java:9-11`: der Umlaut in «jährlich» ist bewusst Sache des Frontends; ein Umlaut im DB-Wert und im API-Contract würde Encoding-Fallen über Datenbank, JSON und E2E-Assertions eröffnen. |

## Contract-Annahme (unbestätigt)

Abgeleitet aus `FixedCost.java` und `Intervall.java:22-24` — **nicht durch einen Backend-Test
belegt**, weil der Endpoint noch nicht existiert. Geht so in den PR-Body:

```
POST /fixed-costs
Request : { bezeichnung: string, betrag: number, intervall: "monatlich"|"quartalsweise"|"jaehrlich" }
Response: 201 { id: number, bezeichnung: string, betrag: number, intervall: <s.o.> }
```

`betrag` ist eine JSON-**Number**, kein String: Jackson serialisiert `BigDecimal` so. Genau
diese Verwechslung war der stärkste Befund in PR #90 (`amount: string`, tatsächlich Number).
Weicht #12 davon ab, ist ein Nachzug in Model und Service nötig.

## Betroffene Files

### Neu — `frontend/src/app/onboarding/`

Feature-Ordner nach der Domänen-Struktur in `CLAUDE.md` (US-03 → `onboarding/`).

- `fixed-cost.model.ts` — Request-/Response-Typen, `Intervall`-Union, `INTERVALL_OPTIONS`
- `fixed-cost.service.ts` — `POST /fixed-costs`, zustandslos (Muster: `pdf-import.service.ts`)
- `fixed-cost.service.spec.ts`
- `fixed-cost-wizard.ts` / `.html` / `.scss`
- `fixed-cost-wizard.spec.ts`

### Geändert

- `frontend/src/app/app.routes.ts` — Route `onboarding`, `authGuard`, lazy `loadComponent`
- `frontend/proxy.conf.json` — `/fixed-costs` an `:8080` (fehlt sonst im Dev-Betrieb)
- `frontend/src/proxy.conf.spec.ts` — `/fixed-costs` in die `it.each`-Liste
- `backend/src/main/java/com/budgetbuddy/config/SpaForwardController.java` — `/onboarding` in
  `CLIENT_ROUTE_PATTERNS` **und** im `@GetMapping`

Der Backend-Touch ist Pflicht, nicht Beiwerk: `SpaForwardController.java:46-50` schreibt für
jede neue Frontend-Route einen Eintrag vor, sonst ist `/onboarding` nach Hard-Reload ein 404.
`SpaRoutingTest` liest die Liste per `FieldSource` und deckt die neue Route automatisch ab.
Weil `SecurityConfig` seine GET-Freigabe aus derselben Liste ableitet, greift im
Security-Review Zeile 2 (Endpoint-Exposition).

## Implementierungsschritte

1. `fixed-cost.model.ts` — Typen und `INTERVALL_OPTIONS` (Wert = ASCII-Label, Anzeige mit Umlaut)
2. `fixed-cost.service.ts` + Spec
3. `fixed-cost-wizard.ts` — `nonNullable.group`: `bezeichnung` `[required]`, `betrag`
   `[required, min(0.01)]`, `intervall` `[required]`, Default `monatlich`
4. Feldspezifische Fehlermethoden nach dem Muster `register.ts:46-72` (`touched && invalid`)
5. `fixed-cost-wizard.html` — `app-card` › `app-field` + `input appInput` bzw. `<select>`;
   `app-notice` für Erfolg und Fehler; `app-button`
6. Erfolgs-Feedback: `app-notice variant="info"`, Formular zurücksetzen (Intervall wieder
   `monatlich`) — der Wizard bleibt stehen, damit mehrere Positionen erfassbar sind
7. `.scss` auf den Design-Tokens der Variante A
8. Route, Proxy und `SpaForwardController` nachziehen
9. `npx ng test --watch=false`, `npx ng build`, `./mvnw test` für `SpaRoutingTest`

## Test-Strategie

Unit/Component mit Vitest + TestBed und `HttpTestingController`, Muster `register.spec.ts`.

| Test | Deckt AC |
| ---- | -------- |
| Leeres Formular → `expectNone`, alle drei Felder `touched`, Fehlertexte gesetzt | AC1 |
| `betrag = 0` und `betrag = -5` → `min`-Fehler, kein Request | AC2 |
| `INTERVALL_OPTIONS` enthält exakt die drei Werte; Template rendert 3 `<option>` | AC3 |
| Gültiger Submit → `POST /fixed-costs`, Body-Assertion, `flush(201)` → Erfolgs-Notice, Formular zurückgesetzt | AC4 |
| Fehlerpfad `flush(500)` → Fehler-Notice, keine Erfolgs-Notice | AC4 |

Dazu: Service-Test (URL, Methode, Body), Proxy-Test (`/fixed-costs` → `:8080`), und
`SpaRoutingTest` im Backend deckt `/onboarding` automatisch ab.

**Kein E2E** — Playwright für US-03 ist ein eigenes Issue (#123, E2E-FC-01).

## Bewusst nicht Teil davon

- **AC4 nur unit-belegt.** End-to-end erst mit #12; die Contract-Annahme oben steht im PR-Body.
- **Keine Wizard-Mechanik** (#25) und **keine Fixkosten-Liste** (#26).
- **Kein Fix an [#126](https://github.com/dfme/budget-buddy/issues/126)** (offener Deep-Link-Bug)
  — eine vorbestehende Lücke wird nicht nebenbei mitgenommen.

## Anmerkung zum Issue

#24 nennt in den Metadaten nur `Depends on: #2 (INFRA-02)`. Die tatsächliche Abhängigkeit auf
#12 (BE-FC-03) fehlte und wurde im Rahmen dieser Umsetzung nachgetragen.
