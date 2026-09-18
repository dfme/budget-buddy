# [BE-CAT-13] Gelernte Lookup-Patterns mit variabler Mitteilung treffen nie wieder — Tabelle wächst pro Transaktion statt pro Händler

- **Issue:** [#321](https://github.com/dfme/budget-buddy/issues/321)
- **Task-ID:** `BE-CAT-13`
- **Branch:** `feature/BE-CAT-13-lookup-pattern-extraction`
- **Story:** US-05 — Transaktionen in Kategorien sehen
- **Sprint:** Sprint 7
- **Bestätigt am:** 2026-09-18

## Befund

Alle acht PDF-Fixtures geparst: Die variable Mitteilung steht bei PostFinance immer **am Ende**
von `fullText()` — `GIRO POST MUSTER IMMOBILIEN AG MIETE JANUAR 2025`, `LASTSCHRIFT SWISSCOM
(SCHWEIZ) AG RECHNUNG 12-2025`, `GUTSCHRIFT MUSTER CONSULTING GMBH LOHN JULI 2026 SOWIE SPE
SENVERGUETUNG`, `… Reg. Nr 10000001`. Kartenzahlungen (Raiffeisen, UBS, Viseca, PostFinance
`KAUF/DIENSTLEISTUNG …`) haben keinen variablen Teil. Im 240er-Auszug erzeugen die 96 Claude-Fälle
51 verschiedene Zeilen, davon 36 allein für Miete (12), Lohn (12) und Swisscom (12).

Die Extraktion muss in `CategoryLearningService.learn()` sitzen, nicht im
`HybridCategorizationService`: Sonst schreibt die manuelle Korrektur (BE-CAT-04) weiterhin den
vollen Text, der Upsert greift nicht, und die Längensortierung in `findMatching` lässt den
Korrektur-Eintrag nur für *diesen* Monat gewinnen — der Blocker aus PR #320 in neuer Form.

## Entscheide

- **Pattern-Extraktion ja, Retention nein (Folge-Issue).** AC1 ist nur mit Extraktion
  erfüllbar; AC2 lässt «entweder/oder» zu. Retention bräuchte eine Migration (`last_hit_at`),
  macht den Lese-Pfad zum Schreib-Pfad und kollidiert mit #319 (BE-CAT-12), das dieselbe Tabelle
  um `user_id` erweitern will.
- **Präfix-Schnitt am ersten variablen Token.** Das Pattern ist damit immer ein Präfix des
  Textes — die `LIKE '%pattern%'`-Semantik der Query bleibt garantiert. Variabel sind:
  1. Monatsname (DE/FR/IT/EN, inkl. `MAERZ`/`MÄRZ`, Abkürzungen) **nur wenn direkt ein Jahr
     folgt** (`MAI 2025`, `MAI 25`, `JAN-2025`); ein alleinstehendes `MAI` bleibt stehen
     (`MAI THAI RESTAURANT`); eine Tagesnummer davor (`15. MAI 2025`) zieht den Schnitt nach vorn
  2. Alleinstehendes Jahr `(19|20)\d{2}`
  3. Numerisches Datum: `15.05.2025`, `15.05.`, `08-2019`, `12/2025`, `2025-08`, `2025-08-15`
  4. Token mit ≥ 5 Ziffern am Stück (`10000001`, `P123456789`, IBAN kompakt) — Telefon
     `044 913 2323`, Filialnummer `COOP-1234` und PLZ bleiben stehen
  5. IBAN-Beginn `[A-Z]{2}\d{2}` (`CH66 0076 …`)
- **Genericity-Guard:** Das Präfix muss ≥ 3 Tokens **und** ≥ die Hälfte der Tokens behalten,
  sonst wird wie bisher der volle Text gelernt (Status quo, nie schlechter). Grund:
  `TWINT KAUF/DIENSTLEISTUNG VOM <variabel>` würde sonst zu einem Pattern, das jede
  TWINT-Zahlung des Kontos in eine Kategorie zwingt. Bekannte Kosten: sehr lange Mitteilungen
  fallen unter den Guard.

## Betroffene Dateien

| Datei | Änderung |
|---|---|
| `categorization/LookupPatternExtractor.java` | neu — package-private, statisch, Stil wie `PromptSanitizer` |
| `categorization/CategoryLearningService.java` | `learn()` normalisiert → extrahiert → upsert |
| `categorization/CategoryLearningPort.java` | Javadoc: Input bleibt `fullText()`, gespeichert wird das Präfix |
| `categorization/HybridCategorizationService.java` | Javadoc `learnFromClaude` präzisieren |
| `transaction/Transaction.java`, `transaction/TransactionCategoryService.java` | Javadoc |
| `docs/adr/ADR-6-hybrid-categorization.md` | Schlüssel-Absatz + Ein-Call-Aussage einschränken |
| `CLAUDE.md` | Hybrid-Abschnitt: Absatz zur Extraktion |

## Implementierungsschritte

1. `LookupPatternExtractor` mit den fünf Regeln und dem Guard
2. In `CategoryLearningService.learn()` einhängen (nach `trim().toUpperCase(Locale.ROOT)`)
3. Javadocs und Docs nachziehen, jede Aussage mit `file:line`
4. Tests, volle Backend-Suite

## Test-Strategie

- **Unit** `LookupPatternExtractorTest`: Korpus-Tabelle aus den Fixtures, Guard-Fälle,
  Negativfälle (`MAI THAI`, `COOP-1234`, `044 913 2323`, `KARTE 1234`), Invariante «Pattern ist
  Präfix des Inputs»
- **Integration** `CategoryLearningServiceIntegrationTest`: Januar lernen → Februar trifft;
  Januar + Februar lernen → 1 Zeile; Korrektur nach Claude → Upsert greift weiterhin
- **Integration, neu** `PdfLookupLearningIntegrationTest` (eigene DB, 240er-Fixture, Claude
  gemockt, gebündelt wie der `ImportJobRunner`): AC1 — beim ersten Import erreicht nur eine
  Mietzahlung Claude, beim zweiten keine; AC2 — Tabelle wächst um genau eine Zeile je
  Gegenpartei, der zweite Import um 0
- **AC4** — `PdfLookupCoverageIntegrationTest` unverändert grün

## Nicht Teil dieses PR

Retention (Folge-Issue), der `LIKE`-Full-Scan pro Transaktion (kein AC).

## Acceptance Criteria (aus dem Issue)

- [ ] Ein Händler mit variabler Mitteilung trifft ab dem zweiten Import die Lookup-Tabelle, ohne
      erneut Claude aufzurufen — Test mit den bestehenden Parser-Fixtures
- [ ] `category_lookup` wächst nicht mehr pro Transaktion, sondern höchstens pro Gegenpartei
- [ ] Die Aussage «jeder Händler kostet höchstens einen Claude-Call» in ADR-6 stimmt danach, oder
      sie ist auf die Fälle eingeschränkt, für die sie gilt
- [ ] Kein Verlust an Trefferqualität für Kartenzahlungen — `PdfLookupCoverageIntegrationTest`
      bleibt grün
