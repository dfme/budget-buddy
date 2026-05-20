5-Minuten-Pitch — Schnellplan

  Top 5 Punkte (60s pro Punkt)

  1. Problem in einem Satz, kein Marketing.
  "Lara verliert Mitte des Monats den Überblick und scrollt panisch durch ihre Banking-App." Eine konkrete Persona zieht stärker als 14 Stories.
  2. Die ONE Number: Safe-to-Spend.
  Formel kurz an die Wand: (Einkommen − Fixkosten − bisherige Ausgaben) ÷ verbleibende Wochen. Das ist eure Differenzierung gegenüber jedem Mint/YNAB-Klon — das alleine macht den Pitch.
  3. MVP-Scope: 4 Must-Stories, bewusst gesetzt.
  Fixkosten → PDF → Kategorien → S2S. Sagt explizit: "Auth und Consent sind Should, weil sie keinen User-Wert liefern — sie ermöglichen ihn nur." Das zeigt Scope-Disziplin und ist genau das, was
  Architekten hören wollen.
  4. Hybrid-Kategorisierung — die spannendste technische Entscheidung.
  "70–80% via Lookup-Table (deterministisch, gratis, schnell), 20–30% via Claude Haiku (flexibel). Manuelle Korrekturen erweitern die Lookup-Table — das System lernt ohne Retraining." Das ist die
  Stelle, an der die Architekten-Augen leuchten.
  5. Drei dokumentierte Risiken — Reifezeichen.
  Einkommen-Erfassung fehlt als Story / Fixkosten-Doppelzählung / nDSG-Datenhaltung. Sagt klar: "Wir haben diese Lücken vor der Implementierung gefunden, nicht im Bugfix." Das ist der stärkste Slide
  eures Decks.

  ---
  Was ein erfahrener Architekt SOFORT hören will

  - Warum SQLite und nicht Postgres? Antwort: MVP, kein Concurrent-Write-Druck, Migrationspfad dokumentiert. Pre-empt die Frage.
  - Warum JWT und nicht Session? SQLite-Write-Pressure-Vermeidung + saubere SPA-Integration. Ein Satz reicht.
  - Wie geht ihr mit Geld um? BigDecimal, keine double — und dass Schweizer Banken 1'234.56 mit Apostroph schreiben, also replace("'", "") vor dem Parse.
  - Was passiert wenn Claude API down ist? Fallback "Sonstiges", Import-Flow blockiert nicht. Failure Mode dokumentiert.
  - Wie messt ihr Erfolg der Auto-Kategorisierung? ≥80% auf einem Test-Set von 200 gelabelten Transaktionen. Messbares Akzeptanzkriterium, nicht "fühlt sich gut an".
  - nDSG-Recht auf Löschung — wirklich umgesetzt? Ja, US-2: DB-Admin findet keinen Eintrag mit gelöschter User-ID.

  ---
  Was ihr NICHT sagen solltet (Zeitfresser)

  - Keine MoSCoW-Methodik erklären. Annahme: Publikum kennt sie.
  - Nicht alle 14 Stories aufzählen. Zwei nennen (US-6 als Kern, US-11 als Won't mit Begründung). Rest "im Doc nachzulesen".
  - Keine Acceptance Criteria vorlesen. Tod jedes Pitches.
  - Keine Versionsnummern (Angular 19.x, Spring 3.5.3, PDFBox 3.0). Eine Folie "Tech Stack" mit Logos — fertig.
  - Nicht apologetisch sein für das, was fehlt. "OpenBanking ist Won't" ist eine Entscheidung, keine Entschuldigung.
  - Keine Persona-Geschichten ausschmücken. Ein Satz pro Persona genügt — Lara für den Pain, Marc für den Datenschutz-Aspekt.
  - Keine Slides zu "Recent Decisions" / Process / Meetings. Niemand will Projekt-Meta hören.
  - Nicht über Tooling sprechen (Flyway, Springdoc, ng2-charts). Nur wenn explizit gefragt.

  ---
  Eine Folie, die ihr garantiert braucht

  Eine Architektur-Skizze — 5 Boxen, max:

  [Angular SPA] ──JWT──> [Spring Boot] ──> [SQLite]
                              │
                              ├──> [PDFBox] (Text-Extraktion)
                              └──> [Lookup-Tabelle] ──fallback──> [Claude Haiku API]

  Das ist die Folie, die euch eine Minute spart und drei Fragen beantwortet bevor sie gestellt werden.
