# [BE-FC-01] FixedCost Entity und Repository

- **Issue:** [#10](https://github.com/dfme/budget-buddy/issues/10)
- **Task-ID:** `BE-FC-01`
- **Branch:** `feature/BE-FC-01-fixedcost-entity`
- **Story:** US-03 — Fixkosten erfassen (Onboarding-Wizard)
- **Sprint:** Sprint 4
- **Bestätigt am:** 2026-08-08

## Ausgangslage

Die Tabelle `fixed_costs` existiert seit DB-03 (`V03__create_fixed_costs_table.sql`) und ist durch
`FixedCostsMigrationTest` abgesichert. Java-seitig gibt es dazu **nichts** — eine Suche über
`backend/`, `frontend/` und `e2e/` nach `fixedcost|fixed_cost|fixkost` findet ausschliesslich die
Migration und ihren Test. Dieses Issue liefert die Persistenzschicht darauf: Entity + Repository,
sonst nichts.

Nachgelagert warten #11 (BE-FC-02, Service mit CRUD und Normalisierung) und #123 (E2E). Beide
Konsumenten sind bekannt, aber ihr Code gehört nicht hierher.

## Entscheide

| Frage | Entscheid | Begründung |
| ----- | --------- | ---------- |
| Package | `com.budgetbuddy.budget` | CLAUDE.md listet genau fünf Domänen-Packages; Fixkosten sind ein Eingabewert der Safe-to-Spend-Rechnung, die laut `budget/package-info.java` dort liegt. Ein eigenes `fixedcost/` wäre ein sechstes Package und erzwänge später ein Interface zwischen ihm und `budget/` (Regel: kein modulübergreifender Repository-Zugriff) — für zwei Klassen, die derselbe Service konsumiert. |
| Intervall-Mapping | Enum `Intervall` + `AttributeConverter` | Typsicher für die Normalisierung in #11 (÷3, ÷12); ein unbekannter DB-Wert schlägt beim Lesen laut fehl statt still `null` zu werden. `@Enumerated(STRING)` schriebe `MONATLICH` in die DB und widerspräche dem Kommentar in V03 („z. B. monatlich, jaehrlich"). Der Plain-String-Weg von `Transaction.category` verschöbe jede Intervall-Prüfung nach #11. |
| Label-Schreibweise | `monatlich`, `quartalsweise`, `jaehrlich` (ASCII) | So im Kommentar von `V03__create_fixed_costs_table.sql:3`. ASCII in DB-Wert und späterem API-Contract vermeidet Encoding-Fallen (SQLite-Datei, JSON, E2E-Assertions). Der Anzeigetext „jährlich" aus US-03 ist Sache des Frontends. |
| Repository-Methoden | jede Methode trägt `userId` | AC3 verlangt Filterung nach `user_id`. Ein `findById(id)` auf einer Entity mit User-Bezug ist ein IDOR: wer die ID hochzählt, liest fremde Fixkosten. Die Einschränkung gehört dorthin, wo die Query steht — nicht in einen Service, der auch von anderswo aufrufbar ist. |
| Setter | nur `bezeichnung`, `betrag`, `intervall` | US-03 verlangt Ändern und Löschen bestehender Einträge; #11 setzt das um. Kein Setter für `id`/`userId`, damit ein Eintrag nicht den Besitzer wechseln kann. |
| Validierung | **nicht** in diesem Issue | `betrag > 0` und nicht-leere Bezeichnung sind Fach-, keine Persistenzregeln — sie gehören in den `FixedCostService` (#11), konsistent zu `User` und `Transaction`, die ebenfalls keine Validierung im Entity tragen. |

## Betroffene Files

### Neu (main)

- `backend/src/main/java/com/budgetbuddy/budget/Intervall.java` — Enum mit Label + `fromLabel`
- `backend/src/main/java/com/budgetbuddy/budget/IntervallConverter.java` — `AttributeConverter<Intervall, String>`
- `backend/src/main/java/com/budgetbuddy/budget/FixedCost.java` — JPA-Entity auf `fixed_costs`
- `backend/src/main/java/com/budgetbuddy/budget/FixedCostRepository.java` — Spring-Data-Repository

### Neu (test)

- `backend/src/test/java/com/budgetbuddy/budget/IntervallTest.java`
- `backend/src/test/java/com/budgetbuddy/budget/FixedCostRepositoryIntegrationTest.java`

### Geändert

- `backend/src/main/java/com/budgetbuddy/budget/package-info.java` — Fixkosten in der Modulbeschreibung ergänzen
- `docs/plans/README.md` — Index-Zeile

## Implementierungsschritte

1. **`Intervall`** — Konstanten `MONATLICH("monatlich")`, `QUARTALSWEISE("quartalsweise")`,
   `JAEHRLICH("jaehrlich")` nach dem Vorbild von `Category`: `getLabel()`, `fromLabel(String)` mit
   `IllegalArgumentException` bei unbekanntem Wert. Der Monats-Divisor (÷1, ÷3, ÷12) kommt
   **nicht** hier rein — das ist die Normalisierung aus #11.
2. **`IntervallConverter`** — `@Converter` (kein `autoApply`), `convertToDatabaseColumn` →
   `getLabel()`, `convertToEntityAttribute` → `fromLabel()`.
3. **`FixedCost`** — `id` (`GenerationType.IDENTITY`), `userId` (`@Column(name = "user_id")`),
   `bezeichnung`, `betrag` als **`BigDecimal`** (ADR-9), `intervall` mit `@Convert`. Alle Spalten
   ausser `id` `nullable = false`, exakt wie V03. `protected` No-Arg-Konstruktor für JPA plus
   öffentlicher Konstruktor `(userId, bezeichnung, betrag, intervall)`.
4. **`FixedCostRepository`** — `extends JpaRepository<FixedCost, Long>` mit:
   - `List<FixedCost> findByUserIdOrderByIdAsc(Long userId)`
   - `Optional<FixedCost> findByIdAndUserId(Long id, Long userId)`
   - `long deleteByIdAndUserId(Long id, Long userId)` — Rückgabewert `0` = fremd oder nicht vorhanden
   - `boolean existsByUserId(Long userId)` — Wizard-Bedingung „mindestens ein Eintrag" (US-03)
5. **Javadoc** in der Tonlage der bestehenden Entities: warum `BigDecimal`, warum Converter, was
   bewusst nach #11 verschoben ist.

## Test-Strategie

**Unit — `IntervallTest`:** `fromLabel("quartalsweise")` → `QUARTALSWEISE`; Round-Trip
`fromLabel(getLabel())` über alle Konstanten; `fromLabel("unbekannt")` wirft
`IllegalArgumentException`.

**Integration — `FixedCostRepositoryIntegrationTest`:** `@SpringBootTest` gegen eine
Temp-File-SQLite mit Flyway und `@DirtiesContext`, nach dem Muster aus
`FixedCostsMigrationTest:31-52` — `jdbc:sqlite::memory:` scheidet aus, weil dort jede Connection
eine eigene DB bekäme und die Flyway-Connection eine andere DB sähe als die Test-Query.
Zwei echte User über das `UserRepository`, damit der FK auf `users.id` trägt.

| Testfall | Nachweis für |
| -------- | ------------ |
| Speichern → per `JdbcTemplate` zurücklesen: Werte stehen in `user_id`/`bezeichnung`/`betrag`/`intervall`, `intervall` als `'jaehrlich'` | AC1 |
| `1234.56` gespeichert → als `BigDecimal` mit Skala 2 und `compareTo`-gleich zurückgelesen | AC2 |
| `findByUserIdOrderByIdAsc(userA)` liefert nur A's Einträge, obwohl B welche hat | AC3 |
| `findByIdAndUserId(idVonA, userB)` → `Optional.empty()` | AC3, IDOR-Gegenprobe |
| `deleteByIdAndUserId(idVonA, userB)` → `0`, A's Zeile existiert danach noch | AC3, IDOR-Gegenprobe |
| `existsByUserId` true für A mit Eintrag, false für User ohne | AC3 |

## Acceptance Criteria (aus dem Issue)

- [ ] Entity mappt korrekt auf `fixed_costs`-Tabelle
- [ ] `betrag` ist `BigDecimal` (kein `double`/`float`)
- [ ] Repository-Queries filtern nach `user_id`

## Bewusst nicht in diesem Issue

Kein Controller, kein Service, keine DTOs, keine Validierung. Der DoD-Punkt „Neue API-Endpoints
sind in Swagger UI sichtbar" ist damit gegenstandslos — dieses Issue liefert keine Endpoints; die
kommen mit #12 (FixedCostController). Das wird im PR vermerkt statt abgehakt.
