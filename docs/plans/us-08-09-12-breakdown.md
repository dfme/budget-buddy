# Zuschnitt: US-08, US-09, US-12

Vorbereitender Zuschnitt für die Issue-Erstellung (noch keine GitHub-Issues angelegt). US-02
bleibt bewusst ausserhalb des Scopes.

Story-Points folgen der Skala aus [SPRINT-05](sprints/SPRINT-05.md) (Fibonacci-nah, DB-Migrationen
und E2E-Tests als Referenzgrössen: einfache Migration = 1 SP, Playwright-Testfall = 3 SP).

## Gemeinsames Fundament: In-App-Benachrichtigungen (7 SP)

US-08 ("Neu"-Label + In-App-Benachrichtigung bei neu erkanntem Abo) und US-09 (In-App-Benachrichtigung
bei neuem Bericht) verlangen beide einen Benachrichtigungsmechanismus. Es existiert im ganzen
Projekt noch keiner — weder Backend noch Frontend (`shared/notice/` ist eine inline Banner-Komponente
für Formulare, kein Inbox-/Toast-System). Zwei unabhängige Ad-hoc-Lösungen würden dieselbe
Funktion zweimal bauen und vermutlich divergieren.

**Vorschlag:** ein schlankes, generisches `notification/`-Modul (analog zur bestehenden
Package-pro-Domäne-Struktur), das US-08 und US-09 als Abhängigkeit nutzen — nicht als Teil einer
der beiden Stories, sondern als eigener, kleiner Vorlauf-Block.

| ID | Titel | Beschreibung | SP | Abhängig von |
| -- | ----- | ------------ | -- | ------------- |
| `DB-08` | Flyway V08: `notifications`-Tabelle | `id, user_id, type, reference_id, message, read_at, created_at`. `type` grenzt spätere Quellen ab (`RECURRING_EXPENSE_DETECTED`, `MONTHLY_REPORT_READY`, …). | 1 | — |
| `BE-NOTIF-01` | NotificationService + REST-Endpoints | `NotificationService.create(userId, type, referenceId, message)` als einfacher Port, den andere Module aufrufen; `GET /api/notifications` (ungelesen zuerst), `POST /api/notifications/{id}/read`. | 3 | `DB-08` |
| `FE-NOTIF-01` | Notification-Glocke in der App-Shell | Badge mit Anzahl ungelesen, Dropdown-Liste, Markieren als gelesen. Lazy-Load beim Login, kein Polling-Intervall im MVP (reicht: Laden bei Navigation/Fokus). | 3 | `BE-NOTIF-01` |

Ohne dieses Fundament brauchen US-08 und US-09 je einen eigenen Bolt-on — das ist der einzige
Punkt in diesem Zuschnitt, der beide Stories blockiert, deshalb zuerst.

---

## US-12 — Zwischen Monaten wechseln (Should, 10 SP)

Kleinster Zuschnitt der drei: kein neues Modul, keine Migration. Die Kategorie-Übersicht hat
Monatswechsel bereits (`FE-CAT-04`, #144) inkl. Query-Param-Sync und `MonthNav`-Komponente
(`frontend/src/app/shared/month-nav/month-nav.ts`) — der fehlt aber der "Keine Daten"-Hinweis laut
AC, und das Dashboard/Safe-to-Spend kennt bislang gar keinen Monat (`SafeToSpendService.calculate`
ist fest auf den aktuellen Kalendermonat verdrahtet).

| ID | Titel | Beschreibung | SP | Abhängig von |
| -- | ----- | ------------ | -- | ------------- |
| `BE-STS-06` | Safe-to-Spend: Monat-Parameter + Abgeschlossen-Status | `SafeToSpendService.calculate(userId, YearMonth)` statt fest auf "heute"; `BudgetController` bekommt `@RequestParam String month` (optional, Default = laufender Monat, `MonthParser` wiederverwenden). Für Monate < aktuellem Monat: Response-Variante `status=CLOSED` statt Wochenbudget-Berechnung. | 3 | — |
| `FE-CAT-08` | Kategorie-Übersicht: "Keine Daten"-Hinweis | Fehlt laut AC noch: Hinweistext "Keine Daten für [Monat Jahr] — PDF hochladen?" wenn der gewählte Monat nicht in `availableMonths()` enthalten ist. Kleine Ergänzung zu #144, kein neuer Screen. | 1 | — |
| `FE-STS-04` | Dashboard-Monatswechsel | `MonthNav` (bestehende Komponente) im Dashboard einbinden, gleiches Pattern wie `category-overview.ts` (Monat-Signal, Query-Param-Sync). "Abgeschlossen"-Banner für Vergangenheitsmonate, "Keine Daten"-Hinweis analog `FE-CAT-08`. | 3 | `BE-STS-06` |
| `E2E-STS-02` | Playwright: Monatswechsel | Happy Path: Default = aktueller Monat, Wechsel zu Vormonat → "Abgeschlossen" auf Dashboard + Kategorien + Safe-to-Spend synchron. Fehlerpfad: Monat ohne importierte Daten → Hinweis + Upload-CTA. | 3 | `FE-STS-04` |

`FE-CAT-08` ist unabhängig von der `BE-STS-06`-Kette und kann parallel/zuerst erledigt werden.

---

## US-08 — Wiederkehrende Ausgaben (Abos) erkennen (Should, 17 SP)

Baut auf dem Notification-Fundament auf. Erkennung läuft am Ende des bestehenden Async-Import-Flows
(`ImportJobRunner`, ADR-14) — analog zu `FixedCostDebitMatcher`, kein neuer Scheduler nötig.
"Kein Abo" muss dauerhaft wirken, deshalb Persistenz statt reiner Laufzeit-Berechnung.

| ID | Titel | Beschreibung | SP | Abhängig von |
| -- | ----- | ------------ | -- | ------------- |
| `DB-09` | Flyway V09: `recurring_expenses`-Tabelle | `id, user_id, payee_key, amount, status ('DETECTED'\|'DISMISSED'), first_detected_month, created_at`. `payee_key` = normalisierter Empfänger (gleiche Normalisierung wie `category_lookup`: Grossschreibung, Vergleich über `upper()`). | 1 | — |
| `BE-REC-01` | RecurringExpenseService: Erkennung | Gruppierung nach `payee_key` + Betrag (±2% Toleranz) über ≥2 aufeinanderfolgende Monate. Aufruf am Ende von `ImportJobRunner` nach der Kategorisierung. Bei neu erkannter Gruppe: Eintrag `status=DETECTED` + `NotificationService.create(..., RECURRING_EXPENSE_DETECTED)`. | 5 | `DB-09`, `BE-NOTIF-01` |
| `BE-REC-02` | REST-Endpoints Abo-Übersicht | `GET /api/recurring-expenses` (inkl. "Neu"-Flag = seit letztem Login/Read nicht gesehen), `POST /api/recurring-expenses/{id}/dismiss` ("Kein Abo" → `status=DISMISSED`, `payee_key` wird künftig von der Erkennung ausgeschlossen). | 3 | `BE-REC-01` |
| `FE-REC-01` | Abo-Übersicht-Screen | Neue Route/Component: gruppierte Liste, "Neu"-Label, "Kein Abo"-Button, Einstieg vom Dashboard aus (Teaser-Card, z. B. "3 Abos erkannt"). | 5 | `BE-REC-02`, `FE-NOTIF-01` |
| `E2E-REC-01` | Playwright: Abo-Erkennung | Happy Path: gleicher Empfänger/Betrag in 2 Folgemonaten importiert → erscheint mit "Neu"-Label + Benachrichtigung. Fehlerpfad/Alt-Pfad: "Kein Abo" entfernt den Eintrag dauerhaft, auch nach erneutem Import. | 3 | `FE-REC-01` |

---

## US-09 — KI-generierter Monatsbericht (Should, 26 SP + Stretch)

Grösster Zuschnitt der drei, aus zwei Gründen: erster `@Scheduled`-Trigger im Projekt (bisher nur
`@Async`, kein zeitgesteuerter Job), und der optionale E-Mail-Versand aus der AC ist echte neue
Infrastruktur (kein Mail-Dependency im Backend vorhanden). Claude-Anbindung folgt dem bestehenden
Port-Pattern aus `categorization/` (`CategorizationPort` → `ClaudeCategorizationService`).

| ID | Titel | Beschreibung | SP | Abhängig von |
| -- | ----- | ------------ | -- | ------------- |
| `DB-10` | Flyway V10: `monthly_reports`-Tabelle + `report_email_enabled`-Spalte | `monthly_reports(id, user_id, month, total_expenses, top_categories, savings_suggestion, status, generated_at)`; `users.report_email_enabled BOOLEAN DEFAULT false`. | 2 | — |
| `BE-RPT-01` | `AiReportPort` + `ClaudeReportService` | Analog `CategorizationPort`/`ClaudeCategorizationService`: `claude-sonnet-5`, Structured Output (Gesamtausgaben, Top-3-Kategorien mit Betrag + Prozent, ≥1 Sparvorschlag mit CHF-Betrag), Timeout + Fallback-Verhalten (Circuit-Breaker-Pattern wiederverwenden). | 5 | `DB-10` |
| `BE-RPT-02` | AiReportGenerationService + monatlicher Trigger | Prüft Vorbedingung (≥28 Tage Daten, Monat abgeschlossen), ruft `AiReportPort`, persistiert Ergebnis, triggert Notification. **Erster `@Scheduled`-Job im Projekt** — täglicher Cron prüft pro Nutzer, ob ein neuer Kalendermonat begonnen hat und noch kein Bericht für den Vormonat existiert. Bei Claude-Fehler: letzter erfolgreicher Bericht bleibt sichtbar (kein Overwrite). | 5 | `BE-RPT-01`, `BE-NOTIF-01` |
| `BE-RPT-03` | REST-Endpoints Monatsbericht | `GET /api/reports/latest`, `GET /api/reports/{month}`, `POST /api/reports/{month}/retry` (manueller "Erneut versuchen"-Button). | 3 | `BE-RPT-02` |
| `BE-RPT-04` | Endpoint E-Mail-Opt-in | `PUT /api/users/me/report-email-enabled` (analog `PUT /api/users/me/income`). Nur das Flag — kein Versand. | 1 | `DB-10` |
| `FE-RPT-01` | KI-Bericht-Screen | Neue Route "KI-Bericht": Gesamtausgaben, Top-3-Kategorien, Sparvorschlag; "zu wenig Daten"-Hinweis; Fehler-Hinweis mit Datum des letzten Berichts + "Erneut versuchen". | 5 | `BE-RPT-03`, `FE-NOTIF-01` |
| `FE-SET-05` | Einstellungen: E-Mail-Opt-in-Checkbox | Checkbox "Monatsbericht per E-Mail erhalten" im Settings-Screen, analog zu FE-SET-02/03. | 2 | `BE-RPT-04` |
| `E2E-RPT-01` | Playwright: Monatsbericht | Happy Path: Bericht mit Testdaten sichtbar (Top-Kategorien, Sparvorschlag). Fehlerpfad: zu wenig Daten → Hinweis; simulierter Claude-Fehler → letzter Bericht + Retry-Button. | 3 | `FE-RPT-01` |

### Offener Punkt: echter E-Mail-Versand

Die AC verlangt einen *aktivierbaren* E-Mail-Versand, nicht nur das Flag. Echter SMTP-Versand
braucht eine neue Abhängigkeit (`spring-boot-starter-mail` o. ä.), einen Provider (Render hat
keinen eigenen Mail-Dienst) und Templates — das ist ein eigener kleiner Infra-Entscheid, ähnlich
gelagert wie ADR-10 (Hosting) oder ADR-12 (Persistenz), nicht nebenbei in `BE-RPT-04` zu erledigen.

**Empfehlung:** `BE-RPT-04`/`FE-SET-05` liefern das Opt-in und persistieren die Präferenz;
tatsächlicher Versand als eigenes Issue `BE-RPT-05` (abhängig von `BE-RPT-04`) — bewusst als
Stretch-Goal markiert, damit die Story ohne Versand bereits einen vollständigen In-App-Bericht
liefert (Kernwert der Story) und der Team-Entscheid zum Mail-Provider nicht den Rest blockiert.

`BE-RPT-05` bekommt hier bewusst **keine SP** — analog zu `INFRA-26` in SPRINT-05: ohne
Provider-Entscheid (Render hat keinen eigenen Mail-Dienst) ist der Aufwand nicht seriös
schätzbar. Grobe Grössenordnung nach Entscheid: 3–5 SP für Anbindung + Template.

---

## Reihenfolge (Abhängigkeits-Empfehlung)

```
US-12  BE-STS-06 → FE-STS-04 → E2E-STS-02     (FE-CAT-08 parallel, unabhängig)
                                                          │
Fundament   DB-08 → BE-NOTIF-01 → FE-NOTIF-01 ────────────┤
                                                          │
US-08        DB-09 → BE-REC-01 → BE-REC-02 → FE-REC-01 → E2E-REC-01
                                                          │
US-09  DB-10 → BE-RPT-01 → BE-RPT-02 → BE-RPT-03 → FE-RPT-01 → E2E-RPT-01
                       └→ BE-RPT-04 → FE-SET-05           (→ BE-RPT-05, Stretch)
```

1. **US-12 zuerst** — keine Abhängigkeit zum Fundament, kleinster Zuschnitt, schnellster Abschluss.
2. **Notification-Fundament** direkt danach oder parallel zu US-12 — blockiert sowohl US-08 als
   auch US-09, deshalb nicht aufschieben.
3. **US-08 vor US-09** — geringere Komplexität (kein neuer Scheduler, keine neue externe
   Abhängigkeit), gleiche Grössenordnung an Nutzen für Marc.
4. **US-09 zuletzt** — grösster Zuschnitt, plus der offene Punkt zum E-Mail-Versand sollte vor
   Sprint-Zusage geklärt sein (Scope-Cut auf `BE-RPT-05` oder volle AC-Erfüllung im selben Sprint).

Gesamt: 3 (US-12) + 3 (Fundament) + 5 (US-08) + 9 inkl. Stretch (US-09) = **20 Issues** über die
drei Stories plus Fundament.

**Story Points:** 10 (US-12) + 7 (Fundament) + 17 (US-08) + 26 (US-09, ohne `BE-RPT-05`) =
**60 SP** — grob eine ganze Sprint-Kapazität (Referenz SPRINT-05: 55–60 SP). Für eine
Sprint-Einplanung eignet sich daher entweder eine Aufteilung über zwei Sprints (z. B. US-12 +
Fundament + US-08 in Sprint N, US-09 in Sprint N+1) oder eine Priorisierung innerhalb dieses
Zuschnitts — Entscheidung bleibt beim Team, nicht Teil dieses Dokuments.
