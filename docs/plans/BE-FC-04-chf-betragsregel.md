# [BE-FC-04] CHF-Betragsregel steht doppelt im Backend

- **Issue:** [#205](https://github.com/dfme/budget-buddy/issues/205)
- **Task-ID:** `BE-FC-04`
- **Branch:** `feature/BE-FC-04-chf-betragsregel`
- **Story:** — (kein `us-*`-Label)
- **Sprint:** Sprint 6
- **Bestätigt am:** 2026-09-07

## Ausgangslage

Seit BE-AUTH-08 (#148 / PR #204) steht dieselbe CHF-Betragsregel zweimal im Backend:

| Stelle | Konstanten | Regel |
| --- | --- | --- |
| `budget/FixedCostService.java:219-241` (`validateBetrag`) | `RAPPEN_SCALE = 2` (Z. 42), `MAX_BETRAG = 99999999.99` (Z. 47) | `> 0`, `stripTrailingZeros().scale() <= 2`, `<= MAX`, dann `setScale(2, UNNECESSARY)` |
| `auth/UserService.java:89-118` (`validateBetrag`) | `RAPPEN_SCALE = 2` (Z. 31), `MAX_INCOME = 99999999.99` (Z. 38) | identisch |

Beide leiten dieselbe Grenze aus derselben Ursache ab: `DECIMAL(10,2)` in den Migrationen
(`V01__create_users_table.sql:10`, `V03__create_fixed_costs_table.sql:8`).

## Entscheide

| Frage aus dem Issue | Entscheid | Begründung |
| --- | --- | --- |
| Wo liegt der Helper? | neues Top-Level-Package `com.budgetbuddy.money`, Klasse `ChfAmounts` | ADR-9 nennt unter *Consequences → Negative → Verbosity* selbst „Utility-Methoden schreiben" als Mitigation — der Helper ist die Einlösung eines alten Entscheids, kein neuer. Das Package hält kein Repository, keine Entity und keinen Service; die CLAUDE.md-Modulregel („kein direkter Zugriff auf Repositories/Services eines anderen Moduls") wird nicht berührt, weil es nichts davon gibt. Festgehalten als `## Nachtrag` in ADR-9, Formatvorbild ist der Nachtrag in ADR-10. |
| Werttyp `ChfAmount` statt `BigDecimal`? | **ausdrücklich abgelehnt**, schriftlich im Nachtrag | Er berührte Entities, DTOs, Jackson-Serialisierung und jede Rechenstelle. Das Issue verlangt genau diese ausgesprochene Ablehnung statt Schweigen. |
| Bean-Validation-Constraint `@ChfAmount`? | nein | Sie erbt die Aktivierungsschwäche jeder Annotation: `UserService.updateIncome` ist über `UserIncomePort` schon heute ohne `@Valid`-Controller erreichbar. Beide DTO-Javadocs, die das begründen, bleiben unverändert gültig. |
| Was wird geteilt? | die *Prüfung* — nicht der Meldungstext, nicht der Exception-Typ | US-03 und #148 verlangen feldspezifische Texte. Ein gemeinsamer Exception-Typ wäre der modulübergreifende Zugriff, den CLAUDE.md untersagt. |
| `transactions.betrag`? | ausserhalb des Scopes | Der Wert kommt aus dem PDF-Parser, nicht von einem Client — dort ist die Frage Rundung beim Parsen, nicht Eingabevalidierung. |

## Scope-Erweiterung (bestätigt, ausserhalb der ACs)

Die enge Suche nach dem Wortlaut des Issues findet zwei Kopien der *Regel*. Die breite Suche über
den zugrundeliegenden Begriff findet die Konstante `RAPPEN_SCALE = 2` in **fünf** Klassen:

```
auth/UserService.java:31
budget/FixedCostService.java:42
budget/FixedCostDebitMatcher.java:51
budget/SafeToSpendService.java:107
transaction/IncomeSuggestionService.java:79
```

Alle fünf werden auf `ChfAmounts.RAPPEN_SCALE` umgestellt. Die nackten `setScale(2)`-Literale in
`TransactionSummaryService` (Z. 65, 115, 160) und `SwissBankStatementParser` (Z. 683, 864) bleiben
stehen: in `TransactionSummaryService` steht daneben ein `PERCENT_SCALE = 2` mit anderer Bedeutung,
und der Parser normalisiert Parser-Ausgabe statt Client-Eingabe.

## API des Helpers

```java
public final class ChfAmounts {
    public static final int RAPPEN_SCALE = 2;
    public static final BigDecimal MAX = new BigDecimal("99999999.99");  // V01/V02/V03: DECIMAL(10,2)
    public static final String MAX_FORMATTED = "99'999'999.99";          // für die Meldungstexte

    public enum Violation { FEHLT, NICHT_POSITIV, ZU_VIELE_NACHKOMMASTELLEN, UEBER_MAXIMUM }

    public static Optional<Violation> check(BigDecimal betrag);
    public static BigDecimal toRappen(BigDecimal betrag);
}
```

`MAX_FORMATTED` ist der Grund, warum AC-3 wirklich erfüllt ist: ohne sie stünde `99'999'999.99`
weiterhin zweimal im Meldungstext, die Zahl wäre also nur halb dedupliziert.

Die Reihenfolge der `Violation`-Prüfung entspricht exakt der heutigen Prüfreihenfolge beider
Services: `null` → Vorzeichen → Skala → Maximum. Eine andere Reihenfolge änderte bei mehrfach
verletzten Eingaben die Meldung und damit sichtbares Verhalten.

## Betroffene und neue Dateien

**Neu**

- `backend/src/main/java/com/budgetbuddy/money/ChfAmounts.java`
- `backend/src/main/java/com/budgetbuddy/money/package-info.java`
- `backend/src/test/java/com/budgetbuddy/money/ChfAmountsTest.java`

**Geändert — die Regel (AC 1–3)**

- `backend/src/main/java/com/budgetbuddy/auth/UserService.java`
- `backend/src/main/java/com/budgetbuddy/budget/FixedCostService.java`

**Geändert — Scope-Erweiterung**

- `backend/src/main/java/com/budgetbuddy/budget/FixedCostDebitMatcher.java`
- `backend/src/main/java/com/budgetbuddy/budget/SafeToSpendService.java`
- `backend/src/main/java/com/budgetbuddy/transaction/IncomeSuggestionService.java`

**Geändert — Tests**

- `backend/src/test/java/com/budgetbuddy/budget/FixedCostServiceTest.java` (Assertions ergänzt)

**Geändert — Doku**

- `docs/adr/ADR-9-bigdecimal-money.md` (Nachtrag)
- `docs/CONVENTIONS.md` (Package-Baum)
- `docs/plans/README.md` (Index-Zeile)

## Implementierungsschritte

1. `money`-Package mit `ChfAmounts` und `package-info.java` anlegen; die Herkunft der Obergrenze
   im Kommentar auf `V01__create_users_table.sql:10` / `V03__create_fixed_costs_table.sql:8`
   zurückführen (AC 3).
2. `UserService.validateBetrag` auf `check`/`toRappen` umstellen; die Meldung über ein `switch`
   auf `Violation`, Texte zeichengleich, `MAX_FORMATTED` eingesetzt. `InvalidIncomeException`
   bleibt.
3. `FixedCostService.validateBetrag` analog, `InvalidFixedCostException` bleibt.
4. Die drei weiteren `RAPPEN_SCALE`-Konstanten entfernen und ihre Verwendungsstellen auf
   `ChfAmounts.RAPPEN_SCALE` zeigen lassen.
5. Javadocs beider Services nachziehen — „die Regel steht hier und nur hier" ist ab jetzt falsch;
   richtig ist „die Regel steht in `ChfAmounts`, Meldung und Exception hier".
6. ADR-9-Nachtrag schreiben und den Package-Baum in `docs/CONVENTIONS.md` ergänzen.
7. `mvn -f backend/pom.xml package`.

## Test-Strategie

| Ebene | Inhalt |
| --- | --- |
| Unit (neu) | `ChfAmountsTest`: je ein Fall pro `Violation`, die Reihenfolge bei mehrfach verletzter Eingabe, `100.000` → gültig (die `stripTrailingZeros()`-Feinheit), `99999999.99` → gültig, `100000000.00` → `UEBER_MAXIMUM`, `toRappen` normalisiert auf Skala 2, `toRappen` auf einem ungeprüften Wert scheitert laut |
| Unit (ergänzt) | `FixedCostServiceTest`: die Betrags-Meldungstexte sind dort heute nicht abgesichert (die Integration prüft nur `$.field`). Assertions auf alle vier Texte plus ein `MAX_BETRAG`-Fall — sonst belegt „unverändert grün" für die budget-Seite nichts. |
| Bestehend | `UserServiceTest`, `UserControllerTest`, `FixedCostControllerIntegrationTest`, `SafeToSpendServiceTest`, `FixedCostDebitMatcherTest` und die `IncomeSuggestionService`-Tests laufen unverändert (AC 6). |

## Acceptance Criteria (aus #205)

- [ ] Die Regel (`> 0`, höchstens zwei Nachkommastellen via `stripTrailingZeros()`, Obergrenze,
      Normalisierung auf Skala 2) steht an genau einer Stelle im Backend
- [ ] `FixedCostService` und `UserService` rufen diese Stelle auf und behalten je ihre eigene
      modul-lokale Exception mit ihrer eigenen feldspezifischen Meldung
- [ ] Die Obergrenze ist einmal definiert und im Code auf die Spaltendefinition der Migration
      zurückgeführt (Kommentar mit Dateiname)
- [ ] Der Ort des Helpers ist entschieden und begründet — als Nachtrag zu ADR-9
- [ ] Keine Bean-Validation-Annotation an den Request-DTOs; die beiden Javadocs, die das
      begründen, bleiben gültig
- [ ] Die bestehenden Tests beider Module laufen unverändert grün — kein nach aussen sichtbares
      Verhalten ändert sich
