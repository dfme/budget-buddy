# [BE-CAT-14] LIKE-Wildcards (%, _) in gelernten Lookup-Patterns werden nicht escaped

- **Issue:** [#322](https://github.com/dfme/budget-buddy/issues/322)
- **Task-ID:** `BE-CAT-14`
- **Branch:** `fix/BE-CAT-14-lookup-literal-matching`
- **Story:** US-05 — Transaktionen kategorisieren
- **Sprint:** Sprint 7
- **Bestätigt am:** 2026-09-19

## Problem

`CategoryLookupRepository.findMatching` baute das Suchmuster als
`upper(:text) LIKE concat('%', upper(c.empfaengerPattern), '%')`. Enthält ein gelerntes Pattern
selbst ein `%` oder `_`, wird es damit zum Wildcard: `RABATT 20% MIGROS` matcht auch
`RABATT 20 CHF – FOO – MIGROS`, `SHOP_X` matcht `SHOPPX`. Die Folge ist eine falsche Kategorie
über Stufe 1 — deterministisch und ohne dass Claude den Text je sieht.

Seit BE-CAT-11 (#314, PR #320) wird **jeder** von Claude kategorisierte Text gelernt. Das Muster
tritt damit systematisch auf, sobald ein Auszug ein Prozentzeichen oder einen Unterstrich im
Buchungs- oder Detailtext trägt — vorher war es auf aktiv korrigierte Texte beschränkt.

## Umfangsprüfung

Die enge Suche des AC (`concat('%'`) und die breite Suche über jedes `LIKE` in
`backend/src/main` plus die Spring-Data-Derived-Queries mit LIKE-Semantik
(`Containing` / `StartingWith` / `EndingWith` / `Like`) liefern dieselbe **eine** Stelle:
`CategoryLookupRepository.java:28`. Kein Scope-Delta, das AC ist vollständig.

Die beiden Migrationstests, deren Kommentar „dieselbe Mechanik wie
`CategoryLookupRepository.findMatching`" nennt (`CategoryLookupMigrationTest:106`,
`RecurringExpensesMigrationTest:202`), vergleichen mit `=`, nicht mit `LIKE` — nicht betroffen.

Kein Seed aus Flyway V04 trägt ein `%` oder `_` im Pattern; die Umstellung ändert für die
Seed-Daten also nichts.

## Entscheid: `locate(...) > 0` statt `ESCAPE`-Klausel

Das AC lässt beide Wege zu.

| | ESCAPE-Klausel | `locate(...) > 0` |
| --- | --- | --- |
| Query | `LIKE concat('%', replace(replace(replace(upper(pattern),'\','\\'),'%','\%'),'_','\_'), '%') ESCAPE '\'` | `locate(upper(c.empfaengerPattern), upper(:text)) > 0` |
| Metazeichen | bleiben im Spiel; das Escape-Zeichen muss sich selbst escapen — fehlt das `\`-Replace, ist der Bug nur verschoben | existieren nicht |
| Index | führendes `%` ⇒ Seq-Scan | Seq-Scan — unverändert |

`locate` ist Standard-JPQL (Hibernate 6.6 unter Spring Boot 3.5.3) und übersetzt auf PostgreSQL zu
`position(... in ...)`. Die Sortierung nach Pattern-Länge bleibt unangetastet, die gespeicherten
Patterns ebenso — AC 3 ist damit ohne Migration erfüllt.

## Betroffene Dateien

| Datei | Änderung |
| --- | --- |
| `backend/src/main/java/com/budgetbuddy/categorization/CategoryLookupRepository.java` | Query auf `locate`, Javadoc um die Wildcard-Begründung ergänzt |
| `backend/src/main/java/com/budgetbuddy/categorization/UserCategoryLookupRepository.java` | dasselbe für den Lern-Pool — nach dem Merge von BE-CAT-12 dazugekommen, siehe Nachtrag unten |
| `backend/src/test/java/com/budgetbuddy/categorization/LookupTableServiceIntegrationTest.java` | sechs neue Tests, beide Lookup-Repositories, `CategoryLearningService` und `JdbcTemplate` autowired, Klassen-Javadoc erweitert |
| `docs/plans/BE-CAT-14-lookup-literal-matching.md` | neu (diese Datei) |
| `docs/plans/README.md` | eine Indexzeile |

Kein neuer Test-Kontext: es gibt bereits 52 `@SpringBootTest`-Klassen, und `PostgresTestDatabase`
dokumentiert den Kontext-Cache-Druck als realen Schmerz. Die bestehende Klasse bringt
Postgres-Datenbank und V04-Seeds schon mit.

## Implementierungsschritte

1. Query in `findMatching` auf `locate(upper(c.empfaengerPattern), upper(:text)) > 0` umstellen.
2. Im Javadoc festhalten, *warum* nicht `LIKE` — sonst baut es jemand als „einfacher" wieder zurück.
3. Tests schreiben (siehe unten).
4. `./mvnw -pl backend test` mit JDK 25 im `JAVA_HOME`, danach `mvn package` und `ng build` für die DoD.

## Test-Strategie

Integrationstests gegen PostgreSQL (Testcontainers), wie das AC verlangt — das Verhalten liegt in
der Query, ein Mockito-Test würde es nicht berühren. Gelernt wird über den echten
`CategoryLearningService`, also über den Pfad, den BE-CAT-11 benutzt.

Auf Repository-Ebene, wortgetreu zum AC:

1. `percentSignMatchesLiterally` — gelernt `RABATT 20% MIGROS`; `findMatching("RABATT 20% MIGROS BERN")` enthält das Pattern.
2. `percentSignIsNoWildcard` — `findMatching("RABATT 20 CHF MIGROS")` enthält es **nicht**. Die Assertion prüft die Pattern-Liste, nicht `isEmpty()`: der Seed `MIGROS` matcht hier zu Recht weiter, ein `isEmpty()` wäre grün aus dem falschen Grund.
3. `underscoreMatchesLiterally` — gelernt `SHOP_X`; `findMatching("SHOP_X FILIALE 3")` enthält es.
4. `underscoreIsNoWildcard` — `findMatching("SHOPPX FILIALE 3")` enthält es nicht.

Auf Service-Ebene der eigentliche Schaden aus dem Issue, eine falsche Kategorie über Stufe 1.
Händler ohne Seed-Kollision, damit die Aussage eindeutig ist:

5. `doesNotCategorizeViaPercentWildcard` — gelernt `RABATT 20% FOOBAR` → Shopping;
   `categorize("RABATT 20% FOOBAR BERN")` liefert Shopping, `categorize("RABATT 20 CHF FOOBAR")`
   liefert `empty()`.

Die gelernten Zeilen überleben innerhalb der Klasse (kein Rollback). Geprüft: keiner der drei
bestehenden Testtexte (`MIGROS BERN 044 913 2323`, `digitec galaxus ag`,
`BAECKEREI MUELLER 12345`) matcht eines der neuen Patterns — weder mit `locate` noch mit dem
alten `LIKE`. Die Reihenfolge-Unabhängigkeit der Klasse bleibt erhalten.

## Bewusst nicht geändert

Ein leeres Pattern würde mit `locate('', text) = 1` alles matchen — wie vorher mit `%%%`.
`CategoryLearningService:39` weist leere Patterns ab und kein V04-Seed ist leer; das Verhalten ist
unverändert, und eine Guard dagegen gehört nicht in diesen Fix.

## Nachtrag: Merge von `main` (BE-CAT-12 / ADR-15)

Während dieser Branch offen war, ist [BE-CAT-12](BE-CAT-12-category-lookup-mandantentrennung.md)
(#319, PR #323) auf `main` gelandet und hat den Lerneffekt mandantengebunden gemacht: Gelernt wird
seither ausschliesslich in die neue Tabelle `user_category_lookup` (V12), `category_lookup` hält
nur noch die kuratierten Seeds.

Damit verschiebt sich der Ort des Bugs. Die Umfangsprüfung oben war zum Zeitpunkt ihrer Aufnahme
korrekt — `CategoryLookupRepository.findMatching` war die einzige Stelle —, aber
`UserCategoryLookupRepository.findMatching` kam mit derselben
`upper(:text) LIKE concat('%', upper(...), '%')`-Konstruktion hinzu. Genau dort liegen jetzt die
Patterns, die ein `%` oder `_` überhaupt tragen können: roher Buchungstext. Der Fix nur auf der
globalen Tabelle würde nach dem Merge nichts mehr reparieren, weil deren 18 Seeds kein Metazeichen
enthalten.

Die Umstellung auf `locate(...) > 0` gilt deshalb für **beide** Queries. Die Metazeichen-Tests
laufen über den Lernpfad und damit gegen `user_category_lookup`; für die globale Tabelle kommt ein
eigener Test dazu, der sein Pattern direkt einfügt — dorthin schreibt der Lerneffekt seit ADR-15
nicht mehr. Der Test-Setup braucht seit V12 einen echten `users`-Eintrag (Fremdschlüssel), die
Klasse legt ihn in `@BeforeEach` an und räumt Lern- und Testzeilen vorher ab.

## Acceptance Criteria (aus #322)

- [ ] `%` und `_` in `empfaenger_pattern` werden vor dem `LIKE` escaped (mit `ESCAPE`-Klausel in
      der JPQL-Query), oder das Matching wird auf `position(...) > 0` / `strpos` umgestellt, das
      keine Wildcards kennt
- [ ] Test: ein gelerntes Pattern `RABATT 20% MIGROS` matcht `RABATT 20% MIGROS BERN`, aber nicht
      `RABATT 20 CHF MIGROS`; ein Pattern mit `_` matcht nur den Unterstrich, nicht ein beliebiges
      Zeichen — als Integrationstest gegen Postgres (Testcontainers), weil das Verhalten in der
      Query liegt, nicht im Java-Code
- [ ] Bestehende Seeds und gelernte Zeilen bleiben ohne Migration gültig (keine Änderung am
      gespeicherten Pattern, nur am Matching)
