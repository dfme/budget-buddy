import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { provideRouter } from '@angular/router';

import { RecurringExpenseList } from './recurring-expense-list';
import { RecurringExpenseResponse } from './recurring-expense.model';

const NETFLIX: RecurringExpenseResponse = {
  id: 1,
  payeeKey: 'NETFLIX',
  amount: 17.9,
  status: 'DETECTED',
  firstDetectedMonth: '2026-07',
  createdAt: '2026-09-08T10:15:00Z',
  isNew: true,
};

const SPOTIFY: RecurringExpenseResponse = {
  id: 2,
  payeeKey: 'SPOTIFY',
  amount: 12.95,
  status: 'DETECTED',
  firstDetectedMonth: '2026-06',
  createdAt: '2026-09-01T08:00:00Z',
  isNew: false,
};

/** Ein per «Kein Abo» verneinter Eintrag, wie `GET` ihn seit FE-NOTIF-03 mitliefert. */
const SWISSCOM_DISMISSED: RecurringExpenseResponse = {
  id: 3,
  payeeKey: 'SWISSCOM',
  amount: 59.9,
  status: 'DISMISSED',
  firstDetectedMonth: '2026-05',
  createdAt: '2026-08-20T08:00:00Z',
  isNew: false,
};

/** Ein ausgelaufenes Abo, wie `GET` es seit BE-REC-04 mitliefert. */
const SALT_ENDED: RecurringExpenseResponse = {
  id: 4,
  payeeKey: 'SALT MOBILE',
  amount: 39.95,
  status: 'ENDED',
  firstDetectedMonth: '2026-03',
  createdAt: '2026-08-20T08:00:00Z',
  isNew: false,
};

describe('RecurringExpenseList', () => {
  let fixture: ComponentFixture<RecurringExpenseList>;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [RecurringExpenseList],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(RecurringExpenseList);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  function el(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  function flushList(expenses: RecurringExpenseResponse[]): void {
    httpMock.expectOne('/api/recurring-expenses').flush(expenses);
    fixture.detectChanges();
  }

  function rows(): HTMLElement[] {
    return Array.from(el().querySelectorAll<HTMLElement>('.expense'));
  }

  function dismissButtons(): HTMLButtonElement[] {
    return Array.from(el().querySelectorAll<HTMLButtonElement>('.expense__dismiss'));
  }

  function dismissedRows(): HTMLElement[] {
    return Array.from(el().querySelectorAll<HTMLElement>('.dismissed-expense'));
  }

  function dismissedSection(): HTMLElement | null {
    return el().querySelector<HTMLElement>('.dismissed');
  }

  function endedRows(): HTMLElement[] {
    return Array.from(el().querySelectorAll<HTMLElement>('.ended-expense'));
  }

  function endedSection(): HTMLElement | null {
    return el().querySelector<HTMLElement>('.ended');
  }

  it('zeigt einen Ladezustand, solange der Request läuft', () => {
    httpMock.expectOne('/api/recurring-expenses');
    fixture.detectChanges();

    expect(el().querySelector('.status')?.textContent).toContain('Lädt');
  });

  // FE-FC-05: ein Abschnitt der Budget-Seite, keine eigene Seite. Seit dem FE-FC-09-Nachtrag h3
  // statt h2, damit die Überschriften-Hierarchie unter der gruppierenden Zwischenüberschrift
  // «Ausgaben» stimmt; der Hinweis auf den Safe-to-Spend erklärt die neue Wirkung eines
  // erkannten Abos.
  it('ist ein Abschnitt «Erkannte Abos» mit h3 und Hinweis auf den Safe-to-Spend', () => {
    flushList([]);

    expect(el().querySelector('h1')).toBeNull();
    expect(el().querySelector('h2')).toBeNull();
    expect(el().querySelector('h3')?.textContent?.trim()).toBe('Erkannte Abos');
    expect(el().querySelector('section')?.getAttribute('aria-labelledby')).toBe(
      el().querySelector('h3')?.id,
    );
    expect(el().querySelector('.intro')?.textContent).toContain('Safe-to-Spend');
  });

  it('zeigt die erkannten Abos als Liste mit Empfänger, Betrag und erstem Monat', () => {
    flushList([NETFLIX, SPOTIFY]);

    const list = rows();
    expect(list).toHaveLength(2);
    expect(list[0].querySelector('.expense__payee')?.textContent).toContain('NETFLIX');
    expect(list[0].querySelector('.expense__since')?.textContent).toBe('seit Juli 2026');
    expect(list[1].querySelector('.expense__payee')?.textContent).toContain('SPOTIFY');

    const amounts = fixture.debugElement.queryAll(By.css('app-amount'));
    expect(amounts[0].componentInstance.value()).toBe(17.9);
    expect(amounts[0].componentInstance.hidePositiveSign()).toBe(true);
  });

  it('trägt das Neu-Label nur bei neu erkannten Einträgen', () => {
    flushList([NETFLIX, SPOTIFY]);

    const list = rows();
    expect(list[0].querySelector('.expense__new')?.textContent).toBe('Neu');
    expect(list[0].classList).toContain('expense--new');
    expect(list[1].querySelector('.expense__new')).toBeNull();
    expect(list[1].classList).not.toContain('expense--new');
  });

  it('ruft bei Kein Abo den dismiss-Endpoint auf und verschiebt den Eintrag nach «Kein Abo»', () => {
    flushList([NETFLIX, SPOTIFY]);
    expect(dismissedSection()).toBeNull();

    dismissButtons()[0].click();
    fixture.detectChanges();

    // Während des Requests sind alle Buttons gesperrt und der betroffene zeigt den Lauf an.
    expect(dismissButtons()[0].disabled).toBe(true);
    expect(dismissButtons()[0].textContent?.trim()).toBe('Wird entfernt …');
    expect(dismissButtons()[1].disabled).toBe(true);
    // FE-FC-08: unter 900px ist das Label unsichtbar — aria-busy trägt den Wartezustand, und nur
    // am laufenden Button, nicht an den bloss gesperrten.
    expect(dismissButtons()[0].getAttribute('aria-busy')).toBe('true');
    expect(dismissButtons()[1].hasAttribute('aria-busy')).toBe(false);
    // Der Tooltip zieht mit: ohne sichtbares Label sonst die einzige Textquelle für die Maus.
    expect(dismissButtons()[0].title).toBe('Wird entfernt …');
    expect(dismissButtons()[1].title).toBe('Kein Abo');

    const req = httpMock.expectOne('/api/recurring-expenses/1/dismiss');
    expect(req.request.method).toBe('POST');
    req.flush({ ...NETFLIX, status: 'DISMISSED', isNew: false });
    fixture.detectChanges();

    const list = rows();
    expect(list).toHaveLength(1);
    expect(list[0].querySelector('.expense__payee')?.textContent).toContain('SPOTIFY');
    expect(dismissButtons()[0].disabled).toBe(false);
    expect(dismissButtons()[0].hasAttribute('aria-busy')).toBe(false);
    // FE-NOTIF-03: die Zeile verlässt die Seite nicht, sie wechselt in den Abschnitt «Kein Abo».
    expect(dismissedRows()).toHaveLength(1);
    expect(dismissedRows()[0].querySelector('.expense__payee')?.textContent).toContain('NETFLIX');
  });

  // FE-NOTIF-03, #333 AC1: Der Klick auf die Benachrichtigung eines inzwischen verneinten
  // Eintrags führt nach `/budget` (bis FE-FC-05: `/abos`, bis FE-FC-07: `/fixkosten`, bis
  // FE-FC-09: `/ausgaben`) — und der Eintrag muss dort stehen.
  // Ohne diesen Abschnitt landete er auf einer Seite, auf der der Eintrag fehlt.
  // FE-FC-08: Icon in beiden Varianten, der zugängliche Name bleibt `Kein Abo: {payeeKey}` —
  // darauf zielt der E2E-Selektor in `recurring-expenses.spec.ts`.
  it('zeigt «Kein Abo» mit Icon und unverändertem zugänglichen Namen', () => {
    flushList([NETFLIX]);

    const button = dismissButtons()[0];
    expect(button.querySelector('svg')?.getAttribute('aria-hidden')).toBe('true');
    expect(button.classList).toContain('btn--icon-only-mobile');
    expect(button.getAttribute('aria-label')).toBe('Kein Abo: NETFLIX');
    expect(button.textContent?.trim()).toBe('Kein Abo');
    expect(button.title).toBe('Kein Abo');
  });

  it('zeigt verneinte Einträge in einem eigenen Abschnitt «Kein Abo», ohne Neu-Label und Button', () => {
    flushList([NETFLIX, SWISSCOM_DISMISSED]);

    // Die Abo-Liste bleibt genau die Abos (US-08 AC3: aus der Abo-Liste entfernt).
    expect(rows()).toHaveLength(1);
    expect(rows()[0].querySelector('.expense__payee')?.textContent).toContain('NETFLIX');

    const section = dismissedSection();
    expect(section?.querySelector('.card__title')?.textContent).toBe('Kein Abo');
    const dismissed = dismissedRows();
    expect(dismissed).toHaveLength(1);
    expect(dismissed[0].querySelector('.expense__payee')?.textContent).toContain('SWISSCOM');
    expect(dismissed[0].querySelector('.expense__since')?.textContent).toContain('seit Mai 2026');
    expect(dismissed[0].querySelector('.expense__amount')?.textContent).toContain('59.90');
    expect(dismissed[0].querySelector('.expense__new')).toBeNull();
    expect(dismissed[0].querySelector('.expense__dismiss')).toBeNull();
  });

  // BE-REC-04: Ein ausgelaufenes Abo verlässt die Abo-Liste, aber nicht die Seite — sonst
  // verschwände es wortlos, und der Klick auf sein Bündel landete ins Leere.
  it('zeigt ausgelaufene Abos in einem eigenen Abschnitt «Beendet», ohne Neu-Label und Button', () => {
    flushList([NETFLIX, SALT_ENDED]);

    expect(rows()).toHaveLength(1);
    expect(rows()[0].querySelector('.expense__payee')?.textContent).toContain('NETFLIX');

    const section = endedSection();
    expect(section?.querySelector('.card__title')?.textContent).toBe('Beendet');
    const ended = endedRows();
    expect(ended).toHaveLength(1);
    expect(ended[0].querySelector('.expense__payee')?.textContent).toContain('SALT MOBILE');
    expect(ended[0].querySelector('.expense__since')?.textContent).toContain('seit März 2026');
    expect(ended[0].querySelector('.expense__amount')?.textContent).toContain('39.95');
    expect(ended[0].querySelector('.expense__new')).toBeNull();
    expect(ended[0].querySelector('.expense__dismiss')).toBeNull();
  });

  it('zeigt den Abschnitt «Beendet» nicht, solange nichts ausgelaufen ist', () => {
    flushList([NETFLIX, SPOTIFY]);

    expect(endedSection()).toBeNull();
  });

  // Alle drei Abschnitte zugleich: die Zuordnung darf nicht daran hängen, dass jeweils nur einer
  // Einträge hat.
  it('hält laufende, beendete und verneinte Einträge auseinander', () => {
    flushList([NETFLIX, SALT_ENDED, SWISSCOM_DISMISSED]);

    expect(rows()).toHaveLength(1);
    expect(endedRows()).toHaveLength(1);
    expect(dismissedRows()).toHaveLength(1);
  });

  it('zeigt den Abschnitt «Kein Abo» nicht, solange nichts verneint ist', () => {
    flushList([NETFLIX, SPOTIFY]);

    expect(dismissedSection()).toBeNull();
  });

  // Nur Verneinte, kein einziges Abo: oben der Leerzustand, unten der Abschnitt — beides.
  it('zeigt Leerzustand und Abschnitt «Kein Abo» zugleich, wenn nur Verneinte da sind', () => {
    flushList([SWISSCOM_DISMISSED]);

    expect(el().querySelector('.status.empty')).not.toBeNull();
    expect(dismissedRows()).toHaveLength(1);
  });

  it('lässt den Eintrag bei einem fehlschlagenden Kein Abo stehen und zeigt eine Meldung', () => {
    flushList([NETFLIX]);

    dismissButtons()[0].click();
    httpMock
      .expectOne('/api/recurring-expenses/1/dismiss')
      .flush(null, { status: 404, statusText: 'Not Found' });
    fixture.detectChanges();

    expect(rows()).toHaveLength(1);
    expect(el().querySelector('.dismiss-error')?.textContent).toContain('NETFLIX');
    expect(dismissButtons()[0].disabled).toBe(false);
  });

  it('zeigt einen Leerzustand mit Upload-Link, wenn keine Abos erkannt sind', () => {
    flushList([]);

    const empty = el().querySelector('.status.empty');
    expect(empty?.textContent).toContain('Keine Abos erkannt');
    expect(empty?.querySelector('a')?.getAttribute('href')).toBe('/import');
    expect(rows()).toHaveLength(0);
  });

  it('zeigt eine Fehlermeldung, wenn das Laden fehlschlägt', () => {
    httpMock
      .expectOne('/api/recurring-expenses')
      .flush(null, { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();

    expect(el().querySelector('app-notice')?.textContent).toContain(
      'Die Abo-Übersicht konnte nicht geladen werden.',
    );
    expect(el().querySelector('app-card')).toBeNull();
  });
});
