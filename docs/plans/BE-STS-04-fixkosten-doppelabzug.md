# [BE-STS-04] Fixkosten werden im Safe-to-Spend doppelt abgezogen

- **Issue:** [#154](https://github.com/dfme/budget-buddy/issues/154)
- **Task-ID:** `BE-STS-04`
- **Branch:** `fix/BE-STS-04-fixkosten-doppelabzug`
- **Story:** US-06 — Wöchentlicher Safe-to-Spend-Betrag
- **Sprint:** Sprint 5
- **Bestätigt am:** 2026-08-21

## Ausgangslage

`SafeToSpendService.calculate(...)` zieht die Fixkosten-Monatssumme **und** die Ausgabensumme des
laufenden Monats ab. Eine per Dauerauftrag bezahlte Position — Miete, Krankenkasse — erscheint nach
dem PDF-Import zusätzlich als Belastung unter den Transaktionen und mindert den Betrag damit
zweimal. Das Verhalten ist in BE-STS-01 (#21) bewusst so umgesetzt und im Javadoc als «Bekannte
Einschränkung — Doppelabzug» dokumentiert.

Der Punkt ist älter als das Issue: `docs/prompts/02_01_mvp-requirements.md` führt ihn als *Risiko 2
— Fixkosten doppelt gezählt?* und lässt ihn offen.

## Entscheid

**Betragsbasiertes 1:1-Matching zur Berechnungszeit, ohne Schemawechsel.** Festgehalten als
**ADR-13**.

```
fixedCosts = Σ monatsbetrag                  (unverändert)
expenses   = Σ Belastungen des Monats − Σ gematchte Belastungen

Match-Regel, je Fixkosten-Position höchstens eine Belastung:
  FixedCost.betrag  ==  Betrag einer noch nicht verbrauchten Belastung
```

Gematcht wird gegen `betrag` (die tatsächliche Abbuchung), **nicht** gegen `monatsbetrag`. Damit
stimmt auch der Nicht-Monats-Fall: eine jährliche Versicherung von 1'200 wird im Zahlungsmonat als
1'200 aus den Ausgaben gestrichen, während auf der Fixkosten-Seite in jedem der zwölf Monate 100
stehen — über das Jahr exakt 1'200.

Die gematchte Transaktion bleibt in der Kategorie-Übersicht sichtbar; `TransactionSummaryService`
`.summarize(...)` wird nicht angefasst. Genau diese Auflösung schlägt Risiko 2 in
`02_01_mvp-requirements.md` vor.

### Warum nicht die Alternativen aus dem Issue

| Alternative | Warum nicht |
| ----------- | ----------- |
| Matching über Empfänger + Betrag + Zeitfenster | Der Empfänger ist heute nicht persistiert. #159 (BE-PDF-07) belegt: bei Daueraufträgen trägt `buchungstext` nur den Buchungstyp (`GIRO POST`), der Gegenpart (`Immo Verwaltung AG`) liegt in den beim Import verworfenen `details`. Übrig bleibt heute das Betragskriterium. |
| Explizite Verknüpfung `transactions.fixed_cost_id` | Fachlich die saubere Lösung und deckungsgleich mit dem «Kein Abo»-Override aus US-08 — aber ohne #159 müsste das Auto-Matching trotzdem über den Betrag laufen. Die Heuristik entfiele also nicht, sie würde nur persistiert. Migration + Migrationstest + Import-Pfad + Endpoint + Korrektur-UI sprengen die 1 SP um ein Vielfaches. Als Upgrade-Pfad in ADR-13 festgehalten. |
| Anteiliger Abzug für den Restmonat | Löst AC2 nicht. Ohne Fälligkeitstag an der Position ist die Verteilung eine lineare Annahme: eine am 1. abgebuchte Miete von 1'200 ergibt am 15. `1'200 × 17/31 = 658.06` Fixkosten **plus** 1'200 Ausgaben = 1'858.06 statt 1'200. Der Doppelabzug wird gemildert, nicht beseitigt. |

## Modulkante

`MonthlyExpensePort.sumExpenses(userId, month)` wird zu **`expenseAmounts(userId, month)`**
(`List<BigDecimal>`) — ersetzt statt ergänzt: der Port hat genau einen Aufrufer
(`SafeToSpendService`), und zwei Methoden nebeneinander könnten auseinanderlaufen.

Über die Kante gehen weiterhin nur Beträge — keine Buchungstexte, keine Kategorien, keine
Entities. Der Port bleibt damit so schmal, wie sein Javadoc es zusagt. Die Zuordnungslogik liegt im
`budget/`-Modul, wo die Fixkosten leben.

## Betroffene Files

### Geändert

| Datei | Änderung |
| ----- | -------- |
| `backend/src/main/java/com/budgetbuddy/transaction/MonthlyExpensePort.java` | `sumExpenses` → `expenseAmounts`, Javadoc |
| `backend/src/main/java/com/budgetbuddy/transaction/TransactionSummaryService.java` | Implementierung über dieselbe Query |
| `backend/src/main/java/com/budgetbuddy/budget/SafeToSpendService.java` | Formel; Abschnitt «Bekannte Einschränkung — Doppelabzug» ersetzen |
| `backend/src/main/java/com/budgetbuddy/budget/BudgetController.java` | OpenAPI-Beschreibung der Formel |
| `backend/src/main/java/com/budgetbuddy/budget/package-info.java` | Kantenbeschreibung nachziehen |
| `backend/src/main/java/com/budgetbuddy/transaction/package-info.java` | Kantenbeschreibung nachziehen |
| `backend/src/test/java/com/budgetbuddy/budget/SafeToSpendServiceTest.java` | Stubs auf `expenseAmounts`; Regressionsfälle |
| `backend/src/test/java/com/budgetbuddy/budget/SafeToSpendServiceIntegrationTest.java` | Regressionsfall über echte Daten |
| `docs/requirements/US-06-safe-to-spend.md` | Formel präzisieren (AC4) |
| `docs/adr/README.md` | ADR-Index + Kategorie-Übersicht |
| `CLAUDE.md` | ADR-Tabelle |

### Neu

| Datei | Zweck |
| ----- | ----- |
| `backend/src/main/java/com/budgetbuddy/budget/FixedCostDebitMatcher.java` | Multiset-Matching, package-private |
| `backend/src/test/java/com/budgetbuddy/budget/FixedCostDebitMatcherTest.java` | Grenzfälle des Matchings |
| `docs/adr/ADR-13-fixkosten-transaktions-zuordnung.md` | Entscheid-Record (AC1) |

## Implementierungsschritte

1. `FixedCostDebitMatcher`: nimmt die Monatsbelastungen und die Fixkosten-Positionen, liefert die
   Summe der **nicht** gematchten Belastungen. Multiset über `setScale(2)`-Schlüssel; zwei gleich
   hohe Positionen streichen zwei Belastungen, eine Position nie zwei.
2. Port umstellen und im `TransactionSummaryService` implementieren.
3. `SafeToSpendService` auf den Matcher umstellen. Der `noIncome`-Zweig bleibt unberührt — dort
   werden weiterhin weder Fixkosten noch Ausgaben geladen.
4. ADR-13 schreiben: Kontext, Entscheid, Konsequenzen inklusive Falsch-Positiv-Risiko, verworfene
   Alternativen und der Upgrade-Pfad über #159 → US-08.
5. Doku nachziehen: ADR-Index, CLAUDE.md-Tabelle, US-06, Javadocs, OpenAPI.

## Test-Strategie

| Ebene | Test | Deckt |
| ----- | ---- | ----- |
| Unit | `FixedCostDebitMatcherTest` (neu) | 1:1-Grenzen: zwei gleich hohe Positionen ↔ eine Belastung; Position ohne Treffer; Belastung ohne Position; leere Listen; Skalen-Ungleichheit (`1200` vs. `1200.00`) |
| Unit | `SafeToSpendServiceTest` | **AC3-Regression:** Fixkosten 1'200 + Belastung 1'200 im selben Monat → genau einmal abgezogen. Dazu Quartals-/Jahresfall und der unveränderte US-06-Beispielfall |
| Integration | `SafeToSpendServiceIntegrationTest` | Derselbe Fall über echtes PostgreSQL + Flyway, plus die bestehende Mandantentrennungs-Gegenprobe: ein fremder User mit gleich hohen Fixkosten darf das Ergebnis nicht verschieben |

Kein Frontend-Anteil: `SafeToSpendResponse` ändert sich weder in Form noch in den Feldern.

## Bewusst nicht im Scope

- **Keine Migration, kein `transactions.fixed_cost_id`** — in ADR-13 als Alternative verworfen und
  als Upgrade-Pfad dokumentiert.
- **Keine Korrektur-UI** für falsch gematchte Transaktionen. Das ist derselbe Bedarf wie «Kein Abo»
  aus US-08 und gehört dorthin, nicht in einen 1-SP-Bugfix. ADR-13 benennt es als offene Konsequenz.
- `docs/prompts/02_01_mvp-requirements.md` bleibt unverändert — ein historisches Analyse-Artefakt,
  kein gepflegtes Dokument. ADR-13 verweist darauf zurück.

## Acceptance Criteria (aus dem Issue)

- [ ] Entscheid über den Zuordnungsmechanismus ist getroffen und als Record oder im Issue festgehalten
- [ ] Eine per Dauerauftrag bezahlte Fixkosten-Position mindert den Safe-to-Spend genau einmal
- [ ] Regressionstest deckt den Fall «Fixkosten-Position + passende Transaktion im selben Monat» ab
- [ ] US-06 bzw. die Formel-Dokumentation ist entsprechend nachgezogen
