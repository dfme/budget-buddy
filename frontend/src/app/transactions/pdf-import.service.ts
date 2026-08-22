import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable, timer } from 'rxjs';
import { switchMap, takeWhile } from 'rxjs/operators';

import { ImportJobStatusResponse, ImportStartedResponse } from './import-response.model';

/**
 * Abstand zwischen zwei Status-Abfragen.
 *
 * <p>Gewählt entlang der serverseitigen Auflösung: Der Fortschritt wächst pro Kategorisierungs-
 * Bündel (20 Transaktionen, ~1–3s). Häufiger zu pollen liefert dieselbe Zahl mehrfach, seltener
 * liesse den Balken ruckeln.
 */
const POLL_INTERVAL_MS = 700;

/**
 * Kapselt den Upload an `POST /api/import/pdf` und die Fortschrittsabfrage an
 * `GET /api/import/{jobId}/status` (BE-PDF-09, US-04).
 *
 * <p>Bewusst zustandslos: der UI-State (Fortschritt/Fehler/Ergebnis) liegt in der
 * {@link PdfUpload}-Komponente als Signals — analog zum Muster von `CategoryOverview` +
 * `TransactionSummaryService`. Das httpOnly-JWT-Cookie wird durch den `credentialsInterceptor`
 * automatisch mitgesendet (ADR-7).
 */
@Injectable({ providedIn: 'root' })
export class PdfImportService {
  private readonly http = inject(HttpClient);

  /**
   * Lädt einen Kontoauszug als PDF hoch und startet den Import.
   *
   * <p>Der Request kehrt zurück, sobald das PDF geparst ist (~2s); die Kategorisierung läuft
   * danach serverseitig weiter. Fehler des Parsens kommen weiterhin als HTTP-Status zurück
   * (400 mit `reason`, 409 Duplikat, 413 zu gross) — erst was während der Kategorisierung
   * schiefgeht, meldet {@link pollJob}.
   *
   * @param file Die PDF-Datei (client-seitig bereits auf Typ und 10 MB geprüft).
   * @param force `true` überspringt den serverseitigen Duplikatcheck und ersetzt einen früheren
   *   Import desselben PDFs. Nur setzen, nachdem der User im Duplikat-Dialog «Trotzdem
   *   importieren» bestätigt hat (FE-PDF-03) — sonst entstehen ungefragt Dubletten.
   */
  importPdf(file: File, force = false): Observable<ImportStartedResponse> {
    const formData = new FormData();
    formData.append('file', file);
    // Der Parameter entfällt im Normalfall ganz: das Backend defaultet auf false, und eine
    // URL ohne force=false ist im Netzwerk-Log eindeutig als regulärer Import lesbar.
    const options = force ? { params: { force: true } } : {};
    return this.http.post<ImportStartedResponse>('/api/import/pdf', formData, options);
  }

  /** Einmalige Statusabfrage — für Tests und gezielte Nachfragen. */
  jobStatus(jobId: number): Observable<ImportJobStatusResponse> {
    return this.http.get<ImportJobStatusResponse>(`/api/import/${jobId}/status`);
  }

  /**
   * Fragt den Job-Status im Takt ab, bis er einen Endzustand erreicht.
   *
   * <p>`takeWhile(..., true)` mit `inclusive`: Der abschliessende Status (`DONE`/`FAILED`) wird
   * noch ausgegeben und erst danach beendet. Ohne das Flag käme der Endzustand nie beim Aufrufer
   * an — die Komponente wüsste, dass der Import fertig ist, aber nicht wie er ausging.
   */
  pollJob(jobId: number): Observable<ImportJobStatusResponse> {
    return timer(0, POLL_INTERVAL_MS).pipe(
      switchMap(() => this.jobStatus(jobId)),
      takeWhile((status) => status.status === 'RUNNING', true),
    );
  }
}
