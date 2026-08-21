import { defineConfig, devices } from '@playwright/test';

import {
  BACKEND_PORT,
  BASE_URL,
  LOCAL_TEST_JWT_SECRET,
  resolveBackendJar,
} from './support/backend';
import { DB_PASSWORD, DB_USER, JDBC_URL } from './support/database';

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

  // Einzelner Worker: Alle Tests teilen sich eine Backend-Instanz und damit eine Datenbank,
  // die `globalSetup` einmal pro Lauf leert. Postgres könnte parallele Writer (anders als SQLite
  // vorher), aber die Tests sind gegeneinander nicht isoliert — Parallelität bräuchte eine
  // Datenbank pro Worker, nicht bloss ein höheres `workers`.
  workers: 1,
  fullyParallel: false,

  // Leert die Nutzertabellen. Läuft nach dem Start des `webServer` — zulässig, seit die
  // Datenbank ein Server und keine Datei mehr ist (Begründung in `support/database.ts`).
  globalSetup: require.resolve('./global-setup'),

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
    // `retain-on-failure` statt `on`: aufgezeichnet wird immer, behalten nur beim Fehlschlag —
    // ein grüner Lauf hinterlässt nichts, ein roter das Video im HTML-Report, den der CI-Job
    // bei Fehlschlag als Artifact hochlädt. Damit steht es neben dem Screenshot, deckt aber
    // den Fall ab, den ein Standbild nicht erklärt: eine Navigation, die woanders endet als
    // erwartet. `on` wäre die Debug-Einstellung — jeder grüne Lauf schriebe ein Video pro Test.
    // Wer einem laufenden Test zusehen will, nimmt `npm run test:ui`.
    video: 'retain-on-failure',
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
    // Nie eine fremde Instanz adoptieren, auch lokal nicht: die Suite leert die Datenbank zu
    // Beginn (globalSetup) und darf deshalb nur gegen den Server testen, den sie selbst gestartet
    // hat. Sonst zieht sie einer laufenden Instanz die Daten unter den Füssen weg und assertet
    // gegen fremden Zustand. Kostet einen JVM-Start (~5s) pro Lauf.
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
      // Relaxed Binding: SPRING_DATASOURCE_* → spring.datasource.*. Zeigt auf die eigene
      // E2E-Datenbank, nicht auf die Dev-Datenbank (Begründung in `support/database.ts`).
      SPRING_DATASOURCE_URL: JDBC_URL,
      SPRING_DATASOURCE_USERNAME: DB_USER,
      SPRING_DATASOURCE_PASSWORD: DB_PASSWORD,
      JWT_SECRET: process.env.JWT_SECRET ?? LOCAL_TEST_JWT_SECRET,
    },
  },
});
