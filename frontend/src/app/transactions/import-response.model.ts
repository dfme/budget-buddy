/**
 * Antwort von `POST /api/import/pdf` (BE-PDF-09) — spiegelt `ImportStartedResponse.java`.
 *
 * <p>Seit ADR-14 ist der Upload der Anfang des Imports, nicht sein Ende: Das PDF ist geparst, die
 * Kategorisierung läuft im Hintergrund. Deshalb kommt hier die Job-ID zum Weiterverfolgen statt
 * einer Endzahl — und `total` als Nenner, damit der Fortschrittsbalken schon vor dem ersten
 * Status-Poll etwas anzeigen kann.
 */
export interface ImportStartedResponse {
  jobId: number;
  total: number;
}

/** Lebenszyklus eines Import-Jobs — spiegelt `ImportJobStatus.java`. */
export type ImportJobState = 'RUNNING' | 'DONE' | 'FAILED';

/**
 * Antwort von `GET /api/import/{jobId}/status` (BE-PDF-09) — die Quelle der Fortschrittsanzeige.
 *
 * <p>`degraded` meldet den Watchdog-Fall: Das serverseitige Zeitbudget war aufgebraucht und der
 * Rest wurde ohne KI-Kategorisierung als «Sonstiges» gespeichert. Der Import ist trotzdem
 * vollständig — genau das ist der Unterschied zum Verhalten vor #192.
 */
export interface ImportJobStatusResponse {
  status: ImportJobState;
  total: number;
  processed: number;
  degraded: boolean;
}
