# [E2E-CAT-01] Playwright: Transaktionen kategorisieren (Happy Path + Fehlerpfad)

- **Issue:** [#124](https://github.com/dfme/budget-buddy/issues/124)
- **Task-ID:** `E2E-CAT-01`
- **Branch:** `feature/E2E-CAT-01-playwright-kategorisierung`
- **Story:** US-05 — Transaktionen kategorisieren (Auto + manuell)
- **Sprint:** Sprint 5
- **Bestätigt am:** 2026-08-25

---

## Ziel

E2E-Abdeckung der Must-Have-Story US-05: importierte Transaktionen sind kategorisiert sichtbar,
und eine Kategorie lässt sich manuell korrigieren. Ein Happy Path und ein Fehlerpfad — die in
CLAUDE.md («Testing: Frameworks») vorgeschriebene Menge, und zwar **pro Story**, nicht pro Issue.

Deshalb liegt der Test hier und nicht in einem Feature-PR: eine ID = eine Arbeitseinheit = ein
Branch = ein PR.

---

## Ausgangslage — nachgemessen, nicht angenommen

Die Fixture `e2e/fixtures/pdf/kontoauszug-synthetisch.pdf` enthält fünf Buchungen aus Juni 2025.
Das Summary bezieht **nur Belastungen** ein (`TransactionSummaryService:20`, `is_income = false`),
der Lohn fällt also heraus. Mit den Seeds aus Migration V04 ergibt das vier Kategoriezeilen:

| Buchungstext | Betrag | Kategorie | Quelle |
| --- | --- | --- | --- |
| `MIETE JUNI TESTVERWALTUNG` | 1'250.00 | `Sonstiges` | kein Seed, kein API-Key → Fallback |
| `DIGITEC GALAXUS AG` | 189.00 | `Shopping` | Seed `DIGITEC` |
| `MIGROS BERN BAHNHOF` | 45.60 | `Lebensmittel` | Seed `MIGROS` |
| `SBB MOBILE TICKET` | 12.40 | `Transport` | Seed `SBB` |

**Der Test hängt trotzdem nicht an diesen Werten.** Er liest die Ist-Kategorie aus dem DOM und
wählt das erste davon abweichende Label. Ein hart geschriebenes `Lebensmittel` würde bei einem
neuen V04-Seed reissen — und das ist eine Änderung, die man haben will, kein Testfehler.

---

## Entscheide

### Import als Vorbedingung läuft über die API, nicht durch die UI

`context.request.post('/api/import/pdf')` als Multipart (`file`, `PdfImportController:90`), danach
`GET /api/import/{jobId}/status` pollen, bis der Job nicht mehr `RUNNING` ist (ADR-14: die
Kategorisierung läuft asynchron).

Der Import ist Vorbedingung, nicht Gegenstand dieses Tests. Ihn durchzuklicken würde US-05 an
US-04 aufhängen: ein Bug im Upload-UI liesse dann auch diese beiden Fälle rot werden, ohne Hinweis
auf die eigentliche Ursache. Das ist dieselbe Begründung, mit der `fixtures/auth.fixture.ts` die
Registrierung über die API macht statt durchs Login-Formular.

### Die Vorbedingung bleibt lokal im Spec

Nicht als neue Fixture in `fixtures/auth.fixture.ts`. Eine zusätzliche geteilte Fixture würde
jeden bestehenden Test mit-riskieren, und bisher braucht nur dieser Spec sie. Wenn E2E-STS-01
(#125) dieselbe Vorbedingung braucht, ist das Herausziehen dann eine eigene, begründete Änderung.

### Notice-Text wird am `.notice__body` assertiert, nicht am Host

FE-UI-07 ([#195](https://github.com/dfme/budget-buddy/pull/195)) ist seit dem 24.08.2026 auf
`main` und rendert ein Icon-Zeichen in den `textContent` des Hosts. Eine Assertion am Host wäre
von Anfang an falsch — genau der Fehler, den #195 in `pdf-import.spec.ts` und
`fixed-cost-wizard.spec.ts` korrigieren musste.

### Der Test räumt sein gelerntes Lookup-Pattern selbst auf

**Scope-Erweiterung, im Planning bestätigt.** `PUT /api/transactions/{id}/category` lernt den
Buchungstext als Lookup-Pattern (`TransactionCategoryService:57`), und `category_lookup` ist
global — `empfaenger_pattern TEXT PRIMARY KEY`, kein `user_id` (`V04:12`). Genau diese Tabelle
nimmt `resetDatabase()` bewusst aus (`e2e/support/database.ts:36`), mit der Begründung, ihr Inhalt
stamme aus V04 und sei «Teil des Schemas, nicht Zustand eines Laufs».

Ohne Cleanup schreibt der Test also dauerhaft z. B. `MIGROS BERN BAHNHOF → Wohnen` in die
E2E-Datenbank. `findMatching` sortiert nach Pattern-Länge absteigend
(`CategoryLookupRepository:29`) — 19 Zeichen schlagen das Seed `MIGROS` mit 6. Ab dem zweiten
lokalen Lauf käme die Buchung in einer anderen Kategorie an, und die dokumentierte Invariante wäre
verletzt. In CI fällt das nicht auf: dort ist die Datenbank jedes Mal frisch.

Der Test merkt sich deshalb die korrigierten Buchungstexte und löscht sie in einem `afterEach`
wieder — über einen eigenen `pg`-Client aus den bereits exportierten `DB_*`-Konstanten
(`e2e/support/database.ts:13-17`).

**Bewusst nicht `resetDatabase()` erweitert:** das ist gemeinsamer Harness-Code, an dem jeder
andere Spec hängt. Für einen PR, der einen Test hinzufügt, ist das der falsche Blast Radius.

---

## Betroffene Dateien

| Datei | Art |
| --- | --- |
| `e2e/tests/categorization.spec.ts` | neu — beide Testfälle, Import-Vorbedingung, Cleanup |
| `e2e/README.md` | geändert — eine Zeile in der Tabelle «Aufbau» |
| `docs/plans/E2E-CAT-01-playwright-kategorisierung.md` | neu — dieser Plan |
| `docs/plans/README.md` | geändert — eine Indexzeile |

Kein Produktionscode: kein `.java`, kein Frontend, kein `pom.xml`.

---

## Implementierungsschritte

1. **Import-Vorbedingung** — Helper im Spec: Multipart-POST auf `/api/import/pdf`, `202` erwarten,
   `jobId` lesen, Status pollen bis `!== 'RUNNING'`, `DONE` assertieren.
2. **Happy Path** — `/categories?month=2025-06`, erste Zeile über `.drilldown-toggle` aufklappen,
   ersten Eintrag lesen (Buchungstext + Ist-Kategorie aus dem `<select>`), erstes abweichendes
   Label wählen, keine Fehlermeldung, **Reload**, Zielkategorie aufklappen, dieselbe Buchung steht
   dort mit der neuen Kategorie.
3. **Fehlerpfad** — `page.route('**/api/transactions/*/category', …)` antwortet `500`. Erwartet:
   `<app-notice class="save-notice" variant="error">` mit «Die Kategorie konnte nicht gespeichert
   werden.» (`category-overview.ts:406`) und ein `<select>`, das auf den alten Wert zurückfällt
   (`applyCategory(…, previous)`, Zeile 405). Der Rollback ist die eigentliche Aussage: eine
   Anzeige, die einen Stand behauptet, den der Server nicht hat, wäre schlimmer als die Meldung.
4. **Cleanup** — `afterEach` löscht die gemerkten Patterns aus `category_lookup`.
5. `e2e/README.md` und `docs/plans/README.md` nachziehen.

---

## Test-Strategie

Zwei Playwright-Fälle, keine neuen Unit- oder Integrationstests — der Task fügt keinen
Produktionscode hinzu, es gibt also nichts zu unit-testen.

**Gegenprobe, dass die Tests diskriminieren** (ein grüner Lauf allein beweist nichts). Drei
Mutationen, jede muss den betroffenen Test rot machen:

- **A — Server quittiert, persistiert aber nicht.** Der Korrektur-PUT wird mit `200` abgefangen,
  ohne dass sich am Bestand etwas ändert. Der Happy Path muss fallen, sonst prüft er nur das
  optimistische Update und nicht die Persistenz.
- **B — Interception im Fehlerpfad entfernt.** Dann läuft der echte PUT durch, es gibt kein
  Fehlerbanner, und der Fehlerpfad muss fallen. Damit ist belegt, dass das Banner am `500` hängt
  und nicht an etwas Beiläufigem.
- **C — die Korrektur ist ein No-op.** Im Fehlerpfad wird dieselbe Kategorie erneut gewählt;
  `changeCategory` kehrt dann sofort zurück (`previous === category`), es fliegt kein PUT. Der
  Test muss fallen. Das schliesst die Vakuitätslücke der Rollback-Assertion: ohne sie könnte
  `toHaveValue(previous)` auch dann grün sein, wenn die Auswahl gar nicht gegriffen hat.

Zur Reihenfolge: A prüft den Happy Path, B und C prüfen den Fehlerpfad an zwei verschiedenen
Stellen — B die Ursache der Meldung, C die Nicht-Trivialität des Rollbacks.

---

## Acceptance Criteria (aus #124)

- [ ] Happy Path: eingeloggt → Kategorie-Übersicht zeigt die Kategorien der importierten
      Transaktionen → eine Transaktion manuell umkategorisieren → neue Kategorie ist nach Reload
      persistent
- [ ] Fehlerpfad: Korrektur schlägt serverseitig fehl (500 auf `PUT /api/transactions/{id}/category`)
      → Fehlermeldung, die angezeigte Kategorie fällt auf den alten Wert zurück
- [ ] Der Test nutzt die `authenticatedPage`-Fixture aus #91 als Vorbedingung — kein Durchklicken
      durchs Login-Formular
- [ ] Test liegt unter `e2e/tests/` und läuft grün via `npm test` in `e2e/`
- [ ] Der Test läuft im bestehenden CI-Job `E2E (Playwright)` mit — kein neuer Job nötig
- [ ] Der Test hängt nicht an einem echten Claude-API-Call — die Kategorisierung läuft über die
      Lookup-Tabelle oder den `Sonstiges`-Fallback (kein `ANTHROPIC_API_KEY` in CI)

**Zum AC-Wortlaut:** Die ACs nennen `PUT /transactions/{id}/category`; der echte Pfad ist
`/api/transactions/{id}/category` (`TransactionCategoryController:29`, `/api`-Präfix aus INFRA-17).
Nur der Issue-Text ist knapp, keine Abweichung in der Sache.

---

## Nicht im Scope

- Keine Änderung an `resetDatabase()` (Begründung oben)
- Keine neue geteilte Fixture in `fixtures/auth.fixture.ts`
- Kein neuer CI-Job — `npm test` in `e2e/` nimmt jeden Spec unter `tests/` mit
- `Area` im Board bleibt leer; Board-Metadaten sind laut CLAUDE.md eine Team-Entscheidung
