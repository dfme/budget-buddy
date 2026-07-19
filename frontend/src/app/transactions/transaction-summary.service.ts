import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { CategorySummary } from './category-summary.model';

/**
 * Kapselt den Zugriff auf `GET /transactions/summary` (BE-CAT-05, US-05).
 *
 * <p>Bewusst zustandslos: der UI-State (laden/Fehler/Daten) liegt in der
 * {@link CategoryOverview}-Komponente als Signals — analog zum Muster von
 * `Login` + `AuthService`. Das httpOnly-JWT-Cookie wird durch den
 * `credentialsInterceptor` automatisch mitgesendet (ADR-7).
 */
@Injectable({ providedIn: 'root' })
export class TransactionSummaryService {
  private readonly http = inject(HttpClient);

  /**
   * Lädt das Ausgaben-Summary pro Kategorie für einen Monat.
   *
   * @param month Monat im Format `YYYY-MM` (z. B. `2026-07`).
   */
  getSummary(month: string): Observable<CategorySummary> {
    return this.http.get<CategorySummary>('/transactions/summary', {
      params: new HttpParams().set('month', month),
    });
  }
}
