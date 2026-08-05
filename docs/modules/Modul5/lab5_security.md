# AI Agent Risk Classification — Budget Buddy - Finanzberater anhand von Kontoauszüge

_Teaching version · scored 2026-08-05_


## Produkt-Einstufung

Für jede Achse: höchste Stufe wählen, die ehrlich zutrifft
- SURFACE: 2 - Die Abfragen sind sehr statisch und der Benutzer kann die nicht beeinflussen.
- DATA: 4 - Es werden Kontoauszüge von Kunden eingelesen.
- AUTONOMY: 3 - Neben der statischen Kategorieserung wird auch KI von Anthropic als Fallback verwendet. Diese Verzweigung geschiet aber in der App.
- EXPOSURE: 3 - KI von Anthropic wird verwendet und ist in der USA.

### Summary

> **RISK TIER: CRITICAL**
> Gate: Explicit sign-off + human-in-the-loop

| Axis | Score | Rating |
| --- | --- | --- |
| Surface | 2 | Uses knowledge sources |
| Data | 4 | Payment, prices & special category |
| Autonomy | 3 | Chooses within limits |
| Exposure | 3 | Outside our jurisdiction |

**How the tier was derived:** Base level 3 (highest axis = 3). Combinations fired: acts on its own × sensitive data; external knowledge × acts on its own; sensitive data × leaves our jurisdiction (+3) → level 4. Red line triggered: Payment or card data is put into the model's context (prompt or conversation history) → Critical (bypasses arithmetic).

### Required Controls & Obligations

#### Always on — never switched off

- Prompt injection and jailbreak detection
- Harmful content filter
- Protection of system instructions and tool names
- AI disclosure to the customer

#### If a safety check is to be switched off

- Red-line checks cannot be relaxed at all. Anything else needs explicit sign-off.
- At this level an exception is a decision with a name on it, not a configuration change.

#### Compliance obligations

- AI disclosure to the customer (EU AI Act, Art. 50)
- Interaction documented
- Personal data in the prompt documented (GDPR)
- Security-relevant events logged
- Data-protection assessment checked (DPIA needed?)
- Least-privilege access to tools and data
- Payment data never reaches the model (PCI)
- DPIA / FRIA completed
- Human confirms the action

#### Responsibilities

- Documented and proceed — no separate approval
- Security review before merge
- Named owner
- Pass/fail test before go-live
- Kill-switch verified
- Explicit security sign-off before go-live
- Documented, logged, assignable

### Massnahme: Datenminimierung an der Port-Grenze

**Befund:** Die Red Line ist keine Theorie — [`PdfImportService`](../../../backend/src/main/java/com/budgetbuddy/transaction/PdfImportService.java#L118) übergibt `tx.fullText()`, also den kompletten Buchungstext aus dem Kontoauszug, und der landet unverändert im [Prompt](../../../backend/src/main/java/com/budgetbuddy/categorization/ClaudeCategorizationService.java#L141). Bei Schweizer Auszügen steckt in diesem Feld je nach Buchungsart auch Gegenpartei-Name, IBAN, Referenznummer oder maskierte Kartennummer.

**Massnahme:** Ein Sanitizer direkt vor dem Claude-Call, im `CategorizationPort`-Pfad. Der Port ist bereits der einzige Engpass (ADR-6) — es gibt genau eine Stelle, die man dichtmachen muss:

- Nur der Händler-/Zwecktoken geht raus, nicht `fullText()`
- IBAN, Karten-/Kontonummern, Personennamen, Referenznummern werden per Regex ersetzt
- **Der Betrag geht gar nicht raus** — für eine Kategorisierung wird er nicht gebraucht, und er ist das, was die Red Line „Payment data" auslöst

Das ist die einzige Massnahme mit Hebel auf die Tier-Herleitung: Sie entfernt den Red-Line-Trigger, der die Arithmetik überstimmt. Ehrlich dazugesagt: Die Achse `DATA: 4` bleibt trotzdem 4, weil die App die Auszüge weiterhin verarbeitet und speichert — die Massnahme begrenzt, *wohin* die Daten fliessen, nicht dass es sie gibt.

Zwei Dinge, die zur selben Massnahme gehören und heute offen sind:

1. **Logging** — der volle Transaktionstext steht in vier Log-Statements in [`ClaudeCategorizationService`](../../../backend/src/main/java/com/budgetbuddy/categorization/ClaudeCategorizationService.java#L91-L93) (Zeilen 91–93, 102–103, 154–155, 162–163). Personendaten in Applikations-Logs sind derselbe Befund über einen zweiten Kanal, und die Pflicht „Security-relevant events logged" meint das Ereignis, nicht die Nutzdaten.
2. **AVV + Zero Data Retention mit Anthropic** — adressiert `EXPOSURE: 3` und die Prozess-Red-Line „Dienst ohne Vertrag / ohne geklärte Verarbeitung". Ohne das bleibt die Bekanntgabe ins Ausland nach nDSG Art. 16/19 ungedeckt, egal wie gut der Sanitizer ist.

Der Rest der Pflichtenliste (Consent-Text in US-02 um Anthropic/USA ergänzen, DSFA, Kill-Switch als `anthropic.enabled=false`) ist Dokumentations- und Config-Arbeit — nötig, aber ohne die Minimierung nur Papier.


## Prozess-Einstufung

Für jede Achse: höchste Stufe wählen, die ehrlich zutrifft
- SURFACE: 3 - Darf Befehle ausführen in Github (Issues erstellen oder ändern).
- DATA: 2 - Wir verwenden auschliesslich synthetische Tesdaten (Kontoauszüge PDF).
- AUTONOMY: 2 - Wir implementieren im Plan Mode (im skill `implement-issue` so festgehalten) und erst danach nach einem OK des Devs wird umgesetzt. PRs werden durch die Devs approved und auch gemerged.
- EXPOSURE: 3 - Kostenloses Conumser Tool (Github, Render, Anthropic, etc.)

### Red Lines für den Prozess — ehrlich durchgehen:
- [ ] Echte Kunden- oder Produktionsdaten sind schon einmal in einem Prompt gelandet
- [ ] Secrets, Tokens oder Zugangsdaten waren im Kontext (auch versehentlich, auch kurz)
- [x] Code oder Daten gingen an einen Dienst ohne Vertrag / ohne geklärte Verarbeitung
- [x] Etwas wurde gemergt oder deployt, das niemand gelesen hat
