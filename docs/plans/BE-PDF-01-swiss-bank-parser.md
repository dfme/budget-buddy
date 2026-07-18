# [BE-PDF-01] PDFBox-Parser für Schweizer Bank-PDFs

- **Issue:** #13
- **Task-ID:** BE-PDF-01
- **Branch:** `feature/BE-PDF-01-swiss-bank-parser-v2`
- **Story:** US-04 (PDF-Upload)

## Scope

Nur der **Parser** (Text-Extraktion aus PDF → Transaktionsobjekte). Import-Service
(BE-PDF-02, #17) und REST-Endpoint (BE-PDF-03, #18) sind eigene Issues und nicht
Teil dieses Issues.

## Entscheidungen

- **Test-Fixture:** Synthetische Bank-PDFs, erzeugt durch
  `backend/tools/generate_pdf_fixtures.py` (reportlab) und als Fixtures im Repo
  abgelegt. Fiktiver Kontoinhaber, Beispiel-IBAN, `Synthetische Testdaten`-Footer.
  Kein echtes Bank-PDF im Repo — auch nicht in der Historie.
  *(Geändert gegenüber der ursprünglichen Fassung „zur Laufzeit mit PDFBox erzeugt":
  der Generator liefert realistischere Layouts — Spaltensatz, Seitenumbrüche,
  Detailzeilen — bei gleicher Determiniertheit. Die zur Laufzeit erzeugten PDFs
  bleiben für die Layout-Unit-Tests bestehen.)*
- **Formate:** Vier Layouts, erkannt an Kopfzeilen-Schlüsselwörtern im ersten
  Dokumentabschnitt: Viseca-Kreditkarte, PostFinance, UBS, generisch (Raiffeisen).
  Erkennung bewusst nur im Kopfbereich, damit ein Händlertext („MASTERCARD PMT …")
  das Format nicht umleitet.
- **Output-Modell:** Entkoppeltes `ParsedTransaction`-Record — nicht die JPA
  `Transaction`-Entity (die gehört zu BE-PDF-02).
- **Richtungserkennung (isIncome):** über Saldo-Delta. Steigt der Saldo gegenüber
  der Vorzeile → Gutschrift (Income); sinkt er → Belastung. Die `Saldovortrag`-Zeile
  setzt den Startsaldo. Entspricht dem in CLAUDE.md genannten "Saldo als Row-Anchor".
  Ohne Saldovortrag Fallback `isIncome=false` (dokumentiert, mit `log.warn`).
  UBS ist absteigend sortiert → Rückwärtsberechnung ab `Anfangssaldo`.
  PostFinance druckt den Saldo nur am Tagesende → Richtungen eines Blocks werden aus
  dem Delta rekonstruiert; nur eine **eindeutige** Vorzeichenkombination wird
  übernommen, sonst Fallback auf Belastung.

### Fortsetzungszeilen einlesen (Review-Befund aus #77 / #81)

**Problem:** Nur der generische Zweig hängt Fortsetzungszeilen an; Viseca, PostFinance
und UBS verwerfen sie. Genau dort steht bei diesen Formaten aber der Empfänger — die
Buchungszeile selbst trägt nur den Buchungs*typ*:

| Buchungszeile | Detailzeile darunter |
| --- | --- |
| `ESR` | `Stadtwerke Bern` |
| `GIRO POST` | `Muster Immobilien AG` / `MIETE SEPTEMBER 2019` |
| `GIRO INTERNATIONAL` | `Amazon EU S.a.r.l.` / `Luxembourg` |
| `Bezug UBS Bancomat` | Standort |

**Auswirkung auf beide Kategorisierungsstufen (ADR-6):**

1. Der Lookup matcht per `contains` gegen Händler-Pattern. `ESR` enthält kein Pattern
   und fällt durch; `ESR Stadtwerke Bern` kann eines treffen — ohne API-Call.
2. Der Claude-Prompt besteht aus genau einem String. Was nicht drinsteht, kann das
   Modell nicht wissen: `"ESR"` → `Sonstiges`.

Damit kippt zusätzlich die Kalkulation aus ADR-6 (Lookup deckt ~70–80 % ab, nur
~20–30 % gehen an Claude): bei PostFinance/UBS würde die halbe Belastungsliste als
nichtssagender Buchungstyp an Claude gehen — und von dort als `Sonstiges` zurückkommen.
Doppelt schlecht: API-Kosten *und* kein Ergebnis. Trifft US-05 und damit den
Safe-to-Spend (US-06) direkt.

**Lösung:** Fortsetzungszeilen werden in allen vier Layouts eingelesen und landen in
einem neuen Feld `details: List<String>` auf `ParsedTransaction`. BE-PDF-02 übergibt
der Kategorisierung `fullText()` — Buchungszeile plus Detailzeilen.

**Warum ein eigenes Feld statt Anhängen an `buchungstext`:** Konkatenieren ist
irreversibel. Aus `"GIRO POST Muster Immobilien AG MIETE SEPTEMBER 2019"` lässt sich
der Empfänger nie wieder isolieren — US-13 will genau einen Anzeigewert („Datum,
Betrag und Empfänger"), und der steckt je nach Format in der Haupt- oder in der
Detailzeile. Getrennt bleibt diese Ableitung später möglich, konkateniert nicht.
Ebenso für US-08 (Abo-Erkennung), die gleichartige Buchungen über Monate gruppieren
muss — `MIETE SEPTEMBER` vs. `MIETE OKTOBER` im selben String verhindert das.
Der Aufwand ist jetzt null, weil `ParsedTransaction` noch keinen Konsumenten hat.

> **Nicht** die Begründung: „der Lookup-Schlüssel muss stabil bleiben". Der Lookup
> matcht per `contains` gegen gespeicherte Händler-Pattern, nicht per Schlüsselgleichheit
> — ein längerer Text schadet ihm nicht. Was bei einer User-Korrektur als Pattern
> gespeichert wird, entscheidet US-05 / BE-CAT, nicht der Parser.

**Keine Anpassung an BE-CAT nötig:** `CategorizationPort.categorize(String)` bleibt
unverändert, ebenso `LookupTableService` (BE-CAT-01), `ClaudeCategorizationService`
(BE-CAT-02) und `HybridCategorizationService` (BE-CAT-03, PR #79). Beide Stufen
bekommen denselben String — das ist hier korrekt und kein Kompromiss.

## Neue Files

- `transaction/ParsedTransaction.java` — Record (buchungsdatum, buchungstext, details, betrag, isIncome) + `fullText()`
- `transaction/SwissBankStatementParser.java` — `@Component`, `parse(byte[])`
- `transaction/PasswordProtectedPdfException.java`
- `transaction/PdfParseException.java`
- `test/.../transaction/SwissBankStatementParserTest.java` — Layout-Unit-Tests, PDFs zur Laufzeit
- `test/.../transaction/SwissBankStatementParserFixtureTest.java` — Integrationstests gegen die Fixtures
- `test/resources/pdf/*.pdf` — vier synthetische Fixtures
- `tools/generate_pdf_fixtures.py` — Generator für die Fixtures

## Implementierungsschritte

1. `ParsedTransaction`-Record + zwei Exceptions.
2. `SwissBankStatementParser`:
   - `Loader.loadPDF(byte[])` (PDFBox 3.x). `InvalidPasswordException` →
     `PasswordProtectedPdfException`; sonstige `IOException` → `PdfParseException`.
   - Text via `PDFTextStripper`, **seitenweise** extrahiert (`setStartPage`/`setEndPage`) —
     die Seitengrenze ist die Reset-Marke für die Fortsetzungszeilen-Logik. Ohne sie
     hängt der Seitenkopf von Seite 2 an der letzten Buchung von Seite 1.
   - Formaterkennung über Kopfbereich → Delegation an den Layout-Parser.
   - Zeilen-Parsing je Layout; Betrag: Tausendertrennzeichen entfernen →
     `BigDecimal` (scale 2); Datum → `LocalDate` (`dd.MM.yyyy` bzw. `dd.MM.yy`).
   - isIncome via Saldo-Delta (siehe Entscheidungen).
   - **Fortsetzungszeilen**, gemeinsamer Helper für alle vier Layouts. Eine Zeile wird
     der laufenden Buchung zugeordnet, wenn sie kein führendes Datum, keinen Betrag und
     keine bekannte Rauschsignatur (Label-Zeilen, IBAN, maskierte Kartennummer) enthält
     und die Längen-/Anzahlgrenze einhält. Eine Total-/Saldo-Zeile beendet den
     Buchungsteil der Seite — danach wird nichts mehr angehängt.
     *(Ersetzt „Nicht-Datum-Zeilen an laufende Zeile anhängen": das hätte Seitenfuss-,
     Total- und Rechtstexte mit in den Kategorisierungs-Input geschrieben.)*
3. Deutsches Javadoc; CHF durchgehend `BigDecimal`.

## Test-Strategie (JUnit 5 + AssertJ)

**Unit (PDFs zur Laufzeit erzeugt):**
- Happy Path (Raiffeisen-Layout, mehrere Zeilen).
- Tausendertrennzeichen `1'234.56` → korrektes `BigDecimal`.
- Datum `dd.MM.yyyy`; kalendarisch ungültiges Datum → `PdfParseException`.
- Gutschrift vs. Belastung via Saldo-Delta, auch bei negativem Saldo.
- Fortsetzungszeilen landen in `details`, nicht in `buchungstext`.
- Seitenfuss-, Total- und Rechtstexte landen in **keinem** der beiden Felder.
- Mehrdeutiger PostFinance-Block → alle als Belastung.
- Händlertext „MASTERCARD" leitet die Formaterkennung nicht um.
- Passwortgeschütztes PDF → `PasswordProtectedPdfException`.
- Korrupte Bytes → `PdfParseException`.

**Integration (gegen die Fixtures):**
- Kreuzprobe je Auszug: Summe der extrahierten Gutschriften/Belastungen ==
  gedruckte `Total`-/`Umsatztotal`-Zeile. Verifiziert Beträge, Richtung und
  Vollständigkeit in einer Assertion.
- Format-spezifische Kanten: Leerzeichen- und Apostroph-Trennzeichen, zweistellige
  Jahre, absteigende Sortierung, gemischte Tagesblöcke, Fremdwährungszeilen.
- Empfänger aus Detailzeilen kommen an: `ESR` → `Stadtwerke Bern`,
  `GIRO INTERNATIONAL` → `Amazon EU S.a.r.l.`.
- Seitenübergreifend (UBS, 2 Seiten): kein Seitenkopf in den `details`.

## Acceptance Criteria (aus #13)

- [ ] Transaktionen aus Raiffeisen-PDF werden korrekt geparst
- [ ] Beträge mit Tausendertrennzeichen (') → korrekt zu BigDecimal
- [ ] Datum-Format dd.MM.yyyy wird korrekt geparst
- [ ] Passwortgeschützte PDFs werfen definierte Exception
- [ ] PDFBox 3.x API (`Loader.loadPDF()`) wird verwendet, nicht 2.x

## Bewusst nicht in diesem Issue

- **Leeres Parse-Ergebnis** (nicht-leeres PDF, 0 Transaktionen) wirft keine Exception
  → **BE-PDF-04**.
- **Spaltenbasierte Extraktion** über x-Koordinaten (`TextPosition`) statt Regex auf
  der flachen Textzeile. Würde die Fortsetzungszeilen-Heuristik durch einen
  Strukturtest ersetzen *und* die Richtungserkennung überflüssig machen, weil
  `Belastung`/`Gutschrift` direkt als Spalten ablesbar wären. Grösserer Umbau —
  eigenes Issue, sobald ein reales Layout die Heuristik bricht.
- **Kreuzprobe `|saldo-delta| == betrag`** im Parser (bisher nur im Test).
- **Restrisiko Substring-Treffer:** mehr Text im Kategorisierungs-Input heisst mehr
  Chancen auf einen zufälligen Lookup-Treffer. Die „längstes Pattern gewinnt"-Sortierung
  entschärft das; die konservative Filterung der Detailzeilen ebenfalls. Beobachten.
