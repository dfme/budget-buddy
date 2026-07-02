# INFRA-03 — GitHub Actions CI einrichten

- **Issue:** #3 — Part of #3
- **Task-ID:** INFRA-03
- **Branch:** `feature/INFRA-03-github-actions-ci`
- **Area:** DevOps · Sprint 1 · Wave 0 · Story Points 2

## Entscheidungen

- Ein Workflow `.github/workflows/ci.yml`, Trigger `pull_request` gegen `main`.
- Zwei parallele Jobs: `backend` und `frontend`.
- Frontend-Job führt zusätzlich zum Production-Build auch `ng test` (Vitest, headless) aus — Entscheid des Users, deckt DoD "Happy Path durch Test" fürs Frontend ab.
- Maven immer über den Wrapper (`./mvnw`), Java 25 (Temurin), Node 22.
- Keine Secrets nötig: Backend-Tests laufen gegen `jdbc:sqlite::memory:`.

## Files

- **Neu:** `.github/workflows/ci.yml`
- **Neu:** `docs/plans/INFRA-03-github-actions-ci.md` (dieser Plan, via `.claudeignore` ignoriert)

## Implementierungsschritte

### Job `backend` (ubuntu-latest, working-directory: backend)
1. `actions/checkout@v4`
2. `actions/setup-java@v4` — Temurin 25, `cache: maven`
3. `./mvnw -B verify` — führt `mvn test` aus, schlägt bei Test-Fehler fehl

### Job `frontend` (ubuntu-latest, working-directory: frontend)
1. `actions/checkout@v4`
2. `actions/setup-node@v4` — Node 22, `cache: npm`, `cache-dependency-path: frontend/package-lock.json`
3. `npm ci`
4. `npm test -- --no-watch` (Vitest headless)
5. `npx ng build --configuration production`

## Test-Strategie

Der Workflow ist das Artefakt. Verifikation:
- Lokaler Trockenlauf: `cd backend && ./mvnw -B verify`; `cd frontend && npm ci && npm test -- --no-watch && npx ng build --configuration production`
- Nach PR: GitHub-Actions-Run grün.

## Acceptance Criteria (aus Issue)

- [ ] CI-Pipeline läuft bei jedem PR-Push durch
- [ ] `mvn test` schlägt bei Test-Fehler fehl
- [ ] `ng build --configuration production` wird ausgeführt

## Definition of Done

- [ ] Code reviewed (mind. 1 Approval)
- [ ] `mvn package` und `ng build` laufen fehlerfrei durch
- [ ] Happy Path durch automatisierten Test abgedeckt
- [ ] Alle Acceptance Criteria erfüllt
