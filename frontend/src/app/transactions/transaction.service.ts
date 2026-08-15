import { HttpClient, HttpParams } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { Transaction, TransactionPage } from './transaction.model';

/**
 * Buchungen pro Seite — derselbe Wert, den das Backend als Standard für `size` verwendet
 * (`TransactionListService.DEFAULT_PAGE_SIZE`) und den US-13 für die erste Anzeige verlangt.
 */
export const TRANSACTION_PAGE_SIZE = 20;

/**
 * Grösstes `size`, das das Backend annimmt (`TransactionListService.MAX_PAGE_SIZE`); darüber
 * antwortet es mit 400. Begrenzt das Fenster, das nach einer Kategorie-Korrektur am Stück
 * nachgeladen wird.
 */
export const MAX_TRANSACTION_PAGE_SIZE = 100;

/**
 * Kapselt den Zugriff auf die Einzeltransaktionen: `GET /transactions` (FE-CAT-03, FE-CAT-05) und
 * `PUT /transactions/{id}/category` (BE-CAT-04, US-05).
 *
 * <p>Zustandslos wie {@link TransactionSummaryService} — der UI-State (offene Kategorie, geladene
 * Seiten, laden/Fehler) liegt in der {@link CategoryOverview}-Komponente als Signals. Das
 * httpOnly-JWT-Cookie sendet der `credentialsInterceptor` automatisch mit (ADR-7).
 */
@Injectable({ providedIn: 'root' })
export class TransactionService {
  private readonly http = inject(HttpClient);

  /**
   * Lädt eine Seite der Ausgaben eines Monats, absteigend nach Buchungsdatum.
   *
   * @param month Monat im Format `YYYY-MM` (z. B. `2026-07`).
   * @param category Optionaler Kategorie-Filter als deutsches Label (z. B. `"Lebensmittel"`).
   * @param page Nullbasierte Seitennummer.
   * @param size Buchungen pro Seite, höchstens {@link MAX_TRANSACTION_PAGE_SIZE}.
   */
  list(
    month: string,
    category?: string,
    page = 0,
    size = TRANSACTION_PAGE_SIZE,
  ): Observable<TransactionPage> {
    let params = new HttpParams()
      .set('month', month)
      .set('page', page)
      .set('size', size);
    if (category) {
      params = params.set('category', category);
    }
    return this.http.get<TransactionPage>('/transactions', { params });
  }

  /**
   * Lädt die Monate, in denen der User Ausgaben hat — neuester zuerst, im Format `YYYY-MM`
   * (FE-CAT-04, US-12).
   *
   * <p>Eingabe des Direktsprung-Dropdowns. Ohne diese Liste müsste das Frontend eine Jahresspanne
   * raten: zu kurz, und alte Kontoauszüge wären unerreichbar; zu lang, und die Auswahl bestünde
   * aus leeren Jahren.
   */
  availableMonths(): Observable<string[]> {
    return this.http.get<string[]>('/transactions/months');
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
