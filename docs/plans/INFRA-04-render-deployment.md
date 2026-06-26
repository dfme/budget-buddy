# INFRA-04 — render.yaml und application-prod.properties anlegen

**Issue:** #37
**Task-ID:** INFRA-04
**Branch:** `feature/INFRA-04-render-deployment`

## Entscheide

- **Deployment:** Docker via `render.yaml` (`runtime: docker` + `Dockerfile`). Render-Blueprint
  hat keinen nativen `java`-Runtime — Docker ist der realistische Weg. Deckt sich mit dem
  Issue-Kommentar von @sirjoe83.
- **SPA-Bundling:** Angular-Build wird via `frontend-maven-plugin` ins JAR (`static/`) gebündelt
  (Single-Artifact gemäss ADR-10).
- **Maven-Profil `prod`:** aktiviert den Frontend-Build (Dev-Build bleibt backend-only/schnell).
  Spring-Profil `prod` lädt `application-prod.properties`.
- **Render-Plan:** `free` ohne Persistent Disk (Entscheid Team). SQLite läuft auf dem ephemeren
  Filesystem → Daten gehen bei Redeploy verloren. Für MVP bewusst akzeptiert (vgl. ADR-10).

## Betroffene Files

### Neu
- `render.yaml` (Root) — Web Service, `runtime: docker`, Region Frankfurt, `plan: free`,
  Health-Check `/actuator/health`, Env-Vars (`ANTHROPIC_API_KEY`/`JWT_SECRET` mit `sync: false`,
  `SPRING_PROFILES_ACTIVE=prod`).
- `Dockerfile` (Root) — Multi-Stage: `eclipse-temurin:25-jdk` baut via `./mvnw -Pprod package
  -DskipTests` (inkl. Angular), Runtime `eclipse-temurin:25-jre` startet `java -jar app.jar`.
- `.dockerignore` (Root).
- `backend/src/main/resources/application-prod.properties` — DB-Pfad im Arbeitsverzeichnis,
  `server.port=${PORT:8080}`, Log-Level INFO, kein Dev-CORS (Same-Origin).
- `backend/src/test/java/com/budgetbuddy/config/ProdProfileSmokeTest.java` —
  `@SpringBootTest @ActiveProfiles("prod")`, DB auf `:memory:`, Happy-Path-Kontext-Test.
- `docs/plans/INFRA-04-render-deployment.md` (dieses Dokument).

### Geändert
- `backend/pom.xml` — Maven-Profil `prod` mit `frontend-maven-plugin` + `maven-resources-plugin`
  (kopiert `frontend/dist/budgetbuddy/browser` → `classes/static/`).
- `README.md` — Abschnitt Deployment / Environment: `ANTHROPIC_API_KEY` und `JWT_SECRET` als
  required dokumentiert (nie im Git).

## Implementierungsschritte
1. `application-prod.properties` anlegen.
2. `pom.xml`: Maven-Profil `prod` mit Frontend-Build + Static-Copy.
3. `Dockerfile` + `.dockerignore`.
4. `render.yaml`.
5. `README.md` Env-Var-Doku.
6. `ProdProfileSmokeTest`.
7. Lokal verifizieren: `./mvnw -Pprod package` und (falls Docker vorhanden) `docker build`.

## Test-Strategie
- **Unit/Integration:** `ProdProfileSmokeTest` (JUnit, `@SpringBootTest` mit Prod-Profil).
- **Build-Verifikation:** `./mvnw -Pprod package` fehlerfrei (AC #4); `docker build` lokal.

## Acceptance Criteria (aus Issue)
- [ ] render.yaml liegt im Root-Verzeichnis und beschreibt den Web Service
- [ ] application-prod.properties enthält SQLite-Pfad (ephemer, da kein Disk)
- [ ] Env-Vars ANTHROPIC_API_KEY und JWT_SECRET sind im README als required dokumentiert (nie im Git)
- [ ] Lokaler `mvn package` baut erfolgreich mit Prod-Profil
