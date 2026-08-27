# [INFRA-16] GitHub Actions auf Node-24-Runtime heben (Node-20-Deprecation)

- **Issue:** [#121](https://github.com/dfme/budget-buddy/issues/121)
- **Task-ID:** `INFRA-16`
- **Branch:** `feature/INFRA-16-node24-actions`
- **Story:** — (kein us-*-Label)
- **Sprint:** Sprint 5
- **Bestätigt am:** 2026-08-27

## Ausgangslage

Alle vier Fremd-Actions in `build.yml` deklarieren `node20` als Runtime. GitHub führt sie
zwangsweise auf Node.js 24 aus und meldet das als Annotation an jedem Job jedes CI-Runs. Heute
nur eine Warnung — das Risiko ist der Wegfall der Kompatibilitäts-Brücke: dann brechen
`checkout`, `setup-java`, `setup-node` und `upload-artifact` gleichzeitig, und damit CI **und**
CD zusammen (`cd.yml` ruft `build.yml` auf und deployt nur bei grünem Build).

## Entscheide

### Ziel-Versionen: aktueller Major, als floating Tag

| Action | alt | neu | Runtime in `action.yml` des Ziel-Tags |
| --- | --- | --- | --- |
| `actions/checkout` | `v4` | `v7` | `using: node24` |
| `actions/setup-java` | `v4` | `v6` | `using: 'node24'` |
| `actions/setup-node` | `v4` | `v7` | `using: 'node24'` |
| `actions/upload-artifact` | `v4` | `v7` | `using: 'node24'` |

Die Runtime-Spalte ist gegen `action.yml` des jeweiligen Tags verifiziert, nicht aus Release
Notes abgeleitet.

**Abweichung vom Issue:** Die Tabelle im Issue nennt für `setup-java` den Major `v5.6.0`.
Aktueller Major ist inzwischen `v6.0.0`; das Ziel ist deshalb `v6`, nicht `v5`.

**Floating Major statt gepinntem Patch** (`@v7`, nicht `@v7.0.1`) — das ist die im Repo
bestehende Konvention (`@v4` überall, `@v1` bei `claude-code-action`). Ein Wechsel der
Pinning-Strategie wäre ein eigener Entscheid und gehört nicht in einen Runtime-Bump.

### Scope-Erweiterung: `claude-pr-review.yml` wird mitgezogen

Das Issue schreibt „Ausschliesslich `build.yml`" und listet `ci.yml`/`cd.yml` als frei von
Fremd-Actions. Eine Breitensuche über *alle* Workflows (`grep -rn 'uses:' .github/`) findet
jedoch einen neunten Treffer: `claude-pr-review.yml:42` nutzt ebenfalls `actions/checkout@v4`.
Der Workflow entstand mit INFRA-31, **nachdem** das Issue geschrieben wurde — die AC-Aufzählung
ist zu eng, nicht die Implementierung.

Entscheid nach Rückfrage: mitheben und im PR-Body deklarieren. Identische Zeile, identisches
Risiko; ein Folge-Issue für eine Zeile wäre mehr Verwaltung als Arbeit.

`anthropics/claude-code-action@v1` bleibt unberührt: sie ist eine Composite-Action
(`using: "composite"`) und hat gar keine `node20`-Runtime.

## Breaking-Change-Prüfung (AC 2)

Ergebnis: **keine Step-Konfiguration muss angepasst werden.** Nachweise:

**Input-Diff `action.yml` alt → neu, alle vier Actions.** Einziger entfernter Input über alle
übersprungenen Majors hinweg ist `always-auth` bei `setup-node` (deprecated, wird nicht
genutzt). Alle von uns gesetzten Inputs — `distribution`, `java-version`, `cache`,
`node-version`, `cache-dependency-path`, `name`, `path`, `retention-days`, `fetch-depth` —
existieren in den Ziel-Majors.

**Pro Action der relevante Befund:**

- **checkout v6** — „Persist creds to a separate file": betrifft nur Workflows, die den Token
  aus `.git/config` lesen. `grep -in 'git-commit-id\|buildnumber\|jgit\|scm' backend/pom.xml`
  ist leer, der Build liest kein Git-Metadatum. `/actuator/info` bezieht den Commit aus
  `RENDER_GIT_COMMIT`, nicht aus dem Arbeitsverzeichnis.
- **checkout v7** — blockiert den Fork-PR-Checkout bei `pull_request_target` und
  `workflow_run`. `ci.yml` triggert auf `pull_request`, `cd.yml` auf `push`,
  `claude-pr-review.yml` auf `pull_request`. Kein Trigger betroffen.
- **setup-java v6** — entfernt die Legacy-`adopt`-Distributionen; wir nutzen `temurin`. Der
  umbenannte Input `jdkFile` → `jdk-file` wird nicht verwendet.
- **setup-node v5** — führt automatisches Caching ein, sobald `package.json` ein
  `packageManager`-Feld enthält. Das **trifft auf dieses Repo zu**: `frontend/package.json:12`
  setzt `"packageManager": "npm@11.17.0"`. Ein `package-manager-cache: false` ist trotzdem
  nicht nötig, weil unser explizites `cache: npm` Vorrang hat — belegt im Quellcode,
  `actions/setup-node` `src/main.ts@v7.0.0` Z. 71–79:

  ```ts
  if (isCacheFeatureAvailable()) {
    // if the cache input is provided, use it for caching.
    if (cache) { ... }
    // package manager npm is detected from package.json, enable auto-caching for npm.
    } else if (packagemanagercache) { ... }
  ```

  Das Auto-Caching ist der `else`-Zweig und greift nur ohne expliziten `cache`-Input.
- **upload-artifact v7** — neuer `archive`-Parameter für unkomprimierte Einzeldatei-Uploads ist
  opt-in (Default entspricht dem bisherigen Verhalten). `name`, `path` und `retention-days`
  unverändert.

## Betroffene Files

| File | Änderung |
| --- | --- |
| `.github/workflows/build.yml` | 8 `uses:`-Zeilen (Z. 19, 22, 38, 41, 87, 90, 97, 141) |
| `.github/workflows/claude-pr-review.yml` | 1 `uses:`-Zeile (Z. 42) — Scope-Erweiterung |
| `docs/plans/INFRA-16-node24-actions.md` | neu (diese Datei) |
| `docs/plans/README.md` | eine Indexzeile |

Keine neuen Files im Anwendungscode. Kein Backend-, Frontend- oder DB-Anteil.

## Implementierungsschritte

1. Plan und Indexzeile ablegen
2. Branch `feature/INFRA-16-node24-actions` von aktuellem `main`
3. `build.yml`: 8 `uses:`-Zeilen auf die Ziel-Majors heben
4. `claude-pr-review.yml`: `actions/checkout@v4` → `@v7`
5. Push und PR öffnen — erst damit läuft überhaupt ein CI-Run: `ci.yml` triggert
   ausschliesslich auf `pull_request` und hat kein `workflow_dispatch`
6. AC 3 und AC 4 verifizieren: Annotationen des Runs auf die Node-20-Meldung prüfen (nicht nur
   den grünen Haken), alle drei Jobs `Backend`, `Frontend`, `E2E` grün
7. AC 5 verifizieren: temporärer Commit, der einen E2E-Test rot macht → roter Run mit
   hochgeladenem `playwright-report`-Artefakt → Commit revertieren, grünen Run abwarten

## Test-Strategie

Keine Unit- oder Integrationstests. Der Task enthält keine Anwendungslogik — die DoD des Issues
streicht den Testpunkt selbst als n/a. Der Nachweis ist ausschliesslich der CI-Run:

| Nachweis | Wie |
| --- | --- |
| Kein Node-20-Warning mehr | Annotationen des Runs via `gh api .../check-runs`, nicht der grüne Haken |
| Drei Jobs grün | `gh run view` — `Backend`, `Frontend`, `E2E` |
| Artifact-Upload im `if: failure()`-Pfad | bewusst provozierter roter E2E-Lauf, danach revertiert |
| `cd.yml` unverändert lauffähig | ruft nur `build.yml` auf, enthält selbst keine Fremd-Action |

**Sequenz-Einschränkung, bewusst in Kauf genommen:** AC 3, 4 und 5 sind vor Schritt 5 nicht
belegbar, weil kein CI-Run ohne PR startet. Der PR entsteht also vor dem vollständigen
Nachweis; verifiziert und nötigenfalls nachgebessert wird auf demselben Branch.

## Acceptance Criteria (aus #121)

- [ ] `actions/checkout`, `actions/setup-java`, `actions/setup-node` und
      `actions/upload-artifact` in `build.yml` auf den aktuellen Major gehoben (alle 8
      `uses:`-Zeilen)
- [ ] Breaking Changes der übersprungenen Majors pro Action geprüft und, wo nötig, die
      Step-Konfiguration angepasst (`cache`, `cache-dependency-path`, Artifact-Name/-Pfad)
- [ ] Die Node-20-Deprecation-Warnung erscheint in keinem Job des CI-Runs mehr — geprüft über
      die Annotationen des Runs, nicht nur über den grünen Haken
- [ ] Alle drei Jobs (`Backend`, `Frontend`, `E2E`) laufen grün
- [ ] Der Artifact-Upload des E2E-Jobs funktioniert weiter (`if: failure()`-Pfad) — nachweisbar
      an einem Run mit fehlschlagendem Test oder durch bewusstes Durchspielen
- [ ] `cd.yml` ist nach dem Bump unverändert lauffähig (ruft `build.yml` auf; kein eigener
      Action-Bedarf)
