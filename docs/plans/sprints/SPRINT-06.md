# Sprint-6-Vorschlag — BudgetBuddy

**Team:** 3 Entwickler · **Neu-Commitment:** 66 SP · **Kein Carryover** · **Sprint-Nr.:** 6

## Sprint-Ziel

> **Das Notification-Fundament, US-12 (Monatswechsel) und US-08 (Abo-Erkennung) gehen vollständig live** — Nutzer sehen erkannte Abos inkl. Benachrichtigung und können Dashboard/Kategorie-Übersicht monatsweise durchblättern. Ergänzt um die verbliebenen Must-Have-Bugs im PDF-Import-/Safe-to-Spend-Pfad, zwei nDSG-Datenminimierungs-Nachbesserungen bei der Kategorisierung und den ersten Schritt zu besser analysierbaren Produktions-Logs (MDC).

## Ausgangslage

- **Sprint 5 abgeschlossen** (64 SP, alle *Done*), kein Carryover: kein Issue steht auf
  `In Progress` oder `Review`. Sauberer Start wie schon in Sprint 4 und 5.
- **12 von 30 offenen Issues (34 SP) kommen direkt aus dem vorbereitenden Zuschnitt**
  [`docs/plans/us-08-09-12-breakdown.md`](../us-08-09-12-breakdown.md): das Notification-Fundament
  (#245–#247), US-12 Monatswechsel (#248–#251) und US-08 Abo-Erkennung (#252–#256) — bereits als
  GitHub-Issues angelegt, mit SP und (soweit zutreffend) `blocked_by`-Relationships hinterlegt,
  die exakt der im Zuschnittsdokument empfohlenen Reihenfolge entsprechen.
- **US-09 (KI-Monatsbericht) aus demselben Zuschnitt ist bewusst aussen vor** — grösster Block
  (26 SP + Stretch `BE-RPT-05`), noch nicht als Issues angelegt. Das Zuschnittsdokument selbst
  empfiehlt eine Aufteilung über zwei Sprints; dieser Vorschlag folgt dem.
- **Ein neues Issue kam während dieser Planungssession dazu:** [#257](https://github.com/dfme/budget-buddy/issues/257)
  `INFRA-37` (MDC: User-ID in Logs), `P1 - High` / 3 SP, vom Team direkt gesetzt statt aus MoSCoW
  abgeleitet (kein `us-*`-Label).
- **Keine offene `P0 - Critical`.**
- **25 von 30 offenen Issues (66 von 84 SP) sind für diesen Sprint vorgeschlagen** — die restlichen
  5 (18 SP) bewusst zurückgestellt (siehe unten).

### Velocity

| Sprint | Dauer | Erledigte SP | Anmerkung |
| ------ | ----- | ------------ | --------- |
| 1 | 14 Tage | 24 | Team-Ramp-up |
| 2 | 11 Tage | 28 | verkürzter Sprint |
| 3 | 14 Tage | 67 | Ausreisser: Carryover + ungeplante FE-UI-Folgearbeit |
| 4 | 14 Tage | 54 | MVP-Abschluss, kein Carryover, straff gepackt |
| 5 | 14 Tage | 64 | Kompletter einplanbarer Backlog abgedeckt, kein Carryover |

Nach der Sprint-5-Methodik (Mittel der letzten zwei Sprints) ergäben sich **59 SP**
((54+64)/2). Im Planning wurde stattdessen **66 SP** angesetzt — bewusste Entscheidung des Teams,
oberhalb sowohl des 2-Sprint- (59) als auch des 3-Sprint-Mittels (Sprint 3+4+5 ≈ 61.7). Anders als
Sprint 5 (55 SP Commitment bei 60 SP Kapazität, 5 SP Puffer) hat dieser Vorschlag **keinen Puffer**
— siehe Risiko 3.

## Sprint-Backlog (66 SP)

### 1. Notification-Fundament — *blockiert US-08 und (später) US-09, zuerst* (7 SP)

| Issue | Titel | SP | Prio | Abhängig von | Bereit wann |
| ----- | ----- | -- | ---- | ------------ | ----------- |
| [#245](https://github.com/dfme/budget-buddy/issues/245) | DB-08 Flyway V08: notifications-Tabelle | 1 | P1 | — | **sofort** |
| [#246](https://github.com/dfme/budget-buddy/issues/246) | BE-NOTIF-01 NotificationService + REST-Endpoints | 3 | P1 | #245 | nach #245 |
| [#247](https://github.com/dfme/budget-buddy/issues/247) | FE-NOTIF-01 Notification-Glocke in der App-Shell | 3 | P1 | #246 | nach #246 |

Aus [`us-08-09-12-breakdown.md`](../us-08-09-12-breakdown.md#gemeinsames-fundament-in-app-benachrichtigungen-7-sp):
generisches `notification/`-Modul statt zweier divergierender Ad-hoc-Lösungen für US-08 und
(später) US-09. Dieser Block ist der **kritische Pfad des Sprints** — siehe Risiko 1.

### 2. US-12 — Zwischen Monaten wechseln — *kleinster Zuschnitt, keine Fundament-Abhängigkeit* (10 SP)

| Issue | Titel | SP | Prio | Abhängig von | Bereit wann |
| ----- | ----- | -- | ---- | ------------ | ----------- |
| [#249](https://github.com/dfme/budget-buddy/issues/249) | FE-CAT-08 Kategorie-Übersicht: Keine-Daten-Hinweis | 1 | P1 | — | **sofort** |
| [#248](https://github.com/dfme/budget-buddy/issues/248) | BE-STS-06 Safe-to-Spend: Monat-Parameter + Abgeschlossen-Status | 3 | P1 | — | **sofort** |
| [#250](https://github.com/dfme/budget-buddy/issues/250) | FE-STS-04 Dashboard-Monatswechsel | 3 | P1 | #248 | nach #248 |
| [#251](https://github.com/dfme/budget-buddy/issues/251) | E2E-STS-02 Playwright: Monatswechsel | 3 | P1 | #250 | nach #250 |

`#249` ist unabhängig von der `#248`-Kette und kann parallel/zuerst erledigt werden (identisch zur
Empfehlung im Zuschnittsdokument).

### 3. US-08 — Wiederkehrende Ausgaben (Abos) erkennen — *baut auf Block 1 auf* (17 SP)

| Issue | Titel | SP | Prio | Abhängig von | Bereit wann |
| ----- | ----- | -- | ---- | ------------ | ----------- |
| [#252](https://github.com/dfme/budget-buddy/issues/252) | DB-09 Flyway V09: recurring_expenses-Tabelle | 1 | P1 | — | **sofort** |
| [#253](https://github.com/dfme/budget-buddy/issues/253) | BE-REC-01 RecurringExpenseService: Erkennung | 5 | P1 | #252, #246 | nach #246 (Fundament) |
| [#254](https://github.com/dfme/budget-buddy/issues/254) | BE-REC-02 REST-Endpoints Abo-Übersicht | 3 | P1 | #253 | nach #253 |
| [#255](https://github.com/dfme/budget-buddy/issues/255) | FE-REC-01 Abo-Übersicht-Screen | 5 | P1 | #254, #247 | nach #254 **und** #247 (Fundament) |
| [#256](https://github.com/dfme/budget-buddy/issues/256) | E2E-REC-01 Playwright: Abo-Erkennung | 3 | P1 | #255 | nach #255 |

Einzige Kette im Sprint, die auf einen **anderen** Block wartet (`#253`, `#255` hängen an
`BE-NOTIF-01`/`FE-NOTIF-01` aus Block 1) — das ist der teamübergreifende kritische Pfad, siehe
Risiko 1.

### 4. Verbliebene Must-Have-Bugs (PDF-Import, Safe-to-Spend, Kategorisierung) (16 SP)

| Issue | Titel | SP | Prio | Abhängig von | Bereit wann |
| ----- | ----- | -- | ---- | ------------ | ----------- |
| [#186](https://github.com/dfme/budget-buddy/issues/186) | FE-CAT-06 Overflow-Verhalten der Kategorie-Tabelle auf schmalen Viewports | 1 | P1 | — | **sofort** |
| [#190](https://github.com/dfme/budget-buddy/issues/190) | E2E-FC-02 Playwright: Onboarding-Abschluss (Wizard → Dashboard) | 3 | P1 | — | **sofort** |
| [#193](https://github.com/dfme/budget-buddy/issues/193) | BE-PDF-10 Geratene Buchungsrichtung bei mehrdeutigem PostFinance-Saldo-Delta unsichtbar | 3 | P1 | — | **sofort** |
| [#197](https://github.com/dfme/budget-buddy/issues/197) | BE-PDF-11 Verwaiste Import-Jobs bleiben nach Neustart für immer auf RUNNING | 3 | P1 | — | **sofort** |
| [#208](https://github.com/dfme/budget-buddy/issues/208) | BE-STS-05 Einkommens-Heuristik über Absender statt Buchungstext gruppieren | 3 | P1 | #159 ✅ | **sofort** (Abhängigkeit seit Sprint 5 geschlossen) |
| [#257](https://github.com/dfme/budget-buddy/issues/257) | INFRA-37 MDC einführen: User-ID in Logs für bessere Analysierbarkeit | 3 | P1 | — | **sofort** |

### 5. nDSG-Datenminimierung + Auth-/Tech-Debt-Nachbesserungen (16 SP)

| Issue | Titel | SP | Prio | Abhängig von | Bereit wann |
| ----- | ----- | -- | ---- | ------------ | ----------- |
| [#243](https://github.com/dfme/budget-buddy/issues/243) | BE-CAT-09 Token-Verbrauch und Kosten der Claude-Kategorisierung loggen | 1 | P2 | — | **sofort** |
| [#241](https://github.com/dfme/budget-buddy/issues/241) | BE-PDF-13 Gegenpartei-Adresse als einzelne Zeile überlebt DETAIL_NOISE | 2 | P2* | #239 ✅ | **sofort** (Abhängigkeit seit Sprint 5 geschlossen) |
| [#200](https://github.com/dfme/budget-buddy/issues/200) | BE-AUTH-10 Passwort über 72 Bytes führt zu HTTP 500 statt 400 | 2 | P2 | — | **sofort** |
| [#205](https://github.com/dfme/budget-buddy/issues/205) | BE-FC-04 CHF-Betragsregel steht doppelt (FixedCostService/UserService) | 2 | P2 | — | **sofort** |
| [#233](https://github.com/dfme/budget-buddy/issues/233) | BE-CAT-08 Restexposition im Claude-Prompt: Vorname + Händler-Telefonnummer | 3 | P2* | — | **sofort** |
| [#207](https://github.com/dfme/budget-buddy/issues/207) | INFRA-29 CI erkennt nachträglich geänderte Flyway-Migrationen nicht | 3 | P2 | — | **sofort** |
| [#201](https://github.com/dfme/budget-buddy/issues/201) | BE-AUTH-11 JWT bleibt nach Passwort-Änderung gültig — Session-Invalidierung | 3 | P2 | — | **sofort** |

`*` #241 und #233 tragen `us-05` (Must-Have → laut MoSCoW eigentlich `P1`), stehen aber auf
`P2 - Medium` — siehe Board-Hygiene-Befund unten. Trotzdem im Vorschlag, weil beide dieselbe
nDSG-Datenminimierung betreffen wie das bereits umgesetzte `BE-CAT-06`/`BE-PDF-06` (Risiko #2 aus
CLAUDE.md).

**Neu-Commitment: 7 + 10 + 17 + 16 + 16 = 66 SP** ✅

## Empfohlene Bearbeitungsreihenfolge

```
Woche 1                                         Woche 2
──────────────────────────────────────────      ──────────────────────────────────────────
Dev A: #245 DB-08 ─► #246 BE-NOTIF-01 ─►         #200 BE-AUTH-10 ─► #243 BE-CAT-09 ─►
       #247 FE-NOTIF-01 (Fundament zuerst)       #241 BE-PDF-13 ─► #233 BE-CAT-08 ─► #257 INFRA-37

Dev B: #249 FE-CAT-08 ─► #248 BE-STS-06 ─►       #251 E2E-STS-02 ─► #193 BE-PDF-10 ─►
       #250 FE-STS-04                            #186 FE-CAT-06 ─► #205 BE-FC-04 ─► #207 INFRA-29

Dev C: #252 DB-09 (sofort) ─► #190 E2E-FC-02 ─►  #254 BE-REC-02 ─► #255 FE-REC-01
       #197 BE-PDF-11 (Lückenfüller, während      (nach #247) ─► #256 E2E-REC-01
       auf #246 gewartet wird) ─► #253 BE-REC-01
       (sobald #246 fertig)
```

| Dev | Issues | SP |
| --- | ------ | -- |
| A | #245 (1) · #246 (3) · #247 (3) · #200 (2) · #243 (1) · #241 (2) · #233 (3) · #257 (3) | 18 |
| B | #249 (1) · #248 (3) · #250 (3) · #251 (3) · #193 (3) · #186 (1) · #205 (2) · #207 (3) | 19 |
| C | #252 (1) · #190 (3) · #197 (3) · #253 (5) · #254 (3) · #255 (5) · #256 (3) | 23 |

- **Kritischer Pfad ist teamübergreifend, nicht dev-intern:** Dev Cs gesamte US-08-Kette
  (17 SP) hängt an Dev As Fundament — `#253` braucht `#246`, `#255` braucht zusätzlich `#247`.
  Verzögert sich Dev A, steht ein Grossteil von Dev Cs Sprint still.
  → *Gegenmassnahme:* Dev A zieht `#245`→`#246`→`#247` als Allererstes, vor allem anderen. Dev C
  startet mit dem unabhängigen `#252` (DB-09) und füllt die Wartezeit auf `#246` mit den beiden
  unabhängigen P1-Bugs `#190`/`#197` statt zu blockieren.
- **Dev C trägt mit 23 SP am meisten**, aber ausschliesslich die in sich klar geschnittene
  US-08-Vertikale plus zwei kleine, unabhängige Lückenfüller — inhaltlich fokussiert, kein
  Kontextwechsel zwischen unrelated Bereichen.
- **`#249` (FE-CAT-08) ist die einzige Verzweigung ausserhalb des kritischen Pfads** — unabhängig
  von `#248`, sollte trotzdem früh bei Dev B laufen, damit die 3-stufige STS-Kette danach ungestört
  durchlaufen kann.
- **13 von 25 Issues haben keine offene Vorbedingung** und sind ab Tag 1 parallelisierbar; die
  restlichen 12 folgen den beiden dokumentierten Ketten (Fundament→US-08, `#248`-Kette).

## Bewusst *nicht* in Sprint 6

| Bereich | SP | Warum verschoben |
| ------- | -- | ---------------- |
| [#156](https://github.com/dfme/budget-buddy/issues/156) INFRA-26 Spring Boot 4.x Migration bewerten | — (keine SP) | Unverändert seit Sprint 5: TECH-STACK.md schliesst Spring Boot 4 weiterhin explizit aus („milestone releases only"). Solange diese Entscheidung steht, ist eine Bewertung kein Sprint-Kandidat. |
| [#213](https://github.com/dfme/budget-buddy/issues/213) INFRA-30 Migration auf Angular 22.x bewerten | 5 | `P3 - Low`, reine Evaluation ohne unmittelbaren Nutzerwert, konkurriert mit den nDSG-/Bugfix-Blöcken um Kapazität — die haben direkteren Impact auf Marc/Laras Kernwert. |
| [#203](https://github.com/dfme/budget-buddy/issues/203) INFRA-28 Veraltete Branches sind mergebar | 2 | `P3 - Low`, CI-Hygiene ohne akuten Vorfall; bei Team-Grösse 3 manuell beherrschbar. |
| [#231](https://github.com/dfme/budget-buddy/issues/231) BE-AUTH-12 Obergrenzen für String-Felder in Auth-DTOs | 2 | `P3 - Low`, geringes Risiko (kein bekannter Exploit-Pfad), kleiner Härtungs-Task ohne Deadline-Druck. |
| [#194](https://github.com/dfme/budget-buddy/issues/194) FE-UI-08 app-card lässt globales title-Attribut stehen | 1 | `P3 - Low`, rein kosmetisch. |
| **US-09 (KI-Monatsbericht)**, gesamter Zuschnitt aus `us-08-09-12-breakdown.md` | 26 + Stretch (noch keine Issues) | Grösster Block, erster `@Scheduled`-Job im Projekt, plus ungeklärter E-Mail-Versand-Entscheid (`BE-RPT-05`). Das Zuschnittsdokument empfiehlt selbst eine Aufteilung über zwei Sprints — dieser Vorschlag folgt dem. Nächster Schritt: Issues aus dem Zuschnitt anlegen, sobald der E-Mail-Provider-Entscheid ansteht. |

Damit deckt dieser Vorschlag 25 von 30 offenen Issues (66 von 84 SP) ab.

## Risiken & Gegenmassnahmen

1. **Teamübergreifender kritischer Pfad: Dev Cs komplette US-08-Kette (17 SP) hängt an Dev As
   Fundament.** Verzögert sich `#246`/`#247`, kann Dev C nur die 6 SP an unabhängiger Arbeit
   (`#252`, `#190`, `#197`) vorziehen, bevor der Rest des Sprints für diesen Entwickler stillsteht.
   → *Gegenmassnahme:* Fundament hat für Dev A explizit Vorrang vor allem anderen (auch vor den
   ihm zugeteilten P2-Tickets). Tägliches Kurz-Update Dev A → Dev C, damit `#253` sofort nach
   `#246`-Merge startet statt erst am nächsten Steckpunkt.

2. **66 SP Commitment ohne Puffer** — höher als sowohl das 2-Sprint- (59) als auch das
   3-Sprint-Mittel (≈62), und anders als Sprint 5 (55 SP bei 60 SP Kapazität) gibt es hier keinen
   expliziten Streichblock eingerechnet.
   → *Gegenmassnahme:* Block 5 (nDSG/Auth/Tech-Debt, 16 SP) ist der definierte Streichkandidat,
   wenn der Sprint eng wird — keines der sieben Issues blockiert Block 1–4. Zuerst zu streichen:
   `#207`/`#201`/`#205` (8 SP, reine Robustheits-/Cleanup-Tasks ohne Nutzerwirkung diesen Sprint).

3. **Systematische Priority-Abweichung: alle 9 US-08/US-12-Issues stehen auf `P1 - High`, obwohl
   US-08 und US-12 laut MoSCoW `Should` (→ `P2`) sind** (siehe Board-Hygiene unten). Dieser
   Vorschlag übernimmt die Priority unverändert und plant den kompletten Zuschnitt trotzdem ein,
   weil er in sich geschlossen und laut Zuschnittsdokument der nächste sinnvolle Schritt ist —
   aber die Diskrepanz sollte im Planning bewusst bestätigt werden, nicht stillschweigend als
   gegeben hingenommen.
   → *Gegenmassnahme:* Im Planning klären, ob die P1-Einstufung eine bewusste Sprint-6-Priorisierung
   war oder ein Board-Pflegefehler — danach ggf. auf `P2` korrigieren.

4. **US-09 bleibt nach diesem Sprint komplett unangetastet** (0 Issues angelegt). Der nächste
   Sprint startet dort bei null Vorlaufzeit ausser dem Zuschnittsdokument.
   → *Gegenmassnahme:* Den E-Mail-Versand-Entscheid (`BE-RPT-05`, siehe Zuschnittsdokument) bereits
   während Sprint 6 im Team klären, damit Issues für Sprint 7 zu Beginn des nächsten Plannings
   sofort angelegt werden können, statt die Entscheidung erst dann zu beginnen.

## Board-Hygiene — Befunde aus dieser Planung

| Befund | Issues | Empfehlung |
| ------ | ------ | ---------- |
| **Priority-Abweichung (Einzelfälle)** | **#233**, **#241** stehen auf `P2 - Medium`, tragen aber `us-05` (Must-Have → `P1 - High`) | Nicht angefasst (Regel: gesetzte Werte bleiben). Beide trotzdem im Vorschlag enthalten (nDSG-Thema), siehe Block 5. |
| **Priority-Abweichung (systematisch)** | **#248–#256** (9 Issues, US-08 + US-12) stehen auf `P1 - High`, tragen aber `us-08`/`us-12` (Should → `P2 - Medium`) | Nicht angefasst. Vermutlich bewusste Sprint-6-Vorbereitung (passend zum frischen Zuschnittsdokument) statt Board-Pflegefehler — zur Bestätigung ins Planning, siehe Risiko 3. |
| `Area` leer | #251, #256 (E2E-Issues), #257 (INFRA-37) | E2E: unverändert seit Sprint 4/5 — es gibt weiterhin keine `E2E`-Option im `Area`-Feld. #257: Vorschlag `Backend` (Filter + Logback-Config, keine Frontend-Berührung) — noch nicht eingetragen, siehe Rückfrage. |
| `Story Points` leer | keine unter den 30 offenen Issues | Vollständig. |
| Label-Nuance (kein Hygiene-Blocker) | #245–#247 tragen `us-08`, obwohl das Zuschnittsdokument sie als „gemeinsames Fundament" für **US-08 und US-09** beschreibt, nicht exklusiv US-08 | Reine Beobachtung, keine Auswirkung auf die Priority-Rechnung (beide Stories sind `Should`). Nicht geändert. |
| **Keine Sprint-6-Iteration im Board-Feld** | — | `iterations: []` bei dieser Planung — Sprint 5 endet am 2026-09-02 (heute), es gibt weder eine aktive noch eine kommende Iteration. Muss vor Schritt 5 angelegt werden — **siehe Warnung unten**. |
| Plan-Index | `docs/plans/README.md` fehlte eine Zeile (`us-08-09-12-breakdown.md`) | Behoben in dieser Planung via `scripts/plans-index.sh` — geht mit in den Sprint-Commit. |

### Warnung für Schritt 5: Iteration nur über das Board-UI anlegen

Gilt unverändert seit Sprint 5 (dort erstmals dokumentiert, bei Sprint 4 selbst beinahe
datenverlustträchtig erlebt): eine neue Iteration lässt sich per API nur über
`updateProjectV2Field` mit `iterationConfiguration` anlegen — das schreibt die **gesamte
Feld-Konfiguration neu und vergibt für alle Iterationen (auch die bereits abgeschlossenen) neue
IDs**. Die Sprint-Zuordnung aller 200+ historischen Items zeigt danach auf die alten IDs und
erscheint leer, bis sie aus einem vorher gezogenen Snapshot zurückgeschrieben wird.

**Für Sprint 6 gilt weiterhin:** Iteration im **Board-UI** anlegen (*+ Add iteration*), nicht über
die API. Falls die API doch nötig wird: vorher `itemId → Sprint-Titel` aller Items sichern, nach
der Mutation die neuen Iteration-IDs auslesen und zurückschreiben.
