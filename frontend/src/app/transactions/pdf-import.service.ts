import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { ImportResponse } from './import-response.model';

/**
 * Kapselt den Upload an `POST /api/import/pdf` (BE-PDF-03, US-04).
 *
 * <p>Bewusst zustandslos: der UI-State (Spinner/Fehler/Ergebnis) liegt in der
 * {@link PdfUpload}-Komponente als Signals — analog zum Muster von
 * `CategoryOverview` + `TransactionSummaryService`. Das httpOnly-JWT-Cookie wird
 * durch den `credentialsInterceptor` automatisch mitgesendet (ADR-7).
 */
@Injectable({ providedIn: 'root' })
export class PdfImportService {
  private readonly http = inject(HttpClient);

  /**
   * Lädt einen Kontoauszug als PDF hoch; der Import läuft serverseitig synchron.
   *
   * @param file Die PDF-Datei (client-seitig bereits auf Typ und 10 MB geprüft).
   * @param force `true` überspringt den serverseitigen Duplikatcheck und ersetzt einen früheren
   *   Import desselben PDFs. Nur setzen, nachdem der User im Duplikat-Dialog «Trotzdem
   *   importieren» bestätigt hat (FE-PDF-03) — sonst entstehen ungefragt Dubletten.
   */
  importPdf(file: File, force = false): Observable<ImportResponse> {
    const formData = new FormData();
    formData.append('file', file);
    // Der Parameter entfällt im Normalfall ganz: das Backend defaultet auf false, und eine
    // URL ohne force=false ist im Netzwerk-Log eindeutig als regulärer Import lesbar.
    const options = force ? { params: { force: true } } : {};
    return this.http.post<ImportResponse>('/api/import/pdf', formData, options);
  }
}
