# BudgetBuddy — Backend

Spring Boot 3.5 / Java 25 API-Application. Build via Maven Wrapper (`./mvnw`).

## Umgebungsvariablen

Secrets werden **ausschliesslich** über Umgebungsvariablen gesetzt — nie hardcodiert
oder in `application.properties` eingecheckt (siehe ADR-7 und CLAUDE.md).

| Variable            | Pflicht | Beschreibung                                                                 |
| ------------------- | ------- | --------------------------------------------------------------------------- |
| `JWT_SECRET`        | ja      | Secret für HS256-Signierung der JWTs. Min. 32 Zeichen (Fail-fast beim Start). |
| `ANTHROPIC_API_KEY` | nein\*  | Claude API-Key (Kategorisierung + Monatsbericht). \*Kein Fail-fast: Ohne Key startet die App normal und kategorisiert unbekannte Transaktionen als `Sonstiges` — man kann also ohne Anthropic-Account entwickeln. In Produktion gesetzt. |
| `ANTHROPIC_API_MODEL` | nein  | Überschreibt das Kategorisierungs-Modell (`anthropic.api.model`). Default: `claude-haiku-4-5`. |
| `SQLITE_DB_PATH`    | nein    | Abweichender Pfad zur SQLite-Datei. Default: `budgetbuddy.db`.               |

> **Hinweis:** Eine `.env`-Datei wird **nicht** automatisch eingelesen. Die Variablen
> müssen als echte OS- bzw. IDE-Umgebungsvariablen gesetzt sein.

### `JWT_SECRET` erzeugen

```bash
openssl rand -base64 48
```

### `ANTHROPIC_API_KEY` beschaffen

1. Account auf [platform.claude.com](https://platform.claude.com) anlegen (früher
   `console.anthropic.com`).
2. **Guthaben aufladen** unter *Billing*. Ohne Guthaben liefert die API einen Fehler,
   auch wenn der Key gültig ist. Achtung: Ein **Claude.ai-Abo (Pro/Max) ist kein
   API-Guthaben** — das sind getrennte Produkte mit getrennter Abrechnung.
3. *Settings → API Keys → Create Key*. Der Key (`sk-ant-…`) wird **nur einmal
   angezeigt** — direkt kopieren und in einen Passwort-Manager legen.
4. Hinterlegen:
   - **Lokal:** als Umgebungsvariable (siehe unten). Nie ins Git — `.env` steht in
     `.gitignore`, wird aber ohnehin nicht automatisch eingelesen.
   - **Produktion:** im [Render-Dashboard](https://dashboard.render.com) unter
     *Service `budgetbuddy` → Environment*. In [`render.yaml`](../render.yaml) ist die
     Variable mit `sync: false` markiert, damit kein Wert im Blueprint landet.

Ohne Key ist die App lauffähig: unbekannte Transaktionen werden dann als `Sonstiges`
kategorisiert (BE-CAT-02). Für die Arbeit an anderen Modulen braucht es also keinen
Anthropic-Account.

### `ANTHROPIC_API_KEY` verifizieren (nach dem Setzen in Render)

Nach dem Setzen im Render-Dashboard startet der Service automatisch neu. Zwei Checks
bestätigen, dass der Key produktiv greift:

**1. Log-Signal beim Start (definitiv).** Render-Dashboard → Service `budgetbuddy` →
_Logs_. Genau eine der beiden Zeilen erscheint:

| Zustand      | Log-Zeile                                                   | Level |
| ------------ | ---------------------------------------------------------- | ----- |
| ✅ Key erkannt | `Anthropic-Client initialisiert (Modell: claude-haiku-4-5)` | INFO  |
| ❌ Key fehlt   | `ANTHROPIC_API_KEY ist nicht gesetzt — …`                   | WARN  |

Die INFO-Zeile prüft nur die _Anwesenheit_ des Keys, nicht Gültigkeit oder Guthaben.

**2. Funktionaler Test (end-to-end).** Einen Kontoauszug mit mindestens einer
Transaktion hochladen, deren Empfänger **nicht** in `category_lookup` steht — nur solche
erreichen Claude (Hybrid-Ansatz, ADR-6). Erhält sie eine echte Kategorie statt
`Sonstiges`, ist der Key inkl. Guthaben produktiv aktiv. Fällt sie trotz INFO-Zeile auf
`Sonstiges`, ist die häufigste Ursache **fehlendes Guthaben**, nicht der Key.

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
