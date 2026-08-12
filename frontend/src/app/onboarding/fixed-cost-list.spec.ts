import { registerLocaleData } from '@angular/common';
import localeDeCh from '@angular/common/locales/de-CH';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { LOCALE_ID } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { provideRouter } from '@angular/router';

import { FixedCostList } from './fixed-cost-list';
import { FixedCostDetail, FixedCostSummary } from './fixed-cost.model';

// Der CurrencyPipe nutzt den app-weiten LOCALE_ID (de-CH); die Locale-Daten müssen dafür
// registriert sein — im echten App-Bootstrap erledigt das app.config.ts.
registerLocaleData(localeDeCh);

const MIETE: FixedCostDetail = { id: 1, bezeichnung: 'Miete', betrag: 1200, intervall: 'monatlich', monatsbetrag: 1200 };
const SERAFE: FixedCostDetail = { id: 2, bezeichnung: 'Serafe', betrag: 335, intervall: 'jaehrlich', monatsbetrag: 27.92 };

function summaryOf(
  fixedCosts: FixedCostDetail[],
  monthlyIncome: number | null,
  exceedsIncome: boolean,
): FixedCostSummary {
  const summeMonatlich = fixedCosts.reduce((sum, item) => sum + item.monatsbetrag, 0);
  return { fixedCosts, summeMonatlich, monthlyIncome, exceedsIncome };
}

describe('FixedCostList', () => {
  let fixture: ComponentFixture<FixedCostList>;
  let component: FixedCostList;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [FixedCostList],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: LOCALE_ID, useValue: 'de-CH' },
      ],
    }).compileComponents();

    fixture = TestBed.createComponent(FixedCostList);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  function flushInitialLoad(summary: FixedCostSummary): void {
    fixture.detectChanges();
    httpMock.expectOne('/fixed-costs').flush(summary);
    fixture.detectChanges();
  }

  function text(): string {
    return (fixture.nativeElement as HTMLElement).textContent ?? '';
  }

  // --- AC1: Liste zeigt Bezeichnung, Betrag, Intervall ---

  it('rendert alle Positionen mit Bezeichnung, Betrag, Intervall und Monatsbetrag', () => {
    flushInitialLoad(summaryOf([MIETE, SERAFE], 3000, false));

    const rows = (fixture.nativeElement as HTMLElement).querySelectorAll('tbody > tr');
    expect(rows).toHaveLength(2);
    expect(text()).toContain('Miete');
    expect(text()).toContain('1’200.00');
    expect(text()).toContain('monatlich');
    expect(text()).toContain('Serafe');
    // 'jaehrlich' auf der Leitung, «jährlich» im Template (wie im Wizard).
    expect(text()).toContain('jährlich');
  });

  it('zeigt einen Empty-State ohne Positionen', () => {
    flushInitialLoad(summaryOf([], null, false));

    expect(text()).toContain('Noch keine Fixkosten erfasst');
    expect((fixture.nativeElement as HTMLElement).querySelector('table')).toBeNull();
  });

  it('zeigt eine Fehlermeldung, wenn das Laden fehlschlägt', () => {
    fixture.detectChanges();
    httpMock.expectOne('/fixed-costs').flush('boom', { status: 500, statusText: 'Internal Server Error' });
    fixture.detectChanges();

    expect(component.errorMessage()).not.toBeNull();
    expect(text()).toContain('konnten nicht geladen werden');
  });

  // --- AC4: Warnung Fixkosten >= Einkommen ---

  it('zeigt die Warnung prominent, wenn die Fixkosten das Einkommen erreichen oder übersteigen', () => {
    flushInitialLoad(summaryOf([MIETE], 1200, true));

    expect(text()).toContain(
      'Deine Fixkosten übersteigen dein Einkommen — Safe-to-Spend kann nicht berechnet werden.',
    );
  });

  it('zeigt keine Warnung, wenn die Fixkosten das Einkommen nicht erreichen', () => {
    flushInitialLoad(summaryOf([MIETE], 3000, false));

    expect(text()).not.toContain('übersteigen dein Einkommen');
  });

  // --- AC2: Bearbeiten mit vorausgefülltem Formular ---

  it('füllt das Bearbeiten-Formular mit den aktuellen Werten der Position', () => {
    flushInitialLoad(summaryOf([SERAFE], 3000, false));

    component.startEdit(SERAFE);
    fixture.detectChanges();

    expect(component.editForm.getRawValue()).toEqual({
      bezeichnung: 'Serafe',
      betrag: 335,
      intervall: 'jaehrlich',
    });
  });

  it('speichert die Bearbeiten-Form über PUT und lädt die Liste danach neu', () => {
    flushInitialLoad(summaryOf([MIETE], 3000, false));

    component.startEdit(MIETE);
    component.editForm.setValue({ bezeichnung: 'Miete neu', betrag: 1250, intervall: 'monatlich' });
    component.saveEdit(MIETE.id);

    const putReq = httpMock.expectOne('/fixed-costs/1');
    expect(putReq.request.method).toBe('PUT');
    expect(putReq.request.body).toEqual({ bezeichnung: 'Miete neu', betrag: 1250, intervall: 'monatlich' });
    putReq.flush({ ...MIETE, bezeichnung: 'Miete neu', betrag: 1250, monatsbetrag: 1250 });

    // Re-Fetch nach dem Schreiben: summeMonatlich/exceedsIncome hängen von allen Positionen ab.
    const getReq = httpMock.expectOne('/fixed-costs');
    expect(getReq.request.method).toBe('GET');
    getReq.flush(summaryOf([{ ...MIETE, bezeichnung: 'Miete neu', betrag: 1250, monatsbetrag: 1250 }], 3000, false));
    fixture.detectChanges();

    expect(component.editingId()).toBeNull();
  });

  it('bricht das Bearbeiten ohne Request ab', () => {
    flushInitialLoad(summaryOf([MIETE], 3000, false));

    component.startEdit(MIETE);
    component.cancelEdit();
    fixture.detectChanges();

    httpMock.expectNone('/fixed-costs/1');
    expect(component.editingId()).toBeNull();
  });

  it('meldet einen abgelehnten Bearbeiten-Request', () => {
    flushInitialLoad(summaryOf([MIETE], 3000, false));

    component.startEdit(MIETE);
    component.editForm.setValue({ bezeichnung: 'Miete', betrag: 1250, intervall: 'monatlich' });
    component.saveEdit(MIETE.id);
    httpMock.expectOne('/fixed-costs/1').flush('bad', { status: 400, statusText: 'Bad Request' });
    fixture.detectChanges();

    expect(component.editError()).toContain('vom Server abgelehnt');
    expect(component.editingId()).toBe(MIETE.id);
    httpMock.expectNone('/fixed-costs');
  });

  // --- AC3: Löschen nach Bestätigung ---

  it('löscht erst nach Bestätigung im Dialog', () => {
    flushInitialLoad(summaryOf([MIETE], 3000, false));

    component.requestDelete(MIETE);
    fixture.detectChanges();

    expect(text()).toContain('Miete» wirklich löschen?');
    httpMock.expectNone('/fixed-costs/1');
  });

  it('sendet DELETE erst nach Bestätigung und lädt die Liste danach neu', () => {
    flushInitialLoad(summaryOf([MIETE], 3000, false));

    component.requestDelete(MIETE);
    component.confirmDelete();

    const deleteReq = httpMock.expectOne('/fixed-costs/1');
    expect(deleteReq.request.method).toBe('DELETE');
    deleteReq.flush(null, { status: 204, statusText: 'No Content' });

    const getReq = httpMock.expectOne('/fixed-costs');
    getReq.flush(summaryOf([], null, false));
    fixture.detectChanges();

    expect(component.pendingDelete()).toBeNull();
    expect(text()).toContain('Noch keine Fixkosten erfasst');
  });

  it('sendet kein DELETE, wenn der Dialog abgebrochen wird', () => {
    flushInitialLoad(summaryOf([MIETE], 3000, false));

    component.requestDelete(MIETE);
    component.cancelDelete();
    fixture.detectChanges();

    httpMock.expectNone('/fixed-costs/1');
    expect(component.pendingDelete()).toBeNull();
    expect(text()).toContain('Miete');
  });

  it('meldet einen fehlgeschlagenen Löschversuch', () => {
    flushInitialLoad(summaryOf([MIETE], 3000, false));

    component.requestDelete(MIETE);
    component.confirmDelete();
    httpMock.expectOne('/fixed-costs/1').flush('boom', { status: 500, statusText: 'Internal Server Error' });
    fixture.detectChanges();

    expect(component.deleteError()).toContain('Löschen fehlgeschlagen');
    httpMock.expectNone('/fixed-costs');
  });
});
