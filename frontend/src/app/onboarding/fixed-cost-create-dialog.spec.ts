import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { Component, signal } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';

import { FixedCostCreateDialog } from './fixed-cost-create-dialog';

/** Rendert den Dialog per `@if` wie die Budget-Seite und zählt seine Outputs. */
@Component({
  imports: [FixedCostCreateDialog],
  template: `
    @if (open()) {
      <app-fixed-cost-create-dialog
        (saved)="saved.set(saved() + 1); open.set(false)"
        (cancelled)="cancelled.set(cancelled() + 1); open.set(false)"
      />
    }
  `,
})
class Host {
  readonly open = signal(true);
  readonly saved = signal(0);
  readonly cancelled = signal(0);
}

describe('FixedCostCreateDialog', () => {
  let fixture: ComponentFixture<Host>;
  let host: Host;
  let httpMock: HttpTestingController;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Host],
      providers: [provideHttpClient(), provideHttpClientTesting()],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    fixture = TestBed.createComponent(Host);
    host = fixture.componentInstance;
    // Der Fokus-Trap des Modals braucht die Komponente im echten Dokument.
    document.body.appendChild(fixture.nativeElement);
    fixture.detectChanges();
  });

  afterEach(() => {
    fixture.nativeElement.remove();
    httpMock.verify();
  });

  function root(): HTMLElement {
    return fixture.nativeElement as HTMLElement;
  }

  function field<T extends HTMLElement>(id: string): T {
    const element = root().querySelector<T>(`#${id}`);
    if (!element) {
      throw new Error(`Kein Feld #${id}`);
    }
    return element;
  }

  function fill(bezeichnung: string, betrag: string, intervall = 'monatlich'): void {
    const name = field<HTMLInputElement>('create-bezeichnung');
    name.value = bezeichnung;
    name.dispatchEvent(new Event('input'));
    const amount = field<HTMLInputElement>('create-betrag');
    amount.value = betrag;
    amount.dispatchEvent(new Event('input'));
    const interval = field<HTMLSelectElement>('create-intervall');
    interval.value = intervall;
    interval.dispatchEvent(new Event('change'));
    fixture.detectChanges();
  }

  function saveButton(): HTMLButtonElement {
    const buttons = root().querySelectorAll<HTMLButtonElement>('.modal__actions button');
    return buttons[buttons.length - 1];
  }

  function save(): void {
    saveButton().click();
    fixture.detectChanges();
  }

  function errors(): string[] {
    return Array.from(root().querySelectorAll('app-field .field__error'))
      .map((node) => node.textContent?.trim() ?? '')
      .filter(Boolean);
  }

  it('zeigt Bezeichnung, Betrag und Intervall — leer bzw. auf «monatlich»', () => {
    expect(root().querySelector('.modal__title')?.textContent).toContain('Neue Position');
    expect(field<HTMLInputElement>('create-bezeichnung').value).toBe('');
    expect(field<HTMLInputElement>('create-betrag').value).toBe('');
    expect(field<HTMLSelectElement>('create-intervall').value).toBe('monatlich');
  });

  it('verknüpft «Speichern» als Submit-Button mit dem Formular und startet nicht auf «Abbrechen»', () => {
    // Voraussetzung für Enter-Speichern und den Fokus im ersten Feld — dass der Browser beides
    // tatsächlich tut, kann jsdom nicht zeigen (siehe modal.spec.ts).
    const form = root().querySelector('form');
    expect(saveButton().type).toBe('submit');
    expect(saveButton().form).toBe(form);
    expect(root().querySelectorAll('[cdkFocusInitial]')).toHaveLength(0);
    expect(root().querySelector('.modal__panel input')?.id).toBe('create-bezeichnung');
  });

  it('legt die Position per POST an, mit getrimmter Bezeichnung, und meldet saved', () => {
    fill('  Krankenkasse  ', '1200', 'quartalsweise');
    save();

    const req = httpMock.expectOne('/api/fixed-costs');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      bezeichnung: 'Krankenkasse',
      betrag: 1200,
      intervall: 'quartalsweise',
    });
    req.flush({ id: 7, bezeichnung: 'Krankenkasse', betrag: 1200, intervall: 'quartalsweise' });
    fixture.detectChanges();

    expect(host.saved()).toBe(1);
    expect(host.open()).toBe(false);
  });

  it('speichert auch über ngSubmit am Formular — den Weg, den Enter im Browser nimmt', () => {
    fill('Miete', '1500');
    root().querySelector('form')!.dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    httpMock.expectOne('/api/fixed-costs').flush({});
    expect(host.saved()).toBe(1);
  });

  describe('ungültige Eingaben lösen keinen Request aus', () => {
    it('leeres Formular: beide Pflichtfelder melden sich', () => {
      save();

      httpMock.expectNone('/api/fixed-costs');
      expect(errors()).toEqual(['Bezeichnung ist erforderlich.', 'Betrag ist erforderlich.']);
      expect(host.open()).toBe(true);
    });

    it('Bezeichnung nur aus Leerraum', () => {
      fill('   ', '10');
      save();

      httpMock.expectNone('/api/fixed-costs');
      expect(errors()).toEqual(['Bezeichnung ist erforderlich.']);
    });

    it('Betrag 0', () => {
      fill('Miete', '0');
      save();

      httpMock.expectNone('/api/fixed-costs');
      expect(errors()).toEqual(['Betrag muss grösser als 0 sein.']);
    });

    it('Betrag mit drei Nachkommastellen', () => {
      fill('Miete', '10.999');
      save();

      httpMock.expectNone('/api/fixed-costs');
      expect(errors()).toEqual(['Betrag darf höchstens zwei Nachkommastellen haben.']);
    });

    // Bis FE-FC-13 im Wizard-Spec belegt, der ein eigenes Formular führte — seither ist dieser
    // Dialog der einzige Weg, eine Position anzulegen.
    it.each(['-5', '-0.01'])('negativer Betrag %s', (betrag) => {
      fill('Miete', betrag);
      save();

      httpMock.expectNone('/api/fixed-costs');
      expect(errors()).toEqual(['Betrag muss grösser als 0 sein.']);
    });
  });

  // Gegenstück zu `min` und `maxDecimals`: die Grenze liegt genau auf dem Rappen.
  it.each([
    ['0.01', 0.01],
    ['1200.55', 1200.55],
  ])('akzeptiert den rappengenauen Betrag %s', (eingabe, betrag) => {
    fill('Kleinkram', eingabe);
    save();

    const req = httpMock.expectOne('/api/fixed-costs');
    expect(req.request.body.betrag).toBe(betrag);
    req.flush({});
  });

  it('bietet die drei Intervalle des Backends an und zeigt «jährlich» mit Umlaut', () => {
    const options = Array.from(
      field<HTMLSelectElement>('create-intervall').querySelectorAll('option'),
    );
    expect(options.map((option) => option.value)).toEqual([
      'monatlich',
      'quartalsweise',
      'jaehrlich',
    ]);
    // Der Umlaut gehört ins Template, der ASCII-Wert auf die Leitung (Intervall.java).
    expect(options.map((option) => option.textContent?.trim())).toEqual([
      'monatlich',
      'quartalsweise',
      'jährlich',
    ]);
  });

  it('sperrt «Speichern», solange der Request läuft, und sendet nur einmal', () => {
    fill('Miete', '1500');
    save();

    expect(saveButton().disabled).toBe(true);
    expect(saveButton().textContent?.trim()).toBe('Wird gespeichert…');

    // Ein zweites Enter kommt als ngSubmit an, am gesperrten Button vorbei.
    root().querySelector('form')!.dispatchEvent(new Event('submit'));
    fixture.detectChanges();

    httpMock.expectOne('/api/fixed-costs').flush({});
  });

  it('bleibt bei 400 offen, behält die Eingaben und meldet die Ablehnung im Dialog', () => {
    fill('Miete', '1500', 'jaehrlich');
    save();
    httpMock.expectOne('/api/fixed-costs').flush({}, { status: 400, statusText: 'Bad Request' });
    fixture.detectChanges();

    expect(host.open()).toBe(true);
    expect(host.saved()).toBe(0);
    expect(root().querySelector('.modal__panel app-notice')?.textContent).toContain(
      'Die Eingaben wurden vom Server abgelehnt.',
    );
    expect(field<HTMLInputElement>('create-bezeichnung').value).toBe('Miete');
    expect(field<HTMLInputElement>('create-betrag').value).toBe('1500');
    expect(field<HTMLSelectElement>('create-intervall').value).toBe('jaehrlich');
    expect(saveButton().disabled).toBe(false);
  });

  it('meldet einen Serverfehler und lässt einen zweiten Versuch zu', () => {
    fill('Miete', '1500');
    save();
    httpMock.expectOne('/api/fixed-costs').flush({}, { status: 500, statusText: 'Server Error' });
    fixture.detectChanges();

    expect(root().querySelector('.modal__panel app-notice')?.textContent).toContain(
      'Speichern fehlgeschlagen.',
    );

    save();
    httpMock.expectOne('/api/fixed-costs').flush({});
    fixture.detectChanges();
    expect(host.saved()).toBe(1);
  });

  it('bricht über «Abbrechen» ab, ohne Request', () => {
    fill('Miete', '1500');
    root().querySelector<HTMLButtonElement>('.modal__actions button')!.click();
    fixture.detectChanges();

    httpMock.expectNone('/api/fixed-costs');
    expect(host.cancelled()).toBe(1);
    expect(host.open()).toBe(false);
  });

  it('bricht über Escape und den Hintergrund ab, ohne Request', () => {
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    fixture.detectChanges();
    expect(host.cancelled()).toBe(1);

    host.open.set(true);
    fixture.detectChanges();
    root().querySelector<HTMLElement>('.modal__backdrop')!.click();
    fixture.detectChanges();

    httpMock.expectNone('/api/fixed-costs');
    expect(host.cancelled()).toBe(2);
  });

  it('ignoriert das Abbrechen, solange der Request läuft', () => {
    // Sonst verschwände der Dialog, die Position entstünde trotzdem, und die Liste lüde nicht neu.
    fill('Miete', '1500');
    save();

    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    fixture.detectChanges();
    expect(host.open()).toBe(true);
    expect(host.cancelled()).toBe(0);

    httpMock.expectOne('/api/fixed-costs').flush({});
    fixture.detectChanges();
    expect(host.saved()).toBe(1);
  });

  it('startet nach dem Schliessen mit einem leeren Formular', () => {
    fill('Miete', '1500', 'jaehrlich');
    document.dispatchEvent(new KeyboardEvent('keydown', { key: 'Escape', bubbles: true }));
    fixture.detectChanges();

    host.open.set(true);
    fixture.detectChanges();

    expect(field<HTMLInputElement>('create-bezeichnung').value).toBe('');
    expect(field<HTMLInputElement>('create-betrag').value).toBe('');
    expect(field<HTMLSelectElement>('create-intervall').value).toBe('monatlich');
  });
});
