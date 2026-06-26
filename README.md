# BudgetBuddy

BudgetBuddy ist eine Web-App für in der Schweiz wohnhafte Studenten und Berufseinsteiger, die durch das einfache Einlesen von Kontoauszügen einen klaren Überblick über ihre monatlichen Ausgaben erhalten. Die App kategorisiert Transaktionen automatisch und zeigt einen wöchentlichen "Safe-to-Spend"-Betrag an, damit Nutzer jederzeit wissen, wie viel sie noch ausgeben können.

Weitere Details zu Projektidee, Personas, Architektur und Tech-Stack: siehe [CLAUDE.md](CLAUDE.md).

## Environment Variables

Die folgenden Umgebungsvariablen sind **required** und werden ausschliesslich über die
Umgebung übergeben — **niemals** im Git-Repository, in `application.properties` oder im
Code hardcodiert (siehe CLAUDE.md → "Sicherheit: Keine Secrets im Git"):

| Variable            | Required | Beschreibung                                                        |
| ------------------- | -------- | ------------------------------------------------------------------- |
| `ANTHROPIC_API_KEY` | ✅ ja    | API-Key für die Claude-API (Kategorisierung + KI-Monatsbericht).    |
| `JWT_SECRET`        | ✅ ja    | Secret für die HS256-Signatur der JWTs (Auth, ab BE-AUTH-01).       |
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
