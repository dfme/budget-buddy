# BudgetBuddy

BudgetBuddy ist eine Web-App für in der Schweiz wohnhafte Studenten und Berufseinsteiger, die durch das einfache Einlesen von Kontoauszügen einen klaren Überblick über ihre monatlichen Ausgaben erhalten. Die App kategorisiert Transaktionen automatisch und zeigt einen wöchentlichen "Safe-to-Spend"-Betrag an, damit Nutzer jederzeit wissen, wie viel sie noch ausgeben können.

Weitere Details zu Projektidee, Personas, Architektur und Tech-Stack: siehe [CLAUDE.md](CLAUDE.md).

## Lokal starten (Dev)

**Voraussetzungen:** Java 25 (JDK), Node.js 20+ mit npm. Maven kommt über den
Wrapper (`./mvnw`) mit, Angular CLI über `npx` — global muss nichts installiert sein.

Frontend und Backend laufen im Dev-Betrieb als **zwei getrennte Prozesse**. Die SPA
ruft ihre API relativ auf; ein Angular-Dev-Proxy (`frontend/proxy.conf.json`) leitet
diese Pfade an das Backend auf `:8080` weiter — der Browser bleibt same-origin auf
`:4200`, das httpOnly-JWT-Cookie kommt korrekt zurück und es ist keine
CORS-Konfiguration nötig.

```bash
# Terminal 1 — Backend auf :8080
cd backend
export JWT_SECRET="$(openssl rand -base64 48)"   # Pflicht: sonst Fail-fast beim Start
./mvnw spring-boot:run
```

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
_Dev: Full Stack_ — Backend und Frontend parallel, jeweils im eigenen Terminal. Das
`JWT_SECRET` wird dabei automatisch als Wegwerf-Wert generiert; es ist kein Setup nötig.
Alternativ: `Cmd+Shift+P` → _Tasks: Run Task_ → _Dev: Full Stack_.

**Debuggen (mit Breakpoints):** Im _Run and Debug_-Panel (`Cmd+Shift+D`) die Compound
_Debug: Full Stack_ wählen und `F5` drücken — startet das Backend mit Java-Breakpoints und
das Frontend im Chrome-Debugger. `Cmd+F5` startet dieselbe Config ohne Debugger.
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
| `SQLITE_DB_PATH`    | optional | Pfad zur SQLite-Datei. Default: `budgetbuddy.db` im Arbeitsverzeichnis. |
| `PORT`              | optional | Port, auf dem die App bindet (von Render gesetzt). Default: `8080`. |

Lokal können die Secrets z. B. über eine `.env`-Datei (bereits in `.gitignore`) oder
direkt als Shell-Variablen gesetzt werden.

## Deployment (Render)

Deployt wird auf [Render](https://render.com) (Frankfurt/EU) als einzelner Docker-Web-Service
(SPA gebündelt im Spring-Boot-JAR, ADR-10). Die Konfiguration liegt in [`render.yaml`](render.yaml);
der Build erfolgt über das [`Dockerfile`](Dockerfile) (`./mvnw -Pprod package`).

`ANTHROPIC_API_KEY` und `JWT_SECRET` werden im Render-Dashboard gesetzt (in `render.yaml`
mit `sync: false` markiert, damit kein Wert im Blueprint landet).

**Hinweis:** Der Free-Tier hat kein Persistent Disk — die SQLite-Datei liegt auf dem
ephemeren Filesystem und geht bei jedem Redeploy verloren. Für das MVP bewusst akzeptiert.

### Prod-Build lokal

```bash
cd backend
./mvnw -Pprod package
```

Das Profil `-Pprod` baut die Angular-SPA und bündelt sie ins JAR. Der Default-Build
(`./mvnw package`) bleibt backend-only und damit schnell.
