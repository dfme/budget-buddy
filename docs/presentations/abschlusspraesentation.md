# Abschlusspräsentation BudgetBuddy

**Kurs:** CAS Application Development with AI (ADAI) 2026 · BFH Biel · Ilja Rasin
**Dauer:** 30 Minuten gesamt, aufgeteilt in 3 Bereiche à ca. 10 Minuten, 3 Vortragende

> Kein Modul-Präfix (`NN_NN`) im Dateinamen: diese Präsentation fasst das Gesamtprojekt für die
> Klasse zusammen und gehört zu keinem einzelnen Kursmodul.

## Aufteilung

| Bereich | Thema | Zeit | Vortragende:r |
| ------- | ----- | ---- | -------------- |
| 1 | Applikationsaufbau — Architektur & Komponenten | ~10 min | Jason |
| 2 | Methodik, Vorgehen & Arbeitsweise | ~10 min | Daniel |
| 3 | Demo | ~10 min | Sergio |

Zuteilung Bereich 2/3 per Zufall (`random.shuffle`), Bereich 1 fest bei Jason.

---

## Bereich 1 — Applikationsaufbau (Architektur & Komponenten) — ~10 min

**Ziel:** Der Klasse in 10 Minuten zeigen, was BudgetBuddy technisch ist und warum es so gebaut
ist, wie es gebaut ist — ohne in Code-Details abzudriften.

| Nr | Min | Inhalt |
| --- | --- | ------ |
| B11 | 0–1 | Ein Satz Value Proposition: Safe-to-Spend-Betrag aus echten Transaktionsdaten, nicht manueller Eingabe (siehe [README.md](../../README.md)) |
| B12 | 1–4 | C2-Container-Diagramm: Web SPA (Angular) ↔ API (Spring Boot) ↔ Database (PostgreSQL/Neon) ↔ Anthropic Claude als externes System — [ARCHITECTURE.md](../ARCHITECTURE.md) |
| B13 | 4–6 | Zentrale Architekturentscheide (ADRs) mit **verworfener Alternative**, nicht nur Ergebnis: Monolith statt Microservices (ADR-4), REST statt GraphQL (ADR-3), JWT als httpOnly Cookie statt LocalStorage (ADR-7). Zwei Entscheide bewusst als **Korrektur eines eigenen früheren Entscheids** zeigen, nicht nur als Wahl unter mehreren Optionen: (1) **SQLite → PostgreSQL/Neon** (ADR-5 → [ADR-12](../adr/ADR-12-datenpersistenz-produktion.md)) — SQLite war die bewusste MVP-Wahl, scheiterte aber am ephemeren Filesystem des Render Free-Plan (Daten überleben keinen Redeploy/Spin-down); Wechsel bewusst früh, solange noch kaum Migrationen und keine Daten zu erhalten waren. (2) **Synchroner → asynchroner PDF-Import** (ADR-14) — der ursprünglich vollständig synchrone Import lief ab ~110 Transaktionen reproduzierbar ins 30-Sekunden-Timeout und verwarf dabei den *gesamten* Import (#192); seither Parsen synchron (~2s), Kategorisierung als `@Async`-Job mit Fortschritts-Polling |
| B14 | 6–8 | Kernflow Hybrid-Kategorisierung: globale, kuratierte Lookup-Tabelle (~70–80%) → gebündelter Claude-Call (max. 20 Tx/Request, `enum`-Constraint im Structured Output) → Lerneffekt jetzt aus **zwei Quellen**: manuelle Korrektur (BE-CAT-04) *und* erfolgreiche Claude-Kategorisierung selbst (BE-CAT-11, [Issue #314](https://github.com/dfme/budget-buddy/issues/314)) — ein korrekt kategorisierter, nie korrigierter Händler lernt jetzt auch. **Reflexion, weitergedacht (ADR-15):** Gelerntes landet bewusst *nicht* mehr in der globalen Lookup-Tabelle, sondern pro Nutzer getrennt in `user_category_lookup` — sonst würde Claude-Kategorisierung eines Users künftig die Vorschläge aller anderen User beeinflussen. Beim Matching gewinnt bei Konflikt das längere Pattern, bei Gleichstand das eigene; ein User kann `MIGROS` für sich umbiegen, ohne den Seed für alle zu ändern. Fällt mit Kontolöschung weg (eigener Cleanup-Port); asynchroner Import (ADR-14) mit Fallback auf `Sonstiges` bei Timeout |
| B15 | 8–9 | **Security-Aspekt: private Daten ans LLM.** `PromptSanitizer` maskiert IBAN, Karten-/Kontonummern, Beträge, Namen, E-Mail-Adressen **bevor** Text den `ClaudeCategorizationService` verlässt — die Lookup-Stufe davor sieht unmaskierten Text, bleibt aber lokal. Transparent auch die bekannten Lücken (BE-CAT-08: Vorname in Freitext-Zweckzeile, Händler-Telefonnummer) — zeigt reflektierten Umgang mit Restrisiko statt Blackbox-Behauptung |
| B16 | 9–10 | Deployment in einem Satz: Single JAR auf Render (Frankfurt/EU), Neon Postgres getrennt, bewusst **kein** Redis/Queue/Microservices — Overengineering für 3 Devs / 3 Monate vermieden. Observability kurz erwähnen: Prod-Logs laufen zusätzlich per Render-BetterStack-Integration in die Source „Render Prod" (aktiv seit 19.08.2026) — 3 Tage Hot-Storage, ältere Daten per S3-Historie, unabhängig von Renders eigener, kurzlebiger Log-Ansicht |

**Visuals vorschlagen:** C2-Diagramm aus [ARCHITECTURE.md](../ARCHITECTURE.md) 1:1 übernehmen
(ASCII → sauber nachgebaut), plus ein kleines Sequenzdiagramm für den Kategorisierungs-Flow
(Lookup → Claude → Korrektur).

**Nicht vertiefen:** Package-Struktur/Domain-Grenzen im Detail, einzelne Endpunkte — das ist
Stoff für Rückfragen, nicht für den Hauptvortrag.

---

## Bereich 2 — Methodik, Vorgehen & Arbeitsweise — ~10 min

**Ziel:** Zeigen, dass die Prozess-Entscheide (Priorisierung, Doku, Zusammenarbeit mit der KI)
genauso durchdacht sind wie die Architektur — und Kursinhalte sichtbar in echter Projektarbeit
verankern.

| Nr | Min | Inhalt |
| --- | --- | ------ |
| B21 | 0–2 | Requirements-Prozess: 2 Personas (Lara, Marc — [README.md](../../README.md)), 14 User Stories mit Acceptance Criteria ([docs/requirements/](../requirements/)), **MoSCoW-Priorisierung** je Story (Must/Should/Could) — Beispiel: US-03/04/05/06 = Must (Kernpfad zum Safe-to-Spend), US-11 OpenBanking = Could/Nice-to-have |
| B22 | 2–4 | ADRs als **Prozess**, nicht Inhalt (die konkreten Beispiele kamen bereits in B13) — Fokus hier: festes Format erzwingt das Durchdenken von Alternativen *bevor* entschieden wird, 16 ADRs im Projekt als laufendes Nachschlagewerk statt einmaliger Doku, ein ADR wird bei einer Kurskorrektur nicht gelöscht, sondern als `superseded` markiert — Entscheidungsgeschichte bleibt nachvollziehbar statt überschrieben zu werden |
| B23 | 4–6 | Scrum-Rhythmus: **2-Wochen-Sprints**, GitHub Project Board als Sprint Board, **Iteration-Feld** ist die einzige Quelle für die Sprint-Zuordnung (nicht Milestones — bewusste Lehre aus einem Auseinanderlaufen im Juli), Velocity/Carryover aus dem Board abgeleitet, Sprint-Vorschläge dokumentiert unter [docs/plans/sprints/](../plans/sprints/) (SPRINT-04 bis SPRINT-07 bisher) |
| B24 | 6–8 | Git-Disziplin & Nachvollziehbarkeit: Task-ID-Konvention (`BE-CAT-06`, `FE-PDF-04`, …), 1 ID = 1 Branch = 1 PR, kein direkter Commit auf `main`, Pflicht-Review vor Merge. **CI/CD als automatisches Gate:** Jeder PR läuft durch `build.yml` (Backend `mvn verify` + Frontend Test/Build); erst ein grüner PR ist mergebar. Ein Push auf `main` löst `cd.yml` aus — Build/Test wird wiederverwendet, nur bei Erfolg löst der Render Deploy Hook aus, anschliessend verifiziert ein Smoke-Test die tatsächlich deployte Version über `/actuator/info` |
| B25 | 8–10 | **KI-gestützter Workflow: Claude Code Skills & Agents.** Drei Skills automatisieren den Entwicklungszyklus End-to-End: `/plan-sprint` (Backlog-Hygiene, Velocity, Vorschlag), `/implement-issue` (Issue → Plan → Branch → Code+Tests → Security-Review → PR), `/review-pr` (Diff-Review gegen Gegenseite, Tests ausführen, blockierende Befunde als Inline-Threads). Wichtig: Agents **schreiben nie auf `main`** und **mergen nie selbst** — der Mensch bleibt Entscheidungsinstanz (Freigabe, Merge). Zusätzlich automatischer PR-Review via GitHub Action (INFRA-31) als Ergänzung, nicht Ersatz der Dev-Freigabe |

**Roter Faden für den Vortrag:** Jede Methodik-Entscheidung hat einen dokumentierten Grund und
ist im Repo nachlesbar (nicht nur "wir haben das halt so gemacht") — das ist die eigentliche
Botschaft dieses Blocks.

**Visuals vorschlagen:** Kurzer Screenshot des Sprint Boards, ein Burndown-Chart aus den
[GitHub-Project-Insights](https://github.com/users/dfme/projects/4/insights/1) als Beleg für den
2-Wochen-Rhythmus (B23), eine ADR-Tabelle (Entscheid vs. verworfene Alternative) als Folie, ein
Mini-Diagramm des Skill-Workflows (Issue → Plan → Code/Tests → Review → PR), ein einfaches
Pipeline-Diagramm für B24: PR → `build.yml` (Backend + Frontend) → Merge auf `main` → `cd.yml` →
Render Deploy Hook → Smoke-Test gegen `/actuator/info`.

---

## Bereich 3 — Demo — ~10 min

**Ziel:** Den Kernwert der App live erlebbar machen — vom rohen PDF-Kontoauszug bis zum
Safe-to-Spend-Betrag, plus ein Zusatzfeature, falls Zeit bleibt.

**B30 ist Lara selbst** — nicht ein anonymer Testaccount, sondern `lara@demo.bb`, live und öffentlich
per `POST /api/auth/register` angelegt. Der Endpoint setzt direkt das JWT-Cookie (Auto-Login, siehe
[AuthController.java](../../backend/src/main/java/com/budgetbuddy/auth/AuthController.java) —
Register-Endpoint, Swagger-Beschreibung „legt ein Konto an … und setzt direkt ein JWT-Cookie")
— ein separater Login-Schritt danach entfällt. Genau dieselbe Lara läuft dann durch B31–B34 weiter.

**Konsequenz fürs Seed-Skript:** `backend/tools/seed_demo_accounts.sh` legt `lara@demo.bb` bereits
fertig onboardet an (Fixkosten/Einkommen per API, nicht über die Wizard-UI) — würde man es vorher
laufen lassen, schlägt die Live-Registrierung in B30 mit `409` fehl, weil die E-Mail schon existiert.
**Für diese Demo wird das Skript für Lara deshalb nicht mehr ausgeführt.**

**Vorbereitung (vor der Präsentation, nicht während der 10 Minuten):**

- `lara@demo.bb` darf zu Beginn **nicht existieren**. Falls eine frühere Zeitprobe sie angelegt hat
  (und nicht bis B34 durchlief, wo Konto löschen sie wieder entfernt): manuell zurücksetzen, siehe
  [docs/demo/README.md → Zurücksetzen](../demo/README.md#zurücksetzen).
- `ANTHROPIC_API_KEY` gesetzt (Backend) — sonst fällt die Claude-Stufe der Kategorisierung komplett
  aus und B32 zeigt nur Lookup + pauschal „Sonstiges" statt der echten Mischung
  ([docs/demo/README.md](../demo/README.md) misst dazu 50/50 auf dem vollen 6-Monats-Snapshot).
- Kontoauszug für den aktuellen Monat unter [`docs/demo/statements/`](../demo/statements/)
  vorhanden — bei einer Präsentation in einem späteren Monat vorher mit
  `generate_demo_statements.py` neu generieren.
- Laras Fixkosten- und Einkommenswerte vorher nachschlagen, damit sie in B30 nicht improvisiert
  werden müssen (Quelle: [docs/demo/README.md](../demo/README.md)):

  | Feld | Wert |
  | --- | --- |
  | Einkommen | 1'900.– |
  | WG-Zimmer | 650.– (monatlich) |
  | CSS | 180.– (monatlich) |
  | Salt | 25.– (monatlich) |
  | Semestergebühr | 1'500.– (jährlich) |

- Lara-Passwort im Team vereinbaren und lokal bereithalten (Passwortmanager/`.env.demo`,
  siehe [docs/demo/README.md → Login-Daten](../demo/README.md#login-daten)) — live tippen vermeiden.

| Nr | Min | Flow | Zeigt |
| --- | --- | ---- | ----- |
| B30 | 0–2 | Registrierung + Onboarding als Lara (`lara@demo.bb`) | Registrierung (Auto-Login) → Fixkosten-Wizard (US-03) mit Laras echten Werten (Tabelle oben) → Dashboard zeigt den No-Income-Banner „Bitte erfasse dein Monatseinkommen in den Einstellungen" → Einkommen (1'900.–) dort setzen (US-06/US-14). Zeigt den echten Erstnutzer-Flow mit derselben Person, die danach weiterläuft |
| B31 | 2–4 | PDF-Import eines Kontoauszugs | Upload → asynchrone Verarbeitung mit Fortschrittsanzeige (Polling) → Ergebnis |
| B32 | 4–6 | Kategorisierung im Import-Screen | Automatische Kategorien (Lookup + Claude) direkt sichtbar; gezielt eine als **„Sonstiges"** kategorisierte Transaktion von Hand korrigieren — zeigt den Fallback-Fall konkret und den Lerneffekt für zukünftige Importe, siehe B14 (derselbe angenommene Merge-Zustand, siehe „Offene Punkte") |
| B33 | 6–7 | Safe-to-Spend-Widget | Der Kernwert der App: wöchentlicher Betrag kurz herleiten — Fixkosten und Einkommen wurden bereits in B30 live gezeigt, hier nur noch referenzieren („genau das fliesst jetzt hier ein") statt erneut zu erklären |
| B34 | 7–9 | Konto löschen (Einstellungen) | Datenschutz-Feature (US-02, BE-AUTH-14/FE-SET-05): Bestätigungsdialog mit Passwort, danach Logout und Login schlägt fehl — zeigt nDSG-Ernsthaftigkeit, nicht nur den Safe-to-Spend-Wert |

1 Minute Puffer bleibt (9 von 10 Min verplant) — gegenüber der vorherigen Version leicht entspannter,
weil der separate Login-Schritt durch den Auto-Login in B30 entfällt.

**Zusatzfeature nur als Puffer, nicht fest eingeplant:** Abo-Übersicht (US-08) nur zeigen, falls
nach B34 noch Zeit bleibt — im festen 10-Minuten-Budget ist dafür kein fixer Platz. Sparziel
(US-07) ist **nicht implementiert** (nur Requirements-Doku, kein Code) und deshalb kein
Puffer-Kandidat — siehe Feature-Liste unten.

**Timing bleibt eng:** Bei der Zeitprobe (siehe „Offene Punkte") zuerst B30 stoppen — falls
Registrierung + Wizard + Banner + Einkommen dort länger als 2 Minuten brauchen, eher den Wizard auf
1–2 Fixkosten-Positionen begrenzen (z. B. nur WG-Zimmer + CSS) als B30 zu streichen, da das Team
diesen Punkt bewusst in die Demo aufgenommen hat.

**B34 ist bewusst der letzte Schritt — und räumt sich selbst auf:** Konto löschen entfernt genau
den Account, der in B30 entstanden ist. Läuft eine Zeitprobe komplett bis B34 durch, ist
`lara@demo.bb` danach wieder frei, und B30 kann beim nächsten Durchlauf erneut live registrieren
— kein manuelles Zurücksetzen nötig. Nur ein abgebrochener Testlauf (der B34 nicht erreicht)
braucht das manuelle Zurücksetzen aus dem Vorbereitung-Abschnitt oben.

**Risiko-Absicherung:** Falls Live-Demo hakt (Netzwerk, Neon Cold Start nach Inaktivität —
Scale-to-Zero nach 5 Min. ohne Zugriff, siehe [ADR-12](../adr/ADR-12-datenpersistenz-produktion.md)),
App kurz vor der Präsentation einmal "aufwärmen" (einen Request schicken) und einen Screenshot-
Fallback für den Import-Schritt bereithalten, da er am längsten dauert.

**Wechsel zwischen Vortragenden:** Falls die Demo von einer anderen Person übernommen wird als
Bereich 1/2, kurzer Screen-Handoff einplanen (nicht Teil der 10 Minuten, aber im Ablauf
berücksichtigen).

---

### Vollständige Feature-Liste & Zuordnung zu den Präsentationen

Es gibt **zwei Präsentationen**: die Abschlusspräsentation (dieses Dokument, 10-Minuten-Demo
B30–B34 oben) und **eine Woche vorher eine verkürzte Testpräsentation** mit demselben Team, aber
weniger Zeit für die Demo. Tabelle unten listet **jedes einzelne Feature** (= jede User Story,
[docs/requirements/](../requirements/)) mit Ist-Stand, Zuordnung zu den beiden Präsentationen und
1–2 Zusatzpunkten pro Feature, falls die Demo schneller geht als geplant.

**Annahme, die im Team noch bestätigt werden muss:** Die Testpräsentation hat ca. 4–5 Minuten für
die Demo (die Hälfte der Abschlusspräsentation) — falls das Team einen anderen Wert festlegt,
Tabelle und T30–T33 unten entsprechend anpassen (siehe „Offene Punkte").

**Hauptnutzen — Pflicht an beiden Präsentationen:** PDF-Import (US-04) → Kategorisierung
automatisch + 1 manuelle Korrektur (US-05) → Safe-to-Spend-Betrag (US-06). Das ist die Kernkette
aus B31–B33 und entspricht Laras `Core Value` aus [README.md](../../README.md) — unabhängig vom
Zeitbudget nicht kürzbar, da sonst der eigentliche Produktnutzen an keiner der beiden
Präsentationen gezeigt wird.

| US | Feature | Status | Testpräsentation | Abschlusspräsentation | Puffer, falls Zeit übrig |
| --- | ------- | ------ | ----------------- | ---------------------- | ------------------------- |
| US-01 | Login / Registrierung | ✅ implementiert | indirekt — Lara ist vorbereitet, kein Live-Login-Screen (T30) | ja — B30, Live-Registrierung mit Auto-Login | (1) zweiten Login-Versuch mit falschem Passwort zeigen (401) · (2) JWT als httpOnly-Cookie kurz erwähnen (Bezug ADR-7, Bereich 1) |
| US-02 | Datenschutz: Konto löschen | ✅ implementiert (Consent-Checkbox bei Registrierung ⚠️ noch nicht umgesetzt) | nein | ja — B34, Bestätigungsdialog mit Passwort, Login danach schlägt fehl | (1) fehlgeschlagenen Login-Versuch nach Löschung zeigen · (2) erwähnen, dass Transaktionen/Fixkosten mitgelöscht werden, nicht nur der Useraccount |
| US-03 | Fixkosten-Wizard (Onboarding) | ✅ implementiert | verkürzt — nur 1 Position statt aller 3 (T30) | ja — B30, alle Positionen aus der Tabelle oben | (1) Validierungsfehler zeigen (negativer/leerer Betrag) · (2) danach unter „Fixkosten" eine weitere Position live nachtragen |
| **US-04** | **PDF-Upload** | ✅ **Hauptnutzen** | **ja — Kernstück (T31)** | **ja — Kernstück (B31)** | (1) Fortschrittsanzeige/Polling kurz erklären (asynchron seit ADR-14) · (2) Client-Validierung zeigen (falsches Format oder >10 MB wird sofort abgelehnt) |
| **US-05** | **Kategorisierung (automatisch + manuell)** | ✅ **Hauptnutzen** | **ja — Kernstück, 1 Korrektur (T32)** | **ja — Kernstück, 1 Korrektur (B32)** | (1) zweite Korrektur zeigen, diesmal ein Lookup- statt Claude-Fall · (2) Lerneffekt erklären: derselbe Händler wird beim nächsten Import automatisch richtig zugeordnet (B14) |
| **US-06** | **Safe-to-Spend** | ✅ **Hauptnutzen** | **ja — Kernstück (T33)** | **ja — Kernstück (B33)** | (1) Wochen- vs. Monatsbetrag kurz gegenüberstellen · (2) negatives Budget-Banner erwähnen, falls Laras Zahlen es nicht ohnehin zeigen |
| US-07 | Sparziel | ❌ **nicht implementiert** (Could, kein Code — nur Requirements-Doku) | nein — nicht demofähig | nein — nicht demofähig | entfällt; auf Nachfrage: „auf der Roadmap, aber MoSCoW Could" |
| US-08 | Wiederkehrende Ausgaben (Abo-Übersicht) | ✅ implementiert (`/abos`, Teaser-Card auf dem Dashboard, Notification-Glocke) | nein (Zeitgründe) | als Puffer nach B34 (bereits vorgesehen, siehe oben) | (1) Notification-Glocke zeigen, die auf ein erkanntes Abo hinweist · (2) „Kein Abo"-Verneinung zeigen, Abo bleibt trotzdem sichtbar |
| US-09 | KI-Monatsbericht | ❌ **nicht implementiert** (Should — nur ein leerer Package-Stub, keine Klassen) | nein | nein | entfällt; auf Nachfrage: als offene Lücke benennen, nicht beschönigen |
| US-10 | Monatsvergleich | ❌ nicht als eigenes Feature (Could) — die Drei-Monats-Tabelle unter dem Safe-to-Spend-Widget deckt Einnahmen/Ausgaben/Differenz teilweise ab | nein | optional als Puffer: Drei-Monats-Tabelle unter B33 mitzeigen | (1) zeigen, dass ein Monat ohne Daten sauber mit „–" statt „0.00" behandelt wird |
| US-11 | OpenBanking | ❌ explizit kein MVP (eigene Notiz in US-11: „Implementierung erst nach Abschluss aller Must-/Should-Stories") | nein | nein | entfällt; auf Nachfrage: bewusste Priorisierungsentscheidung, konsistent mit MoSCoW aus B21 |
| US-12 | Monatswechsel | ✅ implementiert (Month-Nav auf Dashboard & Kategorien) | nein (Demo bleibt im aktuellen Monat) | implizit sichtbar über B33, auf Wunsch 1 Klick in den Vormonat | (1) einen Klick in den Vormonat zeigen · (2) „Abgeschlossen"-Banner für vergangene Monate erklären (kein Safe-to-Spend für die Vergangenheit) |
| US-13 | Transaktionen pro Kategorie einsehen | ✅ implementiert (Drilldown auf `/categories`) | nein | optional als Puffer nach B32: eine Kategorie aufklappen | (1) Kategorie aufklappen und Einzeltransaktionen zeigen · (2) „mehr laden" bei vielen Transaktionen zeigen |
| US-14 | Einstellungen (Passwort/Einkommen/Theme) | ✅ implementiert (`/einstellungen`) | nein | Einkommen live in B30; Passwort/Theme nur auf Nachfrage | (1) Theme-Umschaltung Light/Dark zeigen · (2) Einkommensvorschlag „Übernehmen" aus erkannten Gutschriften erklären |

### Testpräsentation — verkürzter Demo-Ablauf (T30–T33)

Anders als B30 **keine Live-Registrierung**: Zeitersparnis, indem `lara@demo.bb` vorher regulär
über `backend/tools/seed_demo_accounts.sh` angelegt wird (so, wie es für die Abschlusspräsentation
explizit **nicht** gemacht werden darf, siehe „Konsequenz fürs Seed-Skript" oben — für die
Testpräsentation ist das hier der richtige, zeitsparende Weg). Kein B34 (Konto löschen) in der
Testpräsentation — daher **nach der Testpräsentation `lara@demo.bb` manuell zurücksetzen**
([docs/demo/README.md → Zurücksetzen](../demo/README.md#zurücksetzen)), sonst schlägt die Live-
Registrierung in B30 der Abschlusspräsentation eine Woche später mit `409` fehl.

| Nr | Min | Flow | Unterschied zu B30–B34 |
| --- | --- | ---- | ------------------------ |
| T30 | 0–1 | Kontext + Dashboard als bereits eingeloggte Lara | Kein Live-Registrieren/Onboarding-Wizard; Fixkosten/Einkommen nur kurz auf dem Dashboard zeigen statt Klick für Klick |
| T31 | 1–2.5 | PDF-Import | Wie B31, ungekürzt — Teil des Hauptnutzens |
| T32 | 2.5–4 | Kategorisierung + 1 Korrektur | Wie B32, ungekürzt — Teil des Hauptnutzens |
| T33 | 4–5 | Safe-to-Spend | Wie B33, ungekürzt — Teil des Hauptnutzens |

Kein Zusatzfeature/Puffer-Slot fest eingeplant — bei Testpräsentationen bleibt der Fokus auf dem
Hauptnutzen, damit die Zeitmessung realistisch abbildet, wie lang die **Kernkette** tatsächlich
dauert (das ist der Teil, der an der Abschlusspräsentation ohnehin nicht gekürzt werden darf).

---

## Offene Punkte

- [ ] Namen den drei Bereichen zuordnen (Tabelle oben)
- [ ] Folien/Slides aus dieser Struktur bauen (Format noch offen: Markdown-Slides, PowerPoint, Artifact — im Team entscheiden)
- [ ] Demo-Kontoauszüge für den Präsentationsmonat neu generieren und Login vorab testen
- [ ] Zeitprobe: einmal laut durchsprechen, insbesondere Bereich 3 (Live-Demo läuft oft länger als geplant)
- [ ] **B14/B22 gehen vom gemergten Zustand von [PR #320](https://github.com/dfme/budget-buddy/pull/320)
      (BE-CAT-11) und [PR #323](https://github.com/dfme/budget-buddy/pull/323) (BE-CAT-12, ADR-15) aus** —
      Stand 18.09.2026 sind beide noch **offen**. Vor dem Vortrag verifizieren, dass sie tatsächlich
      gemerged sind; falls nicht, B14 zurück auf den Vor-Merge-Stand (nur manuelle Korrektur trainiert,
      Issue #314 offen) und die ADR-Zahl in B22 zurück auf 15.
      → **Update 20.09.2026:** Auf `main` sind beide inzwischen gemerged (BE-CAT-11 #320, BE-CAT-12
      #323) — B14/B22 stimmen mit `main` überein. Dieser Branch (`feature/abschlusspraesentation`) lag
      beim Schreiben der Feature-Liste unten 39 Commits hinter `main`; die Feature-Liste wurde gegen
      `main` verifiziert (u. a. `/abos`-Route und „Konto löschen"-Dialog existieren dort bereits), vor
      dem Vortrag trotzdem `git merge main` o. Ä. auf diesem Branch nicht vergessen.
- [ ] **Dauer der Testpräsentations-Demo (T30–T33) im Team bestätigen** — in der Feature-Liste oben
      mit ca. 4–5 Minuten angenommen (Hälfte der Abschlusspräsentation); falls das Team einen anderen
      Wert festlegt, Tabelle und T30–T33 entsprechend anpassen.
