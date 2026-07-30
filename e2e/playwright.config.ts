import { defineConfig, devices } from '@playwright/test';

import {
  BACKEND_PORT,
  BASE_URL,
  DB_PATH,
  LOCAL_TEST_JWT_SECRET,
  isPlaywrightMainProcess,
  resetDatabase,
  resolveBackendJar,
} from './support/backend';

// Vor dem Start der Testinstanz, nur im Hauptprozess — siehe resetDatabase().
if (isPlaywrightMainProcess) {
  resetDatabase();
}

/**
 * Playwright-Konfiguration für die BudgetBuddy-E2E-Tests (INFRA-14).
 *
 * Getestet wird gegen EIN JAR aus dem `prod`-Profil: es liefert SPA und API vom selben Origin
 * aus, also genau die Konstellation, die auf Render läuft. Details und Begründung in
 * `support/backend.ts` sowie `README.md`.
 */
export default defineConfig({
  testDir: './tests',

  // Fail-fast gegen versehentlich eingecheckte `test.only`.
  forbidOnly: !!process.env.CI,

  // Lokal keine Retries, damit ein Flake als Flake sichtbar bleibt. In CI zwei, weil ein
  // echter Browser gegen eine echte JVM zwangsläufig gelegentlich am Timing scheitert —
  // ohne Retries würde das PRs blockieren, die nichts kaputt gemacht haben.
  retries: process.env.CI ? 2 : 0,

  // Einzelner Worker: SQLite hat genau einen Writer. Parallele Register-Calls würden
  // `SQLITE_BUSY` riskieren und Fehlschläge produzieren, die nichts über die App aussagen.
  // Bei wachsender Suite ist der Hebel dagegen nicht Parallelität, sondern PostgreSQL (ADR-5).
  workers: 1,
  fullyParallel: false,

  // `github` setzt Annotationen direkt an die Zeilen im PR-Diff; `html` liefert den Report,
  // den der CI-Job bei Fehlschlag als Artifact hochlädt.
  reporter: process.env.CI
    ? [['github'], ['html', { open: 'never' }]]
    : [['list'], ['html', { open: 'never' }]],

  use: {
    baseURL: BASE_URL,
    // Nur bei Fehlschlag/Retry, damit ein grüner Lauf keine Artefakte produziert.
    trace: 'on-first-retry',
    screenshot: 'only-on-failure',
  },

  // Chromium genügt für den MVP: die Tests prüfen Flows und Cookie-Handling, nicht
  // Rendering-Unterschiede. Weitere Browser würden die CI-Zeit vervielfachen, ohne dass
  // ein Cross-Browser-Bug im Scope wäre.
  projects: [{ name: 'chromium', use: { ...devices['Desktop Chrome'] } }],

  webServer: {
    command: `java -jar "${resolveBackendJar()}"`,
    // Auf Health warten, nicht auf `/`: Health wird erst grün, wenn Flyway durch ist und die
    // DataSource steht. Ein antwortendes `/` würde Requests gegen ein leeres Schema zulassen.
    url: `${BASE_URL}/actuator/health`,
    // Nie eine fremde Instanz adoptieren, auch lokal nicht: die Suite setzt die Datenbank vor
    // dem Lauf zurück (resetDatabase) und darf deshalb nur gegen den Server testen, den sie
    // selbst gestartet hat. Andernfalls löscht sie eine Datei, die eine andere Instanz gar nicht
    // benutzt, und assertet gegen fremden Zustand. Kostet einen JVM-Start (~5s) pro Lauf.
    reuseExistingServer: false,
    // JVM-Start plus Flyway-Migrationen; in CI auf kalten Runnern deutlich langsamer als lokal.
    timeout: 120_000,
    // Backend-Logs in die Playwright-Ausgabe: bei einem Fehlstart ist der Stacktrace sonst
    // unsichtbar und man sieht nur den Timeout.
    stdout: 'pipe',
    stderr: 'pipe',
    env: {
      // Relaxed Binding: SERVER_PORT → server.port. Hält die Testinstanz vom Dev-Port weg.
      SERVER_PORT: String(BACKEND_PORT),
      SQLITE_DB_PATH: DB_PATH,
      JWT_SECRET: process.env.JWT_SECRET ?? LOCAL_TEST_JWT_SECRET,
    },
  },
});
