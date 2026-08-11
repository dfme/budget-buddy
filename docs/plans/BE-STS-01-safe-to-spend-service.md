# [BE-STS-01] SafeToSpendService

- **Issue:** [#21](https://github.com/dfme/budget-buddy/issues/21)
- **Task-ID:** `BE-STS-01`
- **Branch:** `feature/BE-STS-01-safe-to-spend-service`
- **Story:** US-06 — Wöchentlicher Safe-to-Spend-Betrag
- **Sprint:** Sprint 4
- **Bestätigt am:** 2026-08-10

---

## Entscheide

Drei Punkte waren vor der Umsetzung offen und wurden im Planungsgespräch entschieden:

| Punkt | Entscheid | Begründung |
| ----- | --------- | ---------- |
| Divisor «verbleibende Wochen» | `weeksLeft = max(1, ceil(Resttage inkl. heute ÷ 7))` | Konservativ: jeder verbleibende Tag ist budgetiert, der Wochenbetrag wird nie zu hoch. Die Prosa in US-06 ist in sich widersprüchlich — das Beispiel (2000 − 800 − 400 = 800 → 200 CHF/Woche) geht nur bei Divisor 4 auf, spricht aber von «den verbleibenden 3 Wochen». Aufrunden trifft das Beispiel im 28-Tage-Monat exakt. |
| Zeitzone für «heute» | `Europe/Zurich`, fest im Service | `ClockConfig` liefert `systemUTC` (`config/ClockConfig.java:18`). In der Schweiz (UTC+1/+2) läge der Safe-to-Spend zwischen 00:00 und 02:00 Ortszeit sonst noch im Vortag — am Monatsersten also im Vormonat. CLAUDE.md beschränkt die App auf die Schweiz; eine Property wäre ein Schalter ohne Anwendungsfall, der falsch stehen kann. |
| Fixkosten-Doppelabzug | Formel wörtlich nach US-06 umsetzen, Einschränkung dokumentieren, Folge-Issue [`BE-STS-04`](https://github.com/dfme/budget-buddy/issues/154) | Fixkosten werden abgezogen **und** erscheinen zusätzlich als Belastung in den importierten Transaktionen (z. B. Miete). Nach der Formel wird die Miete damit doppelt abgezogen. Die Auflösung braucht eine fachliche Entscheidung (Verknüpfung Fixkosten-Position ↔ Transaktion im Datenmodell) und gehört nicht in diesen Task. |

Weitere Entscheide:

- **Rundung von `amount`:** `RoundingMode.HALF_UP` auf Skala 2 — dieselbe Wahl wie in
  `FixedCostService:185`. Konsistenz innerhalb des Moduls schlägt hier die theoretisch
  konservativere Variante `FLOOR`; die Abweichung liegt bei höchstens einem halben Rappen.
- **Kein Einkommen:** `amount = null`, nicht `0.00`. US-06 Zeile 21 verlangt ausdrücklich, dass
  *keine* Division ausgeführt wird; `null` ist für den Client von «Budget ist aufgebraucht»
  unterscheidbar und entspricht der Design-Referenz (`design/variant-a/index.html:216` zeigt «—»).
  `weeksLeft` wird trotzdem gesetzt — der Wert hängt nicht vom Einkommen ab.

## Scope-Abgrenzung

- **Kein Controller.** `GET /budget/safe-to-spend` ist [#23](https://github.com/dfme/budget-buddy/issues/23) (BE-STS-03).
- **Keine Einkommens-Heuristik.** `incomeSuggestion` ist [#22](https://github.com/dfme/budget-buddy/issues/22) (BE-STS-02);
  `SafeToSpendResponse` bekommt das Feld dort ergänzt.
- `design/README.md:132` dokumentiert für Variante C eine andere Herleitung
  (`÷ Resttage × Tage dieser Woche`). Gewählt ist Variante A (ADR-11) — es gilt die Formel aus US-06.

## Betroffene Files

### Neu

| Datei | Zweck |
| ----- | ----- |
| `backend/src/main/java/com/budgetbuddy/transaction/MonthlyExpensePort.java` | `BigDecimal sumExpenses(long userId, YearMonth month)`. Liegt im **liefernden** Modul, gleiche Bauart wie `auth/UserIncomePort.java:20`. Ohne ihn müsste `budget` direkt auf das `TransactionRepository` zugreifen — das untersagt CLAUDE.md. Bewusst schmal: über die Kante geht eine Summe, keine `Transaction`-Entities (Buchungstexte haben im budget-Modul nichts verloren). |
| `backend/src/main/java/com/budgetbuddy/budget/SafeToSpendService.java` | Die Berechnung. |
| `backend/src/main/java/com/budgetbuddy/budget/dto/SafeToSpendResponse.java` | Record mit `amount`, `weeksLeft`, `negative`, `noIncome`. Boolean ohne `is`-Präfix wie `exceedsIncome` — die Feldnamen sind zugleich das Wire-Format. |
| `backend/src/test/java/com/budgetbuddy/budget/SafeToSpendServiceTest.java` | Unit-Test, Ports gemockt, fixe `Clock`. |
| `backend/src/test/java/com/budgetbuddy/budget/SafeToSpendServiceIntegrationTest.java` | Integrationstest gegen Testcontainers-PostgreSQL inkl. Mandantentrennungs-Gegenprobe. |

### Geändert

| Datei | Änderung |
| ----- | -------- |
| `backend/src/main/java/com/budgetbuddy/transaction/TransactionSummaryService.java` | `implements MonthlyExpensePort`; nutzt die vorhandene Query `findByUserIdAndIncomeFalseAndBuchungsdatumBetween` (`TransactionRepository.java:19`) — keine neue Query, keine Summierung in SQL (ADR-9 an einer Stelle). |
| `backend/src/main/java/com/budgetbuddy/budget/package-info.java` | Neue Modulkante nach `transaction` dokumentieren. |
| `backend/src/main/java/com/budgetbuddy/transaction/package-info.java` | Den bereitgestellten Port benennen. |

## Implementierungsschritte

1. `MonthlyExpensePort` anlegen.
2. `TransactionSummaryService` implementiert den Port; Summierung in Java über `BigDecimal`,
   identisch zur `total`-Bildung in `summarize` (`TransactionSummaryService.java:65`).
3. `SafeToSpendResponse` als Record mit vollständigem Javadoc je Feld.
4. `SafeToSpendService` mit `UserIncomePort`, `FixedCostService`, `MonthlyExpensePort` und `Clock`:
   - `heute = LocalDate.ofInstant(clock.instant(), ZURICH)` — liest nur `instant()`, wie
     `PdfImportService`, und ist damit mit `Clock.fixed(...)` und mit einem Mock testbar.
   - Einkommen fehlt → sofort `noIncome`-Antwort, **keine** Division und kein Ausgaben-Query.
   - Fixkostensumme über `fixedCostService.list(userId).summeMonatlich()` — dieselbe gerundete
     Summe, die der Wizard anzeigt (Begründung in `FixedCostService:17-23`); modul-intern erlaubt.
   - `verfuegbar = einkommen − fixkosten − ausgaben`;
     `amount = verfuegbar.divide(weeksLeft, 2, HALF_UP)`; `negative = amount.signum() < 0`.
5. Javadoc: Formel, Divisor-Regel, Zeitzonen-Begründung, bekannter Doppelabzug (→ BE-STS-04).
6. Folge-Issue [`BE-STS-04`](https://github.com/dfme/budget-buddy/issues/154) anlegen (Label `bug`, ohne Milestone und ohne Sprint).

## Test-Strategie

### Unit — `SafeToSpendServiceTest` (`@ExtendWith(MockitoExtension.class)`, fixe `Clock`)

| AC | Test |
| -- | ---- |
| AC1 Formel mit `BigDecimal` | US-06-Beispiel 2000 / 800 / 400 am 01.02. → `200.00`; nicht aufgehende Division → Skala 2, HALF_UP; **ADR-9-Nachweis** mit einer Fixture, bei der `double` ein anderes Ergebnis liefert (2000.00 / 800.07 / 400.03 → `199.98` statt `199.97`) |
| AC2 Divisor ≥ 1 | `ceil`-Grenzfälle 01.08. → 5, 01.02. → 4, 25.08. → 1; letzter Tag des Monats → **1**, keine `ArithmeticException` |
| AC3 Negativ-Flag | `< 0` → `true`; exakt `0.00` → `false` (Grenzfall) |
| AC4 noIncome-Flag | `UserIncomePort` leer → `noIncome = true`, `amount = null`, `verify(monthlyExpensePort, never())` belegt «keine Division» |
| Zeitzone | Clock auf `2026-07-31T23:30Z` (= 01.08. 01:30 Zürich) → gerechnet wird **August** |
| Mandantentrennung | `verify(...)` mit exakter `userId` belegt, dass alle drei Ports für genau diesen User aufgerufen werden |

### Integration — `SafeToSpendServiceIntegrationTest` (`@SpringBootTest`, `PostgresTestDatabase`, `@MockitoBean Clock`)

- Happy Path über echtes PostgreSQL: User mit Einkommen, Fixkosten und Transaktionen → erwarteter
  Betrag nach DB-Round-Trip.
- **Mandantentrennungs-Gegenprobe:** User B hat im selben Monat Fixkosten und Transaktionen — der
  Safe-to-Spend von User A ändert sich dadurch nicht. Test-User und Transaktionen per
  `JdbcTemplate` (wie `FixedCostServiceIntegrationTest:41`): ein Zugriff über `TransactionRepository`
  oder `UserRepository` wäre genau der modulübergreifende Zugriff, den CLAUDE.md untersagt.

Abschliessend `./mvnw verify` (Backend) und `npm run build` (Frontend, für die DoD-Zeile).

## Acceptance Criteria (aus dem Issue)

- [ ] Formel berechnet korrekt mit BigDecimal
- [ ] Divisor ist mindestens 1 (kein Division-by-Zero)
- [ ] Negativ-Flag gesetzt wenn Safe-to-Spend < 0
- [ ] noIncome-Flag gesetzt wenn `monthly_income` nicht erfasst
