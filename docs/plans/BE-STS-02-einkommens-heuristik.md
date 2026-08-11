# [BE-STS-02] Einkommens-Heuristik

- **Issue:** [#22](https://github.com/dfme/budget-buddy/issues/22)
- **Task-ID:** `BE-STS-02`
- **Branch:** `feature/BE-STS-02-einkommens-heuristik`
- **Story:** US-06 — Wöchentlicher Safe-to-Spend-Betrag
- **Sprint:** Sprint 4
- **Bestätigt am:** 2026-08-11

---

## Entscheide

Drei Punkte waren vor der Umsetzung offen und wurden im Planungsgespräch entschieden:

| Punkt | Entscheid | Begründung |
| ----- | --------- | ---------- |
| Branch-Basis | Abzweig von `feature/BE-STS-01-safe-to-spend-service`, nicht von `main` | AC3 verlangt «Heuristik läuft bei jedem Safe-to-Spend-Aufruf». Der `SafeToSpendService` existiert nur auf dem Branch von [#21](https://github.com/dfme/budget-buddy/issues/21) (PR [#155](https://github.com/dfme/budget-buddy/pull/155), zum Planungszeitpunkt offen). Von `main` aus wäre AC3 nicht erfüllbar. Preis: dieser PR ist erst mergebar, wenn #155 durch ist. |
| Gruppierung der Gutschriften | Normalisierter `buchungstext`; Absender-Kriterium aus US-06 als Folge-Issue | US-06 Zeile 25 verlangt «regelmässige Gutschrift **desselben Absenders**». Der Absender steht nicht in der Datenbank: `PdfImportService:123` persistiert nur `tx.buchungstext()`, die `details`-Liste (Empfänger/Absender) wird nach der Kategorisierung verworfen. Das Kriterium ist damit heute nicht implementierbar. Erfasst als [`BE-PDF-07`](https://github.com/dfme/budget-buddy/issues/159). |
| Vorschlagsbetrag | Median der Gruppe | Robust gegen einen Ausreisser innerhalb des ±5 %-Bands und gegen einen 13. Monatslohn, der knapp mit hineinrutscht. Der letzte Betrag folgt einer Lohnerhöhung schneller, kippt aber auf jeden einmaligen Ausreisser; das arithmetische Mittel wird von jedem Ausreisser mitgezogen und liefert krumme Beträge. |

Weitere Entscheide:

- **Modul:** Die Heuristik liegt im `transaction`-Modul, nicht in `budget`. Sie wertet
  ausschliesslich Transaktionsmuster aus, und die Konvention aus CLAUDE.md stellt das Interface ins
  *liefernde* Modul (wie `MonthlyExpensePort` und `auth.UserIncomePort`). Über die Modulkante geht
  damit nur ein Betrag — `MonthlyExpensePort` begründet ausdrücklich, dass Buchungstexte im
  `budget`-Modul nichts verloren haben.
- **Fenster:** Gutschriften der letzten 12 Monate ab «heute». Ohne Grenze zählte ein Jobwechsel vor
  drei Jahren noch mit. «Heute» kommt aus derselben `Clock` und derselben Zone (`Europe/Zurich`)
  wie in BE-STS-01 — sonst läge der Stichtag zwischen 00:00 und 02:00 Ortszeit im Vortag.
- **Rundung:** Median auf Skala 2 mit `HALF_UP` — dieselbe Regel wie `FixedCostService:185` und
  `SafeToSpendService`.

## Verhältnis von AC2 und AC3

AC3 verlangt «Heuristik läuft bei jedem Safe-to-Spend-Aufruf», AC2 «Vorschlag nur wenn kein
Einkommen manuell gesetzt ist». Beides zugleich heisst: der Aufruf sitzt im `noIncome`-Zweig von
`SafeToSpendService.calculate(...)` und wird dort bei jedem Request frisch gerechnet — kein Cache,
kein Batch-Job, kein persistierter Vorschlag. Ist ein Einkommen erfasst, läuft die Heuristik gar
nicht; das ist genau AC2 und spart zugleich die Query. Diese Lesart steht im Javadoc, damit sie
überprüfbar ist und nicht bei der nächsten Änderung still gekippt wird.

## Algorithmus

1. Gutschriften (`is_income = true`) des Users im Fenster `[heute − 12 Monate, heute]` laden.
2. **Gruppenschlüssel** aus `buchungstext`: lowercase → Monatsnamen wortweise entfernen (inklusive
   `märz`/`maerz`) → Ziffern und Referenzfragmente entfernen → Whitespace kollabieren → trimmen.

   ```
   GUTSCHRIFT LOHN SEPTEMBER   →  "gutschrift lohn"
   Saläreingang                →  "saläreingang"
   GIRO AUS KONTO 25-9034-2    →  "giro aus konto"
   ```

   Ohne die Normalisierung fiele der Post-Fall auseinander: dort steht der Monatsname im
   Buchungstext, der Schlüssel wäre jeden Monat ein anderer und die Gruppe käme nie auf zwei
   Vorkommen.
3. **Qualifikation je Gruppe:** Vorkommen in mindestens zwei *verschiedenen* Kalendermonaten **und**
   alle Beträge innerhalb ±5 % des Gruppen-Medians. Ein einzelner Ausreisser kippt die ganze Gruppe —
   «gleicher Betrag (±5 %)» ist eine Aussage über alle Vorkommen, nicht über die Mehrheit.
4. **Mehrere qualifizierte Gruppen:** höchster Median gewinnt (der Lohn, nicht die wiederkehrende
   Kleinrückerstattung). Gleichstand → mehr Vorkommen → Schlüssel alphabetisch, damit das Ergebnis
   deterministisch ist und nicht an der Zeilenreihenfolge der Query hängt.
5. Rückgabe: Median als `BigDecimal`, Skala 2, `HALF_UP`. Keine qualifizierte Gruppe →
   `Optional.empty()`.

## Betroffene Files

| Datei | Änderung |
| ----- | -------- |
| `backend/src/main/java/com/budgetbuddy/transaction/IncomeSuggestionPort.java` | **neu** — Port, `Optional<BigDecimal> suggestMonthlyIncome(long userId)` |
| `backend/src/main/java/com/budgetbuddy/transaction/IncomeSuggestionService.java` | **neu** — Heuristik, implementiert den Port |
| `backend/src/main/java/com/budgetbuddy/transaction/TransactionRepository.java` | + `findByUserIdAndIncomeTrueAndBuchungsdatumBetween` |
| `backend/src/main/java/com/budgetbuddy/budget/SafeToSpendService.java` | Port injizieren, im `noIncome`-Zweig aufrufen |
| `backend/src/main/java/com/budgetbuddy/budget/dto/SafeToSpendResponse.java` | + `incomeSuggestion` als fünfte Komponente; Javadoc-Verweis auf BE-STS-03 korrigieren |
| `backend/src/test/java/com/budgetbuddy/transaction/IncomeSuggestionServiceTest.java` | **neu** — Unit |
| `backend/src/test/java/com/budgetbuddy/transaction/IncomeSuggestionServiceIntegrationTest.java` | **neu** — Integration gegen PostgreSQL |
| `backend/src/test/java/com/budgetbuddy/budget/SafeToSpendServiceTest.java` | Tests für den Vorschlag im `noIncome`-Fall und `never()` bei gesetztem Einkommen |

## Implementierungsschritte

1. `TransactionRepository`: Query für Gutschriften ergänzen, analog zur bestehenden Ausgaben-Query.
2. `IncomeSuggestionPort` anlegen — schmal, gibt nur den Betrag heraus.
3. `IncomeSuggestionService`: Normalisierung, Gruppierung, ±5 %-Prüfung, Median, Auswahlregel.
4. `SafeToSpendResponse` um `incomeSuggestion` erweitern; Javadoc anpassen (das Feld entsteht jetzt
   hier, nicht erst in BE-STS-03).
5. `SafeToSpendService`: Port injizieren und im `noIncome`-Zweig aufrufen.
6. Tests nach der Strategie unten.

## Test-Strategie

### Unit — `IncomeSuggestionServiceTest` (`@ExtendWith(MockitoExtension.class)`, fixe `Clock`)

| AC | Test |
| -- | ---- |
| AC1 Erkennung | `Saläreingang` 6× 6800 → Vorschlag `6800.00`; `GUTSCHRIFT LOHN SEPTEMBER/OKTOBER` → dieselbe Gruppe trotz unterschiedlichem Text |
| AC1 ±5 % | Betrag exakt an der Bandgrenze zählt noch dazu; knapp ausserhalb kippt die Gruppe |
| AC1 ≥ 2 Monate | zwei Gutschriften im *selben* Monat qualifizieren nicht; zwei in verschiedenen schon |
| Auswahl | zwei qualifizierte Gruppen → die mit dem höheren Median gewinnt |
| Median | gerade Anzahl → Mittel der beiden mittleren Werte, Skala 2 |
| Leerfall | keine Gutschriften / keine qualifizierte Gruppe → `Optional.empty()` |
| Fenster | Gutschrift älter als 12 Monate fliesst nicht ein |

### Unit — `SafeToSpendServiceTest` (Ergänzung)

- Kein Einkommen → `incomeSuggestion` gesetzt, `noIncome = true`, `amount = null`.
- Einkommen gesetzt → `verify(incomeSuggestionPort, never())`. Das ist der Nachweis für AC2:
  ein `null`-Feld allein zeigte nicht, dass die Heuristik nicht lief.

### Integration — `IncomeSuggestionServiceIntegrationTest` (`@SpringBootTest`, `PostgresTestDatabase`)

- Happy Path über echtes PostgreSQL mit den Beträgen aus den echten PDF-Fixtures
  (`Saläreingang` 6800, `GUTSCHRIFT LOHN <Monat>` 5500).
- Ausgaben (`is_income = false`) fliessen nicht ein, auch wenn sie wiederkehrend sind.
- **Mandantentrennungs-Gegenprobe:** User B hat im selben Zeitraum andere wiederkehrende
  Gutschriften — der Vorschlag von User A ändert sich dadurch nicht, und B bekommt seinen eigenen.
  Ein Happy Path allein belegt die Trennung nicht.

Abschliessend `./mvnw verify` (Backend) und `npm run build` (Frontend, für die DoD-Zeile).

## Acceptance Criteria (aus dem Issue)

- [ ] Wiederkehrende Gutschrift (±5 %, ≥ 2 Monate) wird erkannt
- [ ] Vorschlag wird nur gemacht wenn kein Einkommen manuell gesetzt ist
- [ ] Heuristik läuft bei jedem Safe-to-Spend-Aufruf

## Bewusst nicht in diesem Task

- **Absender-Kriterium aus US-06** — braucht die Persistenz der `details`-Zeilen, also Migration
  plus Eingriff in den Import-Pfad. Erfasst als [`BE-PDF-07`](https://github.com/dfme/budget-buddy/issues/159).
- **Kein Endpoint.** `GET /budget/safe-to-spend` ist [#23](https://github.com/dfme/budget-buddy/issues/23)
  (BE-STS-03). Die DoD-Zeile «Neue API-Endpoints sind in Swagger UI sichtbar» ist für diesen Task
  deshalb nicht erfüllbar und wird im PR-Body so ausgewiesen statt still abgehakt.
