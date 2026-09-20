# [INFRA-45] Import-Poll-Helper der E2E-Tests liegt dreifach kopiert in den Specs

- **Issue:** [#332](https://github.com/dfme/budget-buddy/issues/332)
- **Task-ID:** `INFRA-45`
- **Branch:** `feature/INFRA-45-import-poll-helper`
- **Story:** — (kein us-*-Label)
- **Sprint:** Sprint 7
- **Bestätigt am:** 2026-09-20

## Ausgangslage

Der Helfer, der einen PDF-Import über die API anstösst und auf den Endzustand des Jobs pollt,
steht dreimal zeichengleich in den Specs:

- `e2e/tests/categorization.spec.ts:105-140` (`importFixtureStatement`)
- `e2e/tests/month-switch.spec.ts:81-119` (`importFixtureStatement`)
- `e2e/tests/recurring-expenses.spec.ts:68-99` (`importFixture`)

Jede Kopie trägt zusätzlich die lokale Konstante `IMPORT_TIMEOUT_MS = 60_000`.

Die drei Kopien sind nicht ganz deckungsgleich: `categorization.spec.ts` UND
`month-switch.spec.ts` prüfen vor dem Poll zusätzlich die Anzahl geparster Buchungen
(`FIXTURE_TRANSACTION_COUNT`) — nicht nur `month-switch.spec.ts`, wie der Issue-Text nahelegt.
`recurring-expenses.spec.ts` hat diese Prüfung nie gehabt und nimmt den Dateipfad bereits als
Parameter (`importFixture(request, fixturePath)`), weil sie zwei verschiedene Fixtures importiert.

## Entscheid

`e2e/support/import.ts` bekommt die Form von `recurring-expenses.spec.ts`s Version
(`fixturePath` als Parameter, Dateiname daraus abgeleitet) plus einen optionalen dritten
Parameter `expectedTransactionCount` für die Vorbedingungs-Prüfung. Die Prüfung bleibt dort
aktiv, wo sie heute schon läuft (`categorization.spec.ts`, `month-switch.spec.ts`), und wird
nicht auf `recurring-expenses.spec.ts` ausgedehnt.

## Betroffene Dateien

- Neu: `e2e/support/import.ts`
- `e2e/tests/categorization.spec.ts`
- `e2e/tests/month-switch.spec.ts`
- `e2e/tests/recurring-expenses.spec.ts`

## Implementierungsschritte

1. `e2e/support/import.ts` anlegen: exportiert `IMPORT_TIMEOUT_MS` und
   `importFixture(request, fixturePath, expectedTransactionCount?)`.
2. `categorization.spec.ts`: lokalen Helper + Konstante entfernen, Import aus
   `../support/import`, Aufruf `importFixture(authenticatedContext.request, FIXTURE_PDF, FIXTURE_TRANSACTION_COUNT)`.
3. `month-switch.spec.ts`: analog.
4. `recurring-expenses.spec.ts`: lokalen Helper + Konstante entfernen, Import aus
   `../support/import`, Aufrufe unverändert (kein dritter Parameter).
5. Spec-lokale JSDoc-Kommentare, die nur die jetzt entfernte Kopie erklärten, bereinigen; die
   allgemeine Begründung (API statt Upload-UI, Warten auf `DONE`) wandert in den Helper.

## Test-Strategie

- `npm run typecheck` in `e2e/`
- `npm test` in `e2e/` (volle Playwright-Suite gegen `-Pprod`-JAR + Postgres)

## Acceptance Criteria (aus dem Issue)

- [ ] Der Import-Poll liegt genau einmal unter `e2e/support/`, alle drei Specs rufen ihn auf
- [ ] Die Zusatzprüfung aus `month-switch.spec.ts` bleibt in ihrer Wirkung erhalten — und gilt
      weiterhin nur dort (präziser: dort und in `categorization.spec.ts`, siehe oben)
- [ ] `npm run typecheck` und `npm test` in `e2e/` laufen grün (volle Suite, gegen
      `-Pprod`-JAR + Postgres)
- [ ] Kein neuer CI-Job; der bestehende `E2E (Playwright)` deckt das weiterhin ab
