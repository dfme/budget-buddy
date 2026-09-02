# Zuschnitt: US-08, US-12

Vorbereitender Zuschnitt für die Issue-Erstellung (noch keine GitHub-Issues angelegt). US-02
bleibt bewusst ausserhalb des Scopes.

Story-Points folgen der Skala aus [SPRINT-05](sprints/SPRINT-05.md) (Fibonacci-nah, DB-Migrationen
und E2E-Tests als Referenzgrössen: einfache Migration = 1 SP, Playwright-Testfall = 3 SP).

## Fundament: In-App-Benachrichtigungen (7 SP)

US-08 ("Neu"-Label + In-App-Benachrichtigung bei neu erkanntem Abo) verlangt einen
Benachrichtigungsmechanismus. Es existiert im ganzen Projekt noch keiner — weder Backend noch
Frontend (`shared/notice/` ist eine inline Banner-Komponente, die z. B. in Dashboard,
Kategorie-Übersicht und PDF-Upload steckt, aber kein Inbox-/Toast-System).

**Vorschlag:** ein schlankes, generisches `notification/`-Modul (analog zur bestehenden
Package-pro-Domäne-Struktur), das US-08 als Abhängigkeit nutzt — nicht als Teil der Story selbst,
sondern als eigener, kleiner Vorlauf-Block.

| ID | Titel | Beschreibung | SP | Abhängig von |
| -- | ----- | ------------ | -- | ------------- |
| `DB-08` | Flyway V08: `notifications`-Tabelle | `id, user_id, type, reference_id, message, read_at, created_at`. `type` grenzt spätere Quellen ab (`RECURRING_EXPENSE_DETECTED`, `MONTHLY_REPORT_READY`, …). | 1 | — |
| `BE-NOTIF-01` | NotificationService + REST-Endpoints | `NotificationService.create(userId, type, referenceId, message)` als einfacher Port, den andere Module aufrufen; `GET /api/notifications` (ungelesen zuerst), `POST /api/notifications/{id}/read`. | 3 | `DB-08` |
| `FE-NOTIF-01` | Notification-Glocke in der App-Shell | Badge mit Anzahl ungelesen, Dropdown-Liste, Markieren als gelesen. Lazy-Load beim Login, kein Polling-Intervall im MVP (reicht: Laden bei Navigation/Fokus). | 3 | `BE-NOTIF-01` |

Ohne dieses Fundament bräuchte US-08 einen eigenen Bolt-on für Benachrichtigungen — sauberer als
eigener Vorlauf-Block, deshalb zuerst.

---

## US-12 — Zwischen Monaten wechseln (Should, 10 SP)

Kleinster Zuschnitt der beiden: kein neues Modul, keine Migration. Die Kategorie-Übersicht hat
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

## Reihenfolge (Abhängigkeits-Empfehlung)

```
US-12  BE-STS-06 → FE-STS-04 → E2E-STS-02     (FE-CAT-08 parallel, unabhängig)
                                                          │
Fundament   DB-08 → BE-NOTIF-01 → FE-NOTIF-01 ────────────┤
                                                          │
US-08        DB-09 → BE-REC-01 → BE-REC-02 → FE-REC-01 → E2E-REC-01
```

1. **US-12 zuerst** — keine Abhängigkeit zum Fundament, kleinster Zuschnitt, schnellster Abschluss.
2. **Notification-Fundament** direkt danach oder parallel zu US-12 — blockiert US-08, deshalb
   nicht aufschieben.
3. **US-08 zuletzt** — baut auf dem Fundament auf, geringste Priorität der verbleibenden Stories.

Gesamt: 4 (US-12) + 3 (Fundament) + 5 (US-08) = **12 Issues** über die zwei Stories plus
Fundament.

**Story Points:** 10 (US-12) + 7 (Fundament) + 17 (US-08) = **34 SP**. Für eine Sprint-Einplanung
eignet sich daher entweder eine Aufteilung über zwei Sprints oder eine Priorisierung innerhalb
dieses Zuschnitts — Entscheidung bleibt beim Team, nicht Teil dieses Dokuments.
