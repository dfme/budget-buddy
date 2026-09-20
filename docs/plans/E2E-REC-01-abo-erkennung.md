# [E2E-REC-01] Playwright: Abo-Erkennung

- **Issue:** [#256](https://github.com/dfme/budget-buddy/issues/256)
- **Task-ID:** `E2E-REC-01`
- **Branch:** `feature/E2E-REC-01-abo-erkennung`
- **Story:** US-08 — Wiederkehrende Ausgaben (Abos) erkennen
- **Sprint:** Sprint 6
- **Bestätigt am:** 2026-09-19

## Kontext

Baut auf der E2E-Harness aus #91 (Setup, CI-Job, `authenticatedPage`-Fixture) sowie auf der
kompletten US-08-Kette auf: `DB-09` (Tabelle), `BE-REC-01` (Erkennung + Notification beim Import),
`BE-REC-02` (REST-Endpoints), `FE-REC-01` (Screen `/abos`). Erkennung und Endpoints existieren
bereits vollständig; dieser Task liefert nur die Playwright-Abdeckung.

Die Erkennung läuft synchron am Ende des Import-Jobs (`ImportJobRunner.java:232`,
`detectRecurringExpenses`), noch bevor der Job auf `DONE` gesetzt wird — sobald der Status-Poll
`DONE` meldet, ist `GET /api/recurring-expenses` bereits aktuell. Regel: ein Empfänger gilt als
Abo, wenn er in zwei aufeinanderfolgenden Kalendermonaten mit ±2 % gleichem Betrag verbucht wurde
(`RecurringExpenseService.qualify`). Ein einmal `DISMISSED`er Empfänger wird nie wieder
aufgenommen (`RecurringExpenseService.java:116-131`) — auch nicht bei einem neuen, für sich
qualifizierenden Monatspaar.

## Entscheide

- **Zwei synthetische PDF-Fixtures statt einer**, im generischen Layout der bestehenden
  `kontoauszug-synthetisch.pdf` (unkomprimiertes ASCII, `Saldovortrag`/`Schlusssaldo`-Rahmen). Die
  Kopfzeile mit dem Auszugszeitraum ist rein dekorativ und wird vom Parser nie geprüft
  (`SwissBankStatementParser.detectFormat`/`parseGeneric`) — eine einzelne Fixture kann deshalb
  Buchungen über zwei Kalendermonate tragen.
  - `kontoauszug-abo-erkennung.pdf`: zwei Buchungen desselben fiktiven Empfängers
    „STREAMBOX.CH ABO" zu je CHF 15.90 in Juni und Juli 2025 — Grundlage für den Happy Path.
  - `kontoauszug-abo-persistenz.pdf`: eine dritte Monatsbuchung (August 2025, gleicher
    Empfänger/Betrag) — Nachweis für den Alt-Pfad, dass die Erkennung nach „Kein Abo" nicht erneut
    anspringt.
  - Empfängertext ohne Ziffern gewählt (`ExpenseHistoryService.normalise` entfernt Ziffernfolgen),
    damit der `payeeKey` in beiden Fixtures unverändert identisch ist.
- **Import über die API**, nicht über die Upload-UI — dieselbe Begründung wie in
  `categorization.spec.ts`: der Import ist Vorbedingung, nicht Gegenstand dieses Tests.
- **Kein Cleanup nötig**: `recurring_expenses` und `notifications` tragen `user_id`, jeder Test
  registriert über `authenticatedPage`/`authenticatedContext` einen frischen User — anders als bei
  `category_lookup` (global, siehe `categorization.spec.ts`) gibt es hier keine Cross-Test-Leiche.

## Betroffene / neue Files

### Neu
- `e2e/fixtures/pdf/kontoauszug-abo-erkennung.pdf`
- `e2e/fixtures/pdf/kontoauszug-abo-persistenz.pdf`
- `e2e/tests/recurring-expenses.spec.ts`

### Geändert
- `e2e/README.md` — neue Zeile in der Aufbau-Tabelle und im Scope-Abschnitt (US-08)
- `docs/plans/README.md` — neue Zeile für diesen Plan

## Implementierungsschritte

1. PDF-Fixtures ablegen (bereits lokal gebaut und gegen die Xref-Struktur verifiziert)
2. `recurring-expenses.spec.ts`: Happy Path (Import, Poll auf `DONE`, `/abos`, Zeile mit „Neu" und
   „seit Juni 2025")
3. `recurring-expenses.spec.ts`: Alt-Pfad (Dismiss entfernt die Zeile, zweiter Import derselben
   Empfänger-Gruppe bringt sie nicht zurück)
4. `e2e/README.md` und `docs/plans/README.md` aktualisieren
5. `npm run typecheck`, lokal `npm test` (falls Backend-JAR + Postgres verfügbar)
6. Security-Review + lokaler Review, PR

## Test-Strategie

Nur Playwright — das ist der gesamte Task. Ein Fall pro AC aus dem Issue:

- Happy Path: gleicher Empfänger/Betrag in 2 Folgemonaten importiert → erscheint mit „Neu"-Label
- Alt-Pfad: „Kein Abo" entfernt den Eintrag dauerhaft, auch nach einem weiteren Import desselben
  Empfängers

Einstieg über `authenticatedPage`/`authenticatedContext` (E2E-Harness aus #91).

## Acceptance Criteria (aus dem Issue)

- [ ] Happy Path: gleicher Empfänger/Betrag in 2 Folgemonaten importiert → erscheint in der
      Abo-Übersicht mit „Neu"-Label
- [ ] Fehlerpfad/Alt-Pfad: „Kein Abo" entfernt den Eintrag dauerhaft, auch nach einem weiteren
      Import desselben Empfängers
- [ ] Der Test nutzt die `authenticatedPage`-Fixture aus #91 als Vorbedingung
- [ ] Test liegt unter `e2e/tests/` und läuft grün via `npm test` in `e2e/`
- [ ] Der Test läuft im bestehenden CI-Job `E2E (Playwright)` mit — kein neuer Job nötig
