## Prompt

Wir wollen nun den Sprint 2 (Umfang ca. 30 Story Points) planen. Wir sind 3 Entwickler. Alle Issues sind in unserem BudgetBuddy Sprint Board Backlog. Den Sprint 1 haben wir bereits erfolgreich fertiggestellt. In den Issues sind auch die Abhänigkeiten hinterlegt und müssen beachtet werden.
Kannst du anhand der Issues im Backlog einen Sinnvollen Sprint 2 vorschlagen. Bitte nur als Markdown antworten und in der Datei @docs/prompts/05_01_prompt_planning_sprint_2.md unter Antwort ablegen.


## Antwort

# Sprint 2 — Vorschlag (v2, inkl. BE-PDF-01)

**Kapazität:** 3 Entwickler · Ziel ~30 SP · **geplant: 32 SP**
**Ausgangslage:** Sprint 1 abgeschlossen (Infra-Skeleton, CI, DB V1–V3 `users`/`transactions`/`fixed_costs`, Backend-Auth `BE-AUTH-01/02/03`). Frontend-Auth und alle Fach-Features stehen noch offen.

**Änderung ggü. v1:** `BE-PDF-01` (#13, 8 SP) wird — wie gefordert — fest eingeplant. Damit der 8-SP-Parser nicht isoliert steht, wird das **gesamte Kategorisierungs-Backend** mitgezogen. Der Fixkosten-Track (US-03) weicht dafür nach Sprint 3; **US-01-Auth bleibt als demonstrierbarer Slice**.

## Sprint-Ziel

> **Die App bekommt ihre Nutzer-Front-Tür und ihr Backend-„Gehirn":** Ein Nutzer kann sich registrieren, einloggen und ausloggen (US-01, End-to-End demoable). Parallel entstehen die **beiden technisch riskantesten Bausteine** — der PDF-Parser für Schweizer Bank-Auszüge und die vollständige Hybrid-Kategorisierung (Lookup + Claude) — als je einzeln getestete Backend-Komponenten. Dazu geht die CD-Pipeline live (INFRA-06).

Begründung: `BE-PDF-01` (PDFBox, Multiline-Parsing, `1'234.56`-Format, Passwortschutz) und `BE-CAT-02` (Claude-API-Anbindung) sind die **grössten Unbekannten** des Projekts (Churn-Risiko #1). Sie jetzt zu bauen und per JUnit abzusichern, **de-riskt Sprint 3**, in dem sie zu einem funktionierenden Import-Endpoint (`BE-PDF-02/03`) zusammengeführt werden.

## Ausgewählte Issues (32 SP)

| # | Task | US | SP | Depends on | Status Dependency |
|---|------|----|----|-----------|-------------------|
| #39 | INFRA-06 · CD Render Deploy Hook | — | 1 | #3, #37, #38 | ✅ Sprint 1 |
| #53 | FE-AUTH-01 · AuthService (Signal-State) | US-01 | 3 | — (BE-Auth fertig) | ✅ |
| #54 | FE-AUTH-02 · Login-Component | US-01 | 3 | #53 | ⏳ Sprint 2 |
| #55 | FE-AUTH-03 · Register-Component | US-01 | 3 | #53 | ⏳ Sprint 2 |
| #56 | FE-AUTH-04 · authGuard + 401-Redirect | US-01 | 2 | #53 | ⏳ Sprint 2 |
| #57 | FE-AUTH-05 · Logout-Button | US-01 | 2 | #53 | ⏳ Sprint 2 |
| **#13** | **BE-PDF-01 · PDFBox-Parser (CH-Bank-PDFs)** | US-04 | **8** | #5 | ✅ Sprint 1 |
| #7 | DB-04 · Flyway V4 `category_lookup` + Seed | US-05 | 2 | #1 | ✅ Sprint 1 |
| #14 | BE-CAT-01 · CategorizationPort + LookupTableService | US-05 | 2 | #7 | ⏳ Sprint 2 |
| #15 | BE-CAT-02 · ClaudeCategorizationService | US-05 | 5 | #14 | ⏳ Sprint 2 |
| #16 | BE-CAT-03 · HybridCategorizationService | US-05 | 2 | #15 | ⏳ Sprint 2 |
| | **Summe** | | **32** | | |

- **US-01 (Konto/Login, *Should*)** — vollständig Frontend: #53, #54, #55, #56, #57 (13 SP) → demoable
- **US-04 (PDF-Import, *Must*)** — Fundament: #13 BE-PDF-01 (8 SP)
- **US-05 (Kategorisierung, *Must*)** — Backend vollständig: #7, #14, #15, #16 (11 SP)
- **INFRA-06 (DevOps)** — #39 (1 SP), war bereits als *Sprint 2* getaggt

## Verteilung auf 3 Entwickler (~11 SP je Person)

Zwei Sub-Chains im Backend (Kategorisierung sequentiell, PDF-Parser unabhängig) + Auth-Frontend. Kritische Vorleistungen (`#53` AuthService, `#7`→`#14`) liegen am Ketten-Anfang.

**Dev A — Auth-Frontend (10 SP)**
`#53 (3)` → dann parallelisierbar `#54 (3)`, `#56 (2)`, `#57 (2)`.
→ `#53` zuerst, da es alle FE-AUTH-Tasks (auch `#55` bei Dev B) entblockt.

**Dev B — PDF-Parser + Auth-Register (11 SP)**
`#13 BE-PDF-01 (8)` (sofort startbar, unabhängig) → `#55 FE-AUTH-03 (3)` (sobald `#53` steht).
→ Der 8-SP-Brocken bekommt bewusst früh einen dedizierten Entwickler.

**Dev C — Kategorisierungs-Backend + CD (11 SP)**
`#39 (1)` (unabhängig, Wave 0) → `#7 (2)` → `#14 (2)` → `#15 (5)` → `#16 (2)`.
→ Vollständige Hybrid-Kategorisierung als testbare Einheit.

### Abhängigkeits-Reihenfolge (kritischer Pfad)

```
Wave 0:  #39 (CD)   #53 (AuthService)   #13 (PDF-Parser)   #7 (DB-04)
                          │                                    │
Wave 1:        ┌──────────┼──────────┐                       #14 (CAT-01)
             #54  #55*   #56   #57                             │
                                                             #15 (CAT-02)
Wave 2:                                                         │
                                                             #16 (CAT-03)
   * #55 liegt bei Dev B, hängt an #53 (Dev A)
```

## Bewusst zurückgestellt (Sprint 3)

| Grund | Issues |
|-------|--------|
| Baut direkt auf diesem Sprint auf: Parser (#13) + Hybrid-Kategorisierung (#16) → funktionierender Import-Endpoint | #17 BE-PDF-02, #18 BE-PDF-03, #19 BE-CAT-04, #20 BE-CAT-05 |
| US-03 Fixkosten (Must, fully unblocked) — bewusst zugunsten des PDF/CAT-Fundaments verschoben | #10, #11, #12 (BE) · #24, #25, #26 (FE) |
| Safe-to-Spend hängt an Fixkosten + Transaktionen | #21, #22, #23 (BE) · #33, #34, #35 (FE) |
| Frontend zu PDF/Kategorisierung — braucht die Endpoints aus Sprint 3 | #27–#32 |
| E2E-Auth — als *Should*-Story-E2E freiwillig; Stretch-Kandidat | #58 (E2E-AUTH-01) |

**Stretch Goal** (falls Kapazität frei wird): `#58 E2E-AUTH-01` (3 SP) sichert den US-01-Flow dauerhaft ab (DoD-Kriterium 4).

## Trade-off (bewusst getroffen)

Diese v2 priorisiert **Risiko-Frontloading** (PDF + AI-Kategorisierung) über **Feature-Vollständigkeit**. Konsequenz: Am Sprint-Ende ist **nur US-01 klickbar demoable**; US-04/US-05 sind „nur" per Test/Swagger vorführbar (noch kein UI). Wer stattdessen zwei sichtbare Features will, müsste `BE-PDF-01` splitten oder in Sprint 3 schieben — was hier explizit **nicht** gewünscht ist.
