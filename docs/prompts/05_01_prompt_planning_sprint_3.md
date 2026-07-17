## Prompt

Wir wollen nun den Sprint 3 (Umfang ca. 30 Story Points) planen. Wir sind 3 Entwickler. Alle Issues sind in unserem BudgetBuddy Sprint Board Backlog. Sprint 1 und 2 haben wir bereits erfolgreich fertiggestellt. In den Issues sind auch die Abhänigkeiten hinterlegt und müssen beachtet werden.
Kannst du anhand der Issues im Backlog einen Sinnvollen Sprint 3 vorschlagen. Bitte nur als Markdown antworten und in der Datei @docs/prompts/05_01_prompt_planning_sprint_3.md unter Antwort ablegen.


## Antwort

# Sprint-3-Vorschlag — BudgetBuddy

**Team:** 3 Entwickler · **Neu-Commitment:** 28 SP (+ Carryover-Restaufwand) · **Sprint-Nr.:** 3

## Sprint-Ziel

> **Die PDF-Import-Pipeline läuft end-to-end: Kontoauszug hochladen → parsen → automatisch kategorisieren → Ergebnis anzeigen → nach Kategorie auswerten.** Damit liefern wir zum ersten Mal den Kern-Nutzen der App (US-04 + US-05). Parallel legen wir mit der UI-Design-Entscheidung und der Produktiv-Konfiguration (API-Key) das Fundament für die restlichen Frontend-Screens.

## Ausgangslage

- Sprint 1 & 2 abgeschlossen; **Auth-, DB- und Kategorisierungs-Fundament steht** (#5 DB-02, #6, #7, #9, #15 BE-CAT-02, #54–#57 FE-Auth — alle *Done*).
- **Zwei Carryover-Items aus Sprint 2** sind noch offen und binden das Team zu Sprint-Beginn:
  - `#16 BE-CAT-03` (Status *Review* — quasi fertig)
  - `#13 BE-PDF-01` (Status *In Progress* — der grosse PDFBox-Parser, SP 8)
  Beide sind direkte Voraussetzung für die PDF-Pipeline und deshalb bewusst Teil dieses Sprints.
- Drei Issues waren bereits für Sprint 3 vorgemerkt (#76, #78, #80) — Story Points sind jetzt ergänzt.

## Sprint-Backlog (30 SP)

### 1. Carryover abschliessen — *zuerst, entblockt alles Weitere* (nicht voll aufs Budget angerechnet)

Der Grossteil dieser beiden Items wurde bereits in Sprint 2 geleistet; hier steht nur noch der **Rest-Aufwand** an. Sie werden deshalb **nicht voll** auf die 28-SP-Kapazität angerechnet — laufen aber zu Sprint-Beginn mit und entblocken die Pipeline.

| Issue | Titel | SP (Original) | Abhängig von | Status |
| ----- | ----- | -- | ------------ | ------ |
| #16 | BE-CAT-03 HybridCategorizationService | 2 | #15 ✅ | Review → Done |
| #13 | BE-PDF-01 PDFBox-Parser für CH-Bank-PDFs | 8 | #5 ✅ | In Progress → Done |

### 2. PDF-Import-Pipeline end-to-end — *Sprint-Kern* (12 SP)

| Issue | Titel | SP | Abhängig von | Bereit wann |
| ----- | ----- | -- | ------------ | ----------- |
| #27 | FE-PDF-01 PDF-Upload Component | 3 | #2 ✅ | **sofort** (parallel) |
| #17 | BE-PDF-02 PdfImportService | 5 | #13, #16 | nach #13 + #16 |
| #18 | BE-PDF-03 `POST /import/pdf` Endpoint | 2 | #17 | nach #17 |
| #28 | FE-PDF-02 Ergebnis-Anzeige nach Import | 2 | #27, #18 | nach #27 + #18 |

→ Ergebnis: Upload → Parsing → Kategorisierung → Resultat sichtbar. **US-04 + US-05 (Auto-Teil) lauffähig.**

### 3. Fundament & vorgemerkte Issues — *parallel, unabhängig* (8 SP)

| Issue | Titel | SP | Abhängig von | Bereit wann |
| ----- | ----- | -- | ------------ | ----------- |
| #80 | FE-UI-01 UI-Design (3 Varianten, Mobile-First) | 5 | — | **sofort** |
| #78 | INFRA-12 SQLite-Persistenz-Doku korrigieren | 2 | — | **sofort** |
| #76 | INFRA-11 ANTHROPIC_API_KEY in Render | 1 | #15 ✅ | **sofort** (org. Entscheid) |

### 4. Kategorie-Auswertung — *verlängert die Pipeline* (8 SP)

Sobald Transaktionen importiert & kategorisiert sind, folgt die Auswertung — der nächste logische Schritt nach dem Import. Vervollständigt US-05 (manuelle Korrektur backend) und legt die Grundlage für die Dashboard-Visualisierung.

| Issue | Titel | SP | Abhängig von | Bereit wann |
| ----- | ----- | -- | ------------ | ----------- |
| #19 | BE-CAT-04 `PUT /transactions/{id}/category` | 2 | #5 ✅, #7 ✅ | **sofort** (parallel) |
| #20 | BE-CAT-05 `GET /transactions/summary` | 3 | #5 ✅ | **sofort** (parallel) |
| #30 | FE-CAT-01 Kategorie-Übersicht | 3 | #20 | nach #20 |

**Neu-Commitment: 12 + 8 + 8 = 28 SP** (+ Carryover-Restaufwand aus #13/#16, nicht voll angerechnet) ✅

## Empfohlene Bearbeitungsreihenfolge (nach Abhängigkeiten)

```
Woche 1                              Woche 2
─────────────────────────────────    ──────────────────────────────
Dev A: #16 (Review→Done) · #19 ──┐   #17 BE-PDF-02 ─► #18 BE-PDF-03
Dev B: #13 BE-PDF-01 ────────────┴─► ┘ (entblockt Pipeline) ─► #28
Dev C: #80 FE-UI-01 · #27 FE-PDF-01   #20 BE-CAT-05 ─► #30 FE-CAT-01
        (parallel: #76, #78 als Füller)
```

- **Kritischer Pfad:** `#13 → #17 → #18 → #28`. #13 (SP 8) ist das grösste Risiko — sollte in Woche 1 abgeschlossen sein, sonst rutscht die Pipeline.
- **Parallelisierbar ab Tag 1:** #27, #80, #78, #76 sowie **#19 und #20** (Kategorie-Auswertung) hängen von nichts Offenem ab → halten das Team ausgelastet, während #13/#16 laufen.
- **#30** folgt auf #20, **#28** auf #18 + #27 — beide realistisch in Woche 2.

## Bewusst *nicht* in Sprint 3

| Bereich | Warum verschoben |
| ------- | ---------------- |
| Fixkosten-Vertikale (#10–#12, #24–#26, 17 SP, US-03) | Vollständig unabhängig & bereit — passt aber nicht zusätzlich in 30 SP. **Erster Kandidat für Sprint 4.** |
| Safe-to-Spend (#21–#23, #33–#35) | `#21 BE-STS-01` hängt an `#11 BE-FC-02` → braucht zuerst die Fixkosten-Vertikale. |
| Kategorie-Visualisierung (#31 Pie-Chart, #32 manuelle Korrektur-UI) | Hängen an #30 bzw. #19 — beide in diesem Sprint fertiggestellt. Damit ist #31/#32 der **direkte Sprint-4-Einstieg** und profitiert vom gewählten UI-Design aus #80. |
| #58 E2E-AUTH-01 (3 SP) | Bereit, aber nicht sprint-kritisch → **Stretch-/Puffer-Item**, falls Kapazität frei wird. |

## Risiken & Gegenmassnahmen

1. **#13 (SP 8) auf dem kritischen Pfad.** Fällt es aus, steht #17/#18/#28. → *Gegenmassnahme:* #13 mit höchster Prio in Woche 1; falls es am Planungstag wackelt, PDF-Slice gegen die unabhängige **Fixkosten-Vertikale** (#10, #11, #12, #24 = 12 SP) tauschen — gleiche Punktzahl, kein Abhängigkeit von #13.
2. **INFRA-11 (#76) ist ein organisatorischer Blocker** (Account + Kostenträger), nicht technisch. → Früh im Sprint klären, sonst läuft die Kategorisierung in Prod nicht.
