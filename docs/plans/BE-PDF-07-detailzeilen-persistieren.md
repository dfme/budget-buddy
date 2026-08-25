# [BE-PDF-07] Absender/Empfänger aus den Detailzeilen wird beim Import verworfen

- **Issue:** [#159](https://github.com/dfme/budget-buddy/issues/159)
- **Task-ID:** `BE-PDF-07`
- **Branch:** `fix/BE-PDF-07-detailzeilen-persistieren`
- **Story:** US-13 — Einzeltransaktionen pro Kategorie einsehen
- **Sprint:** Sprint 5
- **Bestätigt am:** 2026-08-24

## Ausgangslage

`ImportJobRunner.java:161` persistiert nur `tx.buchungstext()`. Die `details`-Liste aus
`ParsedTransaction` — Gegenpartei und Verwendungszweck — geht vorher in die Kategorisierung
(`fullText()`, Zeile 115) und wird danach verworfen.

Der Kommentar von #159 misst die Auswirkung an einem echten PostFinance-Auszug: **240 Transaktionen
verteilen sich auf 10 verschiedene `buchungstext`-Werte** (`GIRO POST`, `LASTSCHRIFT`, `TWINT`,
`KAUF/DIENSTLEISTUNG` …). PostFinance schreibt in die Buchungszeile grundsätzlich die Zahlungsart,
nie den Händler. Für US-13 heisst das nicht «der Empfänger fehlt», sondern die Liste ist
unbrauchbar: Der Nutzer sieht zwölfmal `LASTSCHRIFT` untereinander, während `MIGROS M BERN WANKDORF`
im selben Durchlauf sauber geparst, für die Kategorisierung verwendet und danach weggeworfen wird.

### Vorbedingung erfüllt

Das Issue war `blocked by #192`. PR #196 ist am 24.08.2026 um 17:02 gemergt. Alle drei Gründe der
Sperre einzeln nachgeprüft:

| Grund aus dem Kommentar | Prüfung |
| --- | --- |
| Migrationsnummer kollidiert | `V05` ist die höchste auf `main`; keiner der offenen Branches (#198, #199, #206) enthält eine Migration. `V06` ist frei. |
| Die zu ändernde Zeile wandert | `ImportJobRunner.java` existiert, die Zeile steht auf `:161` (der Kommentar nannte `:153` — die Datei wurde gelesen statt die Nummer übernommen). |
| Der Inhalt ändert sich | `DETAIL_NOISE` ist im Post-#196-Zustand; Fixture-Erwartungen liefern bereinigte Werte wie `["MUSTER, LEA", "SACKGELD LEA"]`. |

## Entscheide

| Frage aus dem Ticket | Entscheid | Begründung |
| --- | --- | --- |
| Ein Textfeld oder normalisiert? | **ein `TEXT`-Feld** `buchungsdetails` | Nach #196 bleiben pro Buchung höchstens 3 Zeilen à 40 Zeichen (`SwissBankStatementParser.java:239`, `:247`) — rund 120 Zeichen. Eine eigene Tabelle dafür wäre Aufwand ohne Gegenwert. |
| Trennzeichen | `\n` | Verlustfrei umkehrbar. `ParsedTransaction` hält die Zeilen laut Javadoc getrennt, weil Konkatenieren irreversibel wäre und US-08 die Trennung braucht — ein Zeilenumbruch erhält genau diese Eigenschaft, weil Detailzeilen konstruktionsbedingt keine enthalten. |
| Spaltenname | `buchungsdetails` | Reiht sich in `buchungsdatum` / `buchungstext` ein. `details` allein wäre in der DB nichtssagend. |
| Backfill? | **nein, Spalte nullable** | Die Detailzeilen existieren nur im PDF; gespeichert wird davon nur der SHA-256 (`Transaction.java:50`), nicht die Datei. Ein Backfill ist ohne Reimport unmöglich — das ist keine Abwägung, sondern eine Feststellung. |
| Leere Details | `NULL`, nicht `""` | Hält «nie importiert» (Altbestand) und «hatte keine Detailzeilen» unterscheidbar. Ohne diese Trennung liesse sich später nicht sagen, ob ein leeres Feld ein Parser-Befund oder ein Datenalter ist. |
| Anzeige (US-13 AC 1) | **zweizeilig**, Details gedämpft unter dem Buchungstext | Verdeckt nichts und unterstellt nicht, dass `details[0]` immer die Gegenpartei ist. Bei Gutschrift und Dauerauftrag stimmt das zwar durchgängig, zugesichert ist es aber nirgends. |

## Betroffene Files

### Backend — neu

- `backend/src/main/resources/db/migration/V06__add_buchungsdetails_to_transactions.sql`

### Backend — geändert

- `ParsedTransaction.java` — `detailsAsText()` neben `fullText()`, `null` bei leerer Liste
- `Transaction.java` — Feld `buchungsdetails`, Konstruktor-Parameter, Getter
- `ImportJobRunner.java:161` — `tx.detailsAsText()` durchreichen
- `dto/TransactionResponse.java` — Feld + `from()`
- `TransactionListService.java:166` — `toResponse()`

### Frontend — geändert

- `transaction.model.ts` — `buchungsdetails: string | null`
- `category-overview.html:101` — zweite Zeile unter dem Buchungstext
- `category-overview.scss:112` — `.transaction__text` wird Container; das Grid `auto 1fr auto auto` bleibt unverändert
- Prettier auf `category-overview.ts`, `transaction.service.ts`, `pdf-import.service.ts`

## Implementierungsschritte

1. `V06` — `ALTER TABLE transactions ADD COLUMN buchungsdetails TEXT;`
2. `ParsedTransaction.detailsAsText()` — `String.join("\n", details)`, `null` wenn leer
3. `Transaction`: Feld, Konstruktor-Parameter (7 → 8 Argumente; sechs Test-Aufrufer ziehen mit), Getter
4. `ImportJobRunner`: `tx.detailsAsText()` als achtes Argument
5. `TransactionResponse` + `TransactionListService.toResponse()`
6. Frontend: Modell, Template, SCSS
7. Prettier über die drei Dateien

## Test-Strategie

| Stufe | Test | Was er beweist |
| --- | --- | --- |
| Integration | `TransactionsMigrationTest` — Spalte in `containsOnlyKeys`, Typ `text`, `notNull == false` | **DoD:** Migration abgedeckt; bestehende Importe brechen nicht |
| Unit | `ImportJobRunnerTest` — Captor auf `saveAll`: Details persistiert; leere Details → `null` | **DoD:** Detailzeilen werden beim Import persistiert |
| Unit | `ParsedTransactionTest` — `detailsAsText()` join und `null` | Umkehrbarkeit für US-08 |
| Integration | `TransactionListControllerIntegrationTest` — Feld im JSON | Das Feld erreicht das Frontend |
| Unit (FE) | `category-overview.spec.ts` — Details gerendert; ohne Details keine leere zweite Zeile | **DoD:** US-13 AC 1 |
| Bestand | `PdfImportServiceIntegrationTest`, `PdfLookupCoverageIntegrationTest` u. a. | Der Import-Flow bleibt unverändert |

## Acceptance Criteria

Aus dem Issue-Text:

- [ ] Detailzeilen werden beim Import persistiert
- [ ] Migration ist durch einen Migrationstest abgedeckt
- [ ] Bestehende Importe brechen nicht (Spalte nullable oder Backfill entschieden)

Aus dem Nachtrag-Kommentar:

- [ ] Empfänger/Zweck wird in der Transaktionsliste angezeigt (US-13, AC 1)
- [ ] `npx prettier --check src/app/transactions/**` läuft ohne Warnung durch

**Delta zum Kommentar:** Der Kommentar nennt zwei Prettier-Dateien, der AC-Befehl meldet **drei** —
`pdf-import.service.ts` ist seit #196 dazugekommen (ebenfalls ein einzelner Zeilenumbruch). Der AC
ist die breitere Aussage, die dritte Datei wird mitgenommen.

## Bewusst nicht in diesem PR

- **BE-STS-02 (#22)** auf Absender-Gruppierung umstellen. Die Lösungsskizze sagt «kann danach», die
  DoD listet es nicht. `IncomeSuggestionService.groupingKey()` (`:213`) umzustellen ist eine eigene
  Heuristik-Änderung mit eigenen Fixture-Erwartungen — im selben PR wäre bei einem Testbruch nicht
  mehr trennbar, ob das neue Feld oder die neue Gruppierung schuld ist. Folge-Issue, im PR-Body
  verlinkt.
- **#134 (BE-CAT-06)**, Maskierung vor dem Claude-Call. Nicht angefasst; der Stand-Abgleich steht
  als Kommentar am Ticket. Dieser PR ändert am Claude-Pfad keine Zeile — `fullText()`,
  `buildUserPrompt` und `buildParams` bleiben unberührt, es geht nicht mehr hinaus als bisher.

## nDSG-Hinweis für den Security-Review

Neu ist, dass der Gegenpartei-Name **dauerhaft in der Datenbank** steht statt nur transient im
Claude-Prompt. `DETAIL_NOISE` hält IBAN, Postanschrift, maskierte Kartennummer und opake Referenzen
bereits beim Parsen heraus (`SwissBankStatementParser.java:215`) — «datenminimiert» heisst hier also
*ohne diese vier*, nicht *ohne Personendaten*. Der Name der Gegenpartei ist der Zweck des Feldes und
bleibt drin. Das ist eine Änderung der Datenhaltung und gehört als solche in den PR-Body, mit
Nachweis Baustein für Baustein.
