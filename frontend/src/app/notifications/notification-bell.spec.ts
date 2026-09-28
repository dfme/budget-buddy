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

/**
 * Abschluss eines Import-Jobs (BE-PDF-15) — die Glocke führt bei den drei Import-Typen nach
 * `/import?job=<referenceId>` (FE-NOTIF-05). `referenceId` ist die Job-ID.
 */
const IMPORT_UNREAD: NotificationResponse = {
  id: 4,
  type: 'IMPORT_COMPLETED',
  referenceId: 42,
  message: 'Import abgeschlossen: 5 Transaktionen importiert.',
  read: false,
  createdAt: '2026-09-21T09:00:00Z',
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
          { path: 'budget', component: RouteStub },
          { path: 'import', component: RouteStub },
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

  // FE-NOTIF-02 (#308): Das Icon war ein Emoji (🔔 mit Textselektor) und lief deshalb weder
  // in Ink-Farbe noch mit Hover und Dark-Theme mit. Die zweite Assertion ist nicht redundant:
  // ohne sie bliebe der Test auch dann grün, wenn jemand das Emoji neben das SVG stellt.
  it('rendert das Glocken-Icon als Inline-SVG in currentColor, nicht als Emoji', () => {
    create();
    flushInitialLoad([UNREAD]);

    const icon = query('.bell__icon')!;
    const strokes = Array.from(icon.querySelectorAll('svg path')).map((path) =>
      path.getAttribute('stroke'),
    );

    expect(strokes.length).toBeGreaterThan(0);
    expect(strokes.every((stroke) => stroke === 'currentColor')).toBe(true);
    expect(icon.textContent?.trim()).toBe('');
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

  // FE-NOTIF-04 (#336): eine Aktion für alle ungelesenen statt N Einzelklicks.
  it('zeigt «Alle als gelesen markieren» nur, solange etwas ungelesen ist', () => {
    create();
    flushInitialLoad([READ]);
    bellButton().click();
    fixture.detectChanges();

    expect(query('.bell-list__read-all')).toBeNull();
  });

  it('markiert per «Alle als gelesen markieren» alle als gelesen, Badge weg, Dropdown bleibt offen', () => {
    create();
    flushInitialLoad([UNREAD, RECURRING_UNREAD]);
    bellButton().click();
    fixture.detectChanges();
    expect(query('.bell__badge')?.textContent?.trim()).toBe('2');

    query<HTMLButtonElement>('.bell-list__read-all')!.click();

    const req = httpMock.expectOne('/api/notifications/read-all');
    expect(req.request.method).toBe('POST');
    req.flush([
      { ...UNREAD, read: true },
      { ...RECURRING_UNREAD, read: true },
    ]);
    fixture.detectChanges();

    expect(query('.bell__badge')).toBeNull();
    expect(query('.bell-list__item--unread')).toBeNull();
    // Gelesene bleiben stehen (Inbox), aber als erledigt markiert.
    expect(el().querySelectorAll('.bell-list__item--read').length).toBe(2);
    expect(query('.bell-list__read-all')).toBeNull();
    expect(query('.bell-list')).not.toBeNull();
    // Kein Ziel für alle zusammen — anders als beim Einzelklick auf eine Abo-Benachrichtigung.
    expect(router.url).not.toBe('/budget');
  });

  it('bleibt bei einem fehlschlagenden read-all-Call still, Badge unverändert', () => {
    create();
    flushInitialLoad([UNREAD]);
    bellButton().click();
    fixture.detectChanges();

    query<HTMLButtonElement>('.bell-list__read-all')!.click();

    httpMock
      .expectOne('/api/notifications/read-all')
      .flush(null, { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();

    expect(query('.bell__badge')?.textContent?.trim()).toBe('1');
    expect(query('.bell-list__read-all')).not.toBeNull();
  });

  // FE-REC-01: die Antwort auf «Netflix wurde als Abo erkannt» ist die Abo-Übersicht.
  it('führt bei einer Abo-Benachrichtigung sofort in die Abo-Übersicht und schliesst das Dropdown', async () => {
    create();
    flushInitialLoad([RECURRING_UNREAD]);
    bellButton().click();
    fixture.detectChanges();

    query<HTMLButtonElement>('.bell-list__item')!.click();
    await fixture.whenStable();
    fixture.detectChanges();

    // Sofort navigiert, nicht erst nach dem Gelesen-Call: Käme die Übersicht erst nach dessen
    // Abschluss an, stünde der Eintrag dort bereits als gelesen und das «Neu»-Label liefe leer
    // (US-08 AC2).
    expect(router.url).toBe('/budget');
    expect(query('.bell-list')).toBeNull();
    // Die Navigation löst den üblichen Reload aus.
    httpMock.expectOne('/api/notifications').flush([]);

    httpMock.expectOne('/api/notifications/3/read').flush({ ...RECURRING_UNREAD, read: true });
  });

  it('führt auch dann in die Abo-Übersicht, wenn das Gelesen-Markieren fehlschlägt', async () => {
    create();
    flushInitialLoad([RECURRING_UNREAD]);
    bellButton().click();
    fixture.detectChanges();

    query<HTMLButtonElement>('.bell-list__item')!.click();
    await fixture.whenStable();
    fixture.detectChanges();

    expect(router.url).toBe('/budget');
    httpMock.expectOne('/api/notifications').flush([]);

    httpMock
      .expectOne('/api/notifications/3/read')
      .flush(null, { status: 500, statusText: 'Server Error' });
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

  // FE-NOTIF-05 (#348): die Antwort auf «Import abgeschlossen» ist die Übersicht dieses Imports.
  // Die Import-Seite nimmt den Job über `?job=` auf; ob er dem Nutzer gehört, entscheidet das
  // Backend mit 404 — die Glocke reicht nur die Job-ID durch.
  it.each(['IMPORT_COMPLETED', 'IMPORT_DEGRADED', 'IMPORT_FAILED'])(
    'führt bei %s nach /import?job=<referenceId>, schliesst das Dropdown und markiert parallel als gelesen',
    async (type) => {
      create();
      flushInitialLoad([{ ...IMPORT_UNREAD, type }]);
      bellButton().click();
      fixture.detectChanges();

      query<HTMLButtonElement>('.bell-list__item')!.click();
      await fixture.whenStable();
      fixture.detectChanges();

      expect(router.url).toBe('/import?job=42');
      expect(query('.bell-list')).toBeNull();
      // Die Navigation löst den üblichen Reload aus.
      httpMock.expectOne('/api/notifications').flush([]);

      // Gelesen-Call parallel zur Navigation, nicht davor — wie beim Abo-Sprung (FE-REC-01).
      httpMock.expectOne('/api/notifications/4/read').flush({ ...IMPORT_UNREAD, type, read: true });
    },
  );

  it('führt bei einer bereits gelesenen Import-Benachrichtigung zur Import-Seite, ohne Read-Call', async () => {
    create();
    flushInitialLoad([{ ...IMPORT_UNREAD, read: true }]);
    bellButton().click();
    fixture.detectChanges();

    query<HTMLButtonElement>('.bell-list__item')!.click();
    await fixture.whenStable();

    expect(router.url).toBe('/import?job=42');
    httpMock.expectOne('/api/notifications').flush([]);
    httpMock.expectNone('/api/notifications/4/read');
  });

  // Das Backend setzt die Job-ID immer (ImportJobRunner); fehlt sie trotzdem, bleibt die
  // Import-Seite das sinnvolle Ziel — nur ohne Parameter, statt `?job=null` zu erzeugen.
  it('führt bei einer Import-Benachrichtigung ohne referenceId nach /import ohne Parameter', async () => {
    create();
    flushInitialLoad([{ ...IMPORT_UNREAD, referenceId: null }]);
    bellButton().click();
    fixture.detectChanges();

    query<HTMLButtonElement>('.bell-list__item')!.click();
    await fixture.whenStable();

    expect(router.url).toBe('/import');
    httpMock.expectOne('/api/notifications').flush([]);
    httpMock.expectOne('/api/notifications/4/read').flush({ ...IMPORT_UNREAD, read: true });
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
