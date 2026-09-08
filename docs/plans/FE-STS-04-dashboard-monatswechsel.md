# [FE-STS-04] Dashboard-Monatswechsel

- **Issue:** [#250](https://github.com/dfme/budget-buddy/issues/250)
- **Task-ID:** `FE-STS-04`
- **Branch:** `feature/FE-STS-04-dashboard-monatswechsel`
- **Story:** US-12 — Zwischen Monaten wechseln
- **Sprint:** Sprint 6
- **Bestätigt am:** 2026-09-07

## Ausgangslage

Das Dashboard kennt keinen Monat: `Dashboard.load()` ruft `GET /api/budget/safe-to-spend` ohne
Parameter, und `currentMonth()` steht als lokale Funktion nur für den Hinweis zur
Buchungsrichtung darin. Die Gegenseite ist seit BE-STS-06 (#248, PR #274) fertig — der Endpoint
nimmt `month=YYYY-MM` entgegen, liefert `status: 'OPEN' | 'CLOSED'` und lehnt Zukunftsmonate mit
400 ab. `safe-to-spend.model.ts` spiegelt das Feld bereits; angezeigt wird es noch nirgends.

Die Kategorie-Übersicht (`category-overview.ts`) löst denselben Monatswechsel seit FE-CAT-03/04
vollständig. Dieser Plan überträgt das Muster, statt ein zweites zu erfinden.

## Entscheide (vom Team bestätigt)

| Frage | Entscheid | Begründung |
| ----- | --------- | ---------- |
| Welcher Monat beim Öffnen ohne Query-Param? | **Neuester Monat mit Daten** aus `GET /api/transactions/months`; leere Liste oder Fehler → laufender Monat | Wörtliche Lesart von US-12 («standardmässig der aktuellste Monat»). Preis: Ohne brauchbaren Query-Param hängt der erste Safe-to-Spend-Request an der Monatsliste — sichtbar als «Lädt …», nicht als leere Seite. Trägt die URL einen gültigen Monat, entfällt die Wartezeit. |
| «Keine Daten» vs. Safe-to-Spend-Karte | Laufender Monat: Hinweis **über** der Karte, Betrag bleibt | Der Betrag ist ohne Buchungen weiterhin gültig (Einkommen − Fixkosten). Ihn zu verbergen widerspräche dem Core Value: eine Zahl, der man trauen kann, wird nicht wegen fehlender Ausgaben versteckt. |
| «Keine Daten» vs. «Abgeschlossen» | Vergangener Monat ohne Daten: Hinweis **statt** des Banners | «Abgeschlossen» sagt dort nichts, woran der Nutzer etwas ändern könnte; «PDF hochladen?» schon. Zwei Banner übereinander wären Lärm. |
| Direktsprung-Dropdown | **Ja**, wie in der Kategorie-Übersicht | `GET /api/transactions/months` wird für den Keine-Daten-Hinweis ohnehin geladen; das Dropdown kostet danach nur noch `[months]` und `[selected]`. |
| Hinweis zur Buchungsrichtung (BE-PDF-10) | Folgt dem gewählten Monat, ausgeblendet bei `CLOSED` | Sein Satz begründet sich über den Safe-to-Spend («kann zu tief sein»); ohne Berechnung trägt er nicht. |

## Betroffene / neue Files

**Ändern**

- `frontend/src/app/dashboard/safe-to-spend.service.ts` — `getSafeToSpend(month?: string)`
- `frontend/src/app/dashboard/dashboard.ts` — Monat-Signal, Query-Param-Sync, Monatsliste, CLOSED
- `frontend/src/app/dashboard/dashboard.html` — `app-month-nav`, Abgeschlossen-Banner, Keine-Daten-Hinweis
- `frontend/src/app/dashboard/dashboard.scss` — `.month-selector`, `.status.empty`
- `frontend/src/app/dashboard/dashboard.spec.ts`, `safe-to-spend.service.spec.ts`

**Neu**

- `docs/plans/FE-STS-04-dashboard-monatswechsel.md` (dieser Plan), Indexzeile in `docs/plans/README.md`

## Implementierungsschritte

1. `getSafeToSpend(month?)` mit optionalem `HttpParams`. Ohne Argument bleibt der Request
   zeichengleich — kein anderer Aufrufer bricht.
2. Monat-Zustand nach dem Muster `category-overview.ts:269-356`: `month`-Signal, `MONTH_PATTERN`,
   `shiftMonth`/`formatMonth`/`currentMonth`, Gleichheits-Wache gegen den doppelten Request,
   `replaceUrl` bei kaputtem Parameter.
3. Default-Auflösung: gültiger Query-Param gewinnt, sonst der laufende Monat — genau wie in
   `category-overview.ts:330`. Die Monatsliste speist ausschliesslich Dropdown und
   Keine-Daten-Hinweis, nie den Default: Ein Default aus der Liste stellte die beiden
   Schwesteransichten am selben Tag auf verschiedene Monate, und weil Kontoauszüge erst nach
   Monatsende kommen, wäre das der Regelfall — die Startseite zeigte statt der Kernzahl das
   «Abgeschlossen»-Banner des Vormonats (Review-Befund zu PR #280).
4. Laufende Requests beim Monatswechsel canceln (`pendingRequest`, `pendingUncertainRequest` wie
   in `category-overview.ts:713/754`) — sonst überschreibt eine spät eintreffende Antwort des
   verlassenen Monats die des neuen. Beim Prüflisten-Request **vor** dem frühen `return` für
   vergangene Monate, weil dort kein Folge-Request nachkommt (Review-Befund zu PR #280).
5. `MonthNav` mit Stepper und Dropdown einbinden; `monthOptions` filtert Zukunftsmonate und nimmt
   den angezeigten Monat immer auf.
6. `status === 'CLOSED'` ersetzt Betrag, Wochen-Label und No-Income-Block durch ein `app-notice`.
7. Keine-Daten-Hinweis mit dem Wortlaut aus FE-CAT-08, nur bei erfolgreich geladener Monatsliste.
8. Hinweis zur Buchungsrichtung folgt dem Monat, verschwindet bei `CLOSED`.
9. `applySuggestion()` lädt den angezeigten Monat neu, nicht den laufenden.

## Test-Strategie

Vitest + TestBed mit `provideRouter([])` und `provideLocationMocks()` wie in
`category-overview.spec.ts`:

- Default ist der laufende Monat, auch ohne Buchungen darin — geprüft am Ergebnis, nicht nur am
  Request-Parameter; Deep-Link gewinnt gegen den Default; kaputter Parameter fällt zurück und rückt
  die URL zurecht
- Stepper und Dropdown: je genau ein Request, URL nachgezogen
- `CLOSED` → Banner statt Betrag; laufender Monat ohne Daten → Hinweis **und** Betrag;
  vergangener Monat ohne Daten → Hinweis **statt** Banner
- Fehlgeschlagene Monatsliste → kein Hinweis, Dashboard funktioniert
- Richtungs-Hinweis folgt dem Monat und verschwindet bei `CLOSED`
- Veraltete Antworten werden verworfen: Safe-to-Spend und Prüfliste je einmal — der Request des
  verlassenen Monats ist `cancelled`, und die Anzeige bleibt beim neuen Monat
- `safe-to-spend.service.spec.ts`: Request mit und ohne `month`

Kein E2E-Test: Die DoD verlangt Vitest/TestBed, und `e2e/tests/safe-to-spend.spec.ts` arbeitet auf
einem frisch registrierten Konto ohne Buchungen — dort gibt es keinen zweiten Monat zu wechseln.

## Bekannte Grenze

`GET /api/transactions/months` liefert laut seiner OpenAPI-Beschreibung nur Monate **mit
Ausgaben**; ein Monat mit ausschliesslich Gutschriften gilt damit als «keine Daten». Dieselbe
Grundlage, auf der FE-CAT-08 heute schon steht.

## Acceptance Criteria (aus #250)

- [ ] Dashboard öffnet standardmässig mit dem aktuellsten Monat
- [ ] `MonthNav` erlaubt Wechsel zu anderen Monaten, synchronisiert über Query-Param
- [ ] Vergangene Monate zeigen ein «Abgeschlossen»-Banner statt der Safe-to-Spend-Berechnung
- [ ] Monate ohne Daten zeigen den «Keine Daten»-Hinweis analog FE-CAT-08
