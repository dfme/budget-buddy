# ADR-6: Hybrid-Kategorisierung mit Lookup + Claude API

**Status:** Accepted  
**Date:** 2026-05-27

## Context

BudgetBuddy muss Transaktionen automatisch in 13 Kategorien klassifizieren (Wohnen, Lebensmittel, Transport, ..., Sonstiges).

**Anforderungen:**

- Automatisch (kein manuelles Labeling für 1.000+ Transaktionen)
- Schnell (<500ms pro Batch)
- Kostengünstig (Benutzer zahlen nichts, also LLM-Calls sparsam)
- Genau (80%+ Genauigkeit)
- Fallback bei API-Fehler (nie Import blockieren)

Alternative Ansätze: Lookup-Only, LLM-Only, ML Model, Rule-Based Patterns

## Decision

Wir nutzen einen **Hybrid-Ansatz: Lookup-Tabelle + Claude API**:

1. **Step 1: Lookup** — Für bekannte Händler (MIGROS, SBB, etc.) in Lookup-Tabelle
2. **Step 2: Claude API** — Für unbekannte Transaktionen an Claude Haiku senden
3. **Step 3: Fallback** — Bei Claude-Fehler → "Sonstiges"
4. **Step 4: Feedback Loop** — Die Lookup-Tabelle wächst aus **zwei** Quellen:
   - **User-Korrekturen** → Lookup-Eintrag (BE-CAT-04, `TransactionCategoryService`)
   - **Erfolgreiche Claude-Kategorisierungen** → Lookup-Eintrag (BE-CAT-11,
     `HybridCategorizationService`)

**Warum zwei Quellen.** Bis BE-CAT-11 lernte nur die Korrektur. Ein Händler, den Claude auf Anhieb
richtig einstufte und den deshalb niemand korrigierte, löste bei jedem Import wieder einen Call
aus — der Lerneffekt griff ausgerechnet dort nicht, wo die Automatik funktionierte.

**Was ausdrücklich nicht gelernt wird.** Nur ein Ergebnis, das Claude *tatsächlich beantwortet*
hat (`CategorizationResult.Source.CLAUDE`). Aussen vor bleiben:

- **Fehler-Fallbacks** — fehlgeschlagener oder unlesbarer Call, im Bündel ausgelassene Nummer
  (`CLAUDE_FALLBACK`), offener Circuit Breaker, fehlender API-Key, überschrittenes Zeitbudget
  (`CLAUDE_SKIPPED`). Würden sie gelernt, fröre ein einzelner Netzwerkfehler einen Händler
  dauerhaft auf "Sonstiges" ein: Step 1 fängt ihn ab dem nächsten Import vor Claude ab, und er
  käme nie wieder zur Bewertung. Ein vorübergehender Ausfall hinterliesse dauerhaft falsche Daten.
- **Ein echtes "Sonstiges" vom Modell** — dieselbe Einfrier-Wirkung, nur ohne Fehler als Ursache,
  und kein Wissensgewinn gegenüber dem Fallback, den unbekannte Händler ohnehin bekommen.

Beide Quellen schreiben mit Upsert-Semantik in dieselbe Tabelle. Kommt eine manuelle Korrektur
nach einer Claude-Einstufung, überschreibt sie diese — der User hat das letzte Wort.

**Der Text ist auf beiden Seiten derselbe:** Buchungstext samt Detailzeilen, mit Leerzeichen
verbunden — `ParsedTransaction.fullText()` beim Import, `Transaction.fullText()` bei der Korrektur.
Das ist Bedingung, keine Kosmetik: Primärschlüssel der Tabelle ist das Pattern selbst. Übergeben
die Quellen verschiedene Texte, greift kein Upsert, es entstehen zwei Zeilen, und die
Sortierung nach Pattern-Länge in `findMatching` lässt den längeren Claude-Eintrag über die
User-Korrektur gewinnen — das Gegenteil des letzten Wortes. Bei Layouts mit Detailzeilen
(PostFinance, UBS, Kreditkarte) war genau das der Fall, solange die Korrektur nur den
`buchungstext` lernte.

**Gespeichert wird das stabile Präfix, nicht der volle Text** (BE-CAT-13, #321). Für
Kartenzahlungen ist der volle Text stabil; für Überweisungen mit Mitteilung nicht — `GIRO POST
MUSTER IMMOBILIEN AG MIETE JANUAR 2025` traf `… MIETE FEBRUAR 2025` nie, und die Tabelle wuchs
pro Monat um eine Zeile, die nie wieder traf. `CategoryLearningService` schneidet den Text
deshalb vor dem ersten variablen Token ab (Monat mit Jahr, alleinstehendes Jahr, numerisches
Datum, Referenz mit fünf und mehr Ziffern, IBAN — `LookupPatternExtractor`). Ein Präfix statt
einer Maskierung, weil `findMatching` per `LIKE '%pattern%'` einen zusammenhängenden Substring
braucht. Ein Guard hält generische Patterns heraus: Das Präfix muss mindestens drei Tokens und
mindestens die Hälfte des Textes behalten, sonst wird wie bisher der volle Text gelernt — sonst
zwänge `TWINT KAUF/DIENSTLEISTUNG VOM` jede TWINT-Zahlung eines Kontos in eine Kategorie. Der
Schnitt sitzt im Service und nicht beim Aufrufer, damit beide Quellen denselben Schlüssel
schreiben.

**PII-Policy für MVP:** Der rohe Transaktionstext (z.B. `"DIGITEC GALAXUS AG 044 913 2323"`) wird ohne Pseudonymisierung an die Anthropic API gesendet. Das ist eine Datenübermittlung an einen US-Dienstleister und fällt unter nDSG Art. 16 (Bekanntgabe ins Ausland).

**Dieser Compliance-Gap wird für das MVP bewusst akzeptiert.** Begründung: Es handelt sich um ein Kurs-Projekt ohne echte Produktionsdaten; ein Data Processing Agreement (DPA) mit Anthropic sowie eine explizite Erwähnung in den Nutzungsbedingungen sind für den Produktionsbetrieb nachzuholen.

## Consequences

### Positive

- **Cost-Optimized:** Lookup deckt ~70-80% ab (bekannte Händler)
  - 1.000 Transaktionen × $0.00075/tx = ~$0.75/Monat
  - vs. LLM-Only: $750/Monat (zu teuer)
- **Fast:** Lookup <1ms; nur 20-30% an Claude (~3ms/tx durchschnittlich)
- **Accurate:** 85-90% Genauigkeit (Haiku) + User können korrigieren
- **Robust:** Claude-Fehler → Fallback zu "Sonstiges" (nie Import blockieren)
- **Learning:** Erfolgreiche Claude-Kategorisierungen und User-Korrekturen erweitern beide den
  Lookup → ein Händler kostet höchstens einen Claude-Call, danach ist er deterministisch. Das gilt
  für Kartenzahlungen (stabiler Text) und für Überweisungen, deren Mitteilung mit einem variablen
  Token beginnt — Monat und Jahr, Datum, Referenz (BE-CAT-13). Es gilt **nicht** für drei Fälle:
  eine Mitteilung, die ohne solches Token variiert (`SACKGELD LEA` / `TASCHENGELD LEA`); einen
  Text, der unter den Guard fällt (Präfix unter drei Tokens oder unter der Hälfte); und ein
  echtes `Sonstiges` vom Modell, das bewusst nicht gelernt wird — dort kostet der Händler einen
  Call pro Import

### Negative

- **Initial Setup:** Lookup-Tabelle muss mit ~200-300 Schweizer Händlern initialisiert werden
- **Fragmented Lookups:** User-spezifische Overrides erschweren globale Optimierung
- **Not Perfect:** 85-90% Genauigkeit = 10-15% Fehler (User muss korrigieren können)
- **Gelernte Patterns sind global und überleben die Kontolöschung (#290):** `category_lookup` hat
  keine `user_id`. Seit BE-CAT-11 landet dort nicht mehr nur ein aktiv korrigierter, sondern jeder
  von Claude eingestufte Händlertext — ungefragt und für jeden importierten Auszug. Die Lücke aus
  #290 wird dadurch breiter, nicht neu
- **PII-Transfer zu Anthropic (akzeptiertes Risiko):** Roher Transaktionstext verlässt die eigene Vertrauenszone; nDSG Art. 16 formal nicht vollständig erfüllt
  - Mitigation MVP: Kein Produktionsbetrieb mit echten Nutzerdaten ohne DPA + AGB-Anpassung
  - Mitigation langfristig: Lokales LLM via Ollama ersetzt Claude für die Kategorisierung (siehe Alternatives)

## Alternatives

### LLM-Only (Claude für alle Transaktionen)

**Rejected.** Flexible, aber:

- Zu teuer: 1.000 User × 1.000 tx/Monat × $0.00075 = $750/Monat = $9.000/Jahr
- Startup kann das nicht tragen

### ML Model (Fine-Tuned)

**Rejected.** Für MVP zu komplex:

- Braucht gelabelte Trainings-Daten (nicht verfügbar am Start)
- Braucht ML-Expertise (nicht im Team)
- Retraining bei User-Korrektionen aufwendig

### Lokales LLM via Ollama (z.B. Llama 3.2 3B, Phi-3 Mini)

**Nicht gewählt für MVP — offen für spätere Evaluation.**

Idee: Ollama-Server läuft neben Spring Boot; LangChain4j oder direkter HTTP-Client ersetzt den Claude-Haiku-Fallback. Nur die Kategorisierung (Step 2) wäre betroffen — der KI-Monatsbericht (Sonnet 4) bleibt weiterhin über die Anthropic API.

**Vorteile:**
- PII verlässt das System nie → löst W-4 (nDSG Art. 16) vollständig
- Kein DPA mit Anthropic für die Kategorisierung nötig
- Keine variablen API-Kosten pro Kategorisierungs-Call

**Nachteile / Risiken:**
- Deployment-Komplexität: Ollama-Server + Modell (~2 GB) muss neben Spring Boot betrieben werden
- Ressourcenbedarf auf dem Hosting-Provider (RAM, evtl. kein Support auf Render Free Tier)
- Klassifikationsqualität kleiner Modelle ungetestet für Schweizer Transaktionstext (CHF-Beträge, Händlernamen)
- Erhöht MVP-Komplexität signifikant

**Fazit:** Technisch valide für die Kategorisierungsaufgabe (13 Labels, kurzer Text). Lohnt sich zu evaluieren, wenn nDSG-Compliance ein K.O.-Kriterium wird oder API-Kosten bei Wachstum steigen.

## Related Decisions

- **ADR-1:** Java + Spring Boot (AnthropicClient Integration)
- **ADR-12:** PostgreSQL bei Neon (Lookup-Tabelle in der gemeinsamen Datenbank)
- **ADR-9:** BigDecimal für Geldbeträge
