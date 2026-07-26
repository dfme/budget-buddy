import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { ImportResponse } from './import-response.model';

/**
 * Kapselt den Upload an `POST /import/pdf` (BE-PDF-03, US-04).
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
   */
  importPdf(file: File): Observable<ImportResponse> {
    const formData = new FormData();
    formData.append('file', file);
    return this.http.post<ImportResponse>('/import/pdf', formData);
  }
}
