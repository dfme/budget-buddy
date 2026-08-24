# [BE-AUTH-08] monthly_income auf Rappen und Kapazität prüfen

- **Issue:** [#148](https://github.com/dfme/budget-buddy/issues/148)
- **Task-ID:** `BE-AUTH-08`
- **Branch:** `fix/BE-AUTH-08-income-rappen-validierung`
- **Story:** — (kein us-*-Label)
- **Sprint:** Sprint 5
- **Bestätigt am:** 2026-08-23

## Problem

`PUT /api/users/me/income` prüft den Betrag nur auf `@NotNull @Positive`. Ein Wert wie `4200.004`
wird mit `200 OK` quittiert und von PostgreSQL beim Schreiben in `numeric(10,2)` auf `4200.00`
gerundet — der User bekommt eine Erfolgsmeldung für einen Betrag, der so nicht gespeichert wurde.
Die Obergrenze `99999999.99` ist ebenfalls ungeprüft und läuft in einen DB-Fehler statt in eine
400-Antwort.

`monthly_income` ist Eingangsgrösse der Safe-to-Spend-Rechnung (US-06) — laut CLAUDE.md der
Kernwert des Produkts. Eine Eingabe, die ohne Rückmeldung verändert wird, untergräbt genau das.

## Entscheid: Validierung in den Service, nicht als `@Digits` ans DTO

Das Issue lässt offen, ob die Regel als `@Digits(integer = 8, fraction = 2)` ans DTO gehört oder in
den `UserService`. **Empirisch geklärt** — Probe gegen die Hibernate-Validator-Version des Projekts:

```
@Digits(integer=8, fraction=2)
  4200.00       scale=2  -> OK
  100.000       scale=3  -> REJECTED     <-- AC 1 verlangt: gültig
  4200.004      scale=3  -> REJECTED
  99999999.99   scale=2  -> OK
  100000000.00  scale=2  -> REJECTED
```

`@Digits` zählt `BigDecimal.scale()` **ohne** `stripTrailingZeros()` und lehnt damit `100.000` ab.
AC 1 verlangt ausdrücklich, dass dieser Wert gültig bleibt (wertgleich zu `100.00`). Deklarative
Bean Validation kann die ACs also nicht erfüllen.

Die Regel geht deshalb in den `UserService` — analog zu `FixedCostService.validateBetrag`
(`FixedCostService.java:219-237`), das dasselbe Problem für `fixed_costs.betrag` schon löst,
inklusive der `stripTrailingZeros()`-Feinheit und der Kapazitätsgrenze aus der Migration.

Der Codebase argumentiert diese Richtung bereits selbst und benennt dieses DTO als den Ausreisser
— `FixedCostRequest`, Javadoc:

> **Ohne Bean-Validation-Annotationen — bewusst.** […] Das weicht von `UpdateIncomeRequest` ab, das
> `@NotNull`/`@Positive` trägt […] Annotationen greifen erst, wenn ein Controller `@Valid` setzt —
> der Service ist damit ungeschützt, sobald er von anderswo aufgerufen wird. Dieselbe Regel an zwei
> Stellen würde ausserdem irgendwann auseinanderlaufen.

Dieser Task richtet das Einkommen an dieser dokumentierten Entscheidung aus, statt ein drittes
Muster einzuführen.

`InvalidFixedCostException` wird **nicht** wiederverwendet: das wäre ein modulübergreifender
Zugriff (CLAUDE.md, Modulgrenzen). Jedes Modul hält seinen eigenen Typ — wie `FixedCost`,
`PdfImport` und `Transaction` es schon tun.

## Scope-Erweiterung (bestätigt)

Nach dem Entfernen der Annotationen tragen alle *fachlichen* 400er dieses Endpoints denselben Body.
`{"betrag": "abc"}` scheitert aber vorher in Jackson (`HttpMessageNotReadableException`) und liefert
weiterhin Springs Default-Body — zwei Formen für 400 am selben Endpoint, also genau der Zustand, den
dieser Task beseitigen soll. `FixedCostExceptionHandler.java:51-59` fängt das dort schon ab; der
praktische Fall war ein Komma-Betrag `"12,50"` aus einem Schweizer Formular, und mit US-14 bekommt
das Einkommen dasselbe Eingabefeld.

Im Planning bestätigt: **wird mitgemacht**, als `UserController`-**scoped** Advice. Nicht im globalen
`UserExceptionHandler` — ein `HttpMessageNotReadableException`-Handler dort würde das
Jackson-Verhalten app-weit für *alle* Controller überschreiben.

## Kein Frontend-Anteil

Einziger Aufrufer ist `dashboard.ts:117` (`applySuggestion`), und der ignoriert den Body
(`error: (_err: HttpErrorResponse) =>` → generische Meldung). Ein freies Einkommens-Eingabefeld
existiert noch nicht; das kommt mit US-14. Der Body ist damit für später richtig geformt, wird heute
aber von niemandem gelesen — kein Contract-Bruch.

## Breite Suche — kein Scope-Delta

Die ACs nennen nur `PUT /users/me/income`. Gegenprobe über das Konzept:

- **Andere Schreibpfade auf `monthly_income`:** nur `UserService.java:43`. Die Registrierung setzt
  das Feld nicht.
- **Andere client-gelieferte `BigDecimal`-Felder in Request-DTOs:** nur `FixedCostRequest.betrag` —
  dort ist die Regel schon vorhanden.

Die ACs sind vollständig.

## Betroffene Files

| Datei | Art |
| --- | --- |
| `backend/src/main/java/com/budgetbuddy/auth/InvalidIncomeException.java` | neu — `field` + `message` |
| `backend/src/main/java/com/budgetbuddy/auth/dto/IncomeErrorResponse.java` | neu — `{field, message}` |
| `backend/src/main/java/com/budgetbuddy/auth/UserIncomeExceptionHandler.java` | neu — auf `UserController` beschränktes Advice |
| `backend/src/main/java/com/budgetbuddy/auth/UserService.java` | ändern — `validateBetrag` im `updateIncome`-Pfad |
| `backend/src/main/java/com/budgetbuddy/auth/dto/UpdateIncomeRequest.java` | ändern — Annotationen entfernen, Javadoc |
| `backend/src/main/java/com/budgetbuddy/auth/UserController.java` | ändern — `@Valid` entfernen, OpenAPI-400 korrigieren |
| `backend/src/test/java/com/budgetbuddy/auth/UserServiceTest.java` | ändern — Regel isoliert |
| `backend/src/test/java/com/budgetbuddy/auth/UserControllerTest.java` | ändern — Status, Body, Round-Trip |

## Implementierungsschritte

1. `InvalidIncomeException` und `IncomeErrorResponse` anlegen. Javadoc hält fest, dass die Meldung
   die Eingabe **nicht** wiederholt (Reflected-XSS-Pfad, gleiche Begründung wie im budget-Modul).
2. `UserService`: privates `validateBetrag(BigDecimal)` —
   - `null` → «Einkommen ist erforderlich.»
   - `signum() <= 0` → «Einkommen muss grösser als 0 sein.»
   - `stripTrailingZeros().scale() > 2` → «Einkommen darf höchstens zwei Nachkommastellen haben.»
   - `> 99999999.99` → «Einkommen darf 99'999'999.99 nicht überschreiten.»
   - Rückgabe `setScale(2, RoundingMode.UNNECESSARY)`, die dann an die Entity geht.
3. `@NotNull @Positive` aus `UpdateIncomeRequest` und `@Valid` aus `UserController` entfernen.
4. `UserIncomeExceptionHandler` (`assignableTypes = UserController.class`): `InvalidIncomeException`
   → 400 mit Body, `HttpMessageNotReadableException` → 400 mit Feldname aus dem Jackson-Pfad.
5. OpenAPI: `@ApiResponse(400)` auf die vier Regeln erweitern, Schema `IncomeErrorResponse`.

## Test-Strategie

**`UserServiceTest`** (Mockito, Regel isoliert):

- `4200.004` → `InvalidIncomeException`, Feld `betrag`
- `100.000` → akzeptiert, an die Entity geht `100.00` (Skala 2)
- `99999999.99` → akzeptiert
- `100000000.00` → abgelehnt
- `null`, `0`, negativ → abgelehnt
- Bei Ablehnung wird `setMonthlyIncome` **nie** aufgerufen

**`UserControllerTest`** (`@SpringBootTest` + `@AutoConfigureMockMvc` gegen echtes PostgreSQL,
bereits vorhanden):

- je Fall Status **und** `jsonPath("$.field")` / `$.message`
- gültiger Randfall `100.000` → 200
- **Round-Trip (AC 4):** `4200.004` → 400 **und** `monthly_income` in der DB unverändert (heute
  stünde dort `4200.00`); ein akzeptierter Betrag kommt per `SELECT` unverändert zurück
- `{"betrag": "abc"}` → 400 mit `field: "betrag"` (Scope-Erweiterung)

Die vier bestehenden 400-Tests prüfen nur den Status und bleiben gültig; sie bekommen zusätzlich die
Body-Assertion.

## Acceptance Criteria (aus dem Issue)

- [ ] `PUT /users/me/income` lehnt einen Betrag mit mehr als zwei Nachkommastellen mit 400 und
      feldspezifischer Meldung ab (`100.000` bleibt gültig — wertgleich zu `100.00`)
- [ ] `PUT /users/me/income` lehnt einen Betrag über `99999999.99` mit 400 ab statt mit einem
      DB-Fehler
- [ ] Test belegt beide Grenzen sowie den gültigen Randfall
- [ ] Ein Round-Trip-Test gegen die echte DB belegt, dass ein akzeptierter Betrag unverändert
      zurückkommt
