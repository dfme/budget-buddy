# Sprint-7-Vorschlag — BudgetBuddy

**Team:** 3 Entwickler (Annahme, wie Sprint 4–6) · **Neu-Commitment:** 18 SP · **Carryover:** 14 SP · **Sprint-Nr.:** 7

## Sprint-Ziel

> **Die zwei offenen Review-Threads aus Sprint 6 (US-02 Konto löschen, US-08 Abo-Übersicht) werden abgeschlossen**, die Kategorienliste wächst auf 17 Einträge inkl. Icons (US-05), und eine Reihe kleinerer Frontend-Bugs sowie CI-/Datenqualitäts-Härtungen werden erledigt — bei deutlichem freiem Kapazitätspuffer (≈31 SP), den das Team gezielt für die überfällige US-09-Zuschnitt-Session nutzen sollte, statt ihn mit Nice-to-have-Arbeit zu füllen.

## Ausgangslage

- **Sprint 6 lief 2026-09-02 bis 2026-09-16** (67 SP erledigt). Keine Iteration für Sprint 7 ist im
  Board-Feld angelegt — analog zur Sprint-6-Planung (`iterations: []`). Muss vor Schritt 5 über das
  **Board-UI** angelegt werden, nicht per API — siehe Warnung unten.
- **Kein klassisches „In Progress"-Carryover**, aber vier Issues hängen aus Sprint 6 nach (14 SP):
  - [#299](https://github.com/dfme/budget-buddy/issues/299) und
    [#255](https://github.com/dfme/budget-buddy/issues/255) stehen auf `Review` — PR
    [#307](https://github.com/dfme/budget-buddy/pull/307) für #255 ist bereits offen. Realer
    Restaufwand ist Review/Merge, nicht Neuentwicklung.
  - [#233](https://github.com/dfme/budget-buddy/issues/233) und
    [#256](https://github.com/dfme/budget-buddy/issues/256) waren für Sprint 6 eingeplant
    (`Sprint = Sprint 6`), stehen aber weiterhin auf `Todo` — nie begonnen. Kein Restaufwand-Rabatt,
    volle SP zählen.
- **Backlog ist ungewöhnlich dünn:** ausserhalb des Carryovers stehen nur 14 offene Issues
  (Backlog + Todo zusammen, 31 SP) zur Verfügung. Selbst mit allen 14 plus Carryover sind es 45 SP
  gegen 63 SP Kapazität — **18 SP Puffer**, auch wenn nichts bewusst zurückgestellt wird. Dieser
  Vorschlag stellt zusätzlich zwei grosse, treiberlose Migrations-Evaluationen zurück (siehe unten),
  was den Puffer auf ~31 SP vergrössert.
- **US-09 (KI-Monatsbericht) hat weiterhin keine einzige Issue.** In Sprint 6 bewusst
  zurückgestellt mit der Absicht, den E-Mail-Versand-Entscheid während Sprint 6 zu klären und Issues
  für Sprint 7 vorzubereiten — das ist laut Board nicht passiert. Das referenzierte
  [`us-08-09-12-breakdown.md`](../us-08-09-12-breakdown.md) deckt aktuell nur noch US-08/US-12 ab,
  nicht mehr US-09. **Das ist die grösste offene Lücke** und der Grund für den bewusst grossen
  Kapazitätspuffer in diesem Vorschlag — siehe Risiko 3.
- **Keine offene `P0 - Critical`.**
- **Board-Hygiene:** Story Points und Area waren für 8 Issues offen (#276, #256, #264, #266, #270,
  #295, #306, #308) — anhand vergleichbarer abgeschlossener Issues geschätzt und nach Bestätigung
  im Board eingetragen (siehe Tabelle unten). Bei einem Issue (#266) spannt die Arbeit laut eigener
  Beschreibung Backend **und** Frontend; `Area` wurde auf `Backend` gesetzt, da dort die Quelle der
  Wahrheit (Enum + Claude-Schema) liegt — Frontend-Anteil (Icons) ist im selben Issue enthalten.
- **Plan-Index war veraltet** (`BE-PDF-14`, `BE-STS-05` fehlten aktuelle Zeilen) — behoben via
  `scripts/plans-index.sh`, geht mit in den Sprint-Commit.
- **Dependencies:** Alle `blocked_by`-Relationships der Kandidaten zeigen auf bereits `CLOSED`e
  Issues — keine Blockade ausser der internen Carryover-Kette `#256` → `#255`.

### Velocity

| Sprint | Dauer | Erledigte SP | Anmerkung |
| ------ | ----- | ------------ | --------- |
| 1 | 14 Tage | 24 | Team-Ramp-up |
| 2 | 11 Tage | 28 | verkürzter Sprint |
| 3 | 14 Tage | 67 | Ausreisser: Carryover + ungeplante FE-UI-Folgearbeit |
| 4 | 14 Tage | 54 | MVP-Abschluss, kein Carryover, straff gepackt |
| 5 | 14 Tage | 64 | Kompletter einplanbarer Backlog abgedeckt, kein Carryover |
| 6 | 14 Tage | 67 | Notification-Fundament, US-08, US-12 live; kein Puffer eingeplant |

Schnitt der letzten 3 Sprints (4–6): 62 SP. Schnitt der letzten 4 (3–6): **63 SP** — bestätigte
Kapazität für diesen Vorschlag. Anders als Sprint 6 (66 SP Commitment, kein Puffer) liegt das
**Neu-Commitment mit 18 SP weit darunter**, weil das Backlog aktuell schlicht keine weiteren
einplanbaren Issues hergibt — kein bewusster Puffer-Entscheid, sondern eine Datenlage, die im
Planning besprochen werden sollte (siehe Risiko 3).

## Sprint-Backlog (Carryover 14 SP + Neu 18 SP = 32 SP)

### 1. Carryover aus Sprint 6 (14 SP)

| Issue | Titel | SP | Prio | Abhängig von | Bereit wann |
| ----- | ----- | -- | ---- | ------------ | ----------- |
| [#299](https://github.com/dfme/budget-buddy/issues/299) | FE-SET-05 Konto löschen: Aktion in den Einstellungen | 3 | P1 | — | **sofort** (Review, PR offen) |
| [#255](https://github.com/dfme/budget-buddy/issues/255) | FE-REC-01 Abo-Übersicht-Screen | 5 | P1 | — | **sofort** (Review, PR #307 offen) |
| [#233](https://github.com/dfme/budget-buddy/issues/233) | BE-CAT-08 Restexposition im Claude-Prompt: Vorname + Händler-Telefonnummer | 3 | P2* | — | **sofort** |
| [#256](https://github.com/dfme/budget-buddy/issues/256) | E2E-REC-01 Playwright: Abo-Erkennung | 3 | P1 | #255 | nach #255-Merge |

`*` #233 trägt `us-05` (Must-Have → laut MoSCoW `P1`), steht aber auf `P2 - Medium` — nicht
angefasst (siehe Board-Hygiene-Regel), trotzdem eingeplant.

### 2. US-05 — Kategorisierung erweitern (3 SP)

| Issue | Titel | SP | Prio | Abhängig von | Bereit wann |
| ----- | ----- | -- | ---- | ------------ | ----------- |
| [#266](https://github.com/dfme/budget-buddy/issues/266) | BE-CAT-10 Vier neue Kategorien (Persönliches, Steuern, Bargeldbezug, Reisen) + Icon je Kategorie | 3 | P3* | — | **sofort** |

`*` trägt `us-05` (Must → `P1`), steht auf `P3 - Low` — grösste Priority-Abweichung im Backlog,
nicht angefasst, im Planning zur Bestätigung vorlegen.

### 3. Datenqualität & Performance (1 SP)

| Issue | Titel | SP | Prio | Abhängig von | Bereit wann |
| ----- | ----- | -- | ---- | ------------ | ----------- |
| [#270](https://github.com/dfme/budget-buddy/issues/270) | DB-10 Cleaner-Query auf import_jobs läuft als Sequential Scan | 1 | P3* | — | **sofort** |

`*` trägt `us-04` (Must → `P1`), steht auf `P3 - Low` — nicht angefasst.

### 4. Security-Hardening (2 SP)

| Issue | Titel | SP | Prio | Abhängig von | Bereit wann |
| ----- | ----- | -- | ---- | ------------ | ----------- |
| [#231](https://github.com/dfme/budget-buddy/issues/231) | BE-AUTH-12 Obergrenzen für String-Felder in den Auth-DTOs fehlen | 2 | P3 | — | **sofort** |

### 5. Frontend-Bugfixes (4 SP)

| Issue | Titel | SP | Prio | Abhängig von | Bereit wann |
| ----- | ----- | -- | ---- | ------------ | ----------- |
| [#194](https://github.com/dfme/budget-buddy/issues/194) | FE-UI-08 app-card lässt globales title-Attribut am Host stehen | 1 | P2 | — | **sofort** |
| [#264](https://github.com/dfme/budget-buddy/issues/264) | FE-AUTH-06 Register-Formular zeigt Backend-Fehlermeldung bei 400 nicht an | 1 | P2 | — | **sofort** |
| [#308](https://github.com/dfme/budget-buddy/issues/308) | FE-NOTIF-02 Glocke: Dropdown ausserhalb Viewport, Icon passt nicht | 2 | P1 | — | **sofort** |

### 6. CI-/Test-Infrastruktur-Härtung (8 SP)

| Issue | Titel | SP | Prio | Abhängig von | Bereit wann |
| ----- | ----- | -- | ---- | ------------ | ----------- |
| [#203](https://github.com/dfme/budget-buddy/issues/203) | INFRA-28 Veraltete Branches sind mergebar — CI ist kein Merge-Gate | 2 | P3 | — | **sofort** |
| [#276](https://github.com/dfme/budget-buddy/issues/276) | INFRA-39 Veralteter grüner CI-Haken lässt Merges durch, die main brechen | 3 | P3 | — | **sofort** |
| [#295](https://github.com/dfme/budget-buddy/issues/295) | INFRA-42 Frontend-Suite auf Node 26 lokal rot (localStorage) | 2 | P3 | — | **sofort** |
| [#306](https://github.com/dfme/budget-buddy/issues/306) | INFRA-43 Prettier-Config und Format-Check für e2e/ | 1 | P3 | — | **sofort** |

`#203` und `#276` behandeln denselben Grundmangel (ein grüner Check sagt nichts über den
Merge-*Ergebniszustand* gegen aktuellen `main` aus) aus zwei verschiedenen Blickwinkeln — inhaltlich
sinnvoll im selben Zug von derselben Person bearbeitet, keine formale Abhängigkeit.

**Neu-Commitment: 3 + 1 + 2 + 4 + 8 = 18 SP** · **Carryover: 14 SP** · **Gesamt: 32 SP** ✅
(63 SP Kapazität, 31 SP ungenutzt)

## Empfohlene Bearbeitungsreihenfolge

```
Dev A: #255 (Review→Merge, bereits zugewiesen) ─► #256 (nach Merge) ─► #270 ─► #194
Dev B: #299 (Review→Merge, bereits zugewiesen) ─► #233 ─► #266 ─► #264
Dev C: #203 ─► #276 (gleiches Thema, direkt danach) ─► #295 ─► #306 ─► #231 ─► #308
```

| Dev | Issues | SP |
| --- | ------ | -- |
| A | #255 (5) · #256 (3) · #270 (1) · #194 (1) | 10 |
| B | #299 (3) · #233 (3) · #266 (3) · #264 (1) | 10 |
| C | #203 (2) · #276 (3) · #295 (2) · #306 (1) · #231 (2) · #308 (2) | 12 |

- **Einzige Abhängigkeit im Sprint:** `#256` wartet auf den Merge von `#255` (beide Abo-Übersicht,
  US-08) — sinnvollerweise dieselbe Person, da der Kontext ohnehin da ist.
- **`#299` und `#255` sind bereits zugewiesen** (Review-Status, PRs teils offen) — Dev A/B sind hier
  Platzhalter für die Personen, die diese PRs bereits führen; die Zuordnung sollte im Planning an
  die realen Namen angepasst werden.
- **Dev C trägt mit 12 SP am meisten, aber ausschliesslich unabhängige Einzel-Issues** ohne
  Zwischenwartezeiten — durchgehend parallelisierbar ab Tag 1.
- **13 von 14 Issues haben keine offene Vorbedingung** und sind sofort startbar.

## Bewusst *nicht* in Sprint 7

| Bereich | SP | Warum verschoben |
| ------- | -- | ---------------- |
| [#156](https://github.com/dfme/budget-buddy/issues/156) INFRA-26 Spring Boot 4.x Migration bewerten | 8 | Unverändert seit Sprint 5/6: `TECH-STACK.md` schliesst Spring Boot 4 weiterhin explizit aus („milestone releases only"). Solange diese Entscheidung steht, ist eine Bewertung kein Sprint-Kandidat. |
| [#213](https://github.com/dfme/budget-buddy/issues/213) INFRA-30 Migration auf Angular 22.x bewerten | 5 | `P3 - Low`, reine Evaluation ohne unmittelbaren Nutzerwert, kein funktionaler Treiber laut Issue selbst. |

Zusätzlich ausserhalb dieser Tabelle, weil (noch) keine Issues existieren: **US-09
(KI-Monatsbericht)** — siehe Risiko 3, das ist der eigentliche Grund für den grossen freien Puffer
in diesem Vorschlag.

Damit deckt dieser Vorschlag 14 von 16 offenen Issues (32 von 45 SP unter den offenen Issues) ab.

## Risiken & Gegenmassnahmen

1. **`#256` hängt an `#255`-Merge.** Verzögert sich der Review von PR #307, steht der
   E2E-Abschluss der Abo-Erkennung still.
   → *Gegenmassnahme:* `#255`/`#299` haben für die zuständige Person Vorrang vor Neuarbeit — beide
   sind faktisch fast fertig (Review-Status), sollten in den ersten Tagen gemerged sein.

2. **18 SP Neu-Commitment gegen 63 SP Kapazität — der Sprint ist strukturell unterbucht,** nicht
   aus einer bewussten Puffer-Entscheidung heraus, sondern weil das Backlog aktuell keine weiteren
   einplanbaren Issues hergibt.
   → *Gegenmassnahme:* Nicht mit weiteren P3-Migrations-Evals (#156/#213) auffüllen, nur um die
   Kapazität auszuschöpfen — stattdessen die freie Zeit gezielt für Risiko 3 nutzen.

3. **US-09 (KI-Monatsbericht) hat nach zwei Sprints in Folge (6 und 7) weiterhin null Issues.**
   Das war bereits Sprint-6-Risiko 4 mit der Gegenmassnahme, den E-Mail-Versand-Entscheid während
   Sprint 6 zu klären — das ist laut Board nicht passiert, und das referenzierte Zuschnittsdokument
   deckt US-09 aktuell nicht mehr ab.
   → *Gegenmassnahme:* Den E-Mail-Provider-Entscheid und den US-09-Zuschnitt als expliziten,
   zeitlich budgetierten Programmpunkt **zu Beginn von Sprint 7** ansetzen (der 31-SP-Puffer trägt
   das), damit Sprint 8 nicht zum dritten Mal ohne vorbereitete Issues startet.

4. **Priority-Abweichungen bei drei eingeplanten Issues** (#233, #266, #270 — alle Must-Have laut
   MoSCoW, aber P2/P3 gesetzt) — nicht angefasst, aber möglicherweise ein Board-Pflegefehler statt
   bewusster Entscheidung.
   → *Gegenmassnahme:* Im Planning kurz bestätigen, ob die niedrigeren Werte gewollt sind.

## Board-Hygiene — Befunde aus dieser Planung

| Befund | Issues | Empfehlung |
| ------ | ------ | ---------- |
| **Priority-Abweichung (Einzelfälle, Must → P2/P3 statt P1)** | #233, #266, #270 | Nicht angefasst. Alle drei trotzdem im Vorschlag, siehe Risiko 4. |
| **Priority-Abweichung (Should → P1 statt P2)** | #256, #308, #299, #255 | Nicht angefasst — konsistent mit Sprint 6 (dort dieselbe systematische Abweichung bei US-08/US-12). |
| **Priority leer, kein `us-*`-Label → keine automatische Ableitung möglich** | #276 | Team entscheidet im Planning; SP/Area wurden geschätzt und gesetzt, Priority bewusst offen gelassen. |
| `Story Points` / `Area` leer | #276, #256, #264, #266, #270, #295, #306, #308 | Geschätzt anhand vergleichbarer Done-Issues, nach Bestätigung im Board eingetragen (siehe Ausgangslage). |
| `Area` mehrdeutig | #266 (spannt laut eigener Beschreibung Backend + Frontend) | Auf `Backend` gesetzt (Quelle der Wahrheit: Enum + Claude-Schema); Frontend-Anteil (Icons) bleibt im selben Issue. |
| Plan-Index | `docs/plans/README.md` fehlten aktuelle Zeilen zu `BE-PDF-14`, `BE-STS-05` | Behoben in dieser Planung via `scripts/plans-index.sh` — geht mit in den Sprint-Commit. |
| **Keine Sprint-7-Iteration im Board-Feld** | — | `iterations: []` bei dieser Planung, wie schon bei Sprint 6. Muss vor Schritt 5 angelegt werden — **siehe Warnung unten**. |

### Warnung für Schritt 5: Iteration nur über das Board-UI anlegen

Unverändert seit Sprint 5/6 dokumentiert: eine neue Iteration lässt sich per API nur über
`updateProjectV2Field` mit `iterationConfiguration` anlegen — das schreibt die **gesamte
Feld-Konfiguration neu und vergibt für alle Iterationen (auch die bereits abgeschlossenen) neue
IDs**. Die Sprint-Zuordnung aller 275+ historischen Items zeigt danach auf die alten IDs und
erscheint leer, bis sie aus einem vorher gezogenen Snapshot zurückgeschrieben wird.

**Für Sprint 7 gilt weiterhin:** Iteration im **Board-UI** anlegen (*+ Add iteration*, Start
2026-09-16, Dauer 14 Tage), nicht über die API. Falls die API doch nötig wird: vorher
`itemId → Sprint-Titel` aller Items sichern, nach der Mutation die neuen Iteration-IDs auslesen und
zurückschreiben.
