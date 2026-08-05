# Sprint-4-Vorschlag — BudgetBuddy

**Team:** 3 Entwickler · **Neu-Commitment:** 47 SP · **Kein Carryover** · **Sprint-Nr.:** 4

## Sprint-Ziel

> **Der MVP ist funktional vollständig: alle vier Must-Have-Stories laufen end-to-end — Fixkosten erfassen (US-03), PDF importieren (US-04), Transaktionen kategorisieren und korrigieren (US-05), Safe-to-Spend sehen (US-06).** Gleichzeitig zieht die Produktion auf eine persistente Datenbank um (Neon Postgres), damit die Daten den nächsten Spin-Down überleben.

Nach diesem Sprint ist die App das erste Mal so benutzbar, wie Lara und Marc sie in den Personas
beschrieben bekommen: Fixkosten einmal erfassen, Kontoauszug hochladen, wöchentlichen
Safe-to-Spend-Betrag ablesen.

## Ausgangslage

- **Sprint 1–3 abgeschlossen**, kein Carryover: kein Issue steht auf `In Progress` oder `Review`.
  Der Sprint startet mit leerem Tisch — das ist die Ausnahme, nicht die Regel.
- **Fundament steht vollständig:** Auth (#8, #9, #46, #53–#57), DB-Migrationen V01–V04 (#4–#7),
  Kategorisierung (#14–#16, #19, #20), PDF-Pipeline (#13, #17, #18, #27, #28, #30),
  Playwright-Setup (#91) und das komplette UI-Fundament Variante A (#80, #99–#104) — alle *Done*.
- **Damit ist erstmals fast alles bereit:** Von den 16 Sprint-Kandidaten hängen nur noch 10 an
  anderen offenen Issues; #89, #10, #22, #24, #29, #31 und #32 könnten an Tag 1 starten.
- **Offener Restbestand nach Sprint 4:** 25 SP — 15 SP E2E-Tests (#58, #122–#125) und 10 SP
  Technik/Doku (#113, #114, #121, #126, #129, #134). Kein Must-Have mehr.

### Velocity

| Sprint | Dauer | Erledigte SP | Anmerkung |
| ------ | ----- | ------------ | --------- |
| 1 | 14 Tage | 24 | — |
| 2 | 11 Tage | 28 | verkürzter Sprint |
| 3 | 14 Tage | 67 | Ausreisser: ~10 SP Carryover (#13, #16) + ~16 SP ungeplante FE-UI-Folgearbeit (#99–#104) |

Angesetzt: **47 SP** = Mittel der letzten zwei Sprints. Bewusst über dem Dreisprint-Mittel (40 SP),
weil Sprint 4 ohne Carryover startet und die Must-Have-Restmenge (39 SP) genau dann vollständig
in einen Sprint passt.

## Sprint-Backlog (47 SP)

### 1. Persistenz in Produktion — *zuerst, verbilligt alles Weitere* (8 SP)

| Issue | Titel | SP | Prio | Abhängig von | Bereit wann |
| ----- | ----- | -- | ---- | ------------ | ----------- |
| [#89](https://github.com/dfme/budget-buddy/issues/89) | DB-05 Migration von SQLite auf Neon Postgres (inkl. ADR) | 8 | **P0** | #78 ✅ | **sofort** |

Steht bewusst als erster Block und nicht als Nebenschauplatz: #89 ist das einzige `P0 - Critical`
auf dem Board, und es wird mit jedem Sprint teurer. Die Migration stellt die Integrationstests von
`jdbc:sqlite::memory:` auf Testcontainers um — jeder Backend-Test, der in diesem Sprint *vor* #89
geschrieben wird, muss danach angefasst werden. Deshalb Woche 1, mit Vorrang vor allem anderen
Backend-Merge-Verkehr.

### 2. Fixkosten-Vertikale — *US-03 komplett* (17 SP)

| Issue | Titel | SP | Abhängig von | Bereit wann |
| ----- | ----- | -- | ------------ | ----------- |
| [#10](https://github.com/dfme/budget-buddy/issues/10) | BE-FC-01 FixedCost Entity und Repository | 2 | #6 ✅ | **sofort** |
| [#11](https://github.com/dfme/budget-buddy/issues/11) | BE-FC-02 FixedCostService: CRUD und Normalisierung | 3 | #9 ✅, #10 | nach #10 |
| [#12](https://github.com/dfme/budget-buddy/issues/12) | BE-FC-03 REST-Endpoints für Fixkosten | 2 | #11 | nach #11 |
| [#24](https://github.com/dfme/budget-buddy/issues/24) | FE-FC-01 Fixkosten-Wizard Component | 5 | #2 ✅ | **sofort** (parallel) |
| [#25](https://github.com/dfme/budget-buddy/issues/25) | FE-FC-02 Route Guard für Onboarding | 2 | #24 | nach #24 |
| [#26](https://github.com/dfme/budget-buddy/issues/26) | FE-FC-03 Fixkosten-Liste | 3 | #12 | nach #12 |

**#11 ist der wichtigste Merge des Sprints.** An ihm hängen zwei Ketten gleichzeitig: #12 → #26
(Fixkosten-UI) und #21 → #23 → #33 (Safe-to-Spend). Je früher #11 steht, desto mehr Luft hat der
Rest des Sprints.

### 3. Safe-to-Spend-Vertikale — *US-06 komplett, der Kern-Nutzen* (15 SP)

| Issue | Titel | SP | Abhängig von | Bereit wann |
| ----- | ----- | -- | ------------ | ----------- |
| [#22](https://github.com/dfme/budget-buddy/issues/22) | BE-STS-02 Einkommens-Heuristik | 3 | #5 ✅ | **sofort** (parallel) |
| [#21](https://github.com/dfme/budget-buddy/issues/21) | BE-STS-01 SafeToSpendService | 5 | #5 ✅, #11 | nach #11 |
| [#23](https://github.com/dfme/budget-buddy/issues/23) | BE-STS-03 `GET /budget/safe-to-spend` | 2 | #21, #22 | nach #21 + #22 |
| [#33](https://github.com/dfme/budget-buddy/issues/33) | FE-STS-01 Safe-to-Spend Dashboard-Widget | 2 | #23 | nach #23 |
| [#34](https://github.com/dfme/budget-buddy/issues/34) | FE-STS-02 Negativ-Banner | 1 | #33 | nach #33 |
| [#35](https://github.com/dfme/budget-buddy/issues/35) | FE-STS-03 No-Income State und Einkommens-Vorschlag | 2 | #33 | nach #33 |

Diese Vertikale liefert den in CLAUDE.md als *Core Value* benannten Nutzen — die wöchentliche Zahl,
der die Nutzer trauen können. Sie ist gleichzeitig die längste Abhängigkeitskette im Backlog und
damit der kritische Pfad (siehe unten).

### 4. Restfrontend US-04 / US-05 — *unabhängige Blätter, halten das Team frei* (7 SP)

| Issue | Titel | SP | Abhängig von | Bereit wann |
| ----- | ----- | -- | ------------ | ----------- |
| [#29](https://github.com/dfme/budget-buddy/issues/29) | FE-PDF-03 Duplikat-Dialog | 2 | #28 ✅ | **sofort** |
| [#31](https://github.com/dfme/budget-buddy/issues/31) | FE-CAT-02 Pie-Chart Ausgaben nach Kategorie | 3 | #30 ✅ | **sofort** |
| [#32](https://github.com/dfme/budget-buddy/issues/32) | FE-CAT-03 Manuelles Korrigieren von Kategorien | 2 | #19 ✅ | **sofort** |

Alle drei hängen an nichts Offenem und profitieren vom fertigen UI-Fundament (#99–#104) und der
Chart-Integration (#102). Sie sind die Puffer-Items des Sprints: als Füller einsetzbar, wenn jemand
auf einen Merge wartet — und die ersten Kandidaten zum Streichen, wenn der kritische Pfad Luft
braucht.

**Neu-Commitment: 8 + 17 + 15 + 7 = 47 SP** ✅

## Empfohlene Bearbeitungsreihenfolge

```
Woche 1                                    Woche 2
──────────────────────────────────────     ──────────────────────────────────────
Dev A: #89 DB-05 ─────────────────────►    #22 BE-STS-02 ─► #23 ─► #31 FE-CAT-02
       (Postgres + Testcontainers)                  (#23 nach #21 von Dev B)

Dev B: #10 ─► #11 ═► #12 ────────────►     #21 BE-STS-01 ═════► #29 FE-PDF-03
       (entblockt zwei Ketten)                      (─► #23 an Dev A)

Dev C: #24 FE-FC-01 ─► #25 ─► #32 ───►     #26 FE-FC-03 ─► #33 ═► #34 · #35
       (Wizard, unabhängig ab Tag 1)              (#26 nach #12, #33 nach #23)
```

| Dev | Issues | SP |
| --- | ------ | -- |
| A | #89 (8) · #22 (3) · #23 (2) · #31 (3) | 16 |
| B | #10 (2) · #11 (3) · #12 (2) · #21 (5) · #29 (2) | 14 |
| C | #24 (5) · #25 (2) · #32 (2) · #26 (3) · #33 (2) · #34 (1) · #35 (2) | 17 |

- **Kritischer Pfad:** `#10 → #11 → #21 → #23 → #33 → #35` — **16 SP rein sequenziell.** Bei
  47 SP auf 3 Devs und 10 Arbeitstagen (≈ 1,6 SP pro Dev-Tag) füllt diese Kette den Sprint
  vollständig aus, mit **null Slack**. Jeder Verzug auf #11 oder #21 schiebt direkt das
  Safe-to-Spend-Frontend aus dem Sprint.
- **Zwei Handoffs auf dem kritischen Pfad:** #21 (Dev B) → #23 (Dev A) → #33 (Dev C). Beide müssen
  am Tag des Merges übernommen werden, nicht am Folgetag.
- **#10 → #11 gehören auf einen Dev** (hier B) und in die ersten Tage. #11 ist die Verzweigung, an
  der zwei Ketten hängen.
- **#89 läuft absichtlich allein bei Dev A** in Woche 1: die Migration fasst alle bestehenden
  Backend-Tests an. Merge-Vorrang vor #10/#11 — wer nach #89 mergt, rebased, nicht umgekehrt.
- **Parallelisierbar ab Tag 1:** #89, #10, #24, #22, #29, #31, #32 — sieben Issues ohne offene
  Vorbedingung. Kein Dev muss zu Sprint-Beginn warten.
- **#32 als Wartefüller** bei Dev C zwischen #25 und #26 (das auf #12 wartet).

## Bewusst *nicht* in Sprint 4

| Bereich | SP | Warum verschoben |
| ------- | -- | ---------------- |
| E2E-Tests der Must-Haves (#122 E2E-PDF, #123 E2E-FC, #124 E2E-CAT, #125 E2E-STS) | 12 | Der Testing-Abschnitt in CLAUDE.md fordert je 1 Happy Path + 1 Fehlerpfad pro Must-Have-Story — die Schuld ist damit bekannt und bleibt offen. Sie sind aber **nicht in Sprint 4 machbar**: #123 hängt an der gesamten Fixkosten-Vertikale, #125 an der gesamten Safe-to-Spend-Vertikale, #124 an #31/#32. Alle vier werden erst in den letzten Sprint-Tagen bereit. Sie sind der **komplette, dann unblockierte Sprint-5-Einstieg**. |
| #58 E2E-AUTH-01 | 3 | Seit Sprint 2 bereit, `P2 - Medium`, war schon in Sprint 3 als Stretch-Item benannt und ist nicht gezogen worden. Kein Must-Have → weicht dem MVP-Abschluss. Gehört mit den anderen vier E2E-Issues in Sprint 5. |
| #114 BE-AUTH-05 Vor-/Nachname im User-Model | 3 | `P2 - Medium`, Komfortfeature. Ändert das User-Model und die Register-Maske — kollidiert unnötig mit #89 (Migrationen) im selben Sprint. |
| #134 BE-CAT-06 Transaktionstext vor Claude-Call maskieren | 3 | Datenminimierung, adressiert Risiko #2 (Liability) und die Datenschutz-Skepsis von Persona Marc. Steht auf `P3 - Low` — **diese Einstufung ist zu prüfen** (siehe Hygiene-Befund unten), bei `P1` wäre es ein Sprint-4-Kandidat. |
| #113 INFRA-15 Prettier · #121 INFRA-16 Node 24 · #126 INFRA-17 Deep-Link-Bug · #129 INFRA-19 Skills auf Deutsch | 4+ | Alle `P3 - Low`. #126 ist als `bug` markiert, aber nur 1 SP und kein Blocker für den MVP-Abschluss. Sammelkandidaten für einen Aufräum-Slot in Sprint 5. |
| US-07 Sparziel · US-08 Abos · US-09 KI-Monatsbericht · US-10 Monatsvergleich · US-11 OpenBanking · US-12 Monatswechsel · US-13 Tx pro Kategorie · US-14 Einstellungen | — | Noch keine Issues angelegt. Kommen nach dem MVP-Abschluss — Should/Could laut MoSCoW. |

## Risiken & Gegenmassnahmen

1. **Der kritische Pfad hat null Slack (Hauptrisiko).** 16 SP sequenziell füllen die 10
   Arbeitstage komplett aus. → *Gegenmassnahme:* #34 und #35 (3 SP) sind die definierten
   Streichkandidaten. #33 allein liefert die sichtbare Safe-to-Spend-Zahl; Negativ-Banner und
   No-Income-State sind Randfall-Verfeinerungen desselben Widgets und gehen ohne Bruch nach
   Sprint 5. Wird früher klar, dass es klemmt: #31 und #32 (5 SP) aus dem Sprint nehmen und die
   Kapazität auf die Kette lenken.

2. **#89 kollidiert mit dem gesamten Backend-Verkehr.** Die Migration ändert Flyway V01–V04, den
   Hibernate-Dialect, `application.properties` und stellt alle Integrationstests auf
   Testcontainers um. Parallel entstehen mit #10/#11/#21/#22 vier neue Backend-Slices mit eigenen
   Tests. → *Gegenmassnahme:* #89 bekommt Merge-Vorrang und soll bis Ende Woche 1 auf `main` sein.
   #10 (2 SP) ist klein genug für einen billigen Rebase; #11 sollte erst nach #89 mergen. Alle
   neuen Backend-Tests ab #89-Merge direkt gegen Testcontainers schreiben — nicht gegen
   `sqlite::memory:`.

3. **#89 hat einen organisatorischen Anteil**, keinen rein technischen: Neon-Projekt in
   Frankfurt/EU anlegen, Connection-String in Render hinterlegen, Zugang so ablegen, dass nicht
   nur eine Person drankommt. Dasselbe Muster wie #76 (INFRA-11) in Sprint 3, das genau daran
   gehangen hat. → *Gegenmassnahme:* Diesen Teil an Tag 1 klären, nicht als letzten Schritt der
   Migration.

4. **Fällt Dev B aus, steht der halbe Sprint.** Dev B hält mit #10 → #11 → #21 den kritischen
   Pfad. → *Gegenmassnahme:* Die Fixkosten-Backend-Kette ist mit 7 SP klein genug, um bei Ausfall
   an Dev A zu gehen (dann rutscht #89 hinter die Kette und die Rebase-Argumentation aus Risiko 2
   kippt — bewusst in Kauf zu nehmender Tausch, kein Automatismus).

5. **Dev C trägt 17 SP über eine 4-stufige Kette** (#24 → #25 → #26 → #33 → #34/#35), davon die
   letzten 8 SP hinter Merges anderer Devs. → *Gegenmassnahme:* #32 ist bereits als Wartefüller
   eingeplant; #29 (2 SP, bei Dev B) lässt sich zusätzlich zu Dev C verschieben, falls #12 früher
   kommt als erwartet.

## Board-Hygiene — Befunde aus dieser Planung

| Befund | Issues | Empfehlung |
| ------ | ------ | ---------- |
| **Priority-Abweichung** | **#134** steht auf `P3 - Low`, trägt aber `us-05` (Must-Have → `P1 - High`) | Im Planning **bewusst auf `P3` bestätigt** — die Datenminimierung bleibt Nice-to-have, obwohl das Label eine Must-Have-Story nennt. Nicht angefasst. |
| `Area` leer | #113, #114, #121, #122, #123, #124, #125, #126, #129 | Alle E2E- und INFRA-Issues. E2E-Issues brauchen einen Wert, der nicht existiert (sie testen Frontend + Backend gemeinsam) — entweder `DevOps` wie bei #58 oder eine neue Option `E2E`. |
| `Story Points` leer | #129 INFRA-19 | Schätzvorschlag **2 SP** — Vergleich: #84 INFRA-13 (1 SP) und #113 INFRA-15 (1 SP), beide reine Doku-/Tooling-Tasks; #129 fasst mehr Dateien an. |
| **Keine Sprint-4-Iteration auf dem Board** | — | Bei der Planung war `iterations: []` — Sprint 3 endete am 2026-08-01, es gab keine aktive und keine kommende Iteration. **Inzwischen angelegt:** *Sprint 4*, 2026-08-05 bis 2026-08-18, `duration: 14`. Zur Fallstricke-Warnung siehe unten. |
| Priority-Lücken | keine | Alle 27 offenen Issues haben einen Priority-Wert — auch die ohne `us-*`-Label. |

### Iteration anlegen löscht die Sprint-Historie — Fallstrick für die nächste Planung

Eine Iteration lässt sich per API **nur** über `updateProjectV2Field` mit
`iterationConfiguration` anlegen; eine anfügende Mutation existiert nicht. Dabei wird die
**gesamte Feld-Konfiguration neu geschrieben, und GitHub generiert für alle Iterationen neue IDs** —
auch für die bereits abgeschlossenen. Die Sprint-Werte der Items zeigen auf die alten IDs und sind
danach leer.

Beim Anlegen von Sprint 4 ist genau das passiert: Sprint 1–3 wurden korrekt mit übergeben und
stehen mit unveränderten Titeln und Daten in `completedIterations`, aber ihre IDs wechselten
(`3b6721d8` → `631bf85e` usw.) und **alle 50 historischen Item-Zuordnungen waren gelöscht**. Sie
wurden aus einem vor der Mutation gezogenen Snapshot (`itemId` → Sprint-Titel) wiederhergestellt,
50 von 50 verifiziert. Ohne dieses Backup wäre die Velocity-Historie — die Datenbasis jeder
weiteren Sprint-Planung — unwiederbringlich weg gewesen.

**Für Sprint 5:** Iteration im **Board-UI** anlegen (*+ Add iteration*) — das hängt an, ohne
bestehende IDs anzufassen. Wenn es doch über die API laufen muss: vorher `itemId` → Sprint-Titel
aller Items sichern, nach der Mutation die neuen Iteration-IDs auslesen und die Zuordnungen
zurückschreiben. Die Schrittfolge in `.claude/skills/plan-sprint/SKILL.md` (Schritt 5) kennt diesen
Fallstrick noch nicht.

### Plan-Index

`docs/plans/README.md` **ist aktuell** — 48 Pläne, keine Abweichung zum Dateibestand. Für den
Sprint-Commit ist nichts nachzuziehen.

Festgestellt beim Prüfen: **`scripts/plans-index.sh` hängt in einer Endlosschleife.** Die
GraphQL-Query deklariert die Pagination-Variable als `$cursor`, `gh api graphql --paginate`
injiziert den Cursor aber unter dem festen Namen `$endCursor`. Der Cursor kommt damit nie an, die
erste Seite wiederholt sich unbegrenzt, und das Skript terminiert nie — weder mit noch ohne
`--check`. Die obige Aussage stammt aus einem Lauf mit lokal auf `$endCursor` umbenannter Variable;
damit terminiert das Skript korrekt (104 Board-Items) und meldet den Index als aktuell.

Der Fix ist eine Umbenennung an drei Stellen (Zeilen 41, 43 im Query-Block). Er gehört als eigenes
Issue ins Backlog — `INFRA-21`, Label `bug`, Branch `fix/INFRA-21-plans-index-pagination`, Bereich
Infrastruktur, geschätzt 1 SP. Bewusst **nicht** in dieser Planung mitgefixt: `/plan-sprint` legt
keine Issues an und schreibt keinen Fremdcode.
