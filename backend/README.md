# BudgetBuddy — Backend

Spring Boot 3.5 / Java 25 API-Application. Build via Maven Wrapper (`./mvnw`).

## Umgebungsvariablen

Secrets werden **ausschliesslich** über Umgebungsvariablen gesetzt — nie hardcodiert
oder in `application.properties` eingecheckt (siehe ADR-7 und CLAUDE.md).

| Variable            | Pflicht | Beschreibung                                                                 |
| ------------------- | ------- | --------------------------------------------------------------------------- |
| `JWT_SECRET`        | ja      | Secret für HS256-Signierung der JWTs. Min. 32 Zeichen (Fail-fast beim Start). |
| `ANTHROPIC_API_KEY` | ja\*    | Claude API-Key (Kategorisierung + Monatsbericht). \*Pflicht für KI-Features. |
| `SQLITE_DB_PATH`    | nein    | Abweichender Pfad zur SQLite-Datei. Default: `budgetbuddy.db`.               |

> **Hinweis:** Eine `.env`-Datei wird **nicht** automatisch eingelesen. Die Variablen
> müssen als echte OS- bzw. IDE-Umgebungsvariablen gesetzt sein.

### Secret erzeugen

```bash
openssl rand -base64 48
```

### Variablen setzen (lokal)

**PowerShell (Windows):**

```powershell
$env:JWT_SECRET = "<generiertes-secret>"
$env:ANTHROPIC_API_KEY = "<dein-api-key>"
./mvnw spring-boot:run
```

**Bash (macOS/Linux):**

```bash
export JWT_SECRET="<generiertes-secret>"
export ANTHROPIC_API_KEY="<dein-api-key>"
./mvnw spring-boot:run
```

**IDE (IntelliJ/VS Code):** In der Run-/Launch-Konfiguration unter *Environment
variables* eintragen.

### Produktion (Render)

Die Variablen werden als System-Environment-Variablen in der Render-Service-Konfiguration
gesetzt und haben automatisch Vorrang vor allen Defaults.

## Tests

```bash
./mvnw test
```

Die Tests benötigen keine lokal gesetzten Env-Vars: Ein Dummy-`JWT_SECRET` wird zentral
über das `maven-surefire-plugin` in `pom.xml` injiziert (kein echtes Secret, nur für Tests).
