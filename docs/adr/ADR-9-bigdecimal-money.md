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

- **Column Definition:** `DECIMAL(10,2)` in SQLite
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

## Related Decisions

- **ADR-5:** SQLite Datenbank (DECIMAL Column Type)
- **ADR-6:** Hybrid Kategorisierung (Amount Parameter)
