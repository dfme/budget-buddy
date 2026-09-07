# ADR-9: BigDecimal für alle Geldbeträge (nicht double/float)

**Status:** Accepted  
**Date:** 2026-05-27

## Context

BudgetBuddy speichert und berechnet CHF-Beträge (Transaktionen, Einkommen, Safe-to-Spend, Fixkosten).

**Problem mit Floating-Point:**

```java
// ❌ double: Rounding Error!
double amount1 = 0.1;
double amount2 = 0.2;
System.out.println(amount1 + amount2);  // Output: 0.30000000000000004 (NOT 0.3!)

// ✅ BigDecimal: Exact
BigDecimal amount1 = new BigDecimal("0.1");
BigDecimal amount2 = new BigDecimal("0.2");
System.out.println(amount1.add(amount2));  // Output: 0.3 (CORRECT!)
```

**Anforderung:** Exakte Berechnung bis auf den Rappen (0.01 CHF), keine Rounding Errors.

Alternative: double, float, long (Cents), Money Library

## Decision

Wir nutzen **BigDecimal für alle Geldbeträge**:

- **Column Definition:** `DECIMAL(10,2)` — seit ADR-12 als echtes PostgreSQL-`numeric(10,2)`, davor als SQLite-Affinität (#141)
- **Java Type:** `BigDecimal` (nicht double/float)
- **Rounding:** Explicit `RoundingMode.HALF_UP` für Division
- **Database:** JPA `@Column(columnDefinition="DECIMAL(10,2)")`

## Consequences

### Positive

- **Exact Arithmetic:** Keine Rounding Errors (0.1 + 0.2 = 0.3 garantiert)
- **Safe for Finance:** CHF-Beträge sind immer auf den Rappen genau
- **User Trust:** "Cent-" oder Rappenausfälle zerstören Vertrauen
- **Tax Compliance:** Schweizer Finanzbehörden erwarten Genauigkeit

### Negative

- **Performance:** BigDecimal ist langsamer als double
  - Mitigation: Nicht bottleneck für BudgetBuddy (<1000 Transaktionen/Monat)
- **Memory:** Größerer Overhead als 8-byte double
  - Mitigation: Nicht relevant für MVP-Scale
- **Verbosity:** Mehr Code als `amount + 10` vs. `amount.add(new BigDecimal("10"))`
  - Mitigation: Utility-Methoden schreiben

## Alternatives

### double (Native Floating-Point)

**Rejected.** Fast, aber:

- IEEE 754 Binary Floating-Point ist nicht exact
- 0.1 + 0.2 ≠ 0.3 (Rounding Errors akkumulieren)
- Finanz-Applikationen verlieren User-Vertrauen bei Cent-Fehlern

### long (Store as Cents)

**Rejected.** Exact (Integer), aber:

- Mehr Code: `10050` statt `100.50`
- Database Type Mismatch: SQLite INTEGER vs. Application Logic CHF
- Nicht Spring JPA compatible ohne Custom Converter

### Money Library (Moneta, Joda-Money)

**Rejected.** Elegant, aber:

- Extra Dependency für MVP
- Nicht nötig (BigDecimal ist ausreichend)
- Später hinzufügbar wenn needed

## Nachtrag 07.09.2026: Das `money`-Package

Die oben unter *Consequences → Negative → Verbosity* angekündigte Mitigation — «Utility-Methoden
schreiben» — ist seit [BE-FC-04](https://github.com/dfme/budget-buddy/issues/205) eingelöst:
`com.budgetbuddy.money.ChfAmounts` hält die Regel für einen client-gelieferten CHF-Betrag
(`> 0`, höchstens zwei Nachkommastellen, höchstens `99'999'999.99`) und die Normalisierung auf
Rappen. Der Entscheid dieses ADR — `BigDecimal`, `DECIMAL(10,2)`, `HALF_UP` bei Division — bleibt
unverändert.

**Warum ein neues Top-Level-Package.** Die Regel stand nach BE-AUTH-08 zweimal im Backend, in
`auth/UserService` und `budget/FixedCostService`, mit derselben Obergrenze aus derselben Ursache
(`DECIMAL(10,2)` in `V01`, `V02` und `V03`). US-07 (Sparziel) und US-14 (Einkommen in den
Einstellungen) bringen die nächsten client-gelieferten Beträge; die dritte Kopie war absehbar.
Ein gemeinsamer Ort war nötig, und keines der bestehenden Packages war einer:

- Sie in `auth` oder `budget` zu legen und vom jeweils anderen Modul aufzurufen, wäre der
  modulübergreifende Zugriff, den CLAUDE.md untersagt.
- `config` enthält ausschliesslich Spring-Verdrahtung (`SecurityConfig`, `ClockConfig`,
  `OpenApiConfig`); eine fachliche CHF-Regel dort sucht niemand.
- Ein `common` wäre über die Zeit der Magnet für alles Heimatlose. `money` ist durch seinen Namen
  auf Geldbeträge begrenzt und wehrt genau das ab.

`money` ist damit die begründete Ausnahme von «Packages nach Domäne» (`docs/CONVENTIONS.md`). Es
bleibt eng: zustandslose Regeln über CHF-Beträge, keine Repositories, keine Entities, keine
Spring-Beans. Was hinein darf und was nicht, steht in `money/package-info.java`.

**Was bewusst modul-lokal bleibt: Meldung und Exception.** `ChfAmounts.check(...)` wirft nichts,
sondern meldet als `Violation`, *welche* Regel verletzt ist. Text und Exception-Typ liefert der
aufrufende Service: US-03 und [#148](https://github.com/dfme/budget-buddy/issues/148) verlangen
feldspezifische Meldungen («Betrag darf …» vs. «Einkommen darf …»), und ein geteilter
Exception-Typ wäre wieder der modulübergreifende Zugriff. Geteilt wird die Prüfung, nicht der
Text.

**Verworfen: ein Werttyp `ChfAmount` statt `BigDecimal`.** Die konsequentere Variante — Beträge
tragen ihre Regeln im Typ, ein ungültiger Betrag ist nicht konstruierbar. Abgelehnt, weil sie
Entities, DTOs, die Jackson-Serialisierung und jede Rechenstelle berührte; das ist kein
Aufräum-Task, sondern ein Umbau. Die Ablehnung steht hier ausdrücklich, damit sie nicht bei jeder
weiteren Geld-Stelle neu diskutiert wird. Falls sie je kommt, ist `ChfAmounts` der Ort, an dem
die Regel schon versammelt ist.

**Verworfen: eine Bean-Validation-Constraint `@ChfAmount`.** Sie griffe erst, wenn ein Controller
`@Valid` setzt — der Service bliebe ungeschützt, sobald ihn jemand anders aufruft, und
`UserService.updateIncome` ist über `UserIncomePort` schon heute aus dem `budget`-Modul
erreichbar. Das eingebaute `@Digits(fraction = 2)` scheitert zusätzlich an `100.000`: es zählt
`BigDecimal.scale()` ohne `stripTrailingZeros()` und lehnt damit einen Wert ab, der `100.00`
gleich ist. Die Request-DTOs tragen deshalb weiterhin keine Bean-Validation-Annotationen.

---

## Related Decisions

- **ADR-12:** PostgreSQL bei Neon — löst `DECIMAL(10,2)` erstmals als echte Dezimalarithmetik ein
- **ADR-6:** Hybrid Kategorisierung (Amount Parameter)
