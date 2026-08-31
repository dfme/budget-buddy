# [INFRA-15] Prettier als npm-Script verdrahten und Bestand formatieren

- **Issue:** [#113](https://github.com/dfme/budget-buddy/issues/113)
- **Task-ID:** `INFRA-15`
- **Branch:** `feature/INFRA-15-prettier-format-ci`
- **Story:** — (kein us-*-Label)
- **Sprint:** Sprint 5
- **Bestätigt am:** 2026-08-31

## Ausgangslage

Prettier ist im Frontend als Dev-Dependency installiert (`prettier ^3.8.1`) und mit
`.prettierrc` konfiguriert, aber nirgends verdrahtet: kein npm-Script, kein CI-Step. Stand
heute (nach Aktualisierung auf `main`, PRs #230/#232 gemerged) meldet
`npx prettier --check .` in `frontend/` 18 unformatierte Dateien — mehr als die 9 aus dem
Issue-Text, weil der Bestand seit Issue-Erstellung weiter gedriftet ist. Genau das ist das
Problem, das dieser Task auflöst.

## Betroffene Files

- `frontend/package.json` — neue Scripts `format` (schreibend) und `format:check` (prüfend)
- `frontend/.prettierignore` — neu
- `.github/workflows/build.yml` — neuer Step im `frontend`-Job
- 18 aktuell unformatierte Dateien im `frontend/`-Bestand (reiner Formatierungs-Diff, kein
  funktionaler Code)

## Implementierungsschritte

1. `frontend/.prettierignore` anlegen, gespiegelt an den Compiled-Output-Einträgen aus
   `frontend/.gitignore`: `dist/`, `.angular/`, `coverage/`, `node_modules/`
2. `frontend/package.json`: Scripts `"format": "prettier --write ."` und
   `"format:check": "prettier --check ."` ergänzen
3. `.github/workflows/build.yml`: Step `npm run format:check` im `frontend`-Job, **vor**
   `Unit tests`. Offene Frage aus dem Issue (Entscheid liegt beim Umsetzenden): ein
   Formatierungsfehler ist der billigste und schnellste Fehlerfall — der Dev soll ihn vor dem
   langsameren Testlauf sehen, statt am Ende eines grünen Testlaufs überrascht zu werden.
4. **Commit 1 (funktional):** Scripts + `.prettierignore` + CI-Step
5. **Commit 2 (separat, laut AC):** `npx prettier --write .` über den gesamten
   `frontend/`-Bestand — nur der reine Formatierungs-Diff, keine funktionale Änderung
   vermischt
6. Verifikation: `npx prettier --check .`, `ng build`, `ng test` laufen nach Commit 2
   unverändert grün

## Test-Strategie

Kein neuer automatisierter Test — laut Definition of Done im Issue ist die Absicherung der
CI-Step selbst (`format:check` schlägt bei Verstoss fehl). Verifikation ist lokal
reproduzierbar:

- `npx prettier --check .` → sauber, keine Warnungen
- `ng build` → grün
- `ng test -- --no-watch` → grün

## Acceptance Criteria (aus Issue #113)

- [ ] `npm run format` und `npm run format:check` in `frontend/package.json` hinterlegt
- [ ] Alle bestehenden Dateien im `frontend/` sind formatiert — `npx prettier --check .` läuft
      sauber durch
- [ ] `.prettierignore` angelegt (`dist/`, `.angular/`, `coverage/`, `node_modules/`)
- [ ] Frontend-Job in `.github/workflows/build.yml` führt `format:check` aus und schlägt bei
      Verstoss fehl
- [ ] Formatierung ist ein eigener Commit, getrennt von Script- und CI-Änderung
- [ ] `ng build` und `ng test` laufen nach der Formatierung unverändert grün

## Nicht Teil dieses Tasks

Formatierung von Dateien ausserhalb `frontend/` (Markdown in `docs/`, `render.yaml`) und
ESLint — beides ein eigener Entscheid.
