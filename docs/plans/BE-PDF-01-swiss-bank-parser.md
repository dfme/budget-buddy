# [BE-PDF-01] PDFBox-Parser für Schweizer Bank-PDFs

- **Issue:** #13
- **Task-ID:** BE-PDF-01
- **Branch:** `feature/BE-PDF-01-swiss-bank-parser`
- **Story:** US-04 (PDF-Upload)

## Scope

Nur der **Parser** (Text-Extraktion aus PDF → Transaktionsobjekte). Import-Service
(BE-PDF-02, #17) und REST-Endpoint (BE-PDF-03, #18) sind eigene Issues und nicht
Teil dieses Issues.

## Entscheidungen

- **Test-Fixture:** Synthetische PDFs werden zur Laufzeit mit PDFBox erzeugt und
  geparst (kein echtes Bank-PDF im Repo). Deterministisch, keine sensiblen Daten.
- **Output-Modell:** Entkoppeltes `ParsedTransaction`-Record — nicht die JPA
  `Transaction`-Entity (die gehört zu BE-PDF-02).
- **Richtungserkennung (isIncome):** über Saldo-Delta. Steigt der Saldo gegenüber
  der Vorzeile → Gutschrift (Income); sinkt er → Belastung. Die `Saldovortrag`-Zeile
  setzt den Startsaldo. Entspricht dem in CLAUDE.md genannten "Saldo als Row-Anchor".
  Ohne Saldovortrag Fallback `isIncome=false` (dokumentiert).

## Neue Files

- `transaction/ParsedTransaction.java` — Record (buchungsdatum, buchungstext, betrag, isIncome)
- `transaction/SwissBankStatementParser.java` — `@Component`, `parse(byte[])`
- `transaction/PasswordProtectedPdfException.java`
- `transaction/PdfParseException.java`
- `test/.../transaction/SwissBankStatementParserTest.java`

## Implementierungsschritte

1. `ParsedTransaction`-Record + zwei Exceptions.
2. `SwissBankStatementParser`:
   - `Loader.loadPDF(byte[])` (PDFBox 3.x). `InvalidPasswordException` →
     `PasswordProtectedPdfException`; sonstige `IOException` → `PdfParseException`.
   - Text via `PDFTextStripper`.
   - Zeilen-Parsing: Row-Regex `dd.MM.yyyy … <betrag> <saldo>`.
   - Betrag: `replace("'","")` → `BigDecimal` (scale 2).
   - Datum: `DateTimeFormatter` `dd.MM.yyyy` → `LocalDate`.
   - isIncome via Saldo-Delta; `Saldovortrag`-Zeile als Startsaldo.
   - Mehrzeiliger Buchungstext (Wrapping): Nicht-Datum-Zeilen an laufende Zeile anhängen.
3. Deutsches Javadoc; CHF durchgehend `BigDecimal`.

## Test-Strategie (JUnit 5 + AssertJ)

Synthetische PDFs zur Laufzeit erzeugen, dann parsen:
- Happy Path (Raiffeisen-Layout, mehrere Zeilen).
- Tausendertrennzeichen `1'234.56` → korrektes `BigDecimal`.
- Datum `dd.MM.yyyy`.
- Gutschrift vs. Belastung via Saldo-Delta.
- Mehrzeiliger Buchungstext.
- Passwortgeschütztes PDF → `PasswordProtectedPdfException`.
- Korrupte Bytes → `PdfParseException`.

## Acceptance Criteria (aus #13)

- [ ] Transaktionen aus Raiffeisen-PDF werden korrekt geparst
- [ ] Beträge mit Tausendertrennzeichen (') → korrekt zu BigDecimal
- [ ] Datum-Format dd.MM.yyyy wird korrekt geparst
- [ ] Passwortgeschützte PDFs werfen definierte Exception
- [ ] PDFBox 3.x API (`Loader.loadPDF()`) wird verwendet, nicht 2.x
