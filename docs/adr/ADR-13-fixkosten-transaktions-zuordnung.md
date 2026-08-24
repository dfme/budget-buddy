# ADR-13: Zuordnung von Fixkosten-Positionen zu importierten Transaktionen

**Status:** Accepted
**Date:** 2026-08-21
**Issue:** [#154](https://github.com/dfme/budget-buddy/issues/154) (BE-STS-04)

## Context

Der Safe-to-Spend aus US-06 rechnet:

```
(Einkommen − Fixkosten − Ausgaben im laufenden Monat) ÷ verbleibende Wochen
```

`Fixkosten` ist die Monatssumme der Positionen, die der Nutzer im Onboarding-Wizard erfasst hat
(US-03). `Ausgaben` ist die Summe der Belastungen aus den importierten Kontoauszügen (US-04).

Eine Fixkosten-Position, die per **Dauerauftrag** abgeht, steht damit in **beiden** Summanden. Eine
Miete von 1'200 CHF mindert den Betrag um 2'400 CHF. Je mehr Fixkosten erfasst sind, desto stärker
fällt der Safe-to-Spend zu tief aus — im Extremfall zeigt das Dashboard fälschlich das Warn-Banner
«Budget überzogen» und trifft damit genau den Kernnutzen der App.

Die Formel ist nicht falsch implementiert: sie ist wörtlich das, was US-06 verlangt. BE-STS-01
(#21) hat sie bewusst so umgesetzt und die Lücke im Javadoc als bekannte Einschränkung vermerkt.
Der Punkt ist älter als beide Issues — `docs/prompts/02_01_mvp-requirements.md` führt ihn in der
ursprünglichen Anforderungsanalyse als *Risiko 2 — Fixkosten doppelt gezählt?* und lässt ihn offen.

**Die eigentliche Frage:** Woran erkennt das System, dass eine importierte Belastung die Zahlung
einer erfassten Fixkosten-Position ist? Im Datenmodell gibt es zwischen `fixed_costs` und
`transactions` keinerlei Verknüpfung.

### Randbedingung: der Empfänger steht nicht zur Verfügung

Das naheliegende Kriterium wäre der Empfänger. Er ist heute nicht persistiert. Der PDF-Import
schreibt nur `buchungstext`; die Detailzeilen aus `ParsedTransaction` gehen zwar in die
Kategorisierung, werden danach aber verworfen ([#159](https://github.com/dfme/budget-buddy/issues/159),
BE-PDF-07). Bei Überweisungen und Daueraufträgen trägt `buchungstext` nur den Buchungs*typ*:

| `buchungstext` (persistiert) | `details` (verworfen) | Betrag |
| --- | --- | --- |
| `GIRO POST` | `[Immo Verwaltung AG, RÜCKZAHLUNG KAUTION]` | 2'400.00 |
| `GIRO AUS KONTO 25-9034-2` | `[Muster Consulting GmbH, Bahnhofstrasse 1, 8000 Zürich]` | 4'589.10 |

Ein Textvergleich gegen die Bezeichnung einer Fixkosten-Position («Miete») hätte damit nichts, woran
er greifen könnte. Solange #159 offen ist, bleibt als Kriterium der **Betrag**.

## Decision

**Betragsbasiertes 1:1-Matching zur Berechnungszeit, ohne Änderung am Datenmodell.**

```
fixedCosts = Σ monatsbetrag aller Positionen        (unverändert)
expenses   = Σ Belastungen des Monats
             − Σ gematchte Belastungen

Match-Regel, je Fixkosten-Position höchstens eine Belastung:
  FixedCost.betrag  ==  Betrag einer noch nicht verbrauchten Belastung des Monats
```

Umgesetzt im `FixedCostDebitMatcher` (`budget/`), aufgerufen vom `SafeToSpendService`. Drei
Festlegungen gehören dazu:

**1. Multiset, nicht Menge.** Zwei Positionen zu je 59.00 CHF streichen zwei Belastungen über
59.00 CHF; eine einzelne Position streicht niemals zwei. Nur so gilt «genau einmal» in beide
Richtungen.

**2. Verglichen wird gegen `betrag`, nicht gegen `monatsbetrag`** — gegen die tatsächliche
Abbuchung also, nicht gegen den auf einen Monat normalisierten Anteil. Nur so stimmt der
Nicht-Monats-Fall:

| | Fixkosten-Seite | Ausgaben-Seite | Summe |
| --- | --- | --- | --- |
| Miete 1'200 monatlich, Abbuchung jeden Monat | 1'200 / Monat | −1'200 gestrichen | 1'200 / Monat ✔ |
| Versicherung 1'200 jährlich, Abbuchung im März | 100 / Monat | März: −1'200 gestrichen | 1'200 / Jahr ✔ |

**3. Die gestrichene Belastung bleibt in der Kategorie-Übersicht sichtbar.** Ausgenommen ist sie
allein aus dem Safe-to-Spend-Summanden. `TransactionSummaryService.summarize(...)` wird nicht
angefasst: das Konto *wurde* belastet, und eine Miete gehört unter «Wohnen». Das ist exakt die
Auflösung, die Risiko 2 in `02_01_mvp-requirements.md` vorgeschlagen hat.

Die Modulkante wandert von der fertigen Summe auf die Einzelbeträge: `MonthlyExpensePort`
liefert `expenseAmounts(userId, month)` statt `sumExpenses(userId, month)`. Über die Kante gehen
weiterhin nur Beträge — keine Buchungstexte, keine Kategorien, keine Entities. Die Zuordnungsregel
selbst bleibt im `budget/`-Modul, wo die Fixkosten liegen.

## Rationale

- **Die Regel erfüllt «genau einmal» exakt**, nicht bloss näherungsweise. Das ist die Zusage, die
  US-06 gegenüber dem Nutzer macht.
- **Kein Schemawechsel, keine Migration, kein Backfill.** Bereits importierte Transaktionen
  profitieren sofort — ein neu erfasster Fixkosten-Eintrag wirkt rückwirkend auf jeden Monat,
  ohne dass irgendetwas nachgezogen werden müsste.
- **Der Aufwand steht im Verhältnis.** #154 ist mit 1 Story Point veranschlagt. Migration plus
  Import-Verknüpfung plus Korrektur-UI wäre ein Vielfaches davon, ohne heute — ohne #159 — eine
  bessere Zuordnung zu liefern.
- **Der Betrag ist bei Daueraufträgen ein starkes Kriterium.** Genau dort ist er konstant und
  rappengenau wiederkehrend; das ist der Fall, um den es geht.

## Consequences

### Positive

- Der Safe-to-Spend ist für den häufigsten Fall — Miete und Krankenkasse per Dauerauftrag — erstmals
  korrekt. Der Kernnutzen aus CLAUDE.md («eine Zahl, der Nutzer vertrauen können») hängt daran.
- Die Formel bleibt an einer Stelle, im `SafeToSpendService`. Es entsteht kein zweiter Ort, an dem
  Fixkosten und Transaktionen zusammengeführt werden.
- `MonthlyExpensePort` hat weiterhin genau eine Methode — es gibt keine zwei Wege auf dieselbe Zahl,
  die auseinanderlaufen könnten.

### Negative

- **Falsch-positive Treffer sind möglich.** Eine echte Ausgabe, die zufällig rappengenau den Betrag
  einer Fixkosten-Position trifft, wird mitgestrichen; der Safe-to-Spend fällt dann um diesen Betrag
  zu **hoch** aus. Das ist die unangenehmere Fehlerrichtung — sie sagt dem Nutzer, er habe mehr
  Spielraum als er hat. Der Fehler ist auf eine Position begrenzt und tritt nur bei exakter
  Gleichheit auf; der bisherige Zustand ohne Matching war dagegen *systematisch* falsch, um die
  volle Fixkosten-Summe und in jedem Monat. Die Abwägung fällt deshalb klar aus.
- **Ein falscher Treffer ist für den Nutzer nicht sichtbar und nicht korrigierbar.** Es gibt keine
  Anzeige «diese Belastung wurde als Fixkosten-Zahlung gewertet» und keinen Weg, sie zu lösen. Das
  ist derselbe Bedarf wie der «Kein Abo»-Override aus US-08 und bleibt offen.
- **Wird eine Fixkosten-Position in einem Monat nicht abgebucht** (Zahlungsaufschub, Wechsel der
  Zahlungsart), streicht die Regel nichts und die Position zählt korrekt einmal — dieser Fall ist
  abgedeckt. Wird sie dagegen *zweimal* im selben Monat abgebucht (Nachzahlung), zählt die zweite
  Abbuchung als variable Ausgabe. Das ist gewollt: sie ist eine zusätzliche Belastung.
- Der Port liefert eine Liste statt einer Zahl. Bei einem Monat mit sehr vielen Buchungen wandern
  entsprechend viele `BigDecimal` über die Kante statt eines Werts. Für die Grössenordnung eines
  privaten Zahlungskontos ist das unerheblich.

## Alternatives

### A) Explizite Verknüpfung `transactions.fixed_cost_id`

Flyway-Migration mit neuer Spalte und Fremdschlüssel, beim Import automatisch gesetzt und vom Nutzer
korrigierbar.

**Fachlich die saubere Lösung** — sie macht die Zuordnung sichtbar, korrigierbar und stabil, und sie
ist deckungsgleich mit dem «Kein Abo»-Override aus US-08.

**Trotzdem heute verworfen:** Ohne #159 fehlt der Empfänger, das Auto-Matching müsste also *ebenfalls*
über den Betrag laufen. Die Heuristik verschwindet nicht, sie wird nur persistiert — und damit auch
ihre Fehler. Dem stünde der volle Aufwand gegenüber: Migration, Migrationstest, Import-Pfad,
Endpoint, Korrektur-UI im Frontend und ein Entscheid über den Backfill bestehender Importe.

**Als Upgrade-Pfad vorgemerkt.** Sobald #159 den Empfänger persistiert und US-08 die
Wiederkehr-Erkennung baut, ist dies die richtige Ablösung dieses ADR: dieselbe Erkennungslogik
trägt dann beide Features, und die hier fehlende Korrigierbarkeit kommt mit dem «Kein Abo»-Override
ohnehin. Der `FixedCostDebitMatcher` ist bewusst zustandslos und an einer Stelle aufgerufen, damit
dieser Wechsel lokal bleibt.

### B) Anteiliger Abzug für den Restmonat

Fixkosten nur für die verbleibenden Tage des Monats abziehen statt für den ganzen Monat.

**Verworfen, weil es das Problem nicht löst.** `fixed_costs` hat keinen Fälligkeitstag, die
Verteilung wäre also eine lineare Annahme. Eine am 1. abgebuchte Miete von 1'200 CHF ergibt am 15.:

```
fixedCosts = 1'200 × 17/31 = 658.06
expenses   = 1'200.00
             ────────────
             1'858.06   statt 1'200.00
```

Der Doppelabzug wird gemildert, nicht beseitigt — die Zusage «genau einmal» aus US-06 wäre nicht
erfüllt, sondern nur unauffälliger verletzt. Das ist die schlechtere Sorte Fehler: schwerer zu
bemerken und schwerer zu erklären.

### C) Fixkosten aus dem Wizard streichen und vollständig aus dem PDF ableiten

US-03 entfiele, Fixkosten würden als wiederkehrende Belastungen erkannt.

**Verworfen:** US-03 ist Must-Have und der Einstiegspunkt des Onboardings — er funktioniert *bevor*
der erste Auszug hochgeladen ist. Ihn zu streichen hiesse, dass ein neuer Nutzer ohne PDF-Import gar
keinen Safe-to-Spend sieht, und trifft damit direkt Risiko #1 aus CLAUDE.md (Churn beim ersten
Upload).

### D) Ganze Kategorien aus den Ausgaben ausnehmen

Etwa «Wohnen» und «Versicherung» gar nicht als variable Ausgaben zählen.

**Verworfen:** Die Kategorien sind zu grob. «Wohnen» enthält neben der Miete auch den Möbelkauf,
«Versicherung» neben der Krankenkasse die einmalige Reiseversicherung. Die Regel würde echte
variable Ausgaben verschlucken und den Safe-to-Spend unbegrenzt zu hoch ausweisen — ohne jede
Obergrenze, anders als beim betragsbasierten Matching.

## Related

- [ADR-9](ADR-9-bigdecimal-money.md) — alle Beträge als `BigDecimal`; das Matching vergleicht auf
  Rappen normalisierte Werte, nie Fliesskomma.
- [US-06](../requirements/US-06-safe-to-spend.md) — Safe-to-Spend-Formel.
- [US-03](../requirements/US-03-fixkosten-wizard.md) — Erfassung der Fixkosten-Positionen.
- [US-08](../requirements/US-08-wiederkehrende-ausgaben.md) — Abo-Erkennung; teilt sich mit diesem
  ADR die Frage nach der Wiederkehr-Erkennung und bringt den «Kein Abo»-Override mit.
- [#159](https://github.com/dfme/budget-buddy/issues/159) (BE-PDF-07) — persistiert den Empfänger
  und ist die Vorbedingung für ein trennschärferes Kriterium.
