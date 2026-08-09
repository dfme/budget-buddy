import { resetDatabase } from './support/database';

/**
 * Setzt die E2E-Datenbank vor dem Lauf zurück (DB-05, ADR-12).
 *
 * Playwright startet den `webServer` **vor** `globalSetup`. Unter SQLite war das der Grund, das
 * Zurücksetzen stattdessen beim Laden der Config zu erledigen: eine gelöschte Datei hätte sonst
 * bloss den Inode unter dem laufenden Backend weg-unlinked. Mit PostgreSQL wirkt das `TRUNCATE`
 * in derselben Datenbank, an der das Backend hängt — `globalSetup` ist damit der richtige Ort,
 * und die Sonderbehandlung für Worker-Prozesse entfällt.
 */
export default async function globalSetup(): Promise<void> {
  await resetDatabase();
}
