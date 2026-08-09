# [FE-CAT-03] Manuelles Korrigieren von Kategorien

- **Issue:** [#32](https://github.com/dfme/budget-buddy/issues/32)
- **Task-ID:** `FE-CAT-03`
- **Branch:** `feature/FE-CAT-03-kategorie-korrektur`
- **Story:** US-05 — Transaktionen kategorisieren (Auto + manuell)
- **Sprint:** Sprint 4
- **Bestätigt am:** 2026-08-10

## Ziel

Eine falsch kategorisierte Transaktion lässt sich direkt in der Kategorie-Übersicht
korrigieren: Kategorie-Zeile aufklappen, Transaktion suchen, im Dropdown die richtige
der 13 Kategorien wählen. Die Auswahl ist sofort sichtbar; scheitert der Server-Call,
steht wieder der alte Wert da.

## Ausgangslage und Scope-Erweiterung

Die Acceptance Criteria verlangen ein Dropdown **pro Transaktion**. Eine Einzeltransaktion
ist im Produkt bisher nirgends sichtbar:

- Backend: nur `GET /transactions/summary` (Aggregate pro Kategorie,
  `TransactionSummaryController.java:33`) und `PUT /transactions/{id}/category`
  (`TransactionCategoryController.java:37`). Kein `GET`, das einzelne Transaktionen
  samt `id` liefert — und ohne `id` ist der PUT nicht adressierbar.
- Frontend: `category-overview.html` rendert ausschliesslich Summen-Zeilen.
- Backlog: US-13 («Einzeltransaktionen pro Kategorie einsehen») hat **kein einziges
  Issue** (`gh issue list --label us-13` → `[]`). Das Ticket, das diese Liste
  normalerweise trägt, existiert nicht.

Die im Issue genannte Abhängigkeit #19 (BE-CAT-04) ist erfüllt, deckt diese Lücke aber
nicht ab. Entscheid des Users (2026-08-10): **`GET /transactions` kommt in diesem PR
mit** — faktisch die fehlende Backend-Hälfte von US-13 —, statt #32 auf ein neues
Backend-Issue zu blocken. Die Erweiterung wird im PR-Body deklariert.

Ebenfalls entschieden: Die Liste lebt als **Drilldown in `/categories`**, nicht als
eigene Route. Sie erbt damit Monatsnavigation, Lade-/Fehler-/Leerzustand und die
`--cat-<slug>`-Farben der bestehenden Übersicht und trifft die Formulierung von US-13
(«pro Kategorie») wörtlich.

## Acceptance Criteria (aus Issue)

- [ ] Dropdown zeigt alle 13 Kategorien aus CLAUDE.md
- [ ] Kategorie-Änderung wird sofort im UI reflektiert (optimistic update)
- [ ] Bei API-Fehler wird die Änderung zurückgerollt

## Betroffene Files

### Backend — neu

| File | Inhalt |
| ---- | ------ |
| `transaction/TransactionListController.java` | `GET /transactions?month=YYYY-MM[&category=<Label>]`, `@AuthenticationPrincipal Long userId`, Swagger-Annotationen (DoD) |
| `transaction/TransactionListService.java` | Monatsgrenzen, Kategorie-Filter, Sortierung, Mapping auf `TransactionResponse` |
| `transaction/MonthParser.java` | package-private Helfer; `parseMonth` aus `TransactionSummaryService` herausgezogen |

### Backend — geändert

| File | Änderung |
| ---- | -------- |
| `transaction/TransactionExceptionHandler.java` | `assignableTypes` um `TransactionListController` ergänzen — sonst greifen 400/404 dort nicht |
| `transaction/TransactionSummaryService.java` | nutzt `MonthParser` statt der eigenen privaten Kopie |

### Frontend — neu

| File | Inhalt |
| ---- | ------ |
| `transactions/transaction.model.ts` | Spiegel von `TransactionResponse` |
| `transactions/transaction.service.ts` | `list(month, category)` + `updateCategory(id, category)`, zustandslos wie `TransactionSummaryService` |

### Frontend — geändert

`transactions/category-overview.ts`, `category-overview.html`, `category-overview.scss`.

## Entscheide

### Kein neues Repository-Query

Wiederverwendet wird `findByUserIdAndIncomeFalseAndBuchungsdatumBetween`
(`TransactionRepository.java:16`). Zwei Gründe:

1. Die Methode ist bereits user-scoped — es entsteht keine neue IDOR-Fläche.
2. Der Kategorie-Filter muss `category = NULL` als `Sonstiges` behandeln, genau wie das
   Summary (`TransactionSummaryService`). Eine SQL-Bedingung müsste diese Sonderregel ein
   zweites Mal kodieren; in Java steht sie an einer Stelle.

Das Volumen ist ein Monat Kontoauszug, also Grössenordnung 50–200 Zeilen — dieselbe
Menge, die das Summary schon heute in Java aggregiert.

### `null` → `"Sonstiges"` bereits in der Response

Sonst hätte das Dropdown bei nicht kategorisierten Transaktionen keine gültige Auswahl
und müsste den Sonderfall im Frontend noch einmal abbilden.

### Sortierung `buchungsdatum DESC, id DESC`

Deterministisch auch bei mehreren Buchungen am selben Tag — Voraussetzung für stabile
Tests.

### Antwort ist `List<TransactionResponse>`

Das DTO existiert und trägt die für den PUT nötige `id`. Kein Wrapper-Objekt: der
Monat steht bereits im Request, und ein Total wie beim Summary gibt es hier nicht.

### Natives `<select>`, keine eigene Dropdown-Komponente

`input.ts:12` hält bereits fest, dass `<select appInput>` bewusst nativ bleibt — die
native Auswahl bringt Tastatur- und Touch-Verhalten mit, das ein Nachbau erst wieder
herstellen müsste.

### Optimistic Update mit Nachladen bei Erfolg

Die Auswahl setzt die Kategorie sofort im Signal (AC 2), dann läuft der PUT. Bei Erfolg
werden Summary **und** die offene Transaktionsliste nachgeladen: sonst stünden Donut,
Summen und Prozentanteile still auf den alten Werten, während die Zeile darunter schon
die neue Kategorie zeigt. Die Transaktion wandert dabei sichtbar in ihre neue Kategorie.
Bei Fehler kommt der alte Wert zurück ins Signal, plus `app-notice variant="error"` (AC 3).

### Immer nur eine Kategorie offen

Hält den State auf ein einzelnes Signal-Objekt `{ category, transactions, loading, error }`
begrenzt statt auf eine Map über alle Kategorien.

## Implementierungsschritte

1. `MonthParser` anlegen, `TransactionSummaryService` darauf umstellen, bestehende Tests
   laufen lassen (reines Refactoring, kein Verhaltensänderung).
2. `TransactionListService` + `TransactionListController` implementieren,
   `TransactionExceptionHandler` erweitern.
3. Backend-Tests schreiben (Unit + Integration, siehe unten), `mvn verify`.
4. `transaction.model.ts` + `transaction.service.ts` anlegen.
5. `CategoryOverview` um Drilldown-State, Toggle und `changeCategory` erweitern.
6. Template: aufklappbare Kategorie-Zeile (`<button>` mit `aria-expanded`/`aria-controls`),
   verschachtelte Transaktionszeilen mit Datum, Buchungstext, Betrag und `<select appInput>`.
7. SCSS für die verschachtelten Zeilen.
8. Frontend-Tests schreiben, `npm test` und `ng build`.

## Test-Strategie

### Backend

`TransactionListServiceTest` (JUnit 5 + Mockito + AssertJ):

- Monatsgrenzen inklusive (erster und letzter Tag)
- Einkommen wird nicht geliefert
- `category = null` erscheint als `Sonstiges`
- Kategorie-Filter `Sonstiges` trifft auch die `null`-Zeilen
- Sortierung `buchungsdatum DESC, id DESC`
- ungültiger Monat → `InvalidMonthException`, ungültige Kategorie → `InvalidCategoryException`

`TransactionListControllerIntegrationTest` (`@SpringBootTest` + Testcontainers, Muster von
`TransactionSummaryControllerIntegrationTest`):

- 200 Happy Path mit Feldern und Reihenfolge
- 400 bei kaputtem `month`
- 401 ohne JWT-Cookie
- **Mandantentrennung:** User B fragt denselben Monat ab und sieht keine Zeile von User A

### Frontend (Vitest + TestBed)

`transaction.service.spec.ts`: URL und Params der Liste, Methode und Body des PUT.

Ergänzungen in `category-overview.spec.ts`:

- Dropdown hat exakt 13 Optionen und trägt die Labels aus `CATEGORIES` (AC 1)
- Aufklappen einer Kategorie lädt die Transaktionen
- Auswahl ist im DOM sichtbar, **bevor** die PUT-Antwort geflusht wird (AC 2)
- Fehlerantwort stellt den alten Wert wieder her und zeigt die Fehlermeldung (AC 3)

### Nicht in diesem PR

E2E: [#124](https://github.com/dfme/budget-buddy/issues/124) (E2E-CAT-01) ist dafür offen.

Kein neues Issue für die US-13-Lücke und keine Board-Änderung — Einplanung ist
Kapazitätsentscheid des Teams. Der PR-Body meldet, dass `GET /transactions` jetzt
existiert, damit ein künftiges US-13-Ticket nur noch die UI braucht.
