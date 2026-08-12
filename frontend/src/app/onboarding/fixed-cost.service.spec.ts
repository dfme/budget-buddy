import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { FixedCost, FixedCostDetail, FixedCostSummary } from './fixed-cost.model';
import { FixedCostService } from './fixed-cost.service';

describe('FixedCostService', () => {
  let service: FixedCostService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(FixedCostService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('posts the entry to /fixed-costs and returns it with its id', () => {
    const created: FixedCost = {
      id: 7,
      bezeichnung: 'Miete',
      betrag: 1200,
      intervall: 'monatlich',
    };
    let received: FixedCost | undefined;

    service
      .create({ bezeichnung: 'Miete', betrag: 1200, intervall: 'monatlich' })
      .subscribe((response) => (received = response));

    const req = httpMock.expectOne('/fixed-costs');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      bezeichnung: 'Miete',
      betrag: 1200,
      intervall: 'monatlich',
    });
    req.flush(created);

    expect(received).toEqual(created);
  });

  it('sends betrag as a JSON number, not a string', () => {
    service.create({ bezeichnung: 'Serafe', betrag: 335.5, intervall: 'jaehrlich' }).subscribe();

    const req = httpMock.expectOne('/fixed-costs');
    // Hält den Contract aus fixed-cost.model.ts ehrlich: das Backend nutzt BigDecimal
    // ohne String-Serializer. Kippt die Annahme mit #12, wird diese Assertion rot.
    expect(typeof req.request.body.betrag).toBe('number');
    req.flush({ id: 1, bezeichnung: 'Serafe', betrag: 335.5, intervall: 'jaehrlich' });
  });

  it('lädt die Übersicht über GET /fixed-costs', () => {
    const summary: FixedCostSummary = {
      fixedCosts: [{ id: 7, bezeichnung: 'Miete', betrag: 1200, intervall: 'monatlich', monatsbetrag: 1200 }],
      summeMonatlich: 1200,
      monthlyIncome: 3000,
      exceedsIncome: false,
    };
    let received: FixedCostSummary | undefined;

    service.list().subscribe((response) => (received = response));

    const req = httpMock.expectOne('/fixed-costs');
    expect(req.request.method).toBe('GET');
    req.flush(summary);

    expect(received).toEqual(summary);
  });

  it('sendet PUT /fixed-costs/{id} und liefert die aktualisierte Position', () => {
    const updated: FixedCostDetail = {
      id: 7,
      bezeichnung: 'Miete',
      betrag: 1250,
      intervall: 'monatlich',
      monatsbetrag: 1250,
    };
    let received: FixedCostDetail | undefined;

    service
      .update(7, { bezeichnung: 'Miete', betrag: 1250, intervall: 'monatlich' })
      .subscribe((response) => (received = response));

    const req = httpMock.expectOne('/fixed-costs/7');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({
      bezeichnung: 'Miete',
      betrag: 1250,
      intervall: 'monatlich',
    });
    req.flush(updated);

    expect(received).toEqual(updated);
  });

  it('sendet DELETE /fixed-costs/{id}', () => {
    let completed = false;

    service.delete(7).subscribe(() => (completed = true));

    const req = httpMock.expectOne('/fixed-costs/7');
    expect(req.request.method).toBe('DELETE');
    req.flush(null, { status: 204, statusText: 'No Content' });

    expect(completed).toBe(true);
  });
});
