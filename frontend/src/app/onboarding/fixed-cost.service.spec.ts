import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';

import { FixedCost } from './fixed-cost.model';
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
});
