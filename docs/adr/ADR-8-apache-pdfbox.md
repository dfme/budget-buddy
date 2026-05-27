# ADR-8: Apache PDFBox 3.x für PDF-Verarbeitung

**Status:** Accepted  
**Date:** 2026-05-27

## Context

BudgetBuddy muss Kontoauszüge von Schweizer Banken (PDF) verarbeiten und Transaktionen extrahieren:

- Datum, Betrag, Empfänger parsen
- Text-Layer Extraktion (nicht gescannte PDFs)
- Tabellen-Struktur erkennen
- Schweizer Formatierung (`1'234.56` mit Apostroph)
- Fehlerbehandlung (passwortgeschützt, ungültige PDFs)
- Performance: <5 Sekunden für 50-Seiten PDF

Alternative PDF-Bibliotheken: iText 7, Tabula-java, pdfplumber

## Decision

Wir nutzen **Apache PDFBox 3.x**:

- **License:** Apache 2.0 (kostenlos, kommerziell freundlich)
- **Text-Layer:** PDFBox extrahiert Text + Character-Positionen
- **Tabellen-Parsing:** Manuelle Regex-basierte Zeilen-Erkennung
- **Password Detection:** PDFBox erkennt passwortgeschützte PDFs
- **Performance:** ~5 Sekunden für typische 50-Seiten PDF

## Consequences

### Positive

- **Free & Open Source:** Apache License (keine kommerziellen Lizenz-Kosten)
- **Java Native:** Spring Boot Integration einfach
- **Mature Library:** 20+ Jahre aktive Entwicklung
- **Swiss Bank PDFs:** Text-Layer Extraktion perfekt für diese Use Case
- **Fast Enough:** <5 Sekunden Performance für MVP

### Negative

- **Table Recognition:** Keine Native Tabellen-Unterstützung
  - Mitigation: Regex-Patterns für Schweizer Bank-Formate
- **Complex PDFs:** Nicht ideal für gescannte oder komplexe Layouts
  - Mitigation: Schweizer Bank-PDFs haben einfache, konsistente Struktur

## Alternatives

### iText 7

**Rejected.** Robust, aber:
- AGPL/Commercial License (zu teuer oder open-source-obligated)
- Overkill für unsere einfachen Use Cases
- Höhere Kosten bei Startup-Budget

### Tabula-java

**Rejected.** Spezialisiert auf Tabellen, aber:
- Slow (~20 Sekunden für 50 Seiten)
- Zu spezialisiert (kein Text-Layer Support)

### pdfplumber (Python)

**Rejected.** Excellent für Tabellen, aber:
- Nicht Java-native
- Würde Python Runtime hinzufügen (Deployment komplexer)

## Related Decisions

- **ADR-1:** Java + Spring Boot (PDFBox Maven Dependency)
- **ADR-4:** Monolith (PDF-Service im selben JAR)
