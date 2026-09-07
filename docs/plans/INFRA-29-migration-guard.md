# [INFRA-29] CI erkennt nachträglich geänderte Flyway-Migrationen nicht

- **Issue:** [#207](https://github.com/dfme/budget-buddy/issues/207)
- **Task-ID:** `INFRA-29`
- **Branch:** `fix/INFRA-29-migration-guard`
- **Story:** — (kein us-*-Label, Label `bug`)
- **Sprint:** Sprint 6
- **Bestätigt am:** 2026-09-06

## Befund vor der Umsetzung

Das Kommando aus AC1 wurde gegen die beiden offenen PRs geprüft, die je eine kollidierende `V08`
anlegen (#272 `feature/DB-08-notifications-table`, #263
`feature/BE-AUTH-11-token-version-invalidation`):

```
git diff --diff-filter=MD --name-only origin/main...origin/feature/DB-08-notifications-table \
    -- backend/src/main/resources/db/migration/
→ (leer)
```

Leer für beide. Die Branches legen `V08` **neu** an (`--diff-filter=A`), während `main` seit #267
bereits `V08__add_direction_uncertain_to_transactions.sql` trägt. Dass Flyway daran stirbt, wurde
lokal nachgestellt (zweite `V08` ins Verzeichnis, `UsersMigrationTest`):

```
Error creating bean with name 'flywayInitializer': Found more than one migration with version 08
```

## Entscheide

### 1. Eine Regel statt der AC-Filterliste

> Keine Migrationsdatei, die auf `main` existiert, darf geändert, gelöscht oder umbenannt werden —
> und jede hinzugefügte muss eine Version tragen, die echt grösser ist als die höchste auf `main`.

Ein Satz, vier Fehlerbilder:

| Fall | Fehler auf einer Live-DB | Sieht CI es? | Deckt `--diff-filter=MD` ab? |
| ---- | ------------------------ | ------------ | ---------------------------- |
| geändert | Checksum-Mismatch | strukturell nie | ja (AC1) |
| gelöscht | fehlende angewandte Migration | strukturell nie | ja (AC1) |
| umbenannt | Description-Mismatch | strukturell nie | **nein** — git klassifiziert als `R` |
| neu, Version ≤ Maximum auf `main` | Out-of-Order (`outOfOrder` ungesetzt, Default `false`) bzw. doppelte Version | strukturell nie bzw. nur auf einem frischen Lauf | **nein** — ist `A` |

**Bewusste Abweichung von AC2.** Die AC verlangt, dass `--diff-filter=A` den Fehler nie auslöst.
Das ist als Beschreibung des Normalfalls richtig, als Regel aber zu weit: eine hinzugefügte Datei
mit zu niedriger Version ist für eine Live-Datenbank genauso tödlich wie eine geänderte, und CI ist
dafür genauso blind — eine frische Datenbank wendet ohnehin alles in Versionsreihenfolge an. Die
Erweiterung ist im PR-Body deklariert.

### 2. Duplikate sind ein anderes Problem — und werden nur nebenbei mitgelöst

#272 und #263 sind **grün**, obwohl beide `main` beim Merge brechen. Der Grund ist nicht ein blinder
Check: Ihre Läufe stammen vom 2026-09-05, `main`s `V08` landete am 2026-09-06T08:40:30Z, und GitHub
startet Checks nicht neu, wenn sich der Base-Branch bewegt. Ein frischer Lauf wäre rot.

Der eigentliche Hebel dagegen ist „Require branches to be up to date before merging" im Ruleset —
eine Repo-Einstellung, keine Codeänderung, und deshalb **nicht Teil dieses PR**. Der Guard hier
fängt den Fall trotzdem mit ab, weil er gegen `origin/${{ github.base_ref }}` zum Zeitpunkt des
Laufs vergleicht und nicht gegen den Stand, aus dem der Branch abzweigte.

### 3. Job in `build.yml`, auf Pull Requests begrenzt

`build.yml` ist die Single Source of Truth, die `ci.yml` (PR) und `cd.yml` (Push auf `main`)
gemeinsam aufrufen — der Guard gilt damit automatisch für jeden künftigen Aufrufer. Der Vergleich
gegen `main` ist nur auf einem PR sinnvoll, deshalb `if: github.event_name == 'pull_request'`.

`fetch-depth: 0` ist Pflicht: der Default `1` holt keine Historie, `origin/main` existiert dann
lokal gar nicht und der Diff liefe ins Leere statt zu scheitern.

Verworfen: ein zweiter Job in `ci.yml` (bräuchte keine Bedingung, ein künftiger dritter Aufrufer von
`build.yml` hätte den Guard aber nicht); ein Schritt im `backend`-Job (der Fehlschlag stünde unter
«Backend (mvn verify)», die Ursache wäre im Namen nicht ablesbar).

### 4. Notfall-Ausweg als PR-Label

`migration-rewrite-ok`. Sichtbar auf dem PR, die Timeline hält fest, wer es wann gesetzt hat, und es
lässt sich wieder entfernen — der Ausweg ist damit eine nachvollziehbare Handlung. Ein
Commit-Marker stünde dauerhaft in der Historie, wäre auf der PR-Seite unsichtbar und liesse sich
nachträglich weder entziehen noch einer Person zuordnen.

Bei gesetztem Label wird der Check übersprungen und gibt ein `::warning::` aus, damit die Umgehung
im Log steht statt lautlos zu passieren.

Der Preis dieser Wahl: Das Label greift erst beim nächsten Lauf. `ci.yml` triggert auf die
Default-Events von `pull_request` (`opened`, `synchronize`, `reopened`), `labeled` gehört nicht
dazu, und ein «Re-run» spielt die alte Event-Payload ohne das Label ab — es braucht also einen
neuen Commit. `types: [..., labeled]` wäre die Alternative, würde aber jede Label-Änderung an jedem
PR zum vollen Build inklusive E2E machen. Bewusst nicht genommen; stattdessen steht der Vorbehalt
in `docs/CONVENTIONS.md`, im Kopf des Skripts und in dessen Fehlerausgabe.

## Betroffene / neue Files

**Neu:**
- `scripts/check-migrations.sh` — die Logik, lokal aufrufbar (`scripts/check-migrations.sh [<base-ref>]`,
  Default `origin/main`). Stil nach `plans-index.sh`: bash, `set -euo pipefail`, deutscher Kopf.
- `scripts/check-migrations.test.sh` — Selbsttest über Wegwerf-Git-Repos in einem Temp-Verzeichnis.

**Geändert:**
- `.github/workflows/build.yml` — neuer Job `migrations`
- `docs/CONVENTIONS.md` — Unveränderlichkeits-Regel und Notfall-Label im bestehenden Abschnitt
  «Datenbank: Flyway-Migrationen», direkt neben der Namenskonvention

**Repo-Metadaten:** Label `migration-rewrite-ok` anlegen — ohne das Label ist AC5 nicht benutzbar.

## Implementierungsschritte

1. `git diff --name-status --find-renames "$base...HEAD"` über das Migrationsverzeichnis auswerten;
   `M`/`D`/`R` sofort als Fehler mit Datei und Grund melden
2. Höchste Version auf `base` per `git ls-tree` ermitteln, jede hinzugefügte Datei dagegen prüfen;
   Duplikate innerhalb des PR ebenfalls
3. Versionsnummern mit `10#` parsen — `$((08))` bricht in bash mit *value too great for base* ab,
   weil die führende Null als Oktal gilt. Genau `08` und `09` sind der aktuelle Stand des Repos
4. Fehlermeldung nennt Datei, Fehlerbild, den Hinweis «neue Version anlegen statt ändern» und den
   Namen des Notfall-Labels
5. Selbsttest schreiben
6. Job in `build.yml`; Check-Schritt bei gesetztem Label überspringen, `::warning::` ausgeben
7. `docs/CONVENTIONS.md` ergänzen, Label anlegen
8. Selbsttest ausführen, Gegenprobe gegen die realen Branches, Security-Review, lokaler Review

## Test-Strategie

`scripts/check-migrations.test.sh` legt je Szenario ein Wegwerf-Repo an und prüft Exit-Code und
Meldung. Das deckt die DoD-Zeile ab, ohne echte PRs zu öffnen:

| Szenario | Erwartung |
| -------- | --------- |
| neue Migration mit höherer Version | grün |
| zwei neue Migrationen in Folge (V09, V10) | grün |
| Änderung ausserhalb des Migrationsverzeichnisses | grün |
| bestehende Migration geändert | rot, Datei genannt |
| bestehende Migration gelöscht | rot |
| bestehende Migration umbenannt | rot |
| neue Migration mit bereits vergebener Version | rot — der Fall #272 / #263 |
| neue Migration mit niedrigerer Version | rot |

Der Selbsttest läuft als eigener Schritt im selben Job und ist **nicht** vom Label abhängig — die
Logik des Guards soll auch dann geprüft sein, wenn der Guard für diesen PR ausgesetzt ist.

**Gegenprobe am realen Fall:** Das Skript gegen `origin/feature/DB-08-notifications-table` und
`origin/feature/BE-AUTH-11-token-version-invalidation` ausführen. Beide müssen rot werden — der
Nachweis am echten Fehler statt nur an konstruierten Fixtures.

## Acceptance Criteria (aus Issue #207)

- [ ] Ein CI-Schritt schlägt fehl, wenn ein PR eine Migrationsdatei ändert oder löscht, die auf
      `main` bereits existiert
- [ ] Neue Migrationsdateien lösen den Fehler nicht aus — **präzisiert:** neue Dateien mit einer
      Version echt grösser als das Maximum auf `main` lösen nicht aus; mit einer bereits vergebenen
      oder niedrigeren Version schon (siehe Entscheid 1)
- [ ] Die Fehlermeldung nennt die betroffene Datei und den Grund
- [ ] Der Schritt läuft in demselben Workflow wie der Build, nicht als separater Workflow
- [ ] Ein bewusster Notfall-Ausweg ist dokumentiert
