# [BE-PDF-13] Gegenpartei-Adresse als einzelne Zeile überlebt DETAIL_NOISE

- **Issue:** [#241](https://github.com/dfme/budget-buddy/issues/241)
- **Task-ID:** `BE-PDF-13`
- **Branch:** `fix/BE-PDF-13-adresse-detail-noise`
- **Story:** US-05 — Transaktionen kategorisieren
- **Sprint:** (Board: kein Sprint gesetzt)
- **Bestätigt am:** 2026-09-02

## Problem

`NOISE_ADDRESS` in `SwissBankStatementParser` verwirft Gegenpartei-Postanschriften
aus den Detailzeilen — kennt aber nur die **getrennten** Formen von PostFinance
(`Zürichstrasse 130` und `8600 Dübendorf` je auf eigener Zeile). Raiffeisen setzt
beides auf **eine** Zeile (`Zürichstrasse 130, 8600 Dübendorf`). Diese Form trifft
keine der Alternativen und geht in den Kategorisierungs-Input — auf der
BE-PDF-12-Fixture 9 von 17 Buchungen. Zwei Folgen (BE-PDF-06): die Adresse geht
unmaskiert an Claude (der `PromptSanitizer` kennt keine Postanschriften), und sie
belegt einen der drei `MAX_DETAIL_LINES`-Plätze auf Kosten der Zweckzeile.

## Entscheid — Diskriminator am abschliessenden `<PLZ> <Ort>`

Dritte Alternative in `NOISE_ADDRESS`:

```
^.+\s[1-9]\d{3}\s+\p{L}[\p{L}.\-' ]*$
```

Bewusst ein **Superset** der im AC genannten Form `<Strasse> <Nr>, <PLZ> <Ort>`:
Es setzt am abschliessenden `<PLZ> <Ort>` an, nicht am Strassen-Keyword, und fängt
damit auch die kommalose Firmenanschrift (`BAHNHOFSTRASSE 1 CH 8000 ZUERICH`) und
den Nicht-Strassen-Präfix (`KOS Archiv, 4503 Solothurn`) — beides
Gegenpartei-Postanschriften ohne Kategoriewert. Die Doppelkomma-Form
(`Schulhausstrasse 2,, 8000 Zürich`) fällt durch das `.+` mit ab. Prototyp gegen die
echten Detailzeilen: 10/10 Adresszeilen verworfen, 13/13 Zweck-/Referenz-/Händler-
zeilen bleiben. `Coop-2020 Bern` bleibt (Ziffern hängen per Bindestrich, nicht per
Leerzeichen); `Coop 2020 Bern` (mit Leerzeichen) fiele mit heraus — die neue
Heuristik-Grenze, die der Javadoc benennt.

## Betroffene Dateien

- `backend/src/main/java/com/budgetbuddy/transaction/SwissBankStatementParser.java`
  — dritte Alternative in `NOISE_ADDRESS`; Javadoc um die kombinierte Form und die
  aktualisierte Heuristik-Grenze ergänzt.
- `backend/src/test/java/com/budgetbuddy/transaction/SwissBankStatementParserFixtureTest.java`
  — `ERLAUBTE_DETAILZEILEN` um die jetzt verworfenen Adresszeilen bereinigt (plus
  ggf. durch freie Plätze neu auftauchende Zeilen); neuer Test
  `keineGegenparteiAdresse_imFullText` (AC #3).

## Test-Strategie

- Neuer `doesNotContain`-Test auf der Raiffeisen-Fixture (AC #3).
- Volle Parser-Fixture-Suite grün, PostFinance unverändert (AC #2).
- `PdfLookupCoverageIntegrationTest` im Zielband (AC #5) — Regressionsbremse gegen
  Über-Verwerfen von Zeilen mit Kategorie-Signal.
- `./mvnw verify` gesamt.

## Acceptance Criteria (aus #241)

1. `NOISE_ADDRESS` erkennt die einzeilige Form `<Strasse> <Nr>, <PLZ> <Ort>` — mit
   und ohne Komma, inkl. Doppelkomma.
2. Die getrennten Formen bleiben erkannt; PostFinance-Fixtures bleiben grün.
3. Test belegt: keine Transaktion trägt eine Gegenpartei-Adresse in `fullText()`.
4. Javadoc-Heuristik-Grenze auf den neuen Stand gezogen.
5. `PdfLookupCoverageIntegrationTest` bleibt im Zielband.
