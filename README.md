# BudgetBuddy

BudgetBuddy ist eine Web-App für in der Schweiz wohnhafte Studenten und Berufseinsteiger, die durch das einfache Einlesen von Kontoauszügen einen klaren Überblick über ihre monatlichen Ausgaben erhalten. Die App kategorisiert Transaktionen automatisch und zeigt einen wöchentlichen "Safe-to-Spend"-Betrag an, damit Nutzer jederzeit wissen, wie viel sie noch ausgeben können.

Weitere Details zu Projektidee, Personas, Architektur und Tech-Stack: siehe [CLAUDE.md](CLAUDE.md).

## Lokal starten (Dev)

**Voraussetzungen:** Java 25 (JDK), Node.js 20+ mit npm, **Docker** (für die lokale
PostgreSQL-Datenbank und die Integrationstests, ADR-12). Maven kommt über den Wrapper
(`./mvnw`) mit, Angular CLI über `npx` — global muss nichts installiert sein.

Für die Claude-Code-Skills (`/implement-issue`, `/review-pr`, `/plan-sprint`) kommt eine
angemeldete GitHub CLI dazu — Setup und Vorbedingungen: [.claude/skills/README.md](.claude/skills/README.md).

Frontend und Backend laufen im Dev-Betrieb als **zwei getrennte Prozesse**. Die SPA
ruft ihre API relativ auf; ein Angular-Dev-Proxy (`frontend/proxy.conf.json`) leitet
diese Pfade an das Backend auf `:8080` weiter — der Browser bleibt same-origin auf
`:4200`, das httpOnly-JWT-Cookie kommt korrekt zurück und es ist keine
CORS-Konfiguration nötig.

```bash
# Terminal 1 — Datenbank + Backend auf :8080
docker compose up -d                             # Postgres 18, Daten im benannten Volume
cd backend
export JWT_SECRET="$(openssl rand -base64 48)"   # Pflicht: sonst Fail-fast beim Start
./mvnw spring-boot:run
```

`docker compose up -d` genügt einmal pro Arbeitstag; `docker compose down` stoppt die
Datenbank und behält die Daten, `docker compose down -v` verwirft sie (Flyway baut beim
nächsten Start wieder von V01 auf). Der Compose-Stack legt neben `budgetbuddy` auch die
E2E-Datenbank `budgetbuddy_e2e` an.

```bash
# Terminal 2 — Frontend Dev-Server auf :4200
cd frontend
npm install        # nur beim ersten Mal bzw. nach Dependency-Änderungen
npx ng serve
```

Danach die App unter **http://localhost:4200/** öffnen.

- **`ANTHROPIC_API_KEY` ist lokal optional** — ohne Key startet alles normal, unbekannte
  Transaktionen werden als `Sonstiges` kategorisiert (BE-CAT-02). Man kann also ohne
  Anthropic-Account entwickeln.
- Windows (PowerShell) statt `export`: `$env:JWT_SECRET = "<secret>"`.

Details zu den Umgebungsvariablen und zum Beschaffen des API-Keys: siehe
[`backend/README.md`](backend/README.md). Mehr zum Dev-Proxy und den Frontend-Befehlen
(Tests, Build): siehe [`frontend/README.md`](frontend/README.md).

### VS Code: Ein-Knopf-Start & Debugging

Für VS Code liegen fertige Konfigurationen unter [`.vscode/`](.vscode/) im Repo — kein
manuelles Setup nötig.

**Starten (ohne Debugger):** `Cmd+Shift+B` (macOS) bzw. `Ctrl+Shift+B` startet die Task
_Dev: Full Stack_ — Datenbank, Backend und Frontend. Die Datenbank kommt per
`docker compose up -d --wait` hoch, bevor das Backend startet; ein manuelles
`docker compose up` ist also nicht nötig (Docker muss laufen). Das `JWT_SECRET` wird
automatisch als Wegwerf-Wert generiert; es ist kein weiteres Setup nötig.
Alternativ: `Cmd+Shift+P` → _Tasks: Run Task_ → _Dev: Full Stack_.

**Debuggen (mit Breakpoints):** Im _Run and Debug_-Panel (`Cmd+Shift+D`) die Compound
_Debug: Full Stack_ wählen und `F5` drücken — startet das Backend mit Java-Breakpoints und
das Frontend im Chrome-Debugger. `Cmd+F5` startet dieselbe Config ohne Debugger. Die
Datenbank bringt auch hier ein `preLaunchTask` hoch.
Voraussetzungen: das **Extension Pack for Java** ist installiert, und `JWT_SECRET` ist in
der Umgebung gesetzt (Launch-Configs generieren — anders als die Task — kein Secret; einmalig
z. B. in `~/.zshrc`: `export JWT_SECRET="$(openssl rand -base64 48)"`, dann VS Code neu
starten).

## Environment Variables

Secrets werden ausschliesslich über die Umgebung übergeben — **niemals** im
Git-Repository, in `application.properties` oder im Code hardcodiert (siehe CLAUDE.md →
"Sicherheit: Keine Secrets im Git"):

| Variable            | Required | Beschreibung                                                        |
| ------------------- | -------- | ------------------------------------------------------------------- |
| `JWT_SECRET`        | ✅ ja    | Secret für die HS256-Signatur der JWTs (Auth, ab BE-AUTH-01). Fehlt er, startet die App nicht. |
| `ANTHROPIC_API_KEY` | prod: ja | API-Key für die Claude-API (Kategorisierung + KI-Monatsbericht). Lokal optional: ohne Key startet die App normal, unbekannte Transaktionen werden dann als `Sonstiges` kategorisiert (BE-CAT-02). |
| `ANTHROPIC_API_MODEL` | optional | Überschreibt das Kategorisierungs-Modell. Default: `claude-haiku-4-5`. |
| `SPRING_DATASOURCE_URL` | prod: ja | JDBC-URL der Datenbank, z. B. `jdbc:postgresql://<host>/<db>?sslmode=require`. Lokal nicht nötig — der Default zeigt auf den Compose-Postgres. |
| `SPRING_DATASOURCE_USERNAME` | prod: ja | Datenbank-Benutzer. Lokal Default `budgetbuddy`. |
| `SPRING_DATASOURCE_PASSWORD` | prod: ja | Datenbank-Passwort. Lokal Default `budgetbuddy`. |
| `POSTGRES_HOST` / `POSTGRES_PORT` / `POSTGRES_DB` / `POSTGRES_USER` / `POSTGRES_PASSWORD` | optional | Einzeln überschreibbare Bestandteile der lokalen Verbindung, falls der Compose-Postgres woanders läuft. |
| `PORT`              | optional | Port, auf dem die App bindet (von Render gesetzt). Default: `8080`. |

Lokal können die Secrets z. B. über eine `.env`-Datei (bereits in `.gitignore`) oder
direkt als Shell-Variablen gesetzt werden.

## Deployment (Render)

Deployt wird auf [Render](https://render.com) (Frankfurt/EU) als einzelner Docker-Web-Service
(SPA gebündelt im Spring-Boot-JAR, ADR-10). Die Konfiguration liegt in [`render.yaml`](render.yaml);
der Build erfolgt über das [`Dockerfile`](Dockerfile) (`./mvnw -Pprod package`).

`ANTHROPIC_API_KEY` und `JWT_SECRET` werden im Render-Dashboard gesetzt (in `render.yaml`
mit `sync: false` markiert, damit kein Wert im Blueprint landet).

Der Web-Service läuft auf dem **Starter**-Plan ($7/Mt) und damit durchgehend — kein Spin-Down
nach 15 Minuten, keine Begrenzung auf 750 Instanzstunden.

Die Datenbank liegt **ausserhalb** von Render: PostgreSQL 18 bei [Neon](https://neon.com),
Region Frankfurt/EU, Free-Plan (ADR-12). Renders Filesystem ist ephemer — alles, was der Service
selbst auf Platte schreibt, verschwindet bei jedem Redeploy und Restart. Der einzige verbleibende
Cold Start kommt von Neon: nach 5 Minuten ohne Zugriff skaliert es auf null und wacht beim
nächsten Request automatisch wieder auf. Das kostet Latenz, keine Daten.

Die drei Verbindungsvariablen werden im Render-Dashboard gesetzt (in `render.yaml` nur mit
`sync: false` deklariert). Neons Connection-String lässt sich dabei **nicht unverändert**
übernehmen — er wird auf drei Variablen aufgeteilt:

```
Neon liefert:
postgresql://<USER>:<PASSWORT>@<HOST>.eu-central-1.aws.neon.tech/<DB>?sslmode=require

Daraus wird:
SPRING_DATASOURCE_URL       jdbc:postgresql://<HOST>.eu-central-1.aws.neon.tech/<DB>?sslmode=require
SPRING_DATASOURCE_USERNAME  <USER>
SPRING_DATASOURCE_PASSWORD  <PASSWORT>
```

Der Wert von `SPRING_DATASOURCE_URL` muss mit `jdbc:postgresql://` beginnen und darf **kein `@`**
enthalten; sonst startet der Container nicht — entweder mit `'url' must start with "jdbc"` oder
mit `JDBC URL invalid port number: …`. Der zweite Fall schreibt zusätzlich das **Passwort im
Klartext** ins Render-Log, weil pgjdbc alles nach dem ersten `:` als Portangabe liest.
Vollständige Anleitung inklusive Anlegen des Neon-Projekts und aller Fehlersignaturen:
[ADR-12, Abschnitt „Setup"](docs/adr/ADR-12-datenpersistenz-produktion.md#setup-neon-projekt-und-render-variablen).

### Prod-Build lokal

```bash
cd backend
./mvnw -Pprod package
```

Das Profil `-Pprod` baut die Angular-SPA und bündelt sie ins JAR. Der Default-Build
(`./mvnw package`) bleibt backend-only und damit schnell.
