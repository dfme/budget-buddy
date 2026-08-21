import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';

import { User } from '../auth/user.model';
import { FixedCostWizard } from './fixed-cost-wizard';
import { FixedCost, INTERVALL_OPTIONS } from './fixed-cost.model';

const MIETE: FixedCost = {
  id: 1,
  bezeichnung: 'Miete',
  betrag: 1200,
  intervall: 'monatlich',
};

/** Antwort von POST /api/users/me/onboarding-complete. */
const LARA_ONBOARDED: User = {
  id: 1,
  email: 'lara@example.ch',
  monthlyIncome: null,
  onboardingCompleted: true,
};

describe('FixedCostWizard', () => {
  let fixture: ComponentFixture<FixedCostWizard>;
  let component: FixedCostWizard;
  let httpMock: HttpTestingController;
  let navigate: ReturnType<typeof vi.spyOn>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [FixedCostWizard],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(FixedCostWizard);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    fixture.detectChanges();
  });

  afterEach(() => httpMock.verify());

  // --- AC1: Validierungsfehler inline ---

  it('sendet nichts und zeigt alle Feldfehler, wenn das Formular leer ist', () => {
    component.submit();
    fixture.detectChanges();

    httpMock.expectNone('/api/fixed-costs');
    expect(component.form.controls.bezeichnung.touched).toBe(true);
    expect(component.form.controls.betrag.touched).toBe(true);
    expect(component.bezeichnungError()).toBe('Bezeichnung ist erforderlich.');
    expect(component.betragError()).toBe('Betrag ist erforderlich.');
  });

  it('haelt Feldfehler zurueck, solange das Feld unberuehrt ist', () => {
    // Ohne diese Zusicherung wuerde das Formular den Nutzer beim ersten Rendern anschreien.
    expect(component.bezeichnungError()).toBeNull();
    expect(component.betragError()).toBeNull();
    expect(component.form.invalid).toBe(true);
  });

  it('lehnt eine Bezeichnung aus reinem Leerraum ab und sendet nicht', () => {
    // `Validators.required` prueft nur die Laenge — '   ' waere damit gueltig, und der Trim in
    // submit() schickte einen leeren String auf die Leitung: eine namenlose Position in einem
    // NOT-NULL-Feld, dazu keine Erfolgs-Notice, weil der leere String falsy ist.
    component.form.setValue({ bezeichnung: '   ', betrag: 100, intervall: 'monatlich' });

    component.submit();
    fixture.detectChanges();

    httpMock.expectNone('/api/fixed-costs');
    expect(component.form.valid).toBe(false);
    expect(component.bezeichnungError()).toBe('Bezeichnung ist erforderlich.');
  });

  it('rendert die Fehlermeldung sichtbar unter dem Feld', () => {
    component.submit();
    fixture.detectChanges();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Bezeichnung ist erforderlich.');
    expect(text).toContain('Betrag ist erforderlich.');
  });

  // --- AC2: Betrag nur positiv ---

  it.each([0, -5, -0.01])('lehnt den Betrag %s ab und sendet nicht', (betrag) => {
    component.form.setValue({ bezeichnung: 'Miete', betrag, intervall: 'monatlich' });

    component.submit();

    httpMock.expectNone('/api/fixed-costs');
    expect(component.form.controls.betrag.hasError('min')).toBe(true);
    expect(component.betragError()).toBe('Betrag muss grösser als 0 sein.');
  });

  it('akzeptiert den kleinsten rappengenauen Betrag', () => {
    component.form.setValue({ bezeichnung: 'Kleinkram', betrag: 0.01, intervall: 'monatlich' });

    expect(component.form.valid).toBe(true);
    expect(component.form.controls.betrag.hasError('min')).toBe(false);
  });

  // Alle drei liegen ueber `min`, damit die Meldung eindeutig aus `maxDecimals` stammt.
  it.each([10.999, 0.015, 1200.123])(
    'lehnt den Betrag %s mit mehr als zwei Nachkommastellen ab',
    (betrag) => {
      // Ohne diese Pruefung liefe der Wert bis in DECIMAL(10,2) und wuerde still gerundet.
      component.form.setValue({ bezeichnung: 'Miete', betrag, intervall: 'monatlich' });

      component.submit();

      httpMock.expectNone('/api/fixed-costs');
      expect(component.form.controls.betrag.hasError('maxDecimals')).toBe(true);
      expect(component.betragError()).toBe('Betrag darf höchstens zwei Nachkommastellen haben.');
    },
  );

  it.each([1200, 1200.5, 1200.55])('akzeptiert den rappengenauen Betrag %s', (betrag) => {
    component.form.setValue({ bezeichnung: 'Miete', betrag, intervall: 'monatlich' });

    expect(component.form.controls.betrag.hasError('maxDecimals')).toBe(false);
    expect(component.form.valid).toBe(true);
  });

  // --- AC3: Intervall-Dropdown ---

  it('bietet genau die drei Intervalle des Backends an', () => {
    expect(INTERVALL_OPTIONS.map((option) => option.value)).toEqual([
      'monatlich',
      'quartalsweise',
      'jaehrlich',
    ]);
  });

  it('rendert drei Optionen und zeigt «jährlich» mit Umlaut an', () => {
    const options = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('#intervall option'),
    );

    expect(options).toHaveLength(3);
    expect(options.map((option) => (option as HTMLOptionElement).value)).toEqual([
      'monatlich',
      'quartalsweise',
      'jaehrlich',
    ]);
    // Der Umlaut gehoert ins Template, der ASCII-Wert auf die Leitung (Intervall.java).
    expect(options.map((option) => option.textContent?.trim())).toEqual([
      'monatlich',
      'quartalsweise',
      'jährlich',
    ]);
  });

  it('steht per Default auf monatlich', () => {
    expect(component.form.controls.intervall.value).toBe('monatlich');
  });

  it('schreibt die Auswahl aus dem select ins FormControl', () => {
    // Die Bindung <select> <-> FormControl ist die Mechanik, die dieser PR neu einfuehrt: die
    // uebrigen Tests setzen den Wert ueber form.setValue() und wuerden einen Bruch hier nicht
    // bemerken.
    const select = (fixture.nativeElement as HTMLElement).querySelector(
      '#intervall',
    ) as HTMLSelectElement;
    expect(select.value).toBe('monatlich');

    select.value = 'jaehrlich';
    select.dispatchEvent(new Event('change'));

    expect(component.form.controls.intervall.value).toBe('jaehrlich');
  });

  // --- AC4: Submit + Erfolgs-Feedback ---

  it('sendet POST /api/fixed-costs und zeigt Erfolgs-Feedback', () => {
    component.form.setValue({ bezeichnung: 'Miete', betrag: 1200, intervall: 'monatlich' });

    component.submit();

    const req = httpMock.expectOne('/api/fixed-costs');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      bezeichnung: 'Miete',
      betrag: 1200,
      intervall: 'monatlich',
    });
    req.flush(MIETE, { status: 201, statusText: 'Created' });
    fixture.detectChanges();

    expect(component.savedBezeichnung()).toBe('Miete');
    expect(component.errorMessage()).toBeNull();
    expect(component.submitting()).toBe(false);
    expect((fixture.nativeElement as HTMLElement).textContent).toContain(
      '«Miete» wurde gespeichert.',
    );
  });

  it('leert das Formular nach dem Speichern und setzt das Intervall zurueck', () => {
    component.form.setValue({ bezeichnung: 'Serafe', betrag: 335, intervall: 'jaehrlich' });

    component.submit();
    httpMock
      .expectOne('/api/fixed-costs')
      .flush({ id: 2, bezeichnung: 'Serafe', betrag: 335, intervall: 'jaehrlich' });

    // Mehrere Positionen am Stueck erfassbar: das Formular bleibt stehen, aber leer.
    expect(component.form.controls.bezeichnung.value).toBe('');
    expect(component.form.controls.betrag.value).toBeNull();
    expect(component.form.controls.intervall.value).toBe('monatlich');
  });

  it('schneidet Leerraum aus der Bezeichnung', () => {
    component.form.setValue({ bezeichnung: '  Miete  ', betrag: 1200, intervall: 'monatlich' });

    component.submit();

    const req = httpMock.expectOne('/api/fixed-costs');
    expect(req.request.body.bezeichnung).toBe('Miete');
    req.flush(MIETE);
  });

  it('meldet einen Serverfehler und zeigt kein Erfolgs-Feedback', () => {
    component.form.setValue({ bezeichnung: 'Miete', betrag: 1200, intervall: 'monatlich' });

    component.submit();
    httpMock
      .expectOne('/api/fixed-costs')
      .flush('boom', { status: 500, statusText: 'Internal Server Error' });
    fixture.detectChanges();

    expect(component.savedBezeichnung()).toBeNull();
    expect(component.errorMessage()).toBe(
      'Speichern fehlgeschlagen. Bitte versuche es später erneut.',
    );
    expect(component.submitting()).toBe(false);
    expect((fixture.nativeElement as HTMLElement).textContent).not.toContain('wurde gespeichert');
  });

  it('unterscheidet die Ablehnung durch den Server (400) vom generischen Fehler', () => {
    component.form.setValue({ bezeichnung: 'Miete', betrag: 1200, intervall: 'monatlich' });

    component.submit();
    httpMock.expectOne('/api/fixed-costs').flush('bad', { status: 400, statusText: 'Bad Request' });

    expect(component.errorMessage()).toContain('vom Server abgelehnt');
  });

  it('raeumt die alte Erfolgsmeldung weg, bevor der naechste Versuch laeuft', () => {
    component.form.setValue({ bezeichnung: 'Miete', betrag: 1200, intervall: 'monatlich' });
    component.submit();
    httpMock.expectOne('/api/fixed-costs').flush(MIETE);
    expect(component.savedBezeichnung()).toBe('Miete');

    component.form.setValue({ bezeichnung: 'Handy', betrag: 40, intervall: 'monatlich' });
    component.submit();

    // Waehrend der zweite Request laeuft, darf die Meldung des ersten nicht stehen bleiben.
    expect(component.savedBezeichnung()).toBeNull();
    expect(component.submitting()).toBe(true);
    httpMock.expectOne('/api/fixed-costs').flush({ ...MIETE, id: 3, bezeichnung: 'Handy', betrag: 40 });
  });

  // --- FE-FC-02: Onboarding abschliessen ---

  /** Speichert eine Position, damit `hasSaved()` steht. */
  function saveMiete(): void {
    component.form.setValue({ bezeichnung: 'Miete', betrag: 1200, intervall: 'monatlich' });
    component.submit();
    httpMock.expectOne('/api/fixed-costs').flush(MIETE, { status: 201, statusText: 'Created' });
  }

  it('schliesst das Onboarding ab und navigiert aufs Dashboard', () => {
    component.finishOnboarding();

    const req = httpMock.expectOne('/api/users/me/onboarding-complete');
    expect(req.request.method).toBe('POST');
    req.flush(LARA_ONBOARDED);
    fixture.detectChanges();

    expect(navigate).toHaveBeenCalledWith(['/dashboard']);
    expect(component.completeError()).toBeNull();
    expect(component.completing()).toBe(false);
  });

  it('loest den Abschluss ueber den Button aus', () => {
    // Die Bindung Button -> Methode ist die Mechanik, die dieser PR neu einfuehrt; die
    // uebrigen Tests rufen finishOnboarding() direkt und wuerden einen Bruch nicht bemerken.
    const button = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('button'),
    ).find((candidate) => candidate.textContent?.includes('weiter zum Dashboard'));
    expect(button).toBeDefined();

    button!.click();

    httpMock.expectOne('/api/users/me/onboarding-complete').flush(LARA_ONBOARDED);
    expect(navigate).toHaveBeenCalledWith(['/dashboard']);
  });

  it('beschriftet den Button mit «Keine Fixkosten», solange nichts gespeichert wurde', () => {
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Keine Fixkosten — weiter zum Dashboard');
    expect(component.hasSaved()).toBe(false);
  });

  it('beschriftet den Button nach der ersten gespeicherten Position um', () => {
    saveMiete();
    fixture.detectChanges();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(component.hasSaved()).toBe(true);
    expect(text).toContain('Fertig — weiter zum Dashboard');
    expect(text).not.toContain('Keine Fixkosten');
  });

  it('schliesst auch nach gespeicherter Position ueber denselben Request ab', () => {
    // US-03 laesst beide Wege aus dem Wizard heraus: «Keine Fixkosten» bestaetigen ODER
    // mindestens eine Position gespeichert. Ohne diesen Pfad sperrte der onboardingGuard
    // genau die Nutzer ein, die ihre Fixkosten korrekt erfasst haben.
    saveMiete();

    component.finishOnboarding();
    httpMock.expectOne('/api/users/me/onboarding-complete').flush(LARA_ONBOARDED);

    expect(navigate).toHaveBeenCalledWith(['/dashboard']);
  });

  it('bleibt im Wizard und meldet den Fehler, wenn der Abschluss scheitert', () => {
    component.finishOnboarding();
    httpMock
      .expectOne('/api/users/me/onboarding-complete')
      .flush('boom', { status: 500, statusText: 'Internal Server Error' });
    fixture.detectChanges();

    // Navigieren trotz gescheitertem Abschluss wuerde der Guard sofort zurueckdrehen.
    expect(navigate).not.toHaveBeenCalled();
    expect(component.completing()).toBe(false);
    expect(component.completeError()).toBe(
      'Onboarding konnte nicht abgeschlossen werden. Bitte versuche es später erneut.',
    );
    expect((fixture.nativeElement as HTMLElement).textContent).toContain(
      'Onboarding konnte nicht abgeschlossen werden.',
    );
  });

  it('raeumt die alte Fehlermeldung weg, bevor der naechste Abschlussversuch laeuft', () => {
    component.finishOnboarding();
    httpMock
      .expectOne('/api/users/me/onboarding-complete')
      .flush('boom', { status: 500, statusText: 'Internal Server Error' });
    expect(component.completeError()).not.toBeNull();

    component.finishOnboarding();

    expect(component.completeError()).toBeNull();
    expect(component.completing()).toBe(true);
    httpMock.expectOne('/api/users/me/onboarding-complete').flush(LARA_ONBOARDED);
  });
});
