# Quality Attributes Analyse — BudgetBuddy

**Stand:** 2026-05-27
**Kurs:** CAS Application Development with AI (ADAI) 2026 · BFH Biel

---

## Bewertung aller relevanten QAs

| QA | Kritikalität | Begründung (Bezug zu BudgetBuddy) |
|---|---|---|
| **Security (inkl. Privacy/Compliance)** | Hoch | Risiko #2 explizit als "existenzbedrohend" gelistet; nDSG zwingend; US-1, US-2 |
| **Usability** | Hoch | Risiko #1 (Churn-Falle); Lara bricht bei mühsamem PDF-Upload sofort ab; US-4 |
| **Reliability/Correctness** | Hoch | Safe-to-Spend ist Kernversprechen ("number users can trust"); Rappen-genau; US-6 |
| Performance | Mittel | 30s PDF-Timeout, Claude ~200ms — wichtig, aber nicht existenziell |
| Maintainability | Mittel | Kursprojekt, kleines Team — wichtig, aber kein Differenzierungs-QA |
| Portability | Niedrig | Schweiz-only, Web-App — kein Plattformwechsel im MVP |
| Scalability | Niedrig | SQLite akzeptiert als MVP-Bottleneck; PG-Migration als Option dokumentiert |

---

## Top 3 — Detailanalyse

### 1. Security (Confidentiality + Integrity + nDSG-Compliance)

**Warum kritisch für BudgetBuddy:**
Wir speichern Klartext-Transaktionen einer ganzen Person: Wohnadresse aus Mietzahlung, Krankheit aus Apotheken-Transaktionen, Beziehungsstatus aus Restaurantbesuchen. Marcs Persona-Hürde lautet wörtlich "Datenschutz-Skepsis — warum private Daten einer Web-App anvertrauen?". Die Konkurrenz heisst UBS/Raiffeisen mit gewachsenem Vertrauen und Bankenlizenz — ein einziger Datenleak macht uns vor der Zielgruppe unglaubwürdig. nDSG verlangt zusätzlich aktive Löschpflicht (US-2: "Datenbankadmin findet keinen Eintrag mehr").

**Konkrete architektonische Massnahmen:**

| Massnahme | Konkret in unserem Stack |
|---|---|
| Passwort-Speicherung | bcrypt mit cost factor ≥12 (Spring Security `BCryptPasswordEncoder`) — Klartext-Passwort darf nirgends ins Log/in die DB |
| Auth-Token | Stateless JWT HS256, kurze Lifetime (15 min) + Refresh-Token; HS256-Secret per env var, nicht in `application.properties` |
| Transport | HTTPS-only, HSTS-Header, `Secure`+`HttpOnly`+`SameSite=Strict` falls Cookies; CORS-Whitelist nur eigene Frontend-Origin |
| Persistente Verschlüsselung | SQLite-DB-Datei auf verschlüsseltem Volume + sensitive Felder (Empfänger, Betrag, Beschreibung) zusätzlich AES-256 auf Spaltenebene mit per-User-Key (KEK/DEK-Pattern) |
| Anthropic API | Transaktionstexte vor Versand pseudonymisieren (IBAN/Account-Nr. raus), kein "store" bei Anthropic, Audit-Log der gesendeten Payloads |
| nDSG-Löschung (US-2) | Hard-Delete in einer Transaktion über alle Tabellen (`user`, `transactions`, `fixed_costs`, `savings_goals`, `monthly_reports`); Integration-Test verifiziert Tabellen-Scan nach `user_id` |
| Input-Sanitization | PDF-Parsing in isoliertem Thread mit Timeout (US-4: 30s); PDFBox 3.x mit `setEnableSandbox` / Resource-Limits gegen Zip-Bomb / Billion-Laughs |
| Audit/Logging | Login-Erfolg/-Fehlschlag, DSGVO-Consent-Events, Datenlöschungs-Events — aber nie Klartext-Transaktionen ins Log |
| Dependency-Hygiene | OWASP Dependency-Check als Gradle-Task, Renovate-Bot für JJWT / PDFBox / Spring-Patches |

**Konsequenz bei Ignorieren:**
Ein Leak von 1.000 Schweizer Studenten-Finanzdaten = Anzeige beim EDÖB + Pflichtmeldung an Betroffene innerhalb 72h (nDSG Art. 24). Reputation tot, Geschäftsmodell tot, in Marcs Worten: "Ich vertraue dieser Web-App nie wieder, und ich erzähle es allen meinen Kollegen." Wir bauen exakt das Tool, das die Zielgruppe NICHT will.

---

### 2. Usability (Frictionless Onboarding + First-Time-PDF-Upload)

**Warum kritisch für BudgetBuddy:**
Risiko #1 wörtlich: "manueller PDF-Import + Kategorisierung führt zu Nutzungsabbruch nach erstem Aha-Effekt". Lara-Persona: "Aufschieberitis — wenn der erste PDF-Upload zu kompliziert ist, bricht sie sofort ab". Wir haben damit ein hartes Funnel-Problem: Registrierung (US-1) → Consent (US-2) → Fixkosten-Wizard (US-3) → PDF-Upload (US-4) — vier Hürden, bevor der User den ersten Wert (Safe-to-Spend, US-6) sieht. Jeder dieser Schritte ist ein Drop-off-Punkt.

**Konkrete architektonische Massnahmen:**

| Massnahme | Konkret in unserem Stack |
|---|---|
| "Time-to-Value" als architektonisches Ziel | Vom Registrierungs-Klick bis zum ersten Safe-to-Spend-Wert maximal 3 Minuten; messen via Frontend-Telemetrie (Events `signup`, `pdf_uploaded`, `s2s_visible`) |
| Asynchrone PDF-Verarbeitung | Spring `@Async` + Status-Endpoint (`/api/uploads/{id}/status`); Frontend zeigt Progress-Bar mit konkreten Schritten ("PDF gelesen → 47 Transaktionen erkannt → kategorisiere…") statt Spinner |
| Optimistic UI nach Upload | Sofort grobe Kategorisierung via Lookup-Tabelle anzeigen, Claude-Ergebnisse "nachträglich" einblenden — User wartet nicht auf das LLM |
| Fehlertoleranz im PDF-Parser | Bank-spezifische Adapter (UBS/Raiffeisen/PostFinance separat); bei unerkanntem Layout nicht "Format nicht unterstützt" sondern "Wir haben X von Y Zeilen erkannt — bitte prüfen" mit Editier-Möglichkeit |
| Onboarding-Wizard mit Skip-Default (US-3) | "Keine Fixkosten" als explizite Option; Mietfeld mit smartem Default (z.B. CH-Durchschnitt für Studenten) — Vorschlag, nicht Pflicht |
| Demo-Modus | Vorbereiteter Beispiel-PDF (anonymisiert) als "Ohne Anmeldung ausprobieren"-Button — Marcs Datenschutz-Skepsis adressieren, ohne Funnel abzubrechen |
| Manuelle Korrektur als First-Class-Feature | Inline-Edit der Kategorie direkt in der Transaktionsliste (1 Klick), nicht via Modal; Hybrid-Ansatz lernt aus Korrekturen (siehe Kategorisierungs-Tabelle in CLAUDE.md) |
| Mobile-First-Layout | Lara checkt panisch in der Banking-App — wir müssen mindestens gleich gut auf dem Handy funktionieren; Angular OnPush + responsive von Anfang an |

**Konsequenz bei Ignorieren:**
Selbst mit perfekter Security und korrekter Math: 80% der Nutzer brechen beim ersten PDF-Upload ab, der Rest checkt 1× und kommt nie wieder. Wir bekommen 0 Bestandsdaten, der Hybrid-Kategorisierer kann nicht lernen, der KI-Monatsbericht (US-9) hat nichts zu berichten, Marcs Sparziel (US-7) wird nie erreicht weil er die App nicht öffnet. Kursprojekt wird abgegeben, aber das Produkt ist tot bevor es lebt.

---

### 3. Reliability / Correctness (Money Math + Safe-to-Spend-Vertrauen)

**Warum kritisch für BudgetBuddy:**
Kernversprechen wörtlich: "A weekly Safe-to-Spend number users can trust". Lara entscheidet auf Basis dieser Zahl, ob sie heute Abend essen geht. Marc plant darauf seinen Notgroschen. Wenn die Zahl auch nur einmal sichtbar falsch ist — Rappen-Differenz zur Bank-App, fehlende Transaktion, Doppelzählung — kippt das Vertrauen sofort. Im Gegensatz zu Security ist ein Reliability-Bug *sichtbar* für den User, jeden Tag. Die User Stories sind voll von harten Korrektheits-Anforderungen: "auf den Rappen genau" (US-3, US-5, US-10), "≥95% Extraktion" (US-4), "≥80% Kategorisierung" (US-5), keine Division durch 0 (US-6, US-10).

**Konkrete architektonische Massnahmen:**

| Massnahme | Konkret in unserem Stack |
|---|---|
| Money-Type strikt `BigDecimal` | Nirgends `double`/`float` für CHF; Jackson-Serialisierung mit `@JsonFormat(shape = STRING)` damit JS-Frontend keine `Number`-Konvertierung macht; SQLite-Spalte als `TEXT` oder `NUMERIC` mit DECIMAL-Wrapper |
| Apostroph-Parsing zentralisiert | Ein einziger `SwissAmountParser`-Service: `1'234.56` → `BigDecimal("1234.56")`; mit ≥30 Unit-Tests inkl. negativer Beträge, Edge-Cases (`-1'234.56`, `0.05`, leerer String) |
| Duplikaterkennung deterministisch | Hash aus `(date, amount, recipient_normalized, account)`; US-4: "bereits importiert"-Warnung; Integration-Test mit identischem PDF 2x importieren |
| Safe-to-Spend als reine Funktion | Pure Function ohne Seiteneffekte; Inputs explizit (`income`, `fixedCostsMonthly`, `spentThisMonth`, `weeksRemaining`); Property-Based-Tests (jqwik): "S2S × Wochen + Ausgaben + Fixkosten = Einkommen" als Invariante |
| Intervall-Normalisierung (US-3) | Quartal÷3, Jahr÷12 in einem `FixedCostNormalizer`; Tests pro Intervall mit Datensatz aus User Story |
| Edge-Cases als Akzeptanztest | "≥7 Tage Restmonat", "Fixkosten ≥ Einkommen", "kein Einkommen erfasst" (US-6) — je 1 Cucumber/JUnit-Test, blockiert Merge bei Regression |
| Claude-Fallback ohne Datenverlust (US-9) | `CategorizationService` returnt bei `AnthropicException` "Sonstiges" + persistiertes `needs_review`-Flag; Monatsbericht zeigt letzten erfolgreichen Bericht mit Datum statt 500-Fehler |
| Flyway-Migrationen | Schema-Änderungen versioniert; CI-Pipeline führt Migration auf Test-DB aus, dann Roundtrip-Tests — verhindert "lokal funktioniert, prod kaputt" |
| Golden-File-Tests PDF-Parser | Pro Bank (UBS/Raiffeisen/PostFinance) mind. 3 anonymisierte PDFs + erwartetes JSON checked-in; jede Parser-Änderung muss Golden Files matchen |
| Monitoring der Quoten | Metriken: `pdf_extraction_accuracy`, `categorization_correction_rate`; Alert wenn unter Threshold der US (95% / 80%) |

**Konsequenz bei Ignorieren:**
Zwei realistische Szenarien:
- **Stiller Bug:** PDFBox-Parser liest `1'234.56` als `1.23456` (kein Apostroph-Strip). User sieht Safe-to-Spend von 8.456 CHF, geht shoppen, ist auf dem Konto 1.200 CHF im Minus. Einmal passiert → App gelöscht, 1-Stern-Review "die App hat mich ins Minus geschickt".
- **Wahrgenommener Bug:** Doppel-Import wegen fehlender Duplikaterkennung. User sieht Restaurant-Kategorie 640 CHF, weiss aber: er war nur 1× essen für 80 CHF. Vertrauen weg, auch wenn das Backend "nur" doppelt gezählt hat.

In beiden Fällen ist Reliability der QA, der am direktesten am Sichtbaren scheitert — und damit am direktesten am Geschäftsmodell.

---

## Zusammenfassung

Die drei QAs spannen ein Dreieck auf, das genau dem Geschäftsmodell entspricht:

- **Security** schützt davor, dass uns die Zielgruppe (Marc) vertraut.
- **Usability** sorgt dafür, dass die Zielgruppe (Lara) überhaupt zum Vertrauenstest kommt.
- **Reliability** sorgt dafür, dass das Vertrauen über die Zeit hält.

Fällt einer davon weg, fallen die anderen zwei mit — keine Sicherheit nützt, wenn niemand die App nutzt; keine Usability nützt, wenn die Zahlen falsch sind; keine Korrektheit nützt, wenn die Daten geleakt werden.
