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
4. **Step 4: User Feedback Loop** — User-Korrektionen → Lookup-Eintrag

## Consequences

### Positive

- **Cost-Optimized:** Lookup deckt ~70-80% ab (bekannte Händler)
  - 1.000 Transaktionen × $0.00075/tx = ~$0.75/Monat
  - vs. LLM-Only: $750/Monat (zu teuer)
- **Fast:** Lookup <1ms; nur 20-30% an Claude (~3ms/tx durchschnittlich)
- **Accurate:** 85-90% Genauigkeit (Haiku) + User können korrigieren
- **Robust:** Claude-Fehler → Fallback zu "Sonstiges" (nie Import blockieren)
- **Learning:** User-Korrektionen erweitern Lookup → bessere Zukunftsvorhersagen

### Negative

- **Initial Setup:** Lookup-Tabelle muss mit ~200-300 Schweizer Händlern initialisiert werden
- **Fragmented Lookups:** User-spezifische Overrides erschweren globale Optimierung
- **Not Perfect:** 85-90% Genauigkeit = 10-15% Fehler (User muss korrigieren können)

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
- **ADR-5:** SQLite (Lookup-Tabelle lokal gespeichert)
- **ADR-9:** BigDecimal für Geldbeträge
