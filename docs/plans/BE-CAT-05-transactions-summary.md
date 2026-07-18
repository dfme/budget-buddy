# [BE-CAT-05] GET /transactions/summary

- **Issue:** [#20](https://github.com/dfme/budget-buddy/issues/20)
- **Task-ID:** BE-CAT-05
- **Branch:** `feature/BE-CAT-05-transactions-summary`
- **Area:** Backend · Story: US-05 · Sprint 3

## Beschreibung

`GET /transactions/summary?month=YYYY-MM` liefert pro Kategorie die CHF-Summe, die
Anzahl Transaktionen und den Prozentanteil. Berechnung mit `BigDecimal`, auf Rappen genau.

## Entscheidungen (mit User bestätigt)

| Frage | Entscheid | Begründung |
|-------|-----------|------------|
| Umfang | **Nur Ausgaben** (`is_income = false`) | Denominator = Summe der Ausgaben → reiner Ausgaben-Breakdown (Pie-Chart FE-CAT-02, US-13). Einkommen wird nicht aggregiert. |
| Kategorien | **Nur im Monat vorkommende** | Kein Rauschen aus 0-CHF-Slices. AC „alle Kategorien" = alle vorkommenden. |
| Unkategorisiert (`category = NULL`) | **Als `Sonstiges` zählen** | Dokumentierter Fallback (ADR-6). |
| Prozent-Rundung | **Largest-Remainder** (HALF_UP-Basis) | Garantiert Summe = exakt 100.00. Naives Runden würde z.B. 99.99 liefern. |
| Gesamtsumme = 0 | Leere Kategorienliste, `totalAmount = 0.00` | Kein Division-durch-0. |

## Kontext-Fund

Es gibt noch **keine persistierte `Transaction`-JPA-Entity** (nur `ParsedTransaction` auf dem
noch nicht gemergten PDF-Branch). Die `transactions`-Tabelle (Flyway V02) existiert.
Dieser Task ist der erste Konsument persistierter Transaktionen und legt daher eine
minimale, schema-treue `Transaction`-Entity + Repository an (mappt exakt V02).

## Betroffene / neue Files

### Neu
- `transaction/Transaction.java` — JPA-Entity zu `transactions` (V02).
- `transaction/TransactionRepository.java` — Ausgaben eines Users in einem Monat.
- `transaction/TransactionSummaryService.java` — Aggregation + Prozentlogik.
- `transaction/TransactionSummaryController.java` — `GET /transactions/summary`.
- `transaction/dto/CategorySummaryResponse.java`
- `transaction/dto/CategorySummaryItem.java`
- `transaction/InvalidMonthException.java`
- `transaction/TransactionExceptionHandler.java` — ungültiges/fehlendes `month` → 400.

### Bestehend
- Keine. `/transactions/**` fällt unter `anyRequest().authenticated()` (SecurityConfig) → 401 ohne JWT automatisch.

## Implementierungsschritte

1. `Transaction`-Entity + `TransactionRepository` (schema-treu zu V02).
2. DTOs (`CategorySummaryResponse`, `CategorySummaryItem`).
3. `TransactionSummaryService`: Monatsgrenzen (`YearMonth`), Ausgaben laden, in Java je
   Kategorie mit `BigDecimal` aggregieren (Summe + Anzahl), NULL→Sonstiges, Prozente via
   Largest-Remainder (scale 2, HALF_UP-Basis).
4. Controller + Springdoc-Annotationen (200/400/401).
5. `InvalidMonthException` + `TransactionExceptionHandler` (400).

## Test-Strategie

- **Unit** (`TransactionSummaryServiceTest`, JUnit5 + Mockito + AssertJ): Prozente = exakt
  100.00 (inkl. 3×33.33 %-Fall), reiner `BigDecimal`-Pfad, NULL→Sonstiges,
  Ausgaben-only-Filter, leerer Monat → leere Liste, ungültiger Monat → `InvalidMonthException`.
- **Integration** (`TransactionSummaryControllerIntegrationTest`, `@SpringBootTest` +
  `MockMvc` + echte SQLite-Temp-File-DB + Flyway, Muster wie `UserControllerTest`):
  Happy Path (Summen/Anzahl/Prozent), 401 ohne JWT, 400 bei ungültigem `month`.

## Acceptance Criteria (aus Issue #20)

- [ ] Response enthält alle (vorkommenden) Kategorien mit Summe (CHF), Anzahl und Prozent
- [ ] Berechnung mit `BigDecimal` (kein `double`)
- [ ] Prozentangaben summieren sich auf 100 % (Rundung: HALF_UP / Largest-Remainder)
- [ ] Endpoint in Swagger UI dokumentiert

## Definition of Done

- [ ] Code reviewed (≥1 Approval im PR)
- [ ] `mvn package` läuft fehlerfrei
- [ ] Endpoint in Swagger UI sichtbar (OpenAPI-Annotation)
- [ ] Happy Path per JUnit abgedeckt
- [ ] Alle AC erfüllt
