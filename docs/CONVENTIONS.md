# Konventionen

Referenzdokument zu [CLAUDE.md](../CLAUDE.md), das die wichtigsten Regeln in Kurzform enthält.
Hier stehen die vollständigen Tabellen und Begründungen.

## Git: Branching-Strategie

| Branch         | Zweck                              | Format                         |
| -------------- | ----------------------------------- | ------------------------------- |
| `main`         | Production-ready — immer deploybar | —                               |
| Feature Branch | Für Tasks/User Stories             | `feature/<TASK-ID>-<Freitext>` |
| Bugfix Branch  | Für Bugfixes                       | `fix/<TASK-ID>-<Freitext>`     |

Beispiele: `feature/US-04-pdf-upload`, `fix/INFRA-05-cors-header`

Regel: Kein direkter Commit auf `main`. Jeder Branch wird per Pull Request gegen `main` gemergt.

## Git: Task-ID-Konvention (GitHub Issues)

Issue-Titel folgen dem Format `[TASK-ID] Kurzbeschreibung`. Die Task-ID kodiert Bereich und Feature:

| Präfix | Bereich | Beispiel |
| ------ | ------- | -------- |
| `INFRA-XX` | Infrastruktur / DevOps | `INFRA-01`, `INFRA-05` |
| `DB-XX` | Datenbank / Flyway-Migrationen | `DB-01`, `DB-05` |
| `BE-AUTH-XX` | Backend — Authentifizierung | `BE-AUTH-01` |
| `BE-FC-XX` | Backend — Fixkosten | `BE-FC-01` |
| `BE-PDF-XX` | Backend — PDF-Import | `BE-PDF-01` |
| `BE-CAT-XX` | Backend — Kategorisierung | `BE-CAT-02` |
| `BE-STS-XX` | Backend — Safe-to-Spend | `BE-STS-01` |
| `FE-FC-XX` | Frontend — Fixkosten | `FE-FC-01` |
| `FE-PDF-XX` | Frontend — PDF-Upload | `FE-PDF-01` |
| `FE-CAT-XX` | Frontend — Kategorisierung | `FE-CAT-01` |
| `FE-STS-XX` | Frontend — Safe-to-Spend | `FE-STS-01` |
| `FE-SET-XX` | Frontend — Einstellungen | `FE-SET-01` |
| `E2E-XX-XX` | End-to-End-Tests (Frontend + Backend gemeinsam) | `E2E-AUTH-01`, `E2E-PDF-01` |

Die Task-ID im Issue-Titel wird direkt als `<TASK-ID>` in der Branch-Namenskonvention verwendet.

## Git: Bug-Tickets

Bugs bekommen **keine eigene ID-Reihe**. Die Task-ID kodiert den **Bereich**, nicht den Typ — der Typ steckt bereits im Branch-Präfix (`feature/` vs. `fix/`) und im Label. Regeln:

1. **Neue, freie ID im betroffenen Bereich** — z. B. `INFRA-08` für einen Bug in der CD-Pipeline. Niemals eine bestehende Task-ID wiederverwenden, auch nicht die des Tasks, der den Bug eingebaut hat: Eine ID = eine Arbeitseinheit = ein Branch = ein PR. Wiederverwendung zerstört die Rückverfolgbarkeit.
2. **Bereich = wo der Fix landet.** Ein Bug ist immer einem Bereich zuordenbar, auch wenn er zu keinem bestehenden Task gehört. Bei bereichsübergreifenden Bugs entscheidet der Ort des Fixes.
3. **Typ via Label** — `bug` am Issue setzen. Nicht als Freitext-Präfix in den Titel (`Bug: …`) schreiben.
4. **Branch:** `fix/<TASK-ID>-<Freitext>`.
5. **Titel wie bei jedem Issue:** `[TASK-ID] Kurzbeschreibung`.

Beispiel: [#68](https://github.com/dfme/budget-buddy/issues/68) — `[INFRA-08] Smoke-Test verifiziert nicht die neue Version`, Label `bug`, Branch `fix/INFRA-08-deploy-version-check`.

## Git: Review-Konvention

1. **Lokaler Review durch Claude** — bevor ein PR erstellt wird, prüft Claude die Änderungen lokal
2. **PR-Erstellung in GitHub** — erst nach erfolgreichem lokalem Review wird der PR in GitHub erstellt
3. **Freigabe durch mind. 1 Dev** — der PR muss von mindestens einem Dev genehmigt werden, bevor er gemergt werden darf
4. **Merge nur durch Dev** — der Merge auf `main` wird ausschliesslich von einem Dev getriggert, nie von Claude

**Automatisches Review (INFRA-31):** [`.github/workflows/claude-pr-review.yml`](../.github/workflows/claude-pr-review.yml)
läuft denselben Skill zusätzlich automatisch bei jedem PR-Event — das ergänzt Punkt 1, ersetzt
aber nicht Punkt 3 (ein Action-Lauf ist kein Dev) und bewegt keine Board-Karte. Details:
[.claude/skills/README.md](../.claude/skills/README.md#automatischer-trigger-via-github-action).

## Sprint-Planung: Iteration-Feld ist führend

Der Sprint wird **ausschliesslich** über das Iteration-Feld `Sprint` im Project Board [#4 „BudgetBuddy Sprint Board"](https://github.com/users/dfme/projects/4) gesetzt — Milestones werden dafür nicht verwendet. Die bestehenden Milestones `Sprint 1`–`Sprint 3` sind Historie und geschlossen; es werden keine weiteren angelegt. Neue Issues landen ohne Milestone/Sprint via `Auto-add to project` im Board-Backlog; die Einplanung erfolgt im Sprint-Planning über das Sprint-Feld.

**Grund:** Milestone und Iteration-Feld sind voneinander unabhängig gepflegt und liefen im Juli 2026 nachweislich auseinander (Milestones sagten Sprint 3, das Board sagte Sprint 2). Das Iteration-Feld ist die richtige führende Quelle, weil es den laufenden Sprint anhand des Datums kennt und nicht manuell geschlossen werden muss.

## Datenbank: Flyway-Migrationen

- Versionsnummer immer **zweistellig mit führender Null**: `V01__`, `V02__`, … `V10__`. Sichert korrekte alphabetische Sortierung im Dateisystem bei vielen Migrationen.
- Dateiname: `V<NN>__<snake_case_beschreibung>.sql` (z. B. `V01__create_users_table.sql`).
- Geldbeträge als `DECIMAL(10,2)`, nie `FLOAT`/`REAL` (siehe ADR-9).

## Backend: Package-Struktur (Modular Monolith)

Packages nach Domäne, nicht nach Schicht, unterhalb von `backend/src/main/java/com/budgetbuddy/`:

```
backend/
  └── src/main/java/com/budgetbuddy/
        ├── auth/           (AuthController, AuthService, User-Entity, JWT-Config)
        ├── transaction/    (TransactionController, PdfImportService, Transaction-Entity)
        ├── categorization/ (CategorizationService, LookupTable, CategorizationPort)
        ├── budget/         (BudgetController, SafeToSpendService, SavingsGoalService)
        └── report/         (ReportController, AiReportService)
```

Regel: Kein direkter Zugriff auf Repositories oder Services eines anderen Moduls. Cross-Modul-Kommunikation nur über definierte Interfaces.

## Backend: REST-Endpoints unter `/api/**`

Jeder Controller bekommt `/api` als Präfix im klassenweiten `@RequestMapping` (z. B. `/api/auth`,
`/api/transactions`). Der Präfix trennt REST-API strukturell von den client-seitigen
Angular-Routen: `SecurityConfig` gibt jedes GET ausserhalb von `/api/**` als SPA-Shell frei,
`SpaForwardController` forwardet es per Catch-all auf `index.html` — ohne enumerierte
Routen-Liste, die mit `app.routes.ts` synchron gehalten werden müsste.

Regel: Kein Controller ohne `/api`-Präfix. Ein neuer Endpoint ausserhalb davon würde entweder vom
Catch-all geschluckt oder unbeabsichtigt öffentlich sein.

## Frontend: Feature-Struktur (nach Domäne)

Angular Feature-Folders analog zu den Backend-Modulen, unterhalb von `frontend/src/app/`:

```
frontend/
  └── src/app/
        ├── auth/          (US-01: Login/Register)
        ├── onboarding/    (US-03: Fixkosten-Wizard)
        ├── transactions/  (US-04: Upload, US-05: Kategorisierung, US-13: pro Kategorie)
        ├── dashboard/     (US-06: Safe-to-Spend, US-10: Monatsvergleich, US-12: Monatswechsel)
        ├── savings/       (US-07: Sparziel)
        ├── reports/       (US-09: KI-Monatsbericht)
        ├── settings/      (US-02: Consent/Löschen, US-14: Passwort/Einkommen)
        ├── shared/        (domänenübergreifende UI-Komponenten, Pipes)
        └── core/          (Guards, Auth-State, HTTP-Error-Handling)
```

Regel: Kein NgRx — State liegt direkt in den Feature-Services via Signals.

## E2E: Verzeichnisstruktur

Playwright-Tests in eigenem Verzeichnis ausserhalb von `backend/` und `frontend/`, da sie Frontend und Backend gemeinsam end-to-end testen:

```
e2e/
  ├── tests/      (1 Testfall pro Must-Have User Story: US-03, US-04, US-05, US-06)
  ├── fixtures/   (auth.fixture.ts — eingeloggte Session als Vorbedingung)
  └── support/    (Port, Pfade, JAR-Auflösung, DB-Reset der Testinstanz)
```

Regel: Pro Must-Have Story je 1 Happy Path + 1 Fehlerpfad.

Getestet wird gegen **ein** JAR aus dem Maven-Profil `prod` auf Port 8081 (nicht 8080 — der gehört dem Dev-Backend): es liefert SPA und API vom selben Origin und ist damit dasselbe Artefakt, das auf Render deployt wird. Nur so verhält sich das `SameSite=Strict`-JWT-Cookie im Test wie in Produktion (ADR-7). Identisch ist das Artefakt, nicht die Laufzeitkonfiguration: die Testinstanz läuft ohne `SPRING_PROFILES_ACTIVE=prod` und damit ohne `app.cookie.secure=true` — das `Secure`-Flag ist über `JwtCookieFactoryTest` abgedeckt, nicht über E2E. Setup, Fixture-Nutzung und CI-Einbindung: [e2e/README.md](../e2e/README.md).

## Backend: Claude API hinter Interface

Die Claude-API immer hinter einem `CategorizationPort`-Interface kapseln:

```java
public interface CategorizationPort {
    Category categorize(String transactionText);
}
```

Das erlaubt Mock in Tests und Austausch des Modells ohne Refactoring im Rest der Codebase.

## Backend: Import Flow

Zweistufig seit ADR-14 (BE-PDF-09). Der vorher vollständig synchrone Flow lief bei ~110
Transaktionen reproduzierbar ins 30-Sekunden-Budget und verwarf dabei den gesamten Import (#192).

| Phase | Wo | Dauer | Fehler |
| ----- | -- | ----- | ------ |
| Hash, Duplikatcheck, Parse, `ImportJob` anlegen | im Request (`PdfImportService`) | ~2s | 400 mit `reason`, 408, 409, 413 |
| Kategorisierung, Persistierung | `@Async` (`ImportJobRunner`) | Sekunden | Job-Status `FAILED` |

`POST /api/import/pdf` antwortet mit `202 Accepted` und `{jobId, total}`;
`GET /api/import/{jobId}/status` liefert `{status, total, processed, degraded}`. Das Frontend
pollt und zeigt `processed`/`total` als Fortschrittsbalken. Kein Kafka, kein Redis — ein
begrenzter `ThreadPoolTaskExecutor` (`AsyncConfig`) genügt.

Regel: Der Schnitt liegt **nach** dem Parsen. Nur der lange Teil gehört in den Hintergrund; alle
Fehler des Parsens bleiben gewöhnliche HTTP-Fehler, die der Nutzer sofort erfährt.

**Zwei Zeitbudgets:** `budgetbuddy.import.timeout-seconds` (30) gilt nur noch fürs Parsen —
überschritten → 408, kein Job angelegt. `budgetbuddy.import.categorization-timeout-seconds` (300)
ist der Watchdog des Hintergrundlaufs; überschritten wird **nicht** abgebrochen, sondern der Rest
ohne Claude-Call als `Sonstiges` gespeichert (`degraded = true`). Ein Import geht nie mehr
verloren.

## Sicherheit: Keine Secrets im Git

Credentials, API-Keys und Passwörter dürfen nie ins Git-Repository gelangen. `.env`-Dateien müssen in `.gitignore` stehen. Der `ANTHROPIC_API_KEY` und der JWT-Secret werden ausschliesslich als Umgebungsvariablen übergeben — nie hardcodiert im Code oder in `application.properties`. Bei versehentlichem Commit: sofortiger Key-Rotation + Incident-Assessment nach nDSG.

## Backend: Geldbeträge immer als `BigDecimal`

Alle CHF-Beträge — in Entities, Services, DTOs und Berechnungen — müssen `BigDecimal` verwenden. `double` und `float` sind verboten (ADR-9: Binäre Gleitkomma-Arithmetik kann CHF-Beträge nicht exakt darstellen und erzeugt Rundungsfehler in der Safe-to-Spend-Berechnung).

In der Datenbank: `DECIMAL(10,2)`. Beim Parsen von PDF-Beträgen: `replace("'", "")` vor `new BigDecimal(...)`.

## Backend: Timeouts + Fallback für externe Calls

Alle Calls zu Claude API und PDFBox müssen einen Timeout haben und bei Fehler auf `"Sonstiges"` fallen (Claude API) bzw. den Fehler an den Caller zurückgeben (PDFBox).

Ein fehlgeschlagener Claude-Call darf nie den gesamten Import-Flow blockieren (Churn-Risiko #1).
Dasselbe gilt seit ADR-14 für ein aufgebrauchtes Zeitbudget: Der Rest fällt auf `Sonstiges`, der
Import wird trotzdem vollständig gespeichert.

## Backend: Logging-Kontext (MDC)

User-ID und Request-ID gehören **nicht** in den Log-Text, sondern in den MDC (INFRA-37).
`LoggingContextFilter` vergibt pro Request eine `requestId`, `JwtCookieAuthenticationFilter`
legt nach gültigem JWT die `userId` dazu; `logging.pattern.level` in `application.properties`
stellt beide jeder Zeile voran:

```
2026-09-02T14:56:24.159+02:00  INFO [req:687a85fe user:99] … : Testendpunkt aufgerufen
```

Regel: Keine neue Log-Zeile schreibt die User-ID selbst in ihren Text — sie stünde sonst doppelt.
Wer sie braucht, bekommt sie automatisch.

Zwei Stellen müssen den Kontext aktiv wieder abräumen, weil Threads wiederverwendet werden:
`LoggingContextFilter` (Tomcat-Thread, `finally` — auch im Fehlerfall) und `MdcTaskDecorator` am
`importExecutor` (Pool-Thread). Ohne das trüge der nächste Request bzw. der nächste Importlauf die
User-ID seines Vorgängers — ein Log, das falsche Zuordnungen behauptet, ist schlechter als eines
ohne Zuordnung.

In den MDC gehen **nur** diese beiden IDs. Kein Name, keine E-Mail, keine Beträge: Render-Logs
liegen ausserhalb der Datenbank und unter anderer Zugriffskontrolle (siehe *Sicherheit* oben und
die Redaktionspraxis aus BE-PDF-06).

## Testing: Frameworks

| Stufe       | Backend                                                                                 | Frontend        | Coverage-Ziel |
| ----------- | ---------------------------------------------------------------------------------------- | ---------------- | -------------- |
| Unit        | JUnit 5 + Mockito + AssertJ                                                               | Vitest            | Backend 80% (90%+ für `budget/`, `categorization/`); Frontend 70–75% |
| Integration | Spring Boot Test (`@DataJpaTest`, `@WebMvcTest`, `@SpringBootTest`) gegen Testcontainers PostgreSQL (`PostgresTestDatabase`, eine DB pro Testklasse) | Angular TestBed | Keine eigene %-Zahl — jeder Endpoint und jede Migration mind. 1× getestet |
| E2E         | Playwright                                                                                 | Playwright        | Keine Coverage-Metrik — alle Must-Have User Stories (US-03/04/05/06): 1 Happy Path + 1 Fehlerpfad |
