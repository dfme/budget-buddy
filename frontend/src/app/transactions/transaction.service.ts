import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { Transaction } from './transaction.model';

/**
 * Kapselt den Zugriff auf die Einzeltransaktionen: `GET /transactions` (FE-CAT-03) und
 * `PUT /transactions/{id}/category` (BE-CAT-04, US-05).
 *
 * <p>Zustandslos wie {@link TransactionSummaryService} — der UI-State (offene Kategorie,
 * laden/Fehler) liegt in der {@link CategoryOverview}-Komponente als Signals. Das
 * httpOnly-JWT-Cookie sendet der `credentialsInterceptor` automatisch mit (ADR-7).
 */
@Injectable({ providedIn: 'root' })
export class TransactionService {
  private readonly http = inject(HttpClient);

  /**
   * Lädt die Ausgaben eines Monats, absteigend nach Buchungsdatum.
   *
   * @param month Monat im Format `YYYY-MM` (z. B. `2026-07`).
   * @param category Optionaler Kategorie-Filter als deutsches Label (z. B. `"Lebensmittel"`).
   */
  list(month: string, category?: string): Observable<Transaction[]> {
    let params = new HttpParams().set('month', month);
    if (category) {
      params = params.set('category', category);
    }
    return this.http.get<Transaction[]>('/transactions', { params });
  }

  /**
   * Setzt die Kategorie einer Transaktion und lernt dabei serverseitig das Händler-Pattern
   * (BE-CAT-04) — die nächste Buchung desselben Händlers wird dadurch ohne Claude-Call
   * kategorisiert.
   *
   * @param id ID der Transaktion.
   * @param category Deutsches Kategorie-Label, z. B. `"Lebensmittel"`.
   */
  updateCategory(id: number, category: string): Observable<Transaction> {
    return this.http.put<Transaction>(`/transactions/${id}/category`, { category });
  }
}
