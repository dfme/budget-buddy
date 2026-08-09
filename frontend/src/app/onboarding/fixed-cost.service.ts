import { HttpClient } from '@angular/common/http';
import { Injectable, inject } from '@angular/core';
import { Observable } from 'rxjs';

import { CreateFixedCostRequest, FixedCost } from './fixed-cost.model';

/**
 * Kapselt `POST /fixed-costs` (US-03, FE-FC-01).
 *
 * <p>Bewusst zustandslos: der UI-State (Submit läuft / Erfolg / Fehler) liegt in der
 * {@link FixedCostWizard}-Komponente als Signals — analog zu `PdfImportService` +
 * `PdfUpload`. Das httpOnly-JWT-Cookie wird durch den `credentialsInterceptor`
 * automatisch mitgesendet (ADR-7); hier steht deshalb kein Token- oder Header-Code.
 *
 * <p><strong>Der Endpoint existiert noch nicht.</strong> Er kommt mit BE-FC-03 (#12);
 * bis dahin ist der Contract aus der Entity abgeleitet und nur durch die Tests dieser
 * Seite belegt (siehe {@link CreateFixedCostRequest}).
 */
@Injectable({ providedIn: 'root' })
export class FixedCostService {
  private readonly http = inject(HttpClient);

  /** Legt eine Fixkosten-Position an und liefert sie mit vergebener ID zurück. */
  create(request: CreateFixedCostRequest): Observable<FixedCost> {
    return this.http.post<FixedCost>('/fixed-costs', request);
  }
}
