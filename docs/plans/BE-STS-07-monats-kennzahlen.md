# [BE-STS-07] Monats-Kennzahlen: Einnahmen, Ausgaben und Differenz pro Monat

- **Issue:** [#288](https://github.com/dfme/budget-buddy/issues/288)
- **Task-ID:** `BE-STS-07`
- **Branch:** `feature/BE-STS-07-monats-kennzahlen`
- **Story:** US-12 — Zwischen Monaten wechseln
- **Sprint:** — (Board-Feld leer; geplant während Sprint 6)
- **Bestätigt am:** 2026-09-09

## Ausgangslage

Die Drei-Monats-Übersicht des Dashboards (FE-STS-04, #250) braucht je Monat Einnahmen, Ausgaben
und deren Differenz. Zwei der drei Werte liefert heute kein Endpoint:

- `GET /api/transactions/summary?month=` gibt ausdrücklich nur Ausgaben
  (`CategorySummaryResponse`: «Einkommen (Gutschriften) fliesst nicht ein»).
- `monthlyIncome` am User ist das *erfasste* Einkommen von heute, nicht die tatsächlichen
  Gutschriften eines vergangenen Monats.

Die nötige Query existiert bereits — `TransactionRepository`
`.findByUserIdAndIncomeTrueAndBuchungsdatumBetween` (`TransactionRepository.java:111`) — ist aber
nur intern vom `IncomeSuggestionService` genutzt und über kein REST-Endpoint erreichbar.

`#250` ist im Relationships-Panel bereits als **blocked by** `#288` eingetragen; dieser Task
blockiert also die Umsetzung des Dashboards und wird deshalb zuerst gemacht.

## Contract

`GET /api/transactions/monthly-totals?month=YYYY-MM&months=3`

Antwort ist ein reines JSON-Array, **neuester Monat zuerst**, mit einer Zeile pro Monat des
Fensters — auch für einen Monat ohne Buchungen:

```json
[
  { "month": "2026-09", "income": 8500.00, "expenses": 3200.50, "difference": 5299.50 },
  { "month": "2026-08", "income": null,    "expenses": null,    "difference": null    }
]
```

Kein Envelope-Record um die Liste: es gibt keine Kennzahl über dem Fenster, die einer trüge —
`GET /api/transactions/months` liefert aus demselben Grund ein nacktes Array.

## Entscheide

### 1. Eigener Controller, nicht ein Endpoint am `TransactionSummaryController`

Das Projekt trennt die Transaktions-Endpoints nach Anliegen (`TransactionListController`,
`TransactionSummaryController`, `TransactionCategoryController`,
`TransactionDirectionController`). Ein `MonthlyTotalsController` setzt diese Linie fort statt sie
zu brechen. Er liegt im transaction-Modul neben dem Summary-Controller, wie die AC es verlangt.

### 2. Der neue Controller braucht zwingend einen Eintrag im `TransactionExceptionHandler`

`MonthParser.parse` wirft `InvalidMonthException`, und das Javadoc des Parsers
(`MonthParser.java:33-39`) hält fest, dass es in diesem Projekt bewusst **kein**
`@RestControllerAdvice` ohne `assignableTypes` gibt: «ein neuer Aufrufer braucht daher immer auch
einen Handler». Ohne den Eintrag wird ein unbrauchbarer `month`-Wert zu einer 500 statt zu einer
400. Das ist keine Formalie, sondern der Unterschied zwischen dokumentiertem und kaputtem
Verhalten.

### 3. Zwei Queries für das ganze Fenster, nicht zwei pro Monat

Die AC verbietet «zwei Queries pro Monat» ausdrücklich. Geladen wird deshalb genau zweimal, über
die gesamte Fensterbreite (`ältester.atDay(1)` .. `jüngster.atEndOfMonth()`):

- `findByUserIdAndIncomeFalseAndBuchungsdatumBetween` — Belastungen
- `findByUserIdAndIncomeTrueAndBuchungsdatumBetween` — Gutschriften

Gruppiert wird anschliessend in Java nach `YearMonth`, summiert mit `BigDecimal.add` (ADR-9), nie
per SQL-Aggregat — dieselbe Begründung, die an den bestehenden Repository-Methoden steht.

### 4. Die Betragsgleichheit mit `CategorySummaryResponse.totalAmount` ist strukturell

`expenses` benutzt **dieselbe** Repository-Methode und dieselbe Auswahlregel wie
`TransactionSummaryService.summarize` (`TransactionSummaryService.java:59-61`): nur Belastungen,
ganzer Monat, **kein** Abzug der per Dauerauftrag bezahlten Fixkosten. Der ADR-13-Abzug gehört
allein in den Safe-to-Spend-Summanden; die Übersicht zeigt wie die Kategorie-Übersicht die echten
Belastungen des Kontos.

Ein Test hält die Gleichheit fest, aber sie folgt aus der geteilten Query — nicht aus dem Test.
Zwei eigene Queries mit «derselben» Regel wären genau die Doppelpflege, die auseinanderläuft.

### 5. `null` statt `0.00` für einen Monat ohne jede Buchung

Ein Monat ist leer ⟺ er hat in **keiner** der beiden Queries eine Zeile. Jede Transaktion ist
entweder Gutschrift oder Belastung, die beiden Queries decken also alle Buchungen des Monats ab —
ein dritter Zugriff nur zum Zählen ist nicht nötig.

Ein Monat mit ausschliesslich Gutschriften trägt folglich `expenses = 0.00`, nicht `null`: dort
gibt es Buchungen, und die Summe der Belastungen ist dann wirklich null. Genau diese
Unterscheidbarkeit ist die Grundlage für die `–`-Darstellung in FE-STS-04, und dieselbe, die
`SafeToSpendResponse.amount` bereits trägt.

### 6. Ein Zukunftsmonat ist hier kein Fehler

Anders als `GET /api/budget/safe-to-spend`, das ihn mit 400 ablehnt: dort hat `weeksLeft` für
einen künftigen Monat keine Bedeutung, hier gibt es einfach keine Buchungen. Die Zeile kommt mit
`null`s zurück — dasselbe Verhalten wie `GET /api/transactions/summary`, das einen leeren Monat
ebenfalls beantwortet statt abzulehnen.

### 7. `months` wird begrenzt, mit eigener Exception

Default 3, zulässig 1..12. Ein unbrauchbarer Wert wird zu 400 über eine neue
`InvalidMonthWindowException` — nicht über die bestehende `InvalidPaginationException`, die
semantisch `page`/`size` gehört und deren Meldung auf ein anderes Problem zeigte.

### 8. Skala 2 als Eigenschaft dieser Schicht

`setScale(2, RoundingMode.UNNECESSARY)` auf jedem Betrag, wie in
`TransactionSummaryService.expenseAmounts` (`TransactionSummaryService.java:115`): die Zusage
«Skala 2 nach aussen» ist damit nicht von der Datenbank geliehen. `UNNECESSARY`, weil
`transactions.betrag` als `numeric(10,2)` nie mehr Stellen tragen kann — täte es das doch, ist
das ein Datendefekt und soll laut scheitern statt still zu runden (der Defekt aus #148).

### 9. Buchungen mit ungeprüfter Richtung zählen als Belastung

Die bestehende konservative Regel aus BE-PDF-10 gilt unverändert: eine darunter liegende
Gutschrift drückt `income` und hebt `expenses`. Nicht hier abgeändert, aber im Endpoint-Javadoc
festgehalten, damit die Abweichung zum Kontoauszug erklärbar bleibt.

## Betroffene Dateien

### Neu

| Datei | Inhalt |
| ----- | ------ |
| `backend/src/main/java/com/budgetbuddy/transaction/MonthlyTotalsController.java` | Endpoint, `@AuthenticationPrincipal Long userId`, OpenAPI-Annotationen, Tag `Transactions` |
| `backend/src/main/java/com/budgetbuddy/transaction/MonthlyTotalsService.java` | `@Transactional(readOnly = true)`, Fenster laden + in Java gruppieren |
| `backend/src/main/java/com/budgetbuddy/transaction/InvalidMonthWindowException.java` | unbrauchbarer `months`-Wert |
| `backend/src/main/java/com/budgetbuddy/transaction/dto/MonthlyTotals.java` | `record(String month, BigDecimal income, BigDecimal expenses, BigDecimal difference)` |
| `backend/src/test/java/com/budgetbuddy/transaction/MonthlyTotalsServiceTest.java` | Unit-Tests |
| `backend/src/test/java/com/budgetbuddy/transaction/MonthlyTotalsControllerIntegrationTest.java` | Integrationstests |
| `backend/src/test/java/com/budgetbuddy/transaction/MonthlyTotalsOpenApiTest.java` | OpenAPI-Nachweis |

### Geändert

| Datei | Änderung |
| ----- | -------- |
| `backend/src/main/java/com/budgetbuddy/transaction/TransactionExceptionHandler.java` | `MonthlyTotalsController.class` in `assignableTypes`; Handler für `InvalidMonthWindowException` |

## Implementierungsschritte

1. `dto/MonthlyTotals.java` — Record mit Javadoc zur `null`-Semantik und zur Skala.
2. `InvalidMonthWindowException.java` — analog `InvalidPaginationException`.
3. `MonthlyTotalsService.java` — Fenster aus `month` + `months` ableiten, zwei Queries, Gruppierung
   nach `YearMonth`, `difference` als `income.subtract(expenses)`, Ausgabe neuester Monat zuerst.
4. `MonthlyTotalsController.java` — `GET /monthly-totals` unter `/api/transactions`, `month` über
   `MonthParser`, `months` mit Default 3 und Grenzen, OpenAPI-Beschreibung inkl. der `null`-Regel
   und des Unterschieds zum Safe-to-Spend-Endpoint bei Zukunftsmonaten.
5. `TransactionExceptionHandler.java` — Controller registrieren, Handler ergänzen (Entscheid 2).
6. Tests schreiben (unten).
7. `cd backend && JAVA_HOME=$(/usr/libexec/java_home -v 25) ./mvnw test` grün.

## Test-Strategie

### Unit — `MonthlyTotalsServiceTest` (Repository gemockt)

- Gruppierung über drei Monate mit Daten
- Reihenfolge: neuester Monat zuerst
- Monat ohne jede Buchung → `income`, `expenses`, `difference` alle `null`
- Monat mit nur Gutschriften → `expenses = 0.00`, nicht `null`
- Negative `difference` (mehr ausgegeben als eingenommen)
- Alle Beträge Skala 2
- `months`-Grenzen: 0 und 13 werfen `InvalidMonthWindowException`

### Integration — `MonthlyTotalsControllerIntegrationTest`

- Happy Path über drei Monate
- Monat ohne Buchungen liefert `null`s
- **Betragsgleichheit:** `expenses` eines Monats == `totalAmount` von
  `GET /api/transactions/summary?month=` desselben Monats
- **Mandantentrennung:** ein fremder User sieht die Zahlen nicht
- 400 bei kaputtem `month`
- 400 bei `months=0` und `months=99`
- Zukunftsmonat → `null`-Zeile, **nicht** 400 (Entscheid 6)

### OpenAPI — `MonthlyTotalsOpenApiTest`

- Pfad `/api/transactions/monthly-totals` im generierten Dokument vorhanden
- `month` und `months` beschrieben, `months` mit `schema.default = 3`

`ControllerApiPrefixTest` deckt das `/api`-Präfix automatisch ab.

## Acceptance Criteria aus dem Issue

### Endpoint

- [ ] `GET /api/transactions/monthly-totals?month=YYYY-MM&months=3`, neuester Monat zuerst,
      `months` Default 3 und plausibel begrenzt
- [ ] Liegt im transaction-Modul neben `TransactionSummaryController`, Präfix `/api`, reine
      Transaktions-Aggregate, kein neuer Port über die Modulgrenze
- [ ] `month` über den bestehenden `MonthParser`; unbrauchbarer Wert → 400
- [ ] User-ID aus `@AuthenticationPrincipal`, jede Query user-gebunden
- [ ] OpenAPI-Annotation vorhanden, in Swagger UI sichtbar, Tag `Transactions`

### Antwort

- [ ] Je Monat `month`, `income`, `expenses`, `difference`
- [ ] Alle Beträge `BigDecimal` Skala 2; `difference` serverseitig als `income.subtract(expenses)`
- [ ] Für jeden Monat des Fensters eine Zeile, auch ohne Transaktionen
- [ ] Monat ohne jede Transaktion trägt `null`, nicht `0.00`
- [ ] Zukunftsmonat ist kein Fehler, sondern eine `null`-Zeile

### Konsistenz mit dem bestehenden Ausgabenbegriff

- [ ] `expenses` betragsgleich mit `CategorySummaryResponse.totalAmount`, kein Fixkosten-Abzug,
      durch Test belegt
- [ ] `income` ist die Summe der Gutschriften des Monats
- [ ] Ungeprüfte Buchungsrichtungen zählen als Belastung, im Javadoc festgehalten
- [ ] Summiert wird in Java mit `BigDecimal`; das Fenster in wenigen Queries, nicht zwei pro Monat
