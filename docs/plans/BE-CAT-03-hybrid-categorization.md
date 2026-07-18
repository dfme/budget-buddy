# [BE-CAT-03] HybridCategorizationService

| Feld | Wert |
| ---- | ---- |
| Issue | [#16](https://github.com/dfme/budget-buddy/issues/16) |
| Task-ID | BE-CAT-03 |
| Branch | `feature/BE-CAT-03-hybrid-categorization` |
| Depends on | #15 (BE-CAT-02) |

## Ziel

Orchestriert die Hybrid-Kategorisierung (ADR-6): zuerst die Lookup-Tabelle, Claude nur für
unbekannte Transaktionen. Implementiert `CategorizationPort` und ist damit der Einstiegspunkt,
den der spätere PDF-Import injiziert.

## Entscheide

1. **`@Primary` auf dem Hybrid-Service** — es gibt drei `CategorizationPort`-Beans
   (`LookupTableService`, `ClaudeCategorizationService`, `HybridCategorizationService`).
   `@Primary` sorgt dafür, dass Aufrufer, die den Port injizieren, automatisch die vollständige
   Kette bekommen, ohne dass jede Injection-Stelle ein `@Qualifier` braucht.
2. **Konstruktor nimmt konkrete Typen** (`LookupTableService`, `ClaudeCategorizationService`)
   statt zweier `CategorizationPort`-Parameter. Die Reihenfolge der Kette ist damit im Code
   sichtbar und hängt nicht von einer Bean-Namensauflösung ab.
3. **Zusätzlicher `RuntimeException`-Catch um den Claude-Call.**
   `ClaudeCategorizationService` fängt selbst nur `AnthropicException`; ein unerwarteter
   Laufzeitfehler (z. B. aus dem SDK) käme sonst bis in den Import-Flow durch. AC 3 verlangt,
   dass Claude-Fehler den Import nie abbrechen.
4. **Lookup-Fehler propagieren.** Ein DB-Fehler ist ein echter Fehler, kein Fallback-Fall — er
   wird nicht zu `Sonstiges` geschluckt.
5. Als letzte Stufe der Kette liefert der Service für jede nicht-leere Eingabe eine Kategorie;
   `Optional.empty()` kommt nur bei null/blank zurück.

## Files

- Neu: `backend/src/main/java/com/budgetbuddy/categorization/HybridCategorizationService.java`
- Neu: `backend/src/test/java/com/budgetbuddy/categorization/HybridCategorizationServiceTest.java`

## Implementierungsschritte

1. `HybridCategorizationService implements CategorizationPort`, `@Service` + `@Primary`.
2. Konstruktor: `LookupTableService`, `ClaudeCategorizationService`.
3. `categorize`: null/blank → `Optional.empty()`; Lookup zuerst, bei Treffer Return ohne
   Claude-Call (AC 1); bei `empty()` Delegation an Claude (AC 2).
4. Claude-Call in `try/catch (RuntimeException)` mit Fallback `Sonstiges` (AC 3).

## Test-Strategie

Unit-Test (JUnit 5 + Mockito + AssertJ), analog zu `ClaudeCategorizationServiceTest`:

- Bekannter Händler → Kategorie aus Lookup, `verifyNoInteractions(claude)` (AC 1)
- Unbekannter Händler → Delegation an Claude, Kategorie wird durchgereicht (AC 2)
- Claude liefert `Sonstiges` → wird durchgereicht
- Claude wirft `RuntimeException` → `Sonstiges` statt Propagation (AC 3)
- Lookup wirft `RuntimeException` → propagiert
- null / blank Input → `Optional.empty()`, keine Interaktion mit beiden Stufen

Keine neuen API-Endpoints → der DoD-Punkt „Swagger UI" entfällt.

## Acceptance Criteria (aus Issue #16)

- [ ] Bekannte Händler werden ohne Claude-API-Call kategorisiert
- [ ] Unbekannte Händler werden an ClaudeCategorizationService delegiert
- [ ] Claude-Fehler führen nie zum Abbruch des Import-Flows
