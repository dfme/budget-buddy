import { registerLocaleData } from '@angular/common';
import localeDeCh from '@angular/common/locales/de-CH';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { LOCALE_ID } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { FixedCostSection } from './fixed-cost-section';
import { FixedCostDetail, FixedCostSummary } from './fixed-cost.model';

// Der CurrencyPipe nutzt den app-weiten LOCALE_ID (de-CH); die Locale-Daten müssen dafür
// registriert sein — im echten App-Bootstrap erledigt das app.config.ts.
registerLocaleData(localeDeCh);

const MIETE: FixedCostDetail = {
  id: 1,
  bezeichnung: 'Miete',
  betrag: 1200,
  intervall: 'monatlich',
  monatsbetrag: 1200,
};
const SERAFE: FixedCostDetail = {
  id: 2,
  bezeichnung: 'Serafe',
  betrag: 335,
  intervall: 'jaehrlich',
  monatsbetrag: 27.92,
};

function summaryOf(
  fixedCosts: FixedCostDetail[],
  monthlyIncome: number | null,
  exceedsIncome: boolean,
): FixedCostSummary {
  const summeMonatlich = fixedCosts.reduce((sum, item) => sum + item.monatsbetrag, 0);
  return { fixedCosts, summeMonatlich, monthlyIncome, exceedsIncome };
}

/**
 * Der Fixkosten-Abschnitt für sich (FE-FC-13) — Tabelle, Bearbeiten, Löschen, Dialog «Neue
 * Position» und Einkommens-Warnung. Bis FE-FC-13 standen diese Tests in `fixed-cost-list.spec.ts`,
 * weil der Abschnitt Teil der Budget-Seite war. Dort bleibt, was die Seite betrifft (Überschriften,
 * Reihenfolge der Abschnitte, Neuladen nach dem Einkommen); die Einbettung im Onboarding-Wizard
 * prüft `fixed-cost-wizard.spec.ts`.
 */
describe('FixedCostSection', () => {
  let fixture: ComponentFixture<FixedCostSection>;
  let component: FixedCostSection;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [FixedCostSection],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        { provide: LOCALE_ID, useValue: 'de-CH' },
      ],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);

    fixture = TestBed.createComponent(FixedCostSection);
    component = fixture.componentInstance;
    fixture.componentRef.setInput('heading', 'Erfasste Fixkosten');
  });

  afterEach(() => httpMock.verify());

  function flushInitialLoad(summary: FixedCostSummary): void {
    fixture.detectChanges();
    httpMock.expectOne('/api/fixed-costs').flush(summary);
    fixture.detectChanges();
  }

  /**
   * Textinhalt mit normalisiertem Whitespace. Die CurrencyPipe setzt unter de-CH ein
   * geschütztes Leerzeichen zwischen `CHF` und Betrag — im Test soll `'CHF 1’245.82'` mit
   * gewöhnlichem Leerzeichen genügen.
   */
  function text(): string {
    return ((fixture.nativeElement as HTMLElement).textContent ?? '').replace(/\s+/g, ' ');
  }

  /** Zeile in der Fixkosten-Tabelle, die `bezeichnung` enthält. */
  function rowFor(bezeichnung: string): HTMLElement {
    const row = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('tbody > tr'),
    ).find((tr) => tr.textContent?.includes(bezeichnung));
    if (!row) {
      throw new Error(`Keine Zeile mit "${bezeichnung}"`);
    }
    return row as HTMLElement;
  }

  /** Klickt den Button mit dieser Beschriftung innerhalb von `container`. */
  function clickButton(container: Element, label: string): void {
    const button = Array.from<HTMLButtonElement>(container.querySelectorAll('button')).find(
      (btn) => btn.textContent?.trim() === label,
    );
    if (!button) {
      throw new Error(`Kein Button mit der Beschriftung "${label}"`);
    }
    button.click();
  }

  /** Klickt die Aktion des Löschen-Dialogs mit dieser Beschriftung. */
  function clickModalButton(label: string): void {
    clickButton(fixture.nativeElement.querySelector('app-modal .modal__actions')!, label);
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
    // 'jaehrlich' auf der Leitung, «jährlich» im Template (wie im Dialog).
    expect(text()).toContain('jährlich');
  });

  it('rendert die Tabelle innerhalb eines horizontal scrollbaren Containers', () => {
    flushInitialLoad(summaryOf([MIETE], 3000, false));

    const root = fixture.nativeElement as HTMLElement;
    const wrapper = root.querySelector('.table-scroll');
    expect(wrapper?.querySelector('table')).not.toBeNull();
    expect(getComputedStyle(wrapper as HTMLElement).overflowX).toBe('auto');
  });

  // --- FE-FC-08: drei Spalten und Icon-Buttons ---
  // jsdom rechnet kein Layout und wertet keine Media Queries aus: hier ist belegt, was im DOM
  // steht (ein Markup für beide Varianten). Dass nichts überläuft und die Labels erst ab 900px
  // sichtbar sind, zeigt `e2e/tests/fixed-cost-list-mobile.spec.ts`.

  /** Text eines Elements, Whitespace (inkl. geschütztem Leerzeichen der Währung) normalisiert. */
  function normalized(el: Element | null): string {
    return (el?.textContent ?? '').replace(/\s+/g, ' ').trim();
  }

  it('zeigt unter der Bezeichnung Betrag und Intervall, bei monatlich nur das Intervall', () => {
    flushInitialLoad(summaryOf([MIETE, SERAFE], 3000, false));

    expect(normalized(rowFor('Serafe').querySelector('.subline'))).toBe('CHF 335.00 · jährlich');
    // Monatlich: der Betrag wäre derselbe wie der Monatsbetrag daneben.
    expect(normalized(rowFor('Miete').querySelector('.subline'))).toBe('monatlich');
  });

  it('bleibt eine Tabelle mit drei Spalten: Bezeichnung, Monatsbetrag, Aktionen', () => {
    flushInitialLoad(summaryOf([MIETE], 3000, false));

    const root = fixture.nativeElement as HTMLElement;
    const headers = Array.from(root.querySelectorAll('thead th')).map((th) => normalized(th));
    expect(headers).toEqual(['Bezeichnung', 'Monatsbetrag', 'Aktionen']);
    const cells = rowFor('Miete').querySelectorAll('td');
    expect(cells).toHaveLength(3);
    expect(normalized(cells[1])).toBe('CHF 1’200.00');
    expect(root.querySelectorAll('tfoot tr > *')).toHaveLength(3);
  });

  it('gibt Bearbeiten und Löschen einen zugänglichen Namen mit der Position', () => {
    flushInitialLoad(summaryOf([MIETE, SERAFE], 3000, false));

    const labels = Array.from(rowFor('Miete').querySelectorAll('button')).map((btn) =>
      btn.getAttribute('aria-label'),
    );
    expect(labels).toEqual(['Bearbeiten: Miete', 'Löschen: Miete']);
  });

  it('zeigt in beiden Buttons ein Icon und das Label genau einmal', () => {
    flushInitialLoad(summaryOf([MIETE], 3000, false));

    const buttons = Array.from(rowFor('Miete').querySelectorAll('button'));
    expect(buttons).toHaveLength(2);
    for (const button of buttons) {
      expect(button.querySelector('svg')?.getAttribute('aria-hidden')).toBe('true');
      // Der Wechsel Icon+Text ↔ Icon ist CSS am Breakpoint — kein zweiter Button-Satz.
      expect(button.classList).toContain('btn--icon-only-mobile');
    }
    expect(buttons.map((btn) => normalized(btn))).toEqual(['Bearbeiten', 'Löschen']);
    // Tooltip mit dem Label, solange unter 900px nur das Icon zu sehen ist.
    expect(buttons.map((btn) => btn.title)).toEqual(['Bearbeiten', 'Löschen']);
  });

  it('zeigt einen Empty-State ohne Positionen', () => {
    flushInitialLoad(summaryOf([], null, false));

    expect(text()).toContain('Noch keine Fixkosten erfasst');
    expect((fixture.nativeElement as HTMLElement).querySelector('table')).toBeNull();
  });

  it('zeigt eine Fehlermeldung, wenn das Laden fehlschlägt', () => {
    fixture.detectChanges();
    httpMock
      .expectOne('/api/fixed-costs')
      .flush('boom', { status: 500, statusText: 'Internal Server Error' });
    fixture.detectChanges();

    expect(component.errorMessage()).not.toBeNull();
    expect(text()).toContain('konnten nicht geladen werden');
  });

  // --- FE-FC-13: Überschrift vom Einbettungsort, Schnittstelle nach aussen ---

  describe('Überschrift und Schnittstelle (FE-FC-13)', () => {
    function root(): HTMLElement {
      return fixture.nativeElement as HTMLElement;
    }

    it('rendert die Überschrift standardmässig als h3, wie unter «Ausgaben» auf /budget', () => {
      flushInitialLoad(summaryOf([MIETE], 3000, false));

      expect(root().querySelector('h3')?.textContent?.trim()).toBe('Erfasste Fixkosten');
      expect(root().querySelector('h2')).toBeNull();
    });

    it('rendert die Überschrift als h2, wenn der Einbettungsort das verlangt', () => {
      fixture.componentRef.setInput('heading', 'Fixkosten');
      fixture.componentRef.setInput('headingLevel', 2);
      flushInitialLoad(summaryOf([MIETE], 3000, false));

      const h2 = root().querySelector('h2');
      expect(h2?.textContent?.trim()).toBe('Fixkosten');
      expect(root().querySelector('h3')).toBeNull();
      // Der Scrollbereich bleibt auf beiden Ebenen über die Überschrift benannt.
      expect(root().querySelector('.table-scroll')?.getAttribute('aria-labelledby')).toBe(h2?.id);
    });

    it('stellt «+ Neue Position» neben die Überschrift', () => {
      flushInitialLoad(summaryOf([MIETE], 3000, false));

      const button = root().querySelector<HTMLButtonElement>('.section-header button[appButton]');
      expect(button?.textContent?.trim()).toBe('+ Neue Position');
      expect(button?.type).toBe('button');
      expect(button?.parentElement?.querySelector('h3')?.textContent?.trim()).toBe(
        'Erfasste Fixkosten',
      );
    });

    it('meldet über hasPositions, ob die geladene Liste Positionen enthält', () => {
      expect(component.hasPositions()).toBe(false);
      flushInitialLoad(summaryOf([MIETE], 3000, false));
      expect(component.hasPositions()).toBe(true);

      // Wer alles wieder löscht, hat nichts mehr erfasst — kein Sitzungs-Flag, das stehen bliebe.
      component.reload();
      httpMock.expectOne('/api/fixed-costs').flush(summaryOf([], 3000, false));
      expect(component.hasPositions()).toBe(false);
    });

    it('lädt über reload() neu und übernimmt die neue Warnung', () => {
      flushInitialLoad(summaryOf([MIETE], 1000, true));
      expect(root().querySelector('.income-warning')).not.toBeNull();

      component.reload();
      httpMock.expectOne('/api/fixed-costs').flush(summaryOf([MIETE], 4000, false));
      fixture.detectChanges();

      expect(root().querySelector('.income-warning')).toBeNull();
    });
  });

  // --- AC4: Warnung Fixkosten >= Einkommen ---

  describe('Dialog «Neue Position» (FE-FC-10)', () => {
    const KRANKENKASSE: FixedCostDetail = {
      id: 3,
      bezeichnung: 'Krankenkasse',
      betrag: 1200,
      intervall: 'quartalsweise',
      monatsbetrag: 400,
    };

    function root(): HTMLElement {
      return fixture.nativeElement as HTMLElement;
    }

    function dialog(): HTMLElement | null {
      return root().querySelector('app-fixed-cost-create-dialog');
    }

    function newPositionButton(): HTMLButtonElement {
      return root().querySelector<HTMLButtonElement>('.section-header button')!;
    }

    function fillAndSave(bezeichnung: string, betrag: string, intervall: string): void {
      const name = root().querySelector<HTMLInputElement>('#create-bezeichnung')!;
      name.value = bezeichnung;
      name.dispatchEvent(new Event('input'));
      const amount = root().querySelector<HTMLInputElement>('#create-betrag')!;
      amount.value = betrag;
      amount.dispatchEvent(new Event('input'));
      const interval = root().querySelector<HTMLSelectElement>('#create-intervall')!;
      interval.value = intervall;
      interval.dispatchEvent(new Event('change'));
      fixture.detectChanges();
      const buttons = dialog()!.querySelectorAll<HTMLButtonElement>('.modal__actions button');
      buttons[buttons.length - 1].click();
      fixture.detectChanges();
    }

    beforeEach(() => {
      // Der Fokus-Trap des Modals braucht die Komponente im echten Dokument.
      document.body.appendChild(fixture.nativeElement);
    });

    afterEach(() => fixture.nativeElement.remove());

    it('öffnet über «+ Neue Position» den Dialog innerhalb der Seite', () => {
      // Dass nicht navigiert wird, belegt das fehlende `href` (Test «stellt «+ Neue Position» …»
      // oben) — ein Button ohne `routerLink` kann gar nicht navigieren. Hier zählt, dass der
      // Dialog in dieser Komponente entsteht und die Tabelle darunter stehen bleibt.
      flushInitialLoad(summaryOf([MIETE], 3000, false));
      expect(dialog()).toBeNull();

      newPositionButton().click();
      fixture.detectChanges();

      expect(root().querySelector('app-fixed-cost-create-dialog')).not.toBeNull();
      expect(rowFor('Miete')).toBeTruthy();
    });

    it('öffnet über «Jetzt erfassen» im Leerzustand denselben Dialog — ein Button, kein Link', () => {
      flushInitialLoad(summaryOf([], 3000, false));

      const link = root().querySelector<HTMLElement>('.status.empty .link-button')!;
      expect(link.tagName).toBe('BUTTON');
      expect(link.hasAttribute('href')).toBe(false);
      expect(link.textContent?.trim()).toBe('Jetzt erfassen');
      link.click();
      fixture.detectChanges();

      expect(dialog()).not.toBeNull();
    });

    it('setzt nach dem Speichern aus dem Leerzustand den Fokus auf «+ Neue Position»', async () => {
      // «Jetzt erfassen» verschwindet mit der ersten Position — ohne gezielten Fokus fiele er
      // auf `body` (Review-Befund #373).
      flushInitialLoad(summaryOf([], 3000, false));
      const link = root().querySelector<HTMLButtonElement>('.status.empty .link-button')!;
      link.focus();
      link.click();
      fixture.detectChanges();

      fillAndSave('Krankenkasse', '1200', 'quartalsweise');
      httpMock.expectOne({ method: 'POST', url: '/api/fixed-costs' }).flush({});
      await fixture.whenStable();

      expect(document.activeElement).toBe(newPositionButton());

      httpMock
        .expectOne({ method: 'GET', url: '/api/fixed-costs' })
        .flush(summaryOf([KRANKENKASSE], 3000, false));
      await fixture.whenStable();

      // Überdauert das Neuladen: der Button steht ausserhalb von Lade- und Leerzustand.
      expect(document.activeElement).toBe(newPositionButton());
    });

    it('schliesst nach dem Speichern und zeigt die neue Zeile samt Total aus dem Neuladen', () => {
      flushInitialLoad(summaryOf([MIETE], 3000, false));
      newPositionButton().click();
      fixture.detectChanges();

      fillAndSave('Krankenkasse', '1200', 'quartalsweise');
      const post = httpMock.expectOne({ method: 'POST', url: '/api/fixed-costs' });
      post.flush({ id: 3, bezeichnung: 'Krankenkasse', betrag: 1200, intervall: 'quartalsweise' });
      fixture.detectChanges();

      httpMock
        .expectOne({ method: 'GET', url: '/api/fixed-costs' })
        .flush(summaryOf([MIETE, KRANKENKASSE], 3000, false));
      fixture.detectChanges();

      expect(dialog()).toBeNull();
      expect(text()).toContain('Krankenkasse');
      expect(root().querySelector('tfoot')?.textContent?.replace(/\s+/g, ' ')).toContain(
        'CHF 1’600.00',
      );
    });

    it('zeigt die Einkommens-Warnung, wenn die neue Position das Einkommen übersteigen lässt', () => {
      flushInitialLoad(summaryOf([MIETE], 1500, false));
      expect(root().querySelector('.income-warning')).toBeNull();
      newPositionButton().click();
      fixture.detectChanges();

      fillAndSave('Krankenkasse', '1200', 'quartalsweise');
      httpMock.expectOne({ method: 'POST', url: '/api/fixed-costs' }).flush({});
      fixture.detectChanges();
      httpMock
        .expectOne({ method: 'GET', url: '/api/fixed-costs' })
        .flush(summaryOf([MIETE, KRANKENKASSE], 1500, true));
      fixture.detectChanges();

      expect(root().querySelector('.income-warning')).not.toBeNull();
    });

    it('schliesst beim Abbrechen ohne Request, und der nächste Dialog ist leer', () => {
      flushInitialLoad(summaryOf([MIETE], 3000, false));
      newPositionButton().click();
      fixture.detectChanges();

      const name = root().querySelector<HTMLInputElement>('#create-bezeichnung')!;
      name.value = 'Halbfertig';
      name.dispatchEvent(new Event('input'));
      document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
      fixture.detectChanges();

      // `httpMock.verify()` im afterEach belegt: weder POST noch erneutes GET.
      expect(dialog()).toBeNull();
      expect(text()).toContain('Miete');

      newPositionButton().click();
      fixture.detectChanges();
      expect(root().querySelector<HTMLInputElement>('#create-bezeichnung')!.value).toBe('');
    });
  });

  it('zeigt die Warnung prominent mit den konkreten Beträgen, wenn die Fixkosten das Einkommen erreichen oder übersteigen', () => {
    flushInitialLoad(summaryOf([MIETE], 1200, true));

    expect(text()).toContain('Deine Fixkosten (CHF 1’200.00)');
    expect(text()).toContain('dein Einkommen (CHF 1’200.00)');
    expect(text()).toContain('Safe-to-Spend kann nicht berechnet werden.');
  });

  it('zeigt keine Warnung, wenn die Fixkosten das Einkommen nicht erreichen', () => {
    flushInitialLoad(summaryOf([MIETE], 3000, false));

    expect(text()).not.toContain('übersteigen dein Einkommen');
  });

  // --- AC2: Bearbeiten mit vorausgefülltem Formular ---

  it('füllt das Bearbeiten-Formular mit den aktuellen Werten der Position', () => {
    flushInitialLoad(summaryOf([SERAFE], 3000, false));

    clickButton(rowFor('Serafe'), 'Bearbeiten');
    fixture.detectChanges();

    const root = fixture.nativeElement as HTMLElement;
    expect(root.querySelector<HTMLInputElement>('#edit-bezeichnung')?.value).toBe('Serafe');
    expect(root.querySelector<HTMLInputElement>('#edit-betrag')?.value).toBe('335');
    expect(root.querySelector<HTMLSelectElement>('#edit-intervall')?.value).toBe('jaehrlich');
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

    const putReq = httpMock.expectOne('/api/fixed-costs/1');
    expect(putReq.request.method).toBe('PUT');
    expect(putReq.request.body).toEqual({
      bezeichnung: 'Miete neu',
      betrag: 1250,
      intervall: 'monatlich',
    });
    putReq.flush({ ...MIETE, bezeichnung: 'Miete neu', betrag: 1250, monatsbetrag: 1250 });

    // Re-Fetch nach dem Schreiben: summeMonatlich/exceedsIncome hängen von allen Positionen ab.
    const getReq = httpMock.expectOne('/api/fixed-costs');
    expect(getReq.request.method).toBe('GET');
    getReq.flush(
      summaryOf(
        [{ ...MIETE, bezeichnung: 'Miete neu', betrag: 1250, monatsbetrag: 1250 }],
        3000,
        false,
      ),
    );
    fixture.detectChanges();

    expect(component.editingId()).toBeNull();
  });

  it('bricht das Bearbeiten ohne Request ab', () => {
    flushInitialLoad(summaryOf([MIETE], 3000, false));

    component.startEdit(MIETE);
    component.cancelEdit();
    fixture.detectChanges();

    httpMock.expectNone('/api/fixed-costs/1');
    expect(component.editingId()).toBeNull();
  });

  it('meldet einen abgelehnten Bearbeiten-Request', () => {
    flushInitialLoad(summaryOf([MIETE], 3000, false));

    component.startEdit(MIETE);
    component.editForm.setValue({ bezeichnung: 'Miete', betrag: 1250, intervall: 'monatlich' });
    component.saveEdit(MIETE.id);
    httpMock
      .expectOne('/api/fixed-costs/1')
      .flush('bad', { status: 400, statusText: 'Bad Request' });
    fixture.detectChanges();

    expect(component.editError()).toContain('vom Server abgelehnt');
    expect(component.editingId()).toBe(MIETE.id);
    httpMock.expectNone('/api/fixed-costs');
  });

  // --- AC3: Löschen nach Bestätigung ---

  it('löscht erst nach Bestätigung im Dialog', () => {
    flushInitialLoad(summaryOf([MIETE], 3000, false));

    clickButton(rowFor('Miete'), 'Löschen');
    fixture.detectChanges();

    expect(text()).toContain('Miete» wirklich löschen?');
    httpMock.expectNone('/api/fixed-costs/1');
  });

  it('sendet DELETE erst nach Bestätigung und lädt die Liste danach neu', () => {
    flushInitialLoad(summaryOf([MIETE], 3000, false));

    clickButton(rowFor('Miete'), 'Löschen');
    fixture.detectChanges();
    clickModalButton('Löschen');

    const deleteReq = httpMock.expectOne('/api/fixed-costs/1');
    expect(deleteReq.request.method).toBe('DELETE');
    deleteReq.flush(null, { status: 204, statusText: 'No Content' });

    const getReq = httpMock.expectOne('/api/fixed-costs');
    getReq.flush(summaryOf([], null, false));
    fixture.detectChanges();

    expect(component.pendingDelete()).toBeNull();
    expect(text()).toContain('Noch keine Fixkosten erfasst');
  });

  it('löst bei doppeltem Bestätigen nur ein einziges DELETE aus', () => {
    flushInitialLoad(summaryOf([MIETE], 3000, false));

    component.requestDelete(MIETE);
    component.confirmDelete();
    component.confirmDelete();

    const deleteReq = httpMock.expectOne('/api/fixed-costs/1');
    deleteReq.flush(null, { status: 204, statusText: 'No Content' });

    httpMock.expectOne('/api/fixed-costs').flush(summaryOf([], null, false));
    fixture.detectChanges();
  });

  it('sendet kein DELETE, wenn der Dialog abgebrochen wird', () => {
    flushInitialLoad(summaryOf([MIETE], 3000, false));

    component.requestDelete(MIETE);
    component.cancelDelete();
    fixture.detectChanges();

    httpMock.expectNone('/api/fixed-costs/1');
    expect(component.pendingDelete()).toBeNull();
    expect(text()).toContain('Miete');
  });

  it('meldet einen fehlgeschlagenen Löschversuch', () => {
    flushInitialLoad(summaryOf([MIETE], 3000, false));

    component.requestDelete(MIETE);
    component.confirmDelete();
    httpMock
      .expectOne('/api/fixed-costs/1')
      .flush('boom', { status: 500, statusText: 'Internal Server Error' });
    fixture.detectChanges();

    expect(component.deleteError()).toContain('Löschen fehlgeschlagen');
    httpMock.expectNone('/api/fixed-costs');
  });
});
