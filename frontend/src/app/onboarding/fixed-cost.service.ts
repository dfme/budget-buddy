import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { CreateFixedCostRequest, FixedCost, FixedCostDetail, FixedCostSummary } from './fixed-cost.model';

/**
 * Kapselt die CRUD-Calls für Fixkosten (US-03, FE-FC-01/FE-FC-03).
 *
 * <p>Bewusst zustandslos: der UI-State (Laden / Submit läuft / Erfolg / Fehler) liegt in den
 * Komponenten als Signals — analog zu `PdfImportService` + `PdfUpload`. Das httpOnly-JWT-Cookie
 * wird durch den `credentialsInterceptor` automatisch mitgesendet (ADR-7); hier steht deshalb
 * kein Token- oder Header-Code.
 */
@Injectable({ providedIn: 'root' })
export class FixedCostService {
  private readonly http = inject(HttpClient);

  /** Legt eine Fixkosten-Position an und liefert sie mit vergebener ID zurück. */
  create(request: CreateFixedCostRequest): Observable<FixedCost> {
    return this.http.post<FixedCost>('/api/fixed-costs', request);
  }

  /** Alle Positionen des Users plus Monatssumme, Einkommen und Warn-Flag (FE-FC-03). */
  list(): Observable<FixedCostSummary> {
    return this.http.get<FixedCostSummary>('/api/fixed-costs');
  }

  /** Überschreibt Bezeichnung, Betrag und Intervall einer bestehenden Position (FE-FC-03). */
  update(id: number, request: CreateFixedCostRequest): Observable<FixedCostDetail> {
    return this.http.put<FixedCostDetail>(`/api/fixed-costs/${id}`, request);
  }

  /** Löscht eine Position endgültig (FE-FC-03). */
  delete(id: number): Observable<void> {
    return this.http.delete<void>(`/api/fixed-costs/${id}`);
  }
}
