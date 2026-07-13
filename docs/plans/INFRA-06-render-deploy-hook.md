# INFRA-06 — GitHub Actions CD: Render Deploy Hook

- **Issue:** #39
- **Task-ID:** INFRA-06
- **Branch:** `feature/INFRA-06-render-deploy-hook`
- **Area:** DevOps · Sprint 2 · Wave 0

## Ziel

GitHub Actions CD-Step: Nach erfolgreichem Build/Test auf `main` wird ein Render
Deploy Hook ausgelöst. Nach dem Deployment folgt ein automatischer Smoke-Test
gegen `/actuator/health` (HTTP 200).

## Entscheidungen

- **Smoke-Test-URL:** `https://budgetbuddy-0myo.onrender.com/actuator/health`,
  **hardcodiert** im Workflow (öffentliche URL, kein Secret).
- **Deploy-Wait:** Health-Endpoint pollen bis HTTP 200 (bis ~10 min), sonst Job
  rot. Robust gegen asynchronen Render-Deploy + Free-Tier-Cold-Start.
- **Deploy-Hook-URL:** GitHub Secret `RENDER_DEPLOY_HOOK_URL` (nie im Code — AC 4).
- **Keine Duplikation:** Die Build/Test-Jobs werden in einen wiederverwendbaren
  Workflow (`build.yml`, `on: workflow_call`) ausgelagert. `ci.yml` (PR) und
  `cd.yml` (push main) rufen ihn via `uses:` auf — Single Source of Truth.

## Betroffene / neue Files

- **Neu:** `.github/workflows/build.yml` — reusable Workflow (`workflow_call`) mit
  `backend`- und `frontend`-Job.
- **Neu:** `.github/workflows/cd.yml` — CD-Pipeline, Trigger `push` auf `main`.
- **Geändert:** `.github/workflows/ci.yml` — ruft jetzt nur noch `build.yml` auf.
- Keine Backend-/Frontend-Code-Änderungen nötig: `/actuator/health` und
  `render.yaml` existieren bereits (INFRA-04/05).

## Workflow-Design

`build.yml` (reusable): `backend`-Job (JDK 25, `./mvnw -B verify`, inkl. `mvn test`)
+ `frontend`-Job (Node 22, `npm ci`, `npm test -- --no-watch`, `ng build --prod`).

`cd.yml`: Trigger `push: branches: [main]`, `concurrency`-Group gegen überlappende Deploys.

1. **`build`-Job** — `uses: ./.github/workflows/build.yml`.
2. **`deploy`-Job** — `needs: build` (rotes Build/Test blockt Deploy → AC 3):
   - `curl -fsS -X POST "$RENDER_DEPLOY_HOOK_URL"` → triggert Render-Deploy (AC 1).
   - Smoke-Test (AC 2): Poll-Loop `curl` gegen `/actuator/health`, alle ~15s,
     bis ~10 min; grün bei erstem HTTP 200, sonst Job rot.

## Test-Strategie

Reine DevOps-Aufgabe — kein Applikationscode, daher kein JUnit-/Playwright-Test.
Happy-Path-Coverage = der Smoke-Test-Step selbst (Health-Check → HTTP 200) im
Workflow. Swagger/OpenAPI-DoD-Punkt N/A (keine neuen Endpoints).

## Manueller Schritt (nicht Teil des Codes)

GitHub → Settings → Secrets and variables → Actions → Secret
`RENDER_DEPLOY_HOOK_URL` anlegen (Wert aus Render-Dashboard → Service →
Settings → Deploy Hook).

## Acceptance Criteria

- [ ] Push auf main triggert automatisch ein Render-Deployment
- [ ] Smoke-Test gegen /actuator/health nach Deploy (HTTP 200)
- [ ] Deploy schlägt fehl wenn `mvn test` oder `ng build` rot sind
- [ ] Render Deploy Hook URL ist als GitHub Secret hinterlegt (nie im Code)
