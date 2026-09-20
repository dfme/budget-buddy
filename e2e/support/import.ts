import { readFileSync } from 'node:fs';

import { type APIRequestContext, expect } from '@playwright/test';

/**
 * Obergrenze für den Import-Job. Seit ADR-14 (BE-PDF-09) läuft die Kategorisierung asynchron; in
 * der Testinstanz ohne `ANTHROPIC_API_KEY` dauert sie Millisekunden, der serverseitige Watchdog
 * steht aber auf 300s. Grosszügig, damit ein legitim langsamer Job nicht als Testfehler
 * erscheint, während das Backend noch innerhalb seiner eigenen Grenze arbeitet.
 */
export const IMPORT_TIMEOUT_MS = 60_000;

/**
 * Importiert eine Fixture über die API und wartet, bis der Job einen Endzustand erreicht hat.
 *
 * <p>Bewusst nicht durch die Upload-UI: der Import ist Vorbedingung der aufrufenden Tests, nicht
 * ihr Gegenstand. Ihn durchzuklicken würde deren Story an US-04 aufhängen — ein Bug im
 * Upload-UI liesse dann auch fremde Tests rot werden, ohne Hinweis auf die eigentliche Ursache.
 *
 * <p>Auf `DONE` gewartet wird nicht aus Vorsicht: `Transaction.category` bleibt bis zum Abschluss
 * des Jobs `null` (`Transaction.java:67`), und Kategorisierung wie Abo-Erkennung
 * (`ImportJobRunner.detectRecurringExpenses`, vor `finishSuccessfully`) setzen darauf auf. Ein
 * `FAILED`-Job würde als blosses «nicht mehr RUNNING» durchgehen und der Test scheiterte danach
 * an einer leeren Ansicht — mit einer Meldung, die auf den eigentlichen Testgegenstand zeigt
 * statt auf den Import.
 *
 * @param expectedTransactionCount Wenn gesetzt, wird die Anzahl der von `POST /api/import/pdf`
 *   gemeldeten geparsten Buchungen dagegen geprüft — Gegenprobe an der Vorbedingung. Optional,
 *   weil nicht jede Aufrufstelle eine feste Erwartung an die Fixture hat.
 */
export async function importFixture(
  request: APIRequestContext,
  fixturePath: string,
  expectedTransactionCount?: number,
): Promise<void> {
  const upload = await request.post('/api/import/pdf', {
    multipart: {
      file: {
        name: fixturePath.split(/[/\\]/).pop()!,
        mimeType: 'application/pdf',
        buffer: readFileSync(fixturePath),
      },
    },
  });
  expect(upload.status(), 'Vorbedingung: POST /api/import/pdf').toBe(202);

  const { jobId, total } = (await upload.json()) as { jobId: number; total: number };
  if (expectedTransactionCount !== undefined) {
    expect(total, 'Vorbedingung: Anzahl geparster Buchungen').toBe(expectedTransactionCount);
  }

  let status = 'RUNNING';
  await expect
    .poll(
      async () => {
        const response = await request.get(`/api/import/${jobId}/status`);
        expect(response.status(), `GET /api/import/${jobId}/status`).toBe(200);
        ({ status } = (await response.json()) as { status: string });
        return status;
      },
      {
        timeout: IMPORT_TIMEOUT_MS,
        message: `Import-Job ${jobId} hat keinen Endzustand erreicht`,
      },
    )
    .not.toBe('RUNNING');

  // DONE statt bloss «nicht mehr RUNNING»: siehe Begründung oben.
  expect(status, `Import-Job ${jobId} endete nicht erfolgreich`).toBe('DONE');
}
