import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { Router, provideRouter } from '@angular/router';

import { AuthService } from '../auth/auth.service';
import { User } from '../auth/user.model';
import { IncomeCard } from '../income/income-card';
import { FixedCostWizard } from './fixed-cost-wizard';
import { FixedCost, INTERVALL_OPTIONS } from './fixed-cost.model';

const MIETE: FixedCost = {
  id: 1,
  bezeichnung: 'Miete',
  betrag: 1200,
  intervall: 'monatlich',
};

/**
 * Eingeloggte Nutzerin mit bereits erfasstem Einkommen — der Normalfall für die Tests dieser
 * Datei, die sich nicht für die eingebettete Einkommens-Card interessieren. Der Konstruktor von
 * `IncomeCard` überspringt dank `monthlyIncome` den Vorschlags-Call (`GET
 * /api/budget/safe-to-spend`) — ohne Login läse `AuthService.currentUser()` `null` und der Call
 * bliebe unbeantwortet offen, was `httpMock.verify()` aufdeckte. Ihr eigenes Verhalten deckt
 * `income-card.spec.ts` ab.
 */
const LARA: User = {
  id: 1,
  email: 'lara@example.ch',
  monthlyIncome: 3000,
  onboardingCompleted: true,
  firstName: null,
  lastName: null,
};

/** Antwort von POST /api/users/me/onboarding-complete. */
const LARA_ONBOARDED: User = LARA;

/** Loggt via `AuthService.login()` ein, damit `currentUser()` synchron befüllt ist. */
function loginAs(mock: HttpTestingController, user: User): void {
  TestBed.inject(AuthService).login(user.email, 'irrelevant').subscribe();
  mock.expectOne('/api/auth/login').flush(user);
}

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

    httpMock = TestBed.inject(HttpTestingController);
    loginAs(httpMock, LARA);

    fixture = TestBed.createComponent(FixedCostWizard);
    component = fixture.componentInstance;
    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    fixture.detectChanges();
  });

  afterEach(() => httpMock.verify());

  // FE-FC-09 (#360): die Einkommens-Card ist dieselbe Komponente wie auf der Budget-Seite; ihr
  // eigenes Verhalten deckt `income-card.spec.ts` ab, hier geht es nur um die Einbettung.
  it('rendert die Einkommens-Card oberhalb des Fixkosten-Formulars', () => {
    const root = fixture.nativeElement as HTMLElement;
    const income = root.querySelector('app-income-card');
    expect(income).not.toBeNull();
    // Über `#bezeichnung` statt `querySelector('form')`: die eingebettete Einkommens-Card
    // bringt ihr eigenes `<form>` mit, das sonst zuerst träfe.
    const fixedCostForm = root.querySelector('#bezeichnung')?.closest('form');
    expect(fixedCostForm).not.toBeNull();
    expect(
      income!.compareDocumentPosition(fixedCostForm!) & Node.DOCUMENT_POSITION_FOLLOWING,
    ).toBeTruthy();
  });

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

  /**
   * Der Submit-Button des Fixkosten-Formulars, über `#bezeichnung` gesucht statt über
   * `button[type="submit"]`: die eingebettete Einkommens-Card bringt ihren eigenen
   * Submit-Button mit, der sonst zuerst träfe.
   */
  function fixedCostSubmitButton(): HTMLButtonElement {
    return (fixture.nativeElement as HTMLElement)
      .querySelector('#bezeichnung')!
      .closest('form')!
      .querySelector('button[type="submit"]') as HTMLButtonElement;
  }

  // FE-FC-09 (#360): vorher liess sich der Button immer klicken (Fehler zeigten sich erst nach
  // dem Klick), die eingebettete Einkommens-Card deaktiviert ihren Button dagegen bis zur
  // Gültigkeit — dieselbe Inkonsistenz nebeneinander auf einer Seite. Angeglichen: beide Buttons
  // verhalten sich jetzt gleich.
  it('sperrt den Submit-Button, solange das Formular ungültig ist', () => {
    expect(fixedCostSubmitButton().disabled).toBe(true);
  });

  it('gibt den Submit-Button frei, sobald alle Pflichtfelder gültig sind', () => {
    component.form.setValue({ bezeichnung: 'Miete', betrag: 1200, intervall: 'monatlich' });
    fixture.detectChanges();

    expect(fixedCostSubmitButton().disabled).toBe(false);
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
    httpMock
      .expectOne('/api/fixed-costs')
      .flush({ ...MIETE, id: 3, bezeichnung: 'Handy', betrag: 40 });
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

  it('beschriftet den Button mit «Später erfassen», solange nichts gespeichert wurde', () => {
    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(text).toContain('Später erfassen — weiter zum Dashboard');
    expect(component.hasSaved()).toBe(false);
    expect(component.hasEnteredData()).toBe(false);
  });

  it('beschriftet den Button nach der ersten gespeicherten Position um', () => {
    saveMiete();
    fixture.detectChanges();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(component.hasSaved()).toBe(true);
    expect(text).toContain('Fertig — weiter zum Dashboard');
    expect(text).not.toContain('Später erfassen');
  });

  // FE-FC-09 (#360): die Einkommens-Card zählt genauso wie eine Fixkosten-Position — wer nur
  // sein Einkommen erfasst, hat nicht «nichts» getan, der alte Text «Keine Fixkosten» wäre hier
  // irreführend gewesen.
  it('beschriftet den Button um, sobald nur das Einkommen gespeichert wurde (ohne Fixkosten)', () => {
    const income = fixture.debugElement.query(By.directive(IncomeCard))
      .componentInstance as IncomeCard;
    income.incomeForm.controls.betrag.setValue(3800);
    income.submitIncome();
    httpMock.expectOne('/api/users/me/income').flush({ ...LARA, monthlyIncome: 3800 });
    fixture.detectChanges();

    const text = (fixture.nativeElement as HTMLElement).textContent ?? '';
    expect(component.hasSaved()).toBe(false);
    expect(component.hasEnteredData()).toBe(true);
    expect(text).toContain('Fertig — weiter zum Dashboard');
    expect(text).not.toContain('Später erfassen');
  });

  it('schliesst auch nach gespeicherter Position ueber denselben Request ab', () => {
    // US-03 laesst beide Wege aus dem Wizard heraus: ohne Eingabe bestaetigen ODER mindestens
    // etwas gespeichert (Fixkosten oder Einkommen, FE-FC-09). Ohne diesen Pfad sperrte der
    // onboardingGuard genau die Nutzer ein, die ihre Fixkosten korrekt erfasst haben.
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
