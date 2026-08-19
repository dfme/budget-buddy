# Sprint-5-Vorschlag — BudgetBuddy

**Team:** 3 Entwickler · **Neu-Commitment:** 55 SP · **Kein Carryover** · **Sprint-Nr.:** 5

## Sprint-Ziel

> **Die Test-Schuld der MVP-Must-Haves wird geschlossen (E2E für US-03/04/05/06), der kritische Import-Blocker und der Safe-to-Spend-Rechenfehler werden behoben, und US-14 (Passwort, Einkommen, Erscheinungsbild ändern) geht vollständig live.** Damit ist der MVP nach Sprint 4 nicht nur funktional, sondern auch automatisiert gegen Regression abgesichert — und Nutzer können erstmals ihre Kontodaten selbst pflegen.

## Ausgangslage

- **Sprint 4 abgeschlossen** (54 SP, 21 Issues, alle *Done*), kein Carryover: kein Issue steht auf
  `In Progress` oder `Review`. Sauberer Start wie schon in Sprint 4.
- **Genau die Restmenge, die Sprint 4 bereits angekündigt hatte:** die vier E2E-Issues der
  Must-Haves (#58, #122–#125) waren als „kompletter, dann unblockierter Sprint-5-Einstieg"
  benannt — alle ihre externen `blocked_by`-Referenzen sind inzwischen geschlossen, keine hängt
  mehr an offener Arbeit.
- **US-14 (Einstellungen) ist neu im Backlog** seit Sprint 4 — 5 Issues (#176–#180), Should-have,
  bisher komplett ungeplant.
- **Ein P0-Bug ist offen:** #173 „Import eines Kontoauszugs schlägt zuverlässig fehl" — das
  einzige `P0 - Critical` auf dem Board, seit Sprint 4 unangetastet.
- **Fast der gesamte offene Backlog passt in diesen Sprint:** 25 von 26 offenen Issues (55 SP)
  sind sinnvoll einplanbar; nur #156 (Spring Boot 4.x Migration) bleibt aussen vor (siehe unten).

### Velocity

| Sprint | Dauer | Erledigte SP | Anmerkung |
| ------ | ----- | ------------ | --------- |
| 1 | 14 Tage | 24 | Team-Ramp-up |
| 2 | 11 Tage | 28 | verkürzter Sprint |
| 3 | 14 Tage | 67 | Ausreisser: Carryover + ungeplante FE-UI-Folgearbeit |
| 4 | 14 Tage | 54 | MVP-Abschluss, kein Carryover, straff gepackt |

Angesetzt: **60 SP** = Mittel der letzten zwei Sprints (Sprint 3+4 = 60.5), bestätigt im Planning.
Der komplette einplanbare Backlog liegt mit 55 SP darunter — es bleiben nur 5 SP Puffer (siehe
Risiko 1).

## Sprint-Backlog (55 SP)

### 1. Kritischer Bugfix — *zuerst, blockiert den Kern-Flow* (2 SP)

| Issue | Titel | SP | Prio | Abhängig von | Bereit wann |
| ----- | ----- | -- | ---- | ------------ | ----------- |
| [#173](https://github.com/dfme/budget-buddy/issues/173) | BE-PDF-08 Import eines Kontoauszugs schlägt zuverlässig fehl | 2 | **P0** | — | **sofort** |

Einziges `P0 - Critical` auf dem Board. Betrifft den Kern-Flow (PDF-Upload, US-04 Must-Have) —
Vorrang vor allem anderen Backend-Merge-Verkehr, analog zu #89 in Sprint 4.

### 2. E2E-Testabdeckung der Must-Haves — *komplett bereit, war der angekündigte Sprint-5-Einstieg* (15 SP)

| Issue | Titel | SP | Prio | Abhängig von | Bereit wann |
| ----- | ----- | -- | ---- | ------------ | ----------- |
| [#122](https://github.com/dfme/budget-buddy/issues/122) | E2E-PDF-01 Playwright: PDF-Upload | 3 | P1 | #91 ✅, #28 ✅, #29 ✅ | **sofort** |
| [#123](https://github.com/dfme/budget-buddy/issues/123) | E2E-FC-01 Playwright: Fixkosten-Wizard | 3 | P1 | #91 ✅, #10–12 ✅, #24–26 ✅ | **sofort** |
| [#124](https://github.com/dfme/budget-buddy/issues/124) | E2E-CAT-01 Playwright: Transaktionen kategorisieren | 3 | P1 | #91 ✅, #31 ✅, #32 ✅ | **sofort** |
| [#125](https://github.com/dfme/budget-buddy/issues/125) | E2E-STS-01 Playwright: Safe-to-Spend | 3 | P1 | #91 ✅, #21–23 ✅, #33–35 ✅, #102 ✅ | **sofort** |
| [#58](https://github.com/dfme/budget-buddy/issues/58) | E2E-AUTH-01 Playwright: Register→Login→Logout | 3 | P2 | #54–57 ✅ | **sofort** |

Deckt CLAUDE.mds Testing-Vorgabe ab: 1 Happy Path + 1 Fehlerpfad pro Must-Have-Story
(US-03/04/05/06). Alle fünf sind seit dieser Planung ohne offene Vorbedingung — reine
Kapazitätsfrage, kein Abhängigkeitsproblem.

### 3. US-14 Einstellungen — *komplett, neue Should-have-Story* (13 SP)

| Issue | Titel | SP | Abhängig von | Bereit wann |
| ----- | ----- | -- | ------------ | ----------- |
| [#176](https://github.com/dfme/budget-buddy/issues/176) | BE-AUTH-09 Passwort-Änderung-Endpoint | 3 | — | **sofort** |
| [#177](https://github.com/dfme/budget-buddy/issues/177) | FE-SET-01 Einstellungen-Screen: Route und Navigation | 2 | — | **sofort** |
| [#178](https://github.com/dfme/budget-buddy/issues/178) | FE-SET-02 Passwort ändern | 3 | #176, #177 | nach #176 + #177 |
| [#179](https://github.com/dfme/budget-buddy/issues/179) | FE-SET-03 Einkommen manuell erfassen und ändern | 2 | #177 | nach #177 |
| [#180](https://github.com/dfme/budget-buddy/issues/180) | FE-SET-04 Erscheinungsbild: Hell, Dunkel, System | 3 | #177 | nach #177 |

**#177 ist die Verzweigung dieses Blocks** — an ihm hängen drei der vier Folge-Issues. Beide
Wurzel-Issues (#176, #177) sollten parallel und früh starten.

### 4. Weitere Bugfixes (10 SP)

| Issue | Titel | SP | Prio | Abhängig von | Bereit wann |
| ----- | ----- | -- | ---- | ------------ | ----------- |
| [#154](https://github.com/dfme/budget-buddy/issues/154) | BE-STS-04 Fixkosten werden im Safe-to-Spend doppelt abgezogen | 1 | P1 | — | **sofort** |
| [#159](https://github.com/dfme/budget-buddy/issues/159) | BE-PDF-07 Absender/Empfänger aus Detailzeilen wird beim Import verworfen | 3 | P2 | — | **sofort** |
| [#162](https://github.com/dfme/budget-buddy/issues/162) | BE-CAT-07 AnthropicStartupHealthCheckTest ordnungsabhängig | 2 | P3 | — | **sofort** |
| [#126](https://github.com/dfme/budget-buddy/issues/126) | INFRA-17 SPA-Routen per Deep-Link nicht erreichbar | 1 | P3 | — | **sofort** |
| [#142](https://github.com/dfme/budget-buddy/issues/142) | DB-07 Foreign Keys auf users ohne ON DELETE | 1 | P2 | — | **sofort** |
| [#172](https://github.com/dfme/budget-buddy/issues/172) | FE-FC-04 Fixkosten-Tabelle läuft auf schmalen Viewports über | 1 | P3 | — | **sofort** |

`#154` ist die höchste Priorität dieses Blocks (`P1`, betrifft US-06 Core Value direkt) und sollte
zuerst gezogen werden.

### 5. Tech-Debt & Kleinverbesserungen (15 SP)

| Issue | Titel | SP | Prio | Abhängig von | Bereit wann |
| ----- | ----- | -- | ---- | ------------ | ----------- |
| [#181](https://github.com/dfme/budget-buddy/issues/181) | FE-UI-07 Notice-Komponente: Icon und optionaler Titel | 3 | P2 | — | **sofort** |
| [#150](https://github.com/dfme/budget-buddy/issues/150) | INFRA-25 Fail-fast im prod-Profil bei fehlender Datasource-URL | 1 | P2 | — | **sofort** |
| [#134](https://github.com/dfme/budget-buddy/issues/134) | BE-CAT-06 Transaktionstext vor Claude-Call maskieren | 3 | P3* | — | **sofort** |
| [#114](https://github.com/dfme/budget-buddy/issues/114) | BE-AUTH-05 Vor-/Nachname im User-Model ergänzen | 3 | P3 | — | **sofort** |
| [#121](https://github.com/dfme/budget-buddy/issues/121) | INFRA-16 GitHub Actions auf Node-24-Runtime heben | 2 | P3 | — | **sofort** |
| [#129](https://github.com/dfme/budget-buddy/issues/129) | INFRA-19 Projekt-Skills sprachlich auf Deutsch vereinheitlichen | 2 | P3 | — | **sofort** |
| [#113](https://github.com/dfme/budget-buddy/issues/113) | INFRA-15 Prettier als npm-Script verdrahten | 1 | P3 | — | **sofort** |

`*` #134 trägt `us-05` (Must-Have → laut MoSCoW eigentlich `P1`), steht aber auf `P3 - Low` — siehe
Board-Hygiene-Befund unten. Dieser Block ist der **erste Streichkandidat**, falls der Sprint eng
wird (siehe Risiko 1).

**Neu-Commitment: 2 + 15 + 13 + 10 + 15 = 55 SP** ✅

## Empfohlene Bearbeitungsreihenfolge

```
Woche 1                                    Woche 2
──────────────────────────────────────     ──────────────────────────────────────
Dev A: #173 BE-PDF-08 (P0) ────────────►   #159 BE-PDF-07 ─► #162 BE-CAT-07
       #154 BE-STS-04 ─► #176 BE-AUTH-09           #150 INFRA-25 ─► #134 BE-CAT-06

Dev B: #177 FE-SET-01 ──────────────────►  #178 FE-SET-02 (nach #176+#177)
       #179 FE-SET-03 ─► #180 FE-SET-04            #181 FE-UI-07 ─► #114 BE-AUTH-05

Dev C: #58 E2E-AUTH-01 ─► #122 E2E-PDF-01 ► #123 E2E-FC-01 ─► #124 E2E-CAT-01
       #125 E2E-STS-01                              #126 · #142 · #172 · #121 · #129 · #113
```

| Dev | Issues | SP |
| --- | ------ | -- |
| A | #173 (2) · #154 (1) · #176 (3) · #159 (3) · #162 (2) · #150 (1) · #134 (3) · #121 (2) | 17 |
| B | #177 (2) · #178 (3) · #179 (2) · #180 (3) · #181 (3) · #114 (3) · #129 (2) | 18 |
| C | #58 (3) · #122 (3) · #123 (3) · #124 (3) · #125 (3) · #126 (1) · #142 (1) · #172 (1) · #113 (1) | 19 |

- **Kein durchgehender kritischer Pfad wie in Sprint 4.** Die tiefste Kette ist 2 Stufen
  (`#176/#177 → #178`) — der Sprint ist überwiegend parallelisierbar. Deutlich entspannter als
  Sprint 4s 6-stufige STS-Vertikale.
- **#173 (P0) zuerst bei Dev A**, noch vor #154 — beide sind Backend-Bugs im Safe-to-Spend-/
  PDF-Umfeld und profitieren davon, auf demselben Kopf gelöst zu werden statt parallel angefasst
  zu werden.
- **#177 ist die einzige echte Verzweigung** — sollte an Tag 1 bei Dev B starten, damit #179/#180
  früh folgen können und #178 nicht auf beide Wurzeln warten muss.
- **Dev C trägt den kompletten E2E-Block** (15 SP) plus vier kleine Bugfixes/Tech-Debt-Posten als
  Lückenfüller — bewusst so geschnitten, weil die fünf E2E-Suiten thematisch zusammengehören und
  ein Kontextwechsel zwischen ihnen günstiger ist als zwischen fünf verschiedenen Devs.
- **Parallelisierbar ab Tag 1:** praktisch alles — 24 von 25 Issues haben keine offene
  Vorbedingung. Einzige Ausnahme: #178.

## Bewusst *nicht* in Sprint 5

| Bereich | SP | Warum verschoben |
| ------- | -- | ---------------- |
| #156 INFRA-26 Spring Boot 4.x Migration bewerten | — (keine SP) | CLAUDE.md schliesst Spring Boot 4 explizit aus („milestone releases only", bewusste Projekt-Risikoentscheidung). Solange diese Entscheidung steht, ist eine Bewertung kein Sprint-Kandidat. Empfehlung: entweder schliessen oder auf „nach MVP + Entscheidungsrevision" verschieben — keine SP-Schätzung vorgenommen. |

Damit ist der Vorschlag der **erste Sprint seit Sprint-Start, der den gesamten sinnvoll
einplanbaren Backlog abdeckt** (25 von 26 offenen Issues). Es gibt aktuell keinen weiteren
Bereich, der bewusst zurückgestellt wird.

## Risiken & Gegenmassnahmen

1. **Nur 5 SP Puffer bei 60 SP Kapazität (92 % Auslastung).** Kein einzelner Issue-Ausfall bringt
   den Sprint zum Kippen, aber mehrere kleine Verzögerungen summieren sich schnell.
   → *Gegenmassnahme:* Block 5 (Tech-Debt & Kleinverbesserungen, 15 SP) ist der definierte
   Streichblock — keiner der sieben Issues ist Pflicht für ein MoSCoW-Must/Should mit Ausnahme
   von #134 (dessen Einstufung ohnehin zu klären ist, siehe Befund 1 unten). Wird es eng: zuerst
   #113/#121/#129 (5 SP, reine Tooling-/Doku-Tasks ohne Nutzerwirkung) aus dem Sprint nehmen.

2. **#134s Priority-Diskrepanz ist eine offene Entscheidung, kein Fakt.** Steht aktuell auf `P3`,
   das `us-05`-Label sagt `P1`. Das Thema (Datenminimierung vor Claude-Call) adressiert Risiko #2
   aus CLAUDE.md (Liability & Compliance) und Marcs Datenschutz-Skepsis direkt.
   → *Gegenmassnahme:* Im Planning explizit entscheiden und den Wert setzen (nicht stillschweigend
   auf P3 belassen) — dieser Vorschlag rührt den Wert nicht an.

3. **US-14 ist komplett neu und ungetestet in der Schätzung** — anders als die anderen Blöcke gibt
   es noch keine abgeschlossenen Vergleichsissues aus früheren Sprints für diese Story.
   → *Gegenmassnahme:* #177 früh und isoliert liefern; zeigt sich dabei, dass die 13 SP zu knapp
   geschätzt sind, ist #180 (Erscheinungsbild, 3 SP, keine Abhängigkeit für andere Blöcke) der
   sauberste Streichkandidat.

4. **Dev C trägt mit 15 SP den kompletten E2E-Block ohne Verteilung auf mehrere Personen** — fällt
   Dev C aus, steht die gesamte Testabdeckung der Must-Haves still.
   → *Gegenmassnahme:* Die fünf E2E-Suiten sind unabhängig voneinander (unterschiedliche
   Vertikalen) und lassen sich bei Ausfall auf Dev A/B aufteilen, ohne dass eine Kette
   unterbrochen wird — anders als der STS-kritische-Pfad in Sprint 4.

## Board-Hygiene — Befunde aus dieser Planung

| Befund | Issues | Empfehlung |
| ------ | ------ | ---------- |
| **Priority-Abweichung** | **#134** steht auf `P3 - Low`, trägt aber `us-05` (Must-Have → `P1 - High`) | Nicht angefasst (Regel: gesetzte Werte bleiben). Zur Entscheidung ins Planning — siehe Risiko 2. |
| `Area` leer | #113, #114, #121, #122, #123, #124, #125, #129 | Unverändert seit Sprint 4. Die vier E2E-Issues (#122–125) sind vermutlich `DevOps` wie #58 — es gibt aber weiterhin keine `E2E`-Option im Feld. |
| `Story Points` leer | #156 INFRA-26 | Kein Schätzvorschlag, da kein Sprint-5-Kandidat (siehe „Bewusst nicht in Sprint 5"). |
| **Keine Sprint-5-Iteration im Board-Feld** | — | `iterations: []` bei dieser Planung — Sprint 4 endete am 2026-08-19 (heute), es gibt weder eine aktive noch eine kommende Iteration. Muss vor Schritt 5 angelegt werden — **siehe Warnung unten**. |
| Priority-Lücken | keine | Alle 26 offenen Issues haben einen Priority-Wert. |
| Plan-Index | `docs/plans/README.md` war veraltet (2 Titel) | Behoben in dieser Planung via `scripts/plans-index.sh` — geht mit in den Sprint-Commit. |

### Warnung für Schritt 5: Iteration nur über das Board-UI anlegen

Sprint 4s eigene Planung dokumentiert einen Fallstrick, der für Sprint 5 genauso gilt: eine neue
Iteration lässt sich per API nur über `updateProjectV2Field` mit `iterationConfiguration` anlegen —
das schreibt die **gesamte Feld-Konfiguration neu und vergibt für alle Iterationen (auch die
bereits abgeschlossenen) neue IDs**. Die Sprint-Zuordnung aller 50+ historischen Items zeigt danach
auf die alten IDs und erscheint leer, bis sie aus einem vorher gezogenen Snapshot zurückgeschrieben
wird — genau das ist beim Anlegen von Sprint 4 passiert und mit knapper Not vollständig
wiederhergestellt worden.

**Für Sprint 5 gilt weiterhin:** Iteration im **Board-UI** anlegen (*+ Add iteration*), nicht über
die API. Falls die API doch nötig wird: vorher `itemId → Sprint-Titel` aller Items sichern, nach
der Mutation die neuen Iteration-IDs auslesen und zurückschreiben.
