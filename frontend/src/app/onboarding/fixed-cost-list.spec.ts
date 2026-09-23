import { registerLocaleData } from '@angular/common';
import localeDeCh from '@angular/common/locales/de-CH';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { LOCALE_ID } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { Router, provideRouter } from '@angular/router';

import { App } from '../app';
import { routes } from '../app.routes';
import { AuthService } from '../auth/auth.service';
import { User } from '../auth/user.model';
import { Dashboard } from '../dashboard/dashboard';
import { IncomeCard } from '../income/income-card';
import { RecurringExpenseResponse } from '../recurring/recurring-expense.model';
import { FixedCostList } from './fixed-cost-list';
import { FixedCostDetail, FixedCostSummary } from './fixed-cost.model';

// Der CurrencyPipe nutzt den app-weiten LOCALE_ID (de-CH); die Locale-Daten müssen dafür
// registriert sein — im echten App-Bootstrap erledigt das app.config.ts.
registerLocaleData(localeDeCh);

/**
 * Eingeloggte Nutzerin mit bereits erfasstem Einkommen (FE-FC-09) — der Normalfall für die
 * meisten Tests dieser Datei, die mit Fixkosten/Abos arbeiten und sich nicht für die
 * Einkommens-Card interessieren. Der Konstruktor überspringt dank `monthlyIncome` den
 * Vorschlags-Call (`GET /api/budget/safe-to-spend`) — ohne Login läse `AuthService.currentUser()`
 * `null` und der Call bliebe unbeantwortet offen, was `httpMock.verify()` aufdeckte.
 */
const LARA: User = {
  id: 1,
  email: 'lara@example.ch',
  monthlyIncome: 3000,
  onboardingCompleted: true,
  firstName: null,
  lastName: null,
};

/** Loggt via `AuthService.login()` ein, damit `currentUser()` synchron befüllt ist. */
function loginAs(mock: HttpTestingController, user: User): void {
  TestBed.inject(AuthService).login(user.email, 'irrelevant').subscribe();
  mock.expectOne('/api/auth/login').flush(user);
}

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

/** Erkanntes Abo — zählt ins Total (FE-FC-07). */
const NETFLIX: RecurringExpenseResponse = {
  id: 1,
  payeeKey: 'NETFLIX',
  amount: 17.9,
  status: 'DETECTED',
  firstDetectedMonth: '2026-07',
  createdAt: '2026-09-08T10:15:00Z',
  isNew: false,
};

/** Per «Kein Abo» verneint — bleibt auf der Seite, zählt aber nicht ins Total. */
const COOP_DISMISSED: RecurringExpenseResponse = {
  id: 2,
  payeeKey: 'COOP PRONTO',
  amount: 24.5,
  status: 'DISMISSED',
  firstDetectedMonth: '2026-03',
  createdAt: '2026-09-01T10:15:00Z',
  isNew: false,
};

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

    httpMock = TestBed.inject(HttpTestingController);
    loginAs(httpMock, LARA);

    fixture = TestBed.createComponent(FixedCostList);
    component = fixture.componentInstance;
  });

  afterEach(() => httpMock.verify());

  function flushInitialLoad(
    summary: FixedCostSummary,
    recurringExpenses: RecurringExpenseResponse[] = [],
  ): void {
    fixture.detectChanges();
    httpMock.expectOne('/api/fixed-costs').flush(summary);
    flushRecurringExpenses(recurringExpenses);
    fixture.detectChanges();
  }

  /**
   * Der eingebettete Abschnitt «Erkannte Abos» (FE-FC-05) lädt beim Aufbau seine eigene Liste.
   * Meist leer beantwortet: was er damit macht, prüft `recurring-expense-list.spec.ts` — dieser
   * Test kümmert sich nur darum, dass der Request nicht offen bleibt (`httpMock.verify()`).
   * Mit Einträgen nur dort, wo das Total (FE-FC-07) sie braucht.
   */
  function flushRecurringExpenses(expenses: RecurringExpenseResponse[] = []): void {
    httpMock.expectOne('/api/recurring-expenses').flush(expenses);
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
    // 'jaehrlich' auf der Leitung, «jährlich» im Template (wie im Wizard).
    expect(text()).toContain('jährlich');
  });

  it('rendert die Tabelle innerhalb eines horizontal scrollbaren Containers', () => {
    flushInitialLoad(summaryOf([MIETE], 3000, false));

    const root = fixture.nativeElement as HTMLElement;
    const wrapper = root.querySelector('.table-scroll');
    expect(wrapper?.querySelector('table')).not.toBeNull();
    expect(getComputedStyle(wrapper as HTMLElement).overflowX).toBe('auto');
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
    flushRecurringExpenses();
    fixture.detectChanges();

    expect(component.errorMessage()).not.toBeNull();
    expect(text()).toContain('konnten nicht geladen werden');
  });

  // FE-FC-07 (#355): die Seite hiess «Ausgaben», die Tabelle bekommt eine Zwischenüberschrift,
  // und darüber steht das Total aus Fixkosten-Monatssumme und erkannten Abos. FE-FC-09 (#360)
  // benennt die Seite zu «Budget» um.
  describe('Seite «Budget» und Total (FE-FC-07, FE-FC-09)', () => {
    function heading(level: 1 | 2 | 3, name: string): HTMLElement | null {
      return (
        Array.from(
          (fixture.nativeElement as HTMLElement).querySelectorAll<HTMLElement>(`h${level}`),
        ).find((h) => h.textContent?.trim() === name) ?? null
      );
    }

    function totalCard(): HTMLElement | null {
      return (fixture.nativeElement as HTMLElement).querySelector('.monthly-total');
    }

    it('trägt den Titel «Budget» und die Zwischenüberschriften «Einkommen», «Ausgaben», «Erfasste Fixkosten» und «Erkannte Abos»', () => {
      flushInitialLoad(summaryOf([MIETE], 3000, false));

      expect(heading(1, 'Budget')).not.toBeNull();
      expect(heading(1, 'Ausgaben')).toBeNull();
      expect(heading(1, 'Fixkosten')).toBeNull();
      expect(heading(2, 'Einkommen')).not.toBeNull();
      // «Ausgaben» gruppiert die beiden Unterabschnitte als h2, die selbst eine Stufe tiefer
      // (h3) sitzen — analog «Einkommen», aber mit einer zusätzlichen Ebene darunter.
      expect(heading(2, 'Ausgaben')).not.toBeNull();
      expect(heading(3, 'Erfasste Fixkosten')).not.toBeNull();
      expect(heading(3, 'Erkannte Abos')).not.toBeNull();
      expect(heading(2, 'Erfasste Fixkosten')).toBeNull();
      expect(heading(2, 'Erkannte Abos')).toBeNull();
    });

    // FE-FC-09 (#360): «Einkommen» steht wie «Erfasste Fixkosten» als eigene Zwischenüberschrift
    // ausserhalb der Card, nicht als `app-card`-Titel — beide Abschnitte auf derselben Ebene.
    it('stellt die Zwischenüberschrift «Einkommen» ausserhalb der Card, nicht als Card-Titel', () => {
      flushInitialLoad(summaryOf([MIETE], 3000, false));

      const root = fixture.nativeElement as HTMLElement;
      expect(heading(2, 'Einkommen')?.closest('app-card')).toBeNull();
      const cardTitles = Array.from(root.querySelectorAll('app-card .card__title')).map((el) =>
        el.textContent?.trim(),
      );
      expect(cardTitles).not.toContain('Einkommen');
    });

    it('stellt «+ Neue Position» neben die Zwischenüberschrift, nicht neben den Titel', () => {
      flushInitialLoad(summaryOf([MIETE], 3000, false));

      const button = (fixture.nativeElement as HTMLElement).querySelector<HTMLAnchorElement>(
        '.section-header a[appButton]',
      );
      expect(button?.textContent?.trim()).toBe('+ Neue Position');
      expect(button?.getAttribute('href')).toBe('/onboarding');
      expect(button?.parentElement?.querySelector('h3')?.textContent?.trim()).toBe(
        'Erfasste Fixkosten',
      );
    });

    it('benennt den Tabellen-Scrollbereich über die Zwischenüberschrift', () => {
      flushInitialLoad(summaryOf([MIETE], 3000, false));

      const region = (fixture.nativeElement as HTMLElement).querySelector('.table-scroll');
      expect(region?.getAttribute('aria-labelledby')).toBe('fixed-costs-heading');
      expect(heading(3, 'Erfasste Fixkosten')?.id).toBe('fixed-costs-heading');
    });

    it('summiert Fixkosten-Monatssumme und erkannte Abos, ohne die verneinten', () => {
      // 1200 + 27.92 = 1227.92 Fixkosten, + 17.90 NETFLIX = 1245.82. COOP PRONTO (24.50) ist
      // verneint und zählt nicht — sonst stünde 1270.32.
      flushInitialLoad(summaryOf([MIETE, SERAFE], 3000, false), [NETFLIX, COOP_DISMISSED]);

      expect(component.monthlyTotal()).toEqual({
        fixedCosts: 1227.92,
        recurring: 17.9,
        total: 1245.82,
      });
      const card = totalCard();
      expect(card?.querySelector('.card__title')?.textContent?.trim()).toBe(
        'Monatliche fixe Ausgaben',
      );
      expect(card?.querySelector('.monthly-total__amount')?.textContent).toContain('1’245.82');
      expect(text()).toContain('CHF 1’227.92 Fixkosten + CHF 17.90 erkannte Abos');
    });

    it('addiert in Rappen — 27.92 + 17.90 ergibt 45.82, nicht 45.8199…', () => {
      flushInitialLoad(summaryOf([SERAFE], 3000, false), [NETFLIX]);

      expect(component.monthlyTotal()?.total).toBe(45.82);
    });

    it('zeigt das Total auch ohne erkannte Abos — dann ist es die Fixkosten-Summe', () => {
      flushInitialLoad(summaryOf([MIETE], 3000, false), []);

      expect(component.monthlyTotal()).toEqual({ fixedCosts: 1200, recurring: 0, total: 1200 });
      expect(totalCard()).not.toBeNull();
    });

    /** Der Satz, der an der Stelle der Card steht, wenn das Total fehlt. */
    function unavailableNote(): string | null {
      return (
        (fixture.nativeElement as HTMLElement)
          .querySelector('.monthly-total__unavailable')
          ?.textContent?.trim() ?? null
      );
    }

    it('blendet das Total aus, wenn die Abos nicht geladen werden konnten, und sagt warum', () => {
      fixture.detectChanges();
      httpMock.expectOne('/api/fixed-costs').flush(summaryOf([MIETE], 3000, false));
      httpMock
        .expectOne('/api/recurring-expenses')
        .flush('boom', { status: 500, statusText: 'Internal Server Error' });
      fixture.detectChanges();

      // Die Fixkosten-Tabelle steht, das Total nicht: ihm fehlte ein Summand. Die Begründung
      // steht an der Stelle der Card — die Meldung des Abo-Abschnitts selbst käme erst
      // unterhalb der Tabelle.
      expect(text()).toContain('Miete');
      expect(component.monthlyTotal()).toBeNull();
      expect(totalCard()).toBeNull();
      expect(unavailableNote()).toBe(
        'Total nicht verfügbar — die erkannten Abos konnten nicht geladen werden.',
      );
    });

    it('blendet das Total aus, wenn die Fixkosten nicht geladen werden konnten, und sagt warum', () => {
      fixture.detectChanges();
      httpMock
        .expectOne('/api/fixed-costs')
        .flush('boom', { status: 500, statusText: 'Internal Server Error' });
      flushRecurringExpenses([NETFLIX]);
      fixture.detectChanges();

      expect(text()).toContain('NETFLIX');
      expect(component.monthlyTotal()).toBeNull();
      expect(totalCard()).toBeNull();
      expect(unavailableNote()).toBe(
        'Total nicht verfügbar — die Fixkosten konnten nicht geladen werden.',
      );
    });

    it('nennt beide Ursachen, wenn beide Requests fehlschlagen', () => {
      fixture.detectChanges();
      httpMock
        .expectOne('/api/fixed-costs')
        .flush('boom', { status: 500, statusText: 'Internal Server Error' });
      httpMock
        .expectOne('/api/recurring-expenses')
        .flush('boom', { status: 500, statusText: 'Internal Server Error' });
      fixture.detectChanges();

      expect(unavailableNote()).toBe(
        'Total nicht verfügbar — Fixkosten und Abos konnten nicht geladen werden.',
      );
    });

    it('sagt nichts, solange die Abschnitte nur laden — der Hinweis blitzt nicht auf', () => {
      fixture.detectChanges();

      // Beide Requests stehen noch offen: kein Total, aber auch keine Begründung.
      expect(component.monthlyTotal()).toBeNull();
      expect(component.totalUnavailableReason()).toBeNull();
      expect(unavailableNote()).toBeNull();

      httpMock.expectOne('/api/fixed-costs').flush(summaryOf([MIETE], 3000, false));
      flushRecurringExpenses();
      fixture.detectChanges();

      expect(totalCard()).not.toBeNull();
      expect(unavailableNote()).toBeNull();
    });

    it('zieht ein per «Kein Abo» verneintes Abo sofort aus dem Total', () => {
      flushInitialLoad(summaryOf([MIETE], 3000, false), [NETFLIX]);
      expect(component.monthlyTotal()?.total).toBe(1217.9);

      clickButton(
        (fixture.nativeElement as HTMLElement).querySelector('app-recurring-expense-list')!,
        'Kein Abo',
      );
      httpMock
        .expectOne('/api/recurring-expenses/1/dismiss')
        .flush({ ...NETFLIX, status: 'DISMISSED' });
      fixture.detectChanges();

      // Kein Reload der Fixkosten nötig: der Service ersetzt den Eintrag im State, das Total
      // folgt als `computed`.
      expect(component.monthlyTotal()).toEqual({ fixedCosts: 1200, recurring: 0, total: 1200 });
    });
  });

  // FE-FC-09 (#360): die Card «Einkommen» — bis dahin FE-SET-03 in den Einstellungen — steht
  // jetzt ganz oben auf der Budget-Seite, als eigene Komponente (`IncomeCard`), weil derselbe
  // Abschnitt auch im Onboarding-Wizard eingebettet ist. Ihr eigenes Verhalten deckt
  // `income-card.spec.ts` ab; hier geht es nur um die Einbettung.
  describe('Abschnitt «Einkommen» (FE-FC-09)', () => {
    it('rendert die Einkommens-Card oberhalb des Totals', () => {
      flushInitialLoad(summaryOf([MIETE], 3000, false));

      const root = fixture.nativeElement as HTMLElement;
      const income = root.querySelector('app-income-card');
      expect(income).not.toBeNull();
      const total = root.querySelector('.monthly-total');
      expect(
        income!.compareDocumentPosition(total!) & Node.DOCUMENT_POSITION_FOLLOWING,
      ).toBeTruthy();
    });
  });

  // FE-FC-05: die Abo-Übersicht ist ein Abschnitt dieser Seite. Zwei getrennte Requests, zwei
  // getrennte Zustände — ein Fehler in der Fixkosten-Liste nimmt den Abo-Abschnitt nicht mit.
  describe('Abschnitt «Erkannte Abos» (FE-FC-05)', () => {
    function recurringSection(): HTMLElement | null {
      return (fixture.nativeElement as HTMLElement).querySelector('app-recurring-expense-list');
    }

    it('rendert den Abschnitt unterhalb der Fixkosten-Tabelle', () => {
      flushInitialLoad(summaryOf([MIETE], 3000, false));

      const section = recurringSection();
      expect(section).not.toBeNull();
      expect(section?.querySelector('h3')?.textContent?.trim()).toBe('Erkannte Abos');
      // Reihenfolge im DOM: erst die Fixkosten-Tabelle, dann der Abo-Abschnitt.
      const table = (fixture.nativeElement as HTMLElement).querySelector('table');
      expect(
        table!.compareDocumentPosition(section!) & Node.DOCUMENT_POSITION_FOLLOWING,
      ).toBeTruthy();
    });

    it('zeigt den Abschnitt auch, wenn die Fixkosten nicht geladen werden konnten', () => {
      fixture.detectChanges();
      httpMock
        .expectOne('/api/fixed-costs')
        .flush('boom', { status: 500, statusText: 'Internal Server Error' });
      httpMock.expectOne('/api/recurring-expenses').flush([
        {
          id: 1,
          payeeKey: 'NETFLIX',
          amount: 17.9,
          status: 'DETECTED',
          firstDetectedMonth: '2026-07',
          createdAt: '2026-09-08T10:15:00Z',
          isNew: false,
        },
      ]);
      fixture.detectChanges();

      expect(text()).toContain('konnten nicht geladen werden');
      expect(recurringSection()?.querySelector('.expense__payee')?.textContent).toContain(
        'NETFLIX',
      );
    });
  });

  // --- AC4: Warnung Fixkosten >= Einkommen ---

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

// FE-FC-09 (#360): dieselbe Vorschlags-Logik wie zuvor in settings.spec.ts, jetzt gegen die
// Budget-Seite — eigenes TestBed, weil der Konstruktor den Vorschlags-Call nur auslöst, solange
// kein Einkommen erfasst ist (siehe `LARA` oben).
// FE-FC-09 (#360): verschoben aus settings.spec.ts («Route /einstellungen», AC5) — die Karte, die
// das Einkommen ändert, liegt jetzt auf /budget statt /einstellungen.
describe('Route /budget (FE-FC-09)', () => {
  let httpMock: HttpTestingController;
  let router: Router;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter(routes)],
    });
    httpMock = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
  });

  afterEach(() => httpMock.verify());

  /** Beantwortet das `GET /api/users/me` des Guards. */
  async function answerProfile(user: User): Promise<void> {
    await new Promise((resolve) => setTimeout(resolve, 0));
    httpMock.expectOne('/api/users/me').flush(user);
  }

  /**
   * FE-STS-04: Das Dashboard lädt beim Aufbau daneben die Monatsliste — sie speist Dropdown und
   * Keine-Daten-Hinweis. Der laufende Monat trägt hier Daten, damit das Dashboard in seinem
   * Normalzustand steht und der Hinweis wegbleibt.
   */
  function flushDashboardMonths(): void {
    const now = new Date();
    const month = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
    httpMock.expectOne('/api/transactions/months').flush([month]);
  }

  it('zeigt auf dem Dashboard den neuen Safe-to-Spend-Betrag, nachdem das Einkommen auf der Budget-Seite gespeichert wurde — ohne Reload', async () => {
    const root = TestBed.createComponent(App);
    root.detectChanges();

    const firstNavigation = router.navigateByUrl('/budget');
    await answerProfile({
      id: 1,
      email: 'lara@example.ch',
      monthlyIncome: null,
      onboardingCompleted: true,
      firstName: null,
      lastName: null,
    });
    await firstNavigation;
    // Konstruktor der Einkommens-Card: kein Einkommen erfasst, also der Vorschlags-Call — dieser
    // Request gehört der Budget-Seite selbst, nicht dem Dashboard.
    httpMock.expectOne('/api/budget/safe-to-spend').flush({
      amount: null,
      weeksLeft: 3,
      negative: false,
      noIncome: true,
      incomeSuggestion: null,
      status: 'OPEN',
    });
    root.detectChanges();
    // ngOnInit lädt die Fixkosten, das eingebettete app-recurring-expense-list seine eigene
    // Liste. FE-NOTIF-01: Sobald isAuthenticated() kippt, rendert die Shell
    // app-notification-bell (Topbar + Sidebar) und beide Instanzen laden beim Erstellen —
    // gebündelt auf einen Request.
    httpMock
      .expectOne('/api/fixed-costs')
      .flush({ fixedCosts: [], summeMonatlich: 0, monthlyIncome: null, exceedsIncome: false });
    httpMock.expectOne('/api/recurring-expenses').flush([]);
    httpMock.expectOne('/api/notifications').flush([]);
    root.detectChanges();

    const incomeCard = root.debugElement.query(By.directive(IncomeCard))
      .componentInstance as IncomeCard;
    incomeCard.incomeForm.controls.betrag.setValue(3800);
    incomeCard.submitIncome();
    httpMock.expectOne('/api/users/me/income').flush({
      id: 1,
      email: 'lara@example.ch',
      monthlyIncome: 3800,
      onboardingCompleted: true,
      firstName: null,
      lastName: null,
    });
    root.detectChanges();

    // Reine SPA-Navigation — kein `location.reload()`, kein neuer `App`-Fixture: dieselbe
    // Instanz von AuthService/HttpClient wie beim Speichern eben.
    const secondNavigation = router.navigateByUrl('/dashboard');
    await secondNavigation;
    flushDashboardMonths();
    // Prädikat statt Zeichenkette: Das Dashboard hängt seit FE-STS-04 `?month=` an, und der
    // String-Matcher von expectOne vergleicht die URL samt Query-String.
    const req = httpMock.expectOne((r) => r.url === '/api/budget/safe-to-spend');
    req.flush({
      amount: 650,
      weeksLeft: 2,
      negative: false,
      noIncome: false,
      incomeSuggestion: null,
      status: 'OPEN',
    });
    // BE-PDF-10: Das Dashboard lädt daneben die Zahl der ungeprüften Buchungsrichtungen. Für
    // diesen Fall ohne Belang, aber `verify()` im afterEach stolperte sonst darüber.
    httpMock.expectOne((r) => r.url === '/api/transactions/uncertain').flush([]);
    // FE-NOTIF-01: Jede Navigation löst bei app-notification-bell einen Reload aus.
    httpMock.expectOne('/api/notifications').flush([]);
    // FE-STS-04: Dasselbe für die Drei-Monats-Übersicht (BE-STS-07).
    httpMock.expectOne((r) => r.url === '/api/transactions/monthly-totals').flush([]);
    // FE-REC-01: Und die Abo-Übersicht für die Teaser-Card.
    httpMock.expectOne('/api/recurring-expenses').flush([]);
    root.detectChanges();

    const dashboard = root.debugElement.query(By.directive(Dashboard))
      .componentInstance as Dashboard;
    expect(dashboard.data()?.amount).toBe(650);
    expect((root.nativeElement as HTMLElement).textContent).not.toContain('Kein Betrag verfügbar');
  });
});
