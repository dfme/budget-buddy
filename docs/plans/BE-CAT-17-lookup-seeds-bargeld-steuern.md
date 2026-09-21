# [BE-CAT-17] Lookup-Seeds für Bargeldbezug und Steuern

- **Issue:** [#325](https://github.com/dfme/budget-buddy/issues/325)
- **Task-ID:** `BE-CAT-17`
- **Branch:** `feature/BE-CAT-17-lookup-seeds-bargeld-steuern`
- **Story:** US-05 — Transaktionen kategorisieren
- **Sprint:** Sprint 7
- **Bestätigt am:** 2026-09-20

## Ausgangslage

BE-CAT-10 (#266) hat `Bargeldbezug` und `Steuern` als Kategorien eingeführt, aber bewusst keine
Lookup-Seeds dafür angelegt. Stufe 1 der Hybrid-Kategorisierung (ADR-6) greift für die beiden
neuen Kategorien deshalb gar nicht — ein Bancomat-Bezug geht weiterhin an Claude oder fällt auf
`Sonstiges`.

## Entscheide

### Task-ID `BE-CAT-17` statt `BE-CAT-13`

Issue #325 trägt im Titel `[BE-CAT-13]`, dieselbe ID wie #321 (offener PR #327, Branch
`feature/BE-CAT-13-lookup-pattern-extraction`, Plan
[BE-CAT-13-lookup-pattern-extraction.md](BE-CAT-13-lookup-pattern-extraction.md)). Zwei Zeilen
mit derselben Task-ID im Index wären ab sofort mehrdeutig. Höchste vergebene ID ist `BE-CAT-16`
(#343), die nächste freie damit `BE-CAT-17`. Der Titel von #325 wird mitgezogen.

### Seed-Liste: PostFinance-Wortlaut mit aufgenommen, `ATM` weggelassen

Gegen die eingecheckte Fixture `Post_Kontoauszug_2025_240_Buchungen.pdf` gerechnet (Skript über
`_post_year_month` + `_matches_lookup`, also gegen dieselbe Matching-Regel wie
`CategoryLookupRepository.findMatching`):

| Pattern | neue Treffer in der 240er-Fixture |
| ------- | --------------------------------- |
| `BANCOMAT`, `GELDAUTOMAT` (Issue-Vorschlag) | 0 |
| `ATM` (Issue-Vorschlag) | 0 |
| `STEUERAMT` (Issue-Vorschlag) | 0 |
| `STEUERVERWALTUNG` | +3 |
| `POSTOMAT` / `BARBEZUG` (**nicht** im Issue) | +12 |

Der Issue-Umfang allein ergäbe 147/240 = 61.25 % — im Band, aber `Bargeldbezug` feuerte auf
**keiner einzigen** Buchung der gepinnten Fixture. Die neue Kategorie wäre damit ungetestet, und
AC 4 liesse sich nur über die Steuer-Zeilen belegen. `POSTOMAT` ist die PostFinance-Entsprechung
zu `BANCOMAT`; sie fehlt im Issue, ist sachlich aber derselbe Gegenstand.

**`ATM` bleibt draussen.** Das Matching ist seit BE-CAT-14 (#322) reine Substring-Suche über
`locate(...)`, ohne Wortgrenzen. Drei Zeichen treffen mitten im Wort — `ATMOSPHERE` als
Lokalname genügt. Eine falsche, deterministische Kategorie aus Stufe 1 ist schlechter als gar
keine: Claude sieht den Text dann nie wieder, und der Fallback `Sonstiges` wäre das ehrlichere
Ergebnis. Das ist dieselbe Abwägung, aus der BE-CAT-14 `LIKE` durch `locate` ersetzt hat.

**`STEUERN` bleibt ebenfalls draussen** — es träfe `MEHRWERTSTEUER`, `VERRECHNUNGSSTEUER` und
`STEUERBERATUNG`.

**`BARBEZUG` ist bewusst ein Buchungstyp in einer `empfaenger_pattern`-Spalte.** Zulässig, weil
hier der Zahlungstyp die Kategorie *ist* — anders als bei `LASTSCHRIFT` oder `TWINT`, die über
alle Kategorien hinweg vorkommen.

### Zielband 65 % ± 5 statt 60 % ± 5

Mit der gewählten Seed-Liste steigt die Quote auf 159/240 = 66.25 % und verlässt das bisherige
Band 55–65 %. Drei Wege standen offen:

1. `POSTOMAT`/`BARBEZUG` weglassen — verworfen, siehe oben.
2. Den Generator umbauen, bis 60 % wieder hält. Das hiesse, drei Lookup-Treffer pro Jahr in
   Unbekannte umzuwandeln; Beträge und Saldokette änderten sich, und die gedruckten Totale in
   `SwissBankStatementParserFixtureTest` müssten nachgezogen werden. Grosser Diff für eine Zahl,
   die selbst nur ein Messpunkt ist.
3. **Gewählt:** `POST_YEAR_LOOKUP_TARGET` von 0.60 auf 0.65, Band damit 60–70 %.

Der Grund, aus dem 60 % gewählt worden war, trägt weiter: die Quote soll *unter* den 70–80 % aus
ADR-6 liegen, damit die Claude-Stufe spürbar Last bekommt. 66.25 % erfüllt das — Claude bekommt
noch 81 Transaktionen, verteilt auf unverändert 12 Bündel (kein 20er-Slice läuft leer, maximal 7
Unbekannte je Slice).

### Die Fixture wird nicht neu committet

AC 2 verlangt, die 240er-Fixture neu zu erzeugen. Das ist wirkungslos: `via_lookup` wird nur in
`_assert_lookup_share` (`generate_pdf_fixtures.py:760`) und `_post_report` (`:584`) gelesen, nie
in `_post_render`. Die Seeds beeinflussen die PDF-Bytes nicht. Ein Neulauf änderte allein den
reportlab-Erzeugungszeitstempel — genau der unreviewbare Binärdiff, vor dem der Skript-Header
selbst warnt.

Der Generator läuft trotzdem, als Nachweis: Die Bandprüfung muss die 66.25 % durchwinken, und
der neu erzeugte Auszug muss ausser dem Zeitstempel identisch sein. Danach werden alle acht
Fixtures per `git checkout --` zurückgeholt. AC 2 bleibt bewusst offen, mit dieser Begründung im
PR-Body.

## Betroffene Dateien

### Neu

| Datei | Zweck |
| ----- | ----- |
| `backend/src/main/resources/db/migration/V15__seed_bargeldbezug_and_steuern_lookup.sql` | sieben Seeds, upper-case |

Seeds:

| Pattern | Kategorie | Wortlaut von |
| ------- | --------- | ------------ |
| `BANCOMAT` | Bargeldbezug | UBS, Raiffeisen (»Bezug Bancomat …«) |
| `POSTOMAT` | Bargeldbezug | PostFinance, Gerät in der Detailzeile |
| `GELDAUTOMAT` | Bargeldbezug | generisch |
| `BARGELDBEZUG` | Bargeldbezug | Demo-Auszüge, diverse Banken |
| `BARBEZUG` | Bargeldbezug | PostFinance-Buchungszeile |
| `STEUERVERWALTUNG` | Steuern | kantonal / eidgenössisch |
| `STEUERAMT` | Steuern | kommunal |

### Geändert

| Datei | Änderung |
| ----- | -------- |
| `backend/tools/generate_pdf_fixtures.py` | `_lookup_patterns()` liest alle Migrationen; `POST_YEAR_LOOKUP_TARGET` 0.60 → 0.65 |
| `backend/src/test/java/com/budgetbuddy/transaction/PdfLookupCoverageIntegrationTest.java` | `EXPECTED_LOOKUP_HITS` 144 → 159, `POST_YEAR_LOOKUP_SHARE` 0.60 → 0.6625, Javadoc, zwei neue Assertions |
| `backend/src/test/java/com/budgetbuddy/db/CategoryLookupMigrationTest.java` | Test, dass V15 beide Kategorien seedet |
| `CLAUDE.md`, `docs/adr/ADR-6-hybrid-categorization.md` | `category_lookup` (V04) → (V04, V15) |
| `docs/adr/ADR-15-mandantengebundener-lerneffekt.md` | »die 18 Seeds aus V04« → Seeds aus V04 und V15 |
| `docs/demo/README.md` | Snapshot-Zahlen und Claude-Liste nachgezogen |
| `docs/plans/README.md` | Index-Zeile |

**`_lookup_patterns()` muss mitgezogen werden**, sonst entsteht eine stille Lücke: Die Funktion
liest heute ausschliesslich `V04`. Mit Seeds in `V15` rechnete die Bandprüfung des Generators
gegen einen veralteten Pattern-Satz weiter und schützte nichts mehr. Ein naives Glob über alle
`V*.sql` wäre falsch — `V11:34` enthält `CHECK (status IN ('DETECTED', 'DISMISSED'))` und passt
auf dieselbe Tupel-Regex. Die Extraktion wird deshalb auf
`INSERT INTO category_lookup … VALUES …;`-Blöcke eingegrenzt.

**`docs/demo/README.md` ist eine deklarierte Scope-Erweiterung.**
`generate_demo_statements.py:150` erzeugt `BARGELDBEZUG` / `BANCOMAT BERN BAHNHOF`, `:261`
`Bezug Bancomat Zuerich Stauffacher`. Die dortige Snapshot-Tabelle (Lara 65/130, Marc 154/324)
und der Satz »Betroffen sind … und Bargeldbezüge« werden durch diesen Diff falsch. Beide Zahlen
werden über einen temporären Messlauf gegen `docs/demo/statements/` neu bestimmt; der Messtest
wird danach wieder gelöscht.

## Implementierungsschritte

1. Branch anlegen, Titel von #325 auf `[BE-CAT-17]` ziehen.
2. `V15__seed_bargeldbezug_and_steuern_lookup.sql` schreiben, inklusive der Kommentare zu `ATM`,
   `STEUERN` und `BARBEZUG`.
3. `_lookup_patterns()` auf alle Migrationen umstellen, `POST_YEAR_LOOKUP_TARGET` anheben.
4. Generator laufen lassen — Bandprüfung muss 66.25 % durchwinken; Inhaltsgleichheit der
   240er-Fixture belegen, danach alle Fixtures per `git checkout --` zurückholen.
5. Test-Konstanten und Javadoc nachziehen, positive Assertions für beide neuen Kategorien
   ergänzen, `CategoryLookupMigrationTest` erweitern.
6. Temporärer Messlauf für die Demo-Zahlen, Doku nachziehen, Messtest löschen.
7. `JAVA_HOME=$(/usr/libexec/java_home -v 25) ./mvnw clean package` — `clean` ist nötig, weil
   `backend/target/classes/db/migration` noch einen Stand bis V11 trägt.

## Test-Strategie

| Test | Art | Was er belegt |
| ---- | --- | ------------- |
| `PdfLookupCoverageIntegrationTest` | Integration (Postgres) | AC 2/3 — 159/240 = 66.25 % im Band, 81 Claude-Calls, 12 Bündel |
| dort neue Assertions | Integration | AC 1/4 — `BARBEZUG POSTOMAT BAHNHOF BERN` → `Bargeldbezug`, `GUTSCHRIFT STEUERVERWALTUNG KT. BERN …` → `Steuern` |
| `CategoryLookupMigrationTest` (+1) | Integration | AC 1 — V15 lief, Patterns upper-case, Kategorien aus der fixen Liste |
| Generatorlauf | Werkzeug | Bandprüfung greift nach der V15-Umstellung gegen die echten Seeds |
| `./mvnw clean package` | Suite | keine Regression |

## Belegte Wirkung (AC 4)

15 Buchungen der 240er-Fixture wechseln von Claude auf den Lookup:

| Anzahl | Buchung | neue Kategorie |
| ------ | ------- | -------------- |
| 12 | `BARBEZUG POSTOMAT BAHNHOF BERN` | Bargeldbezug |
| 3 | `GUTSCHRIFT STEUERVERWALTUNG KT. BERN RUECKERSTATTUNG {APRIL,AUGUST,DEZEMBER} 2025` | Steuern |

`BANCOMAT`, `GELDAUTOMAT`, `BARGELDBEZUG` und `STEUERAMT` treffen in dieser Fixture nichts. Sie
greifen in den UBS- und Raiffeisen-Auszügen (`generate_pdf_fixtures.py:810`, `:1015`) sowie in
den Demo-Auszügen.

## Acceptance Criteria (aus dem Issue)

- [ ] Neue Migration seedet Patterns für `Bargeldbezug` und `Steuern`, durchgehend upper-case.
- [ ] Die 240er-Fixture ist neu erzeugt und `EXPECTED_LOOKUP_HITS` nachgezogen.
      *Bewusst geteilt: `EXPECTED_LOOKUP_HITS` wird nachgezogen, die Fixture nicht neu committet
      — Begründung oben.*
- [ ] `PdfLookupCoverageIntegrationTest` ist grün und die Quote liegt im Zielband.
- [ ] Der PR belegt, welche Buchungen durch die neuen Seeds von Claude auf den Lookup wechseln.
