# [BE-STS-06] Safe-to-Spend: Monat-Parameter + Abgeschlossen-Status

- **Issue:** [#248](https://github.com/dfme/budget-buddy/issues/248)
- **Task-ID:** `BE-STS-06`
- **Branch:** `feature/BE-STS-06-safe-to-spend-monat`
- **Story:** US-12 — Zwischen Monaten wechseln
- **Sprint:** Sprint 6
- **Bestätigt am:** 2026-09-06

## Ausgangslage

`SafeToSpendService.calculate(long)` leitet den Monat intern aus der Clock ab und übergibt
`YearMonth.from(heute)` an `MonthlyExpensePort.expenseAmounts` — der Monat ist damit kein
Eingabewert, sondern eine Eigenschaft des Abrufzeitpunkts. US-12 braucht ihn als Parameter.

## Entscheide

### 1. `MonthParser` wird `public`, bleibt in `com.budgetbuddy.transaction`

AC2 verlangt die Wiederverwendung des bestehenden `MonthParser`. Der ist heute package-private
(`MonthParser.java:14`), das budget-Modul kommt also nicht heran. Eine breite Suche über
Monatsbehandlung (`YYYY-MM`, `YearMonth.parse`, `RequestParam String month`) zeigt: ausserhalb von
`com.budgetbuddy.transaction` parst niemand Monate — es gibt nichts zu vereinheitlichen, nur
freizugeben.

Gewählt: Sichtbarkeit erhöhen, Ort belassen. Die von `MonthParser` geworfene
`InvalidMonthException` ist an derselben Stelle **bereits public** und wird bereits ausserhalb des
Pakets erwartet; die Modulkante überschreitet also schon heute ein Typ dieses Paars. CONVENTIONS.md
(Zeile 90) verbietet den direkten Zugriff auf *Repositories und Services* eines anderen Moduls — ein
zustandsloser Parser ohne Spring-Bean und ohne Datenzugriff ist keins von beidem.

Verworfen:

- **Neues `shared`-Package.** Neutral, aber legt ein Package an, das es im Projekt nicht gibt, und
  berührt vier bestehende transaction-Klassen — unverhältnismässig für 3 SP.
- **`MonthParsePort`.** Ein Port ist die Naht für Datenzugriff über Modulgrenzen
  (`MonthlyExpensePort`, `UserIncomePort`). Für eine statische Formatprüfung wäre er eine
  Spring-Bean ohne Gegenwert.

### 2. Monat in der Zukunft → HTTP 400

Die AC nennt nur Vergangenheit (`CLOSED`) und laufenden Monat. Für einen späteren Monat ist die
Berechnung nicht definiert: `weeksLeft` wird aus «heute» hergeleitet und ergäbe für einen
Folgemonat einen Wert ohne Bedeutung, Ausgaben gibt es dort noch keine. 400 sagt das, statt eine
Zahl zu liefern, der niemand trauen kann — der Core Value der App ist ein Betrag, dem der Nutzer
vertraut. US-12 kennt ohnehin nur Vergangenheit und Gegenwart.

Verworfen: als `CLOSED` behandeln («Abgeschlossen» ist für einen künftigen Monat schlicht falsch);
ein dritter Status `FUTURE` (das Frontend müsste einen Fall behandeln, den die Monatsnavigation
nicht anbieten kann — sie listet nur Monate mit Daten).

### 3. `CLOSED` ist ein reiner Marker

`amount` und `incomeSuggestion` `null`, `weeksLeft` `0`, `negative` und `noIncome` `false`.
US-12 sagt ausdrücklich, dass für vergangene Monate nicht gerechnet wird — dann darf auch kein Feld
so aussehen, als wäre gerechnet worden. Die bisherige Zusage «`weeksLeft` immer mindestens 1» wird
im Javadoc und im TS-Modell auf «mindestens 1, solange `status = OPEN`» präzisiert.

Verworfen: `weeksLeft` = Wochen des ganzen Monats (hielte die alte Zusage wörtlich, lieferte aber
eine Zahl, die nach Berechnung aussieht); getrennte DTOs je Status (Springdoc bildet das nur über
`oneOf` ab, das Frontend bräuchte eine Discriminated Union).

### 4. Eigener `BudgetExceptionHandler` statt Erweiterung des `TransactionExceptionHandler`

AC4 (400 bei ungültigem Format) folgt **nicht** automatisch aus der Wiederverwendung des
`MonthParser`. Sämtliche `@RestControllerAdvice` dieses Projekts sind über `assignableTypes`
begrenzt, und das ist begründet: `UserExceptionHandler.java:35` hält fest, dass ein unscoped Advice
Springdoc das falsche Fehlerschema an alle Endpoints hängt. `TransactionExceptionHandler.java:17-22`
listet vier transaction-Controller; `BudgetController` ist nicht darunter. Eine
`InvalidMonthException` aus dem budget-Modul liefe deshalb heute in einen 500.

`BudgetController` dort einzutragen wäre die kleinere Änderung, zöge aber die Zuständigkeit für
einen budget-Endpoint in das transaction-Modul — genau das benennt `FixedCostExceptionHandler.java:14-17`
als das Falsche. Der neue Handler liegt deshalb im budget-Modul, direkt neben dem des
`FixedCostController`.

## Betroffene / neue Files

**Geändert (Backend):**

- `backend/src/main/java/com/budgetbuddy/transaction/MonthParser.java` — Klasse und `parse()` auf
  `public`, Javadoc um die budget-Nutzung ergänzt
- `backend/src/main/java/com/budgetbuddy/budget/SafeToSpendService.java` —
  `calculate(long, YearMonth)` als eigentliche Methode; `calculate(long)` bleibt als Overload und
  delegiert mit dem laufenden Monat. Der Default liegt damit an genau einer Stelle, die
  `Europe/Zurich`-Kenntnis bleibt im Service, und die 30 bestehenden Testaufrufe bleiben gültig
- `backend/src/main/java/com/budgetbuddy/budget/BudgetController.java` —
  `@RequestParam(required = false) String month`, Swagger-Doku für Parameter, 400 und CLOSED
- `backend/src/main/java/com/budgetbuddy/budget/dto/SafeToSpendResponse.java` — Komponente `status`,
  `weeksLeft`-Zusage präzisiert

**Neu (Backend):**

- `backend/src/main/java/com/budgetbuddy/budget/dto/SafeToSpendStatus.java` — `enum { OPEN, CLOSED }`
- `backend/src/main/java/com/budgetbuddy/budget/FutureMonthException.java`
- `backend/src/main/java/com/budgetbuddy/budget/BudgetExceptionHandler.java`

**Geändert (Tests):** `SafeToSpendServiceTest.java`, `BudgetControllerIntegrationTest.java`

**Neu (Tests):** `BudgetOpenApiTest.java`

**Frontend (bewusste kleine Scope-Erweiterung, im PR-Body deklariert):**

- `frontend/src/app/dashboard/safe-to-spend.model.ts` — `status` ergänzt, `weeksLeft`-Kommentar
  präzisiert. Die Datei bezeichnet sich als «Spiegel des Backend-DTOs»; ohne das Feld wäre diese
  Aussage ab diesem PR falsch
- `dashboard.spec.ts` (5), `settings.spec.ts` (3), `safe-to-spend.service.spec.ts` (1) — die neun
  Objekt-Literale um das Pflichtfeld ergänzt, rein mechanisch

Die Anzeige des Zustands im Dashboard bleibt bei `FE-STS-04`.

## Implementierungsschritte

1. `MonthParser` public machen
2. `SafeToSpendStatus`, `FutureMonthException`, `BudgetExceptionHandler` anlegen
3. `SafeToSpendResponse` um `status` erweitern, Javadoc nachziehen
4. `SafeToSpendService`: Monat-Parameter, Vergangenheits- und Zukunftszweig, Default-Overload
5. `BudgetController`: Parameter, Default, Swagger-Doku
6. Tests schreiben (siehe Test-Strategie)
7. `./mvnw -f backend/pom.xml test` mit JDK 25, `npm test` im Frontend
8. Security-Review (Matrix-Zeilen 1 und 2) und lokaler Review

## Test-Strategie

**Unit — `SafeToSpendServiceTest`** (feste Clock, Mockito):

- expliziter laufender Monat verhält sich wie der bisherige parameterlose Aufruf
- `expenseAmounts` wird mit dem **übergebenen** Monat gerufen — der eigentliche Regressionsschutz,
  heute ist dort `YearMonth.from(heute)` hartverdrahtet
- Vormonat → `status = CLOSED`, `amount = null`, `weeksLeft = 0`
- Folgemonat → `FutureMonthException`
- `calculate(userId)` entspricht `calculate(userId, laufender Monat)`

**Integration — `BudgetControllerIntegrationTest`** (Clock als `MockitoBean`):

- ohne `month` → laufender Monat, Wire-Format unverändert plus `status: "OPEN"`
- `?month=2020-01` → 200, `status: "CLOSED"`, `amount: null`
- `?month=kaputt` und `?month=2026-13` → 400 (AC4)
- Zukunftsmonat → 400
- ohne JWT → 401; User B sieht die Werte von User A nicht (Mandantentrennung)

**OpenAPI — `BudgetOpenApiTest`** (neu): `month` erscheint als Parameter an
`/api/budget/safe-to-spend`, `status` im `SafeToSpendResponse`-Schema, 400 dokumentiert. Das ist der
automatisierte Nachweis für die DoD-Zeile «Neue/geänderte API-Parameter sind in Swagger UI
sichtbar» — ein Klick durch die UI wäre keiner.

## Acceptance Criteria (aus Issue #248)

- [ ] `SafeToSpendService.calculate(userId, YearMonth)` ersetzt die feste Verdrahtung auf "heute"
- [ ] `GET /api/budget/safe-to-spend` akzeptiert optionalen `@RequestParam String month`
      (Format `YYYY-MM`, bestehender `MonthParser`), Default = laufender Monat
- [ ] Für Monate vor dem aktuellen Monat liefert der Endpoint `status=CLOSED` statt der
      Wochenbudget-Berechnung
- [ ] Ungültiges Monatsformat → HTTP 400

**Zu AC1:** «ersetzt die feste Verdrahtung» ist als *die Berechnung kennt keinen impliziten Monat
mehr* umgesetzt. Die parameterlose `calculate(userId)` bleibt als Default-Overload bestehen; sie
leitet den laufenden Monat aus derselben Clock ab und ist damit keine zweite Verdrahtung, sondern
der Default an genau einer Stelle.
