import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { MonthlyTotals } from './monthly-totals.model';

/** Fenstergrösse der Drei-Monats-Übersicht (FE-STS-04) — der Default des Endpoints. */
export const MONTHLY_TOTALS_WINDOW = 3;

/**
 * Kapselt den Zugriff auf `GET /api/transactions/monthly-totals` (BE-STS-07, US-12).
 *
 * <p>Bewusst zustandslos: der UI-State (laden/Fehler/Daten) liegt in der {@link Dashboard}-
 * Komponente als Signals — analog zum Muster von `CategoryOverview` +
 * {@link TransactionSummaryService}. Das httpOnly-JWT-Cookie wird durch den
 * `credentialsInterceptor` automatisch mitgesendet (ADR-7).
 */
@Injectable({ providedIn: 'root' })
export class MonthlyTotalsService {
  private readonly http = inject(HttpClient);

  /**
   * Lädt die Kennzahlen eines Monatsfensters, **neuester Monat zuerst**.
   *
   * <p>Der Endpoint liefert für jeden Monat des Fensters eine Zeile, auch für einen ohne
   * Buchungen — der Aufrufer muss keine Lücken füllen.
   *
   * @param month jüngster Monat des Fensters als `YYYY-MM`.
   * @param months Fenstergrösse; Standard {@link MONTHLY_TOTALS_WINDOW}. Das Backend begrenzt den
   *     Wert auf 1..12 und antwortet ausserhalb mit HTTP 400.
   */
  getMonthlyTotals(month: string, months = MONTHLY_TOTALS_WINDOW): Observable<MonthlyTotals[]> {
    return this.http.get<MonthlyTotals[]>('/api/transactions/monthly-totals', {
      params: new HttpParams().set('month', month).set('months', months),
    });
  }
}
