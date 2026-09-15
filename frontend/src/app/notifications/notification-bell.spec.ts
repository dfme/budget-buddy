import { registerLocaleData } from '@angular/common';
import localeDeCh from '@angular/common/locales/de-CH';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component, LOCALE_ID } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';

import { NotificationResponse } from './notification.model';
import { NotificationBell } from './notification-bell';

registerLocaleData(localeDeCh);

/**
 * Ungelesen, mit einem Typ, den das Frontend nicht interpretiert — die Klick-Tests unten prüfen
 * damit allein das Gelesen-Markieren. Die Abo-Benachrichtigung hat ihren eigenen Fall.
 */
const UNREAD: NotificationResponse = {
  id: 1,
  type: 'MONTHLY_REPORT_READY',
  referenceId: 42,
  message: 'Dein Monatsbericht ist bereit',
  read: false,
  createdAt: '2026-09-08T10:15:00Z',
};

const READ: NotificationResponse = {
  id: 2,
  type: 'MONTHLY_REPORT_READY',
  referenceId: null,
  message: 'Dein Monatsbericht ist bereit',
  read: true,
  createdAt: '2026-09-01T08:00:00Z',
};

/** Neu erkanntes Abo (BE-REC-01) — die Glocke führt bei diesem Typ in die Abo-Übersicht. */
const RECURRING_UNREAD: NotificationResponse = {
  id: 3,
  type: 'RECURRING_EXPENSE_DETECTED',
  referenceId: 42,
  message: 'Netflix wurde als Abo erkannt',
  read: false,
  createdAt: '2026-09-08T10:15:00Z',
};

/** Navigationsziel für den NavigationEnd-Test. */
@Component({ template: 'stub' })
class RouteStub {}

describe('NotificationBell', () => {
  let fixture: ComponentFixture<NotificationBell>;
  let httpMock: HttpTestingController;
  let router: Router;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [NotificationBell],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([
          { path: 'dashboard', component: RouteStub },
          { path: 'abos', component: RouteStub },
        ]),
        { provide: LOCALE_ID, useValue: 'de-CH' },
      ],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
  });

  afterEach(() => httpMock.verify());

  function create(): void {
    fixture = TestBed.createComponent(NotificationBell);
    fixture.detectChanges();
  }

  function flushInitialLoad(notifications: NotificationResponse[] = []): void {
    httpMock.expectOne('/api/notifications').flush(notifications);
    fixture.detectChanges();
  }

  function el(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  function query<T extends HTMLElement>(selector: string): T | null {
    return el().querySelector<T>(selector);
  }

  function bellButton(): HTMLButtonElement {
    return query<HTMLButtonElement>('.bell')!;
  }

  it('lädt die Benachrichtigungen beim Erstellen (Login/Mount)', () => {
    create();

    httpMock.expectOne('/api/notifications').flush([UNREAD]);
  });

  it('zeigt keinen Badge, solange keine Benachrichtigung ungelesen ist', () => {
    create();
    flushInitialLoad([READ]);

    expect(query('.bell__badge')).toBeNull();
  });

  it('zeigt die Anzahl ungelesener Benachrichtigungen im Badge', () => {
    create();
    flushInitialLoad([UNREAD, READ]);

    expect(query('.bell__badge')?.textContent?.trim()).toBe('1');
  });

  it('ist das Dropdown initial geschlossen', () => {
    create();
    flushInitialLoad([UNREAD]);

    expect(bellButton().getAttribute('aria-expanded')).toBe('false');
    expect(query('.bell-list')).toBeNull();
  });

  it('öffnet das Dropdown auf Klick und zeigt die Benachrichtigungen in Backend-Reihenfolge', () => {
    create();
    flushInitialLoad([UNREAD, READ]);

    bellButton().click();
    fixture.detectChanges();

    expect(bellButton().getAttribute('aria-expanded')).toBe('true');
    const items = Array.from(el().querySelectorAll<HTMLElement>('.bell-list__item'));
    expect(
      items.map((item) => item.querySelector('.bell-list__message')?.textContent?.trim()),
    ).toEqual([UNREAD.message, READ.message]);
    expect(items[0].classList.contains('bell-list__item--unread')).toBe(true);
    expect(items[1].classList.contains('bell-list__item--unread')).toBe(false);
  });

  it('zeigt einen Hinweis, wenn keine Benachrichtigungen vorliegen', () => {
    create();
    flushInitialLoad([]);

    bellButton().click();
    fixture.detectChanges();

    expect(query('.bell-list__empty')).not.toBeNull();
    expect(query('.bell-list__item')).toBeNull();
  });

  it('schliesst auf Escape', () => {
    create();
    flushInitialLoad([UNREAD]);
    bellButton().click();
    fixture.detectChanges();

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape' }));
    fixture.detectChanges();

    expect(query('.bell-list')).toBeNull();
  });

  it('schliesst bei einem Klick ausserhalb der Komponente', () => {
    create();
    flushInitialLoad([UNREAD]);
    bellButton().click();
    fixture.detectChanges();

    document.body.click();
    fixture.detectChanges();

    expect(query('.bell-list')).toBeNull();
  });

  it('markiert eine ungelesene Benachrichtigung per Klick als gelesen', () => {
    create();
    flushInitialLoad([UNREAD]);
    bellButton().click();
    fixture.detectChanges();

    query<HTMLButtonElement>('.bell-list__item')!.click();

    const req = httpMock.expectOne('/api/notifications/1/read');
    expect(req.request.method).toBe('POST');
    req.flush({ ...UNREAD, read: true });
    fixture.detectChanges();

    expect(query('.bell__badge')).toBeNull();
  });

  it('löst bei einer bereits gelesenen Benachrichtigung keinen weiteren Call aus', () => {
    create();
    flushInitialLoad([READ]);
    bellButton().click();
    fixture.detectChanges();

    query<HTMLButtonElement>('.bell-list__item')!.click();

    httpMock.expectNone('/api/notifications/2/read');
  });

  // FE-REC-01: die Antwort auf «Netflix wurde als Abo erkannt» ist die Abo-Übersicht.
  it('führt bei einer Abo-Benachrichtigung in die Abo-Übersicht und schliesst das Dropdown', async () => {
    create();
    flushInitialLoad([RECURRING_UNREAD]);
    bellButton().click();
    fixture.detectChanges();

    query<HTMLButtonElement>('.bell-list__item')!.click();
    await fixture.whenStable();

    // Noch nicht navigiert: erst muss der Gelesen-Call durch sein, sonst hinge das «Neu»-Label
    // in der Übersicht vom Race zwischen beiden Requests ab.
    expect(router.url).toBe('/');
    httpMock.expectOne('/api/notifications/3/read').flush({ ...RECURRING_UNREAD, read: true });
    await fixture.whenStable();
    fixture.detectChanges();

    expect(router.url).toBe('/abos');
    expect(query('.bell-list')).toBeNull();
    // Die Navigation löst den üblichen Reload aus.
    httpMock.expectOne('/api/notifications').flush([]);
  });

  it('führt auch dann in die Abo-Übersicht, wenn das Gelesen-Markieren fehlschlägt', async () => {
    create();
    flushInitialLoad([RECURRING_UNREAD]);
    bellButton().click();
    fixture.detectChanges();

    query<HTMLButtonElement>('.bell-list__item')!.click();
    httpMock
      .expectOne('/api/notifications/3/read')
      .flush(null, { status: 500, statusText: 'Server Error' });
    await fixture.whenStable();

    expect(router.url).toBe('/abos');
    httpMock.expectOne('/api/notifications').flush([]);
  });

  it('navigiert bei einer Benachrichtigung anderen Typs nicht', async () => {
    create();
    flushInitialLoad([READ]);
    bellButton().click();
    fixture.detectChanges();

    query<HTMLButtonElement>('.bell-list__item')!.click();
    await fixture.whenStable();

    expect(router.url).toBe('/');
    expect(query('.bell-list')).not.toBeNull();
  });

  it('lädt bei einer Navigation erneut (kein Polling, aber Reload bei Navigation)', async () => {
    create();
    flushInitialLoad([READ]);

    await router.navigate(['/dashboard']);

    httpMock.expectOne('/api/notifications').flush([UNREAD, READ]);
    fixture.detectChanges();

    expect(query('.bell__badge')?.textContent?.trim()).toBe('1');
  });

  // Regressionsschutz für die error-Handler aus 506b096 (Review-Befund @dfme, PR #285). Per
  // Mutationsprobe verifiziert: ohne den Handler in `reload()`/`select()` wirft der fehlschlagende
  // Call einen unbehandelten `HttpErrorResponse` — RxJS meldet ihn asynchronisch
  // (`reportUnhandledError`), also nicht innerhalb dieses synchronen `it()`-Blocks selbst, sondern
  // als "Unhandled Errors" des gesamten Testlaufs, der dadurch mit Exit-Code ≠ 0 fehlschlägt (und
  // damit `npm test`/CI). Die Assertions unten prüfen zusätzlich den unveränderten Zustand.
  it('bleibt bei einem fehlschlagenden initialen Laden still, Badge unverändert', () => {
    create();

    httpMock
      .expectOne('/api/notifications')
      .flush(null, { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();

    expect(query('.bell__badge')).toBeNull();
  });

  it('bleibt bei einem fehlschlagenden markAsRead-Call still, die Benachrichtigung bleibt ungelesen', () => {
    create();
    flushInitialLoad([UNREAD]);
    bellButton().click();
    fixture.detectChanges();

    query<HTMLButtonElement>('.bell-list__item')!.click();

    httpMock
      .expectOne('/api/notifications/1/read')
      .flush(null, { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();

    expect(query('.bell__badge')?.textContent?.trim()).toBe('1');
    expect(query('.bell-list__item--unread')).not.toBeNull();
  });
});
