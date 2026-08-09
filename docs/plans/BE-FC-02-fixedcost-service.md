# [BE-FC-02] FixedCostService: CRUD und Normalisierung

- **Issue:** [#11](https://github.com/dfme/budget-buddy/issues/11)
- **Task-ID:** `BE-FC-02`
- **Branch:** `feature/BE-FC-02-fixedcost-service`
- **Story:** US-03 — Fixkosten erfassen (Onboarding-Wizard)
- **Sprint:** Sprint 4
- **Bestätigt am:** 2026-08-09

## Abgrenzung

Nur die Service-Schicht. REST-Endpoints, OpenAPI-Annotationen und
`POST /users/me/onboarding-complete` gehören zu
[#12 (BE-FC-03)](https://github.com/dfme/budget-buddy/issues/12) — ebenso der
`@RestControllerAdvice`, der die hier geworfenen Exceptions auf HTTP-Statuscodes abbildet.

Beide Abhängigkeiten waren zum Zeitpunkt der Planung geschlossen: #10 (BE-FC-01, Entity +
Repository) und #9 (BE-AUTH-02, `monthly_income` am User).

## Entscheide

### Einkommen über die Modulgrenze: Port statt Repository

Die Warnung «Fixkosten ≥ Einkommen» braucht `users.monthly_income` — das liegt im `auth`-Modul.
CLAUDE.md untersagt den direkten Zugriff auf Repositories oder Services eines anderen Moduls;
erlaubt ist Kommunikation über definierte Interfaces. Deshalb ein neuer `UserIncomePort` im
**liefernden** Modul (`auth`), implementiert von `UserService`, injiziert in den
`FixedCostService`.

Präzedenz ist der Weg, den `transaction` → `categorization` bereits geht: `CategorizationPort`
(lesend) und `CategoryLearningPort` (schreibend), beide im liefernden Modul definiert
(`TransactionCategoryService.java:17-18`). Derselbe Grund steht schon im
`FixedCostRepositoryIntegrationTest:29-31`, wo die Test-User per `JdbcTemplate` statt über das
`UserRepository` eingefügt werden.

### Rundung: pro Position, nicht erst auf der Summe

Nicht jeder Betrag ist glatt teilbar — 100.00 CHF quartalsweise sind 33.333… CHF/Monat. Normalisiert
wird deshalb **jede Position einzeln** mit `divide(divisor, 2, RoundingMode.HALF_UP)`; die
Monatssumme ist die Summe dieser bereits gerundeten Zeilen.

Die Alternative — intern volle Genauigkeit, Rundung erst auf der Gesamtsumme — ist mathematisch
exakter, aber dann addieren sich die im Wizard sichtbaren Zeilenbeträge nicht mehr zur angezeigten
Summe (Abweichung bis 1 Rappen pro Position). US-03 verlangt Rappen-Genauigkeit im Safe-to-Spend;
eine Summe, die der User nicht nachrechnen kann, ist an dieser Stelle der grössere Schaden.

### Einkommen NULL: keine Warnung

`users.monthly_income` ist nullable, solange das Onboarding läuft (`User.java:14-15`). Ohne
Vergleichswert gibt es keine belegbare Aussage: `exceedsIncome = false`, `monthlyIncome = null` in
der Antwort. Der Client unterscheidet «kein Einkommen erfasst» am null-Feld von «Einkommen reicht».
Im Wizard ist NULL der Normalzustand — eine Warnung dort wäre Rauschen.

### Warnung: `≥`, nicht `>`

`exceedsIncome = income != null && summeMonatlich.compareTo(income) >= 0`. Wortlaut des AC und von
US-03: «Summe aller Fixkosten (auf Monatsbasis) ≥ Monatseinkommen».

### Skala 2 an der DTO-Grenze

`FixedCostRepositoryIntegrationTest:146-150` hält als Charakterisierungstest fest, dass die aus
SQLite gelesene `BigDecimal` **keine garantierte Skala 2** hat (`335.00` kommt als Skala 0,
`0.10` als Skala 1 zurück, #141) — und delegiert den Fix ausdrücklich an #11: «Der Fix gehört als
`setScale(2)` an die DTO-Grenze in #11, nicht in die Persistenzschicht.» Genau dort passiert er:
`FixedCostResponse.from()` setzt `setScale(2, RoundingMode.UNNECESSARY)`.

### Validierung im Service

`FixedCost.java:20-22` übergibt die fachliche Validierung ausdrücklich an BE-FC-02, konsistent zu
`User` und `Transaction`, die im Entity ebenfalls keine Regeln tragen. US-03 verlangt eine
**feldspezifische** Fehlermeldung — deshalb eine `InvalidFixedCostException`, die Feldname und
Meldung trägt, statt drei Exception-Klassen.

Regeln:

| Feld | Regel | Grund |
| ---- | ----- | ----- |
| `bezeichnung` | nicht null, getrimmt nicht leer, ≤ 100 Zeichen | US-03 AC2. Die Spalte ist unlimitiertes `VARCHAR` (V03) — ohne Obergrenze landet ungebremster Input in der DB und in jeder Response. |
| `betrag` | nicht null, `> 0`, `scale ≤ 2`, `≤ 99'999'999.99` | US-03 AC2. `scale ≤ 2`, damit `12.3456` abgelehnt statt still gerundet wird; die Obergrenze ist die Kapazität von `DECIMAL(10,2)` aus V03. |
| `intervall` | nicht null, ∈ `{monatlich, quartalsweise, jaehrlich}` | US-03 AC2. Geparst über `Intervall.fromLabel()`. |

Die 100-Zeichen-Grenze ist eine Setzung dieses Tasks, keine Vorgabe aus Issue oder Migration.

## Betroffene Files

### Neu

| Datei | Inhalt |
| ----- | ------ |
| `backend/src/main/java/com/budgetbuddy/auth/UserIncomePort.java` | `Optional<BigDecimal> findMonthlyIncome(long userId)` |
| `backend/src/main/java/com/budgetbuddy/budget/FixedCostService.java` | `list`, `get`, `create`, `update`, `delete` + Normalisierung + Warnung |
| `backend/src/main/java/com/budgetbuddy/budget/FixedCostNotFoundException.java` | 404-Fall; Nicht-Existenz und Fremdbesitz bewusst ununterscheidbar |
| `backend/src/main/java/com/budgetbuddy/budget/InvalidFixedCostException.java` | 400-Fall mit Feldname |
| `backend/src/main/java/com/budgetbuddy/budget/dto/FixedCostRequest.java` | Eingabe für `create`/`update` |
| `backend/src/main/java/com/budgetbuddy/budget/dto/FixedCostResponse.java` | Eine Position inkl. `monatsbetrag` |
| `backend/src/main/java/com/budgetbuddy/budget/dto/FixedCostSummaryResponse.java` | Liste + `summeMonatlich` + `monthlyIncome` + `exceedsIncome` |

### Geändert

| Datei | Änderung |
| ----- | -------- |
| `backend/src/main/java/com/budgetbuddy/auth/UserService.java` | `implements UserIncomePort` |
| `backend/src/main/java/com/budgetbuddy/auth/package-info.java` | Port erwähnen |
| `backend/src/main/java/com/budgetbuddy/budget/package-info.java` | Service erwähnen |

## Implementierungsschritte

1. `UserIncomePort` in `auth` definieren, `UserService` implementieren lassen.
2. DTO-Records in `budget/dto` anlegen; `FixedCostResponse.from(FixedCost)` setzt `setScale(2)` und
   rechnet den `monatsbetrag`.
3. `FixedCostNotFoundException` und `InvalidFixedCostException` anlegen.
4. `FixedCostService`: Validierung → Normalisierung → CRUD ausschliesslich über die
   user-gebundenen Repository-Methoden (`findByUserIdOrderByIdAsc`, `findByIdAndUserId`,
   `deleteByIdAndUserId`). Kein geerbtes `findById`/`deleteById`.
5. Package-Infos nachziehen.

## Test-Strategie

Coverage-Ziel für `budget/` ist 90 %+ (CLAUDE.md).

### `FixedCostServiceTest` — Unit, JUnit 5 + Mockito + AssertJ

- Je ein Szenario pro Intervall: `monatlich` ÷ 1, `quartalsweise` ÷ 3, `jaehrlich` ÷ 12
- Nicht glatt teilbar: `100.00 quartalsweise → 33.33` (HALF_UP)
- Warnung bei `summe < income`, `summe = income`, `summe > income` und bei `income = null`
- Alle Validierungsfehler einzeln, jeweils mit erwartetem Feldnamen
- `FixedCostNotFoundException` bei leerem `Optional` aus dem Repository (`get`, `update`) und bei
  `deleteByIdAndUserId == 0`
- `betrag` im DTO hat Skala 2, auch wenn das Repository Skala 0 liefert

### `FixedCostServiceIntegrationTest` — `@SpringBootTest`, echte SQLite + Flyway

Muster (Temp-File-DB, `@DirtiesContext`) von `FixedCostRepositoryIntegrationTest` übernommen.

- **Mandantentrennungs-Gegenprobe:** User B ruft `get`, `update` und `delete` auf einer Position
  von User A auf → `FixedCostNotFoundException`, und die Zeile von User A bleibt unverändert bzw.
  existiert weiter. Ein grüner Happy Path belegt die Trennung nicht.
- Summe über gemischte Intervalle bleibt nach DB-Round-Trip rappen-genau.
- `list` eines Users ohne Positionen: leere Liste, Summe `0.00`.

### `UserServiceTest` — Ergänzung

- `findMonthlyIncome` liefert `Optional.empty()` bei unbekanntem User und bei
  `monthly_income IS NULL`, sonst den Betrag.

## Acceptance Criteria (aus #11)

- [ ] Quartalskosten werden korrekt auf Monat normalisiert (÷ 3)
- [ ] Jahreskosten werden korrekt normalisiert (÷ 12)
- [ ] Service gibt Warning-Flag zurück wenn Fixkosten ≥ Einkommen
- [ ] Alle Berechnungen mit BigDecimal
