# [FE-FC-13] Onboarding-Wizard an das Tabelle+Dialog-Muster von /budget angleichen

- **Issue:** [#376](https://github.com/dfme/budget-buddy/issues/376)
- **Task-ID:** `FE-FC-13`
- **Branch:** `feature/FE-FC-13-wizard-tabelle-dialog`
- **Story:** — (kein us-*-Label)
- **Sprint:** Sprint 7
- **Bestätigt am:** 2026-09-28

## Entscheide

- **Gemeinsame Komponente statt Duplikat.** Der Fixkosten-Abschnitt von `/budget` (Laden, Tabelle
  mit Total, Inline-Edit, Löschen-Modal, «+ Neue Position», `FixedCostCreateDialog`,
  Einkommens-Warnung) wird aus `FixedCostList` in `FixedCostSection` herausgelöst; `/budget` und
  `/onboarding` betten sie beide ein. Doppelpflege zweier Erfassungs-UIs ist der Anlass des Issues.
- **Datenquelle `GET /api/fixed-costs`** wie auf `/budget` — Neuladen nach jedem Schreiben. Die
  Beschriftung des Abschluss-Buttons hängt an «Tabelle nicht leer oder Einkommen gespeichert»
  statt am Session-Flag `hasSaved`. Folge: wer alles wieder löscht, sieht wieder «Später
  erfassen»; nach einem Reload mitten im Onboarding bleiben erfasste Positionen sichtbar.
- **Überschrift als Input** (`heading`, `headingLevel` 2 | 3): `/budget` führt «Erfasste Fixkosten»
  als h3 unter «Ausgaben», der Wizard «Fixkosten» als h2.
- **Wizard-Breite 40rem statt 28rem**, wie `/budget` — die beschrifteten Aktions-Buttons passen laut
  FE-FC-08 erst in die 40rem-Spalte.
- **Scope-Erweiterung (bestätigt):** Die E2E-Tests `fixed-cost-wizard.spec.ts` und
  `onboarding-completion.spec.ts` fahren das alte Inline-Formular; E2E läuft auf jedem PR
  (`build.yml`). Sie und die Doku werden im selben PR nachgezogen.

## Betroffene Dateien

Neu:

- `frontend/src/app/onboarding/fixed-cost-section.{ts,html,scss,spec.ts}`

Geändert:

- `frontend/src/app/onboarding/fixed-cost-list.{ts,html,scss,spec.ts}`
- `frontend/src/app/onboarding/fixed-cost-wizard.{ts,html,scss,spec.ts}`
- `frontend/src/app/onboarding/fixed-cost-create-dialog.ts` (Javadoc)
- `frontend/src/app/settings/settings.ts` (Kommentar-Verweis auf das entfallene Wizard-Muster)
- `e2e/tests/fixed-cost-wizard.spec.ts`, `e2e/tests/onboarding-completion.spec.ts`, `e2e/README.md`
- `docs/requirements/US-03-*.md`, ggf. `docs/CONVENTIONS.md`

## Implementierungsschritte

1. `FixedCostSection` anlegen, Abschnitt samt SCSS aus `FixedCostList` verschieben; öffentlich
   `reload()` und `hasPositions`.
2. `FixedCostList` auf Seite reduzieren; `IncomeCard (saved)` → `reload()` der Section.
3. `FixedCostWizard`: Inline-Formular entfernen, Section einbetten, `hasEnteredData` aus
   `hasPositions` + `incomeSaved`, `finishOnboarding()` unverändert, Javadoc nimmt den
   «bewusst nicht Teil»-Absatz zurück.
4. E2E auf Dialog + Tabelle umstellen.
5. Doku nachziehen.

## Test-Strategie

- `fixed-cost-section.spec.ts`: Verhalten der Section (Tabelle, Edit, Delete, Dialog, Warnung,
  Überschrift, `hasPositions`, `reload()`).
- `fixed-cost-list.spec.ts`: Seitenebene (Überschriften, Reihenfolge, Neuladen nach Einkommen,
  Dashboard-Integration).
- `fixed-cost-wizard.spec.ts`: AC7 (Position über Dialog erscheint ohne Navigation), Dialog statt
  Inline-Formular, Edit/Delete vorhanden, IncomeCard oberhalb, Button-Beschriftung, Abschluss.
- Playwright: Happy Path, Fehlerpfad, Weg A über den Dialog.

## Acceptance Criteria (aus dem Issue)

- [ ] `/onboarding` zeigt oberhalb bzw. unterhalb der Aktion «+ Neue Position» eine Tabelle der
      in dieser Session bereits erfassten Fixkosten (Bezeichnung, Monatsbetrag, Total) — analog
      zur Tabelle auf `/budget`
- [ ] «+ Neue Position» öffnet denselben `FixedCostCreateDialog` wie auf `/budget`, statt ein
      dauerhaftes Inline-Formular anzuzeigen
- [ ] Nach dem Anlegen einer Position im Dialog erscheint sie sofort in der Tabelle (kein
      zusätzlicher Page-Reload nötig)
- [ ] Bearbeiten/Löschen bereits erfasster Positionen ist im Wizard möglich
- [ ] Der Abschluss-Button bleibt erhalten und funktioniert unverändert (`finishOnboarding()`,
      `hasEnteredData()`)
- [ ] Die eingebettete `IncomeCard` bleibt wie bisher oberhalb des Fixkosten-Abschnitts bestehen
- [ ] Test: Fixkosten-Tabelle im Wizard zeigt eine über den Dialog angelegte Position, ohne dass
      die Seite neu geladen wird (Vitest/Angular TestBed)
