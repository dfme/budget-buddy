import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { SafeToSpendResponse } from './safe-to-spend.model';

/**
 * Kapselt den Zugriff auf `GET /budget/safe-to-spend` (BE-STS-03, US-06).
 *
 * <p>Bewusst zustandslos: der UI-State (laden/Fehler/Daten) liegt in der
 * {@link Dashboard}-Komponente als Signals — analog zum Muster von
 * `CategoryOverview` + `TransactionSummaryService`. Das httpOnly-JWT-Cookie wird
 * durch den `credentialsInterceptor` automatisch mitgesendet (ADR-7).
 */
@Injectable({ providedIn: 'root' })
export class SafeToSpendService {
  private readonly http = inject(HttpClient);

  /** Lädt den wöchentlichen Safe-to-Spend-Betrag des eingeloggten Users. */
  getSafeToSpend(): Observable<SafeToSpendResponse> {
    return this.http.get<SafeToSpendResponse>('/budget/safe-to-spend');
  }
}
