# [FE-FC-10] Neue Fixkosten-Position im Overlay-Dialog statt Wizard-Navigation erfassen

- **Issue:** [#372](https://github.com/dfme/budget-buddy/issues/372)
- **Task-ID:** `FE-FC-10`
- **Branch:** `feature/FE-FC-10-neue-position-dialog`
- **Story:** US-03 — Fixkosten erfassen (Onboarding-Wizard)
- **Sprint:** Sprint 7
- **Bestätigt am:** 2026-09-28

## Ausgangslage

Auf der Budget-Seite (`/budget`) führen «+ Neue Position» (`section-header`) und «Jetzt
erfassen» (Leerzustand) per `routerLink="/onboarding"` aus der Seite hinaus in den Wizard.
Stattdessen öffnet sich ein Dialog mit Bezeichnung, Betrag und Intervall.

## Befunde aus der Analyse

| Punkt | Befund |
| ----- | ------ |
| Enter im Dialog | `app-modal` rendert beide Aktionen als `type="button"` ausserhalb des projizierten `<form>`. Nach HTML-Spec gibt es Implicit Submission bei mehr als einem Feld nur mit einem Submit-Button des Formulars — das Settings-Muster (Konto löschen) funktioniert nur, weil sein Formular ein einziges Feld hat. |
| Initialfokus | `cdkFocusInitial` sitzt fest auf «Abbrechen» (`modal.html`). |
| AC 5, Karte «Monatliche fixe Ausgaben» | Seit FE-STS-06 (#369) auf dem Dashboard, nicht mehr auf `/budget`. Das Dashboard lädt die Summen bei jedem Aufruf selbst neu; auf der Budget-Seite bleiben Tabelle, Tabellen-Total und Einkommens-Warnung — alle aus `summary`. |
| Breite Suche | «Neue Position», «Jetzt erfassen», `routerLink="/onboarding"` über Doku, Requirements, E2E und Frontend: nur die zwei Template-Stellen und eine Spec-Assertion. Kein Delta ausserhalb der ACs. |

## Entscheide

| Punkt | Entscheid |
| ----- | --------- |
| Modal-Erweiterung | Zwei optionale Inputs, Default unverändert: `initialFocus: 'cancel' \| 'content'` und `confirmForm` (ID eines Formulars). Mit `confirmForm` wird der Bestätigen-Button `type="submit" form="<id>"` — Default-Button des Formulars, Klick und Enter laufen beide über `ngSubmit`; `confirm` feuert dann nicht (sonst Doppel-Submit). |
| Dialog-Komponente | Eigene `FixedCostCreateDialog`, vom Parent per `@if` gerendert — das Formular ist bei jedem Öffnen frisch, ohne Reset-Logik. Emittiert `saved` / `cancelled`. |
| Abbrechen während Request | Ignoriert (Muster `settings.ts` `cancelDeleteDialog`). Sonst bräche `takeUntilDestroyed` die Subscription ab, die Position entstünde trotzdem, und die Tabelle lüde nicht neu. |
| Fehlertexte | `bezeichnungErrorMessage` / `betragErrorMessage` nach `fixed-cost.validators.ts`, genutzt von Wizard, Inline-Edit und Dialog — «dieselben Fehlermeldungen» (AC 3) konstruktiv statt per Kopie. Scope-Erweiterung, im PR deklariert. |
| «Jetzt erfassen» | `<button type="button">` im Link-Look — ein `<a>` ohne `href` ist für Tastatur und Screenreader kein Button. |
| E2E | Kein neuer Test: US-03 hat 1 Happy Path + 1 Fehlerpfad (CONVENTIONS.md, Testing). Fokus und Enter werden im echten Browser per Playwright-Skript geprüft, weil jsdom 28 weder Implicit Submission noch echte Tabbability kennt. |

## Betroffene Dateien

- `frontend/src/app/shared/modal/modal.{ts,html,spec.ts}`
- **neu** `frontend/src/app/onboarding/fixed-cost-create-dialog.{ts,html,scss,spec.ts}`
- `frontend/src/app/onboarding/fixed-cost-list.{ts,html,scss,spec.ts}`
- `frontend/src/app/onboarding/fixed-cost-wizard.ts`
- `frontend/src/app/onboarding/fixed-cost.validators.ts`

## Implementierungsschritte

1. `app-modal`: Inputs `initialFocus` und `confirmForm`.
2. `FixedCostCreateDialog` mit Formular, `submitting`, `error`, `saved` / `cancelled`.
3. Fehlertexte in `fixed-cost.validators.ts` teilen, Wizard und Liste umstellen.
4. `FixedCostList`: beide Einstiege auf `openCreate()`, `saved` → schliessen + `load()`, `RouterLink` entfernen, Javadoc nachführen.

## Test-Strategie

- **Unit (Vitest/TestBed):** Modal-Inputs; Dialog: gültig → 1 POST mit getrimmter Bezeichnung
  → `saved`, jede Validierungsregel → 0 Requests + Fehlertext, Doppel-Submit → 1 POST,
  400/500 → Dialog offen + Werte erhalten + Meldung, Abbrechen ohne Request, Abbrechen während
  Request ignoriert; Liste: beide Einstiege öffnen den Dialog ohne Navigation, `saved` → neuer
  GET mit neuer Zeile, Abbrechen/Escape/Backdrop → kein Request, Formular beim erneuten Öffnen leer.
- **Browser (manuell, Playwright-Skript):** Fokus im Feld «Bezeichnung», Enter speichert.
- **E2E (bestehend):** `fixed-cost-wizard.spec.ts` als Regression für AC 9.

## Acceptance Criteria (aus dem Issue)

- [ ] Klick auf «+ Neue Position» öffnet einen modalen Dialog auf der Budget-Seite; die Route
      bleibt `/budget` (keine Navigation nach `/onboarding`)
- [ ] Der Link «Jetzt erfassen» im Leerzustand der Fixkosten-Tabelle öffnet denselben Dialog
- [ ] Der Dialog enthält die Felder Bezeichnung, Betrag (CHF) und Intervall mit denselben
      Validierungsregeln und Fehlermeldungen wie Wizard und Inline-Edit (nicht leer, Betrag ≥
      0.01, höchstens zwei Nachkommastellen); ungültige Eingaben lösen keinen Request aus
- [ ] Beim Öffnen liegt der Fokus im Feld «Bezeichnung»; Enter im Formular speichert wie der
      Bestätigen-Button
- [ ] «Speichern» legt die Position an, schliesst den Dialog, und die neue Zeile erscheint ohne
      Seiten-Reload in der Tabelle; Tabellen-Total, Karte «Monatliche fixe Ausgaben» und
      Einkommens-Warnung spiegeln den neuen Stand
- [ ] Während der Request läuft, ist «Speichern» gesperrt (kein Doppel-Submit)
- [ ] Schlägt das Speichern fehl, bleibt der Dialog offen, die Eingaben bleiben erhalten und
      eine Fehlermeldung erscheint im Dialog
- [ ] «Abbrechen», Escape und Klick auf den Hintergrund schliessen den Dialog ohne Request; die
      Tabelle bleibt unverändert, und beim nächsten Öffnen ist das Formular leer
- [ ] Der Onboarding-Wizard (`/onboarding`) funktioniert für neue User unverändert
