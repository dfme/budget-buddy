import { registerLocaleData } from '@angular/common';
import localeDeCh from '@angular/common/locales/de-CH';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { LOCALE_ID } from '@angular/core';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { Router, provideRouter } from '@angular/router';

import { AuthService } from '../auth/auth.service';
import { User } from '../auth/user.model';
import { IncomeCard } from '../income/income-card';
import { FixedCostWizard } from './fixed-cost-wizard';
import { FixedCostDetail, FixedCostSummary } from './fixed-cost.model';

// Der CurrencyPipe der Fixkosten-Tabelle nutzt den app-weiten LOCALE_ID (de-CH); die Locale-Daten
// müssen dafür registriert sein — im echten App-Bootstrap erledigt das app.config.ts.
registerLocaleData(localeDeCh);

const MIETE: FixedCostDetail = {
  id: 1,
  bezeichnung: 'Miete',
  betrag: 1200,
  intervall: 'monatlich',
  monatsbetrag: 1200,
};

const KRANKENKASSE: FixedCostDetail = {
  id: 2,
  bezeichnung: 'Krankenkasse',
  betrag: 1200,
  intervall: 'quartalsweise',
  monatsbetrag: 400,
};

function summaryOf(fixedCosts: FixedCostDetail[]): FixedCostSummary {
  const summeMonatlich = fixedCosts.reduce((sum, item) => sum + item.monatsbetrag, 0);
  return { fixedCosts, summeMonatlich, monthlyIncome: 3000, exceedsIncome: false };
}

/**
 * Eingeloggte Nutzerin mit bereits erfasstem Einkommen — der Normalfall für die Tests dieser
 * Datei, die sich nicht für die eingebettete Einkommens-Card interessieren. Der Konstruktor von
 * `IncomeCard` überspringt dank `monthlyIncome` den Vorschlags-Call (`GET
 * /api/budget/safe-to-spend`) — ohne Login läse `AuthService.currentUser()` `null` und der Call
 * bliebe unbeantwortet offen, was `httpMock.verify()` aufdeckte. Ihr eigenes Verhalten deckt
 * `income-card.spec.ts` ab.
 *
 * <p>Onboarding noch offen: seit FE-FC-12 (#375) ist der Wizard nur in diesem Zustand erreichbar
 * (`onboardingPendingGuard`). Die Komponente liest das Flag nicht, der Test bildet damit aber den
 * realen Zustand ab.
 */
const LARA: User = {
  id: 1,
  email: 'lara@example.ch',
  monthlyIncome: 3000,
  onboardingCompleted: false,
  firstName: null,
  lastName: null,
};

/** Antwort von POST /api/users/me/onboarding-complete — das Flag steht danach auf `true`. */
const LARA_ONBOARDED: User = { ...LARA, onboardingCompleted: true };

/** Loggt via `AuthService.login()` ein, damit `currentUser()` synchron befüllt ist. */
function loginAs(mock: HttpTestingController, user: User): void {
  TestBed.inject(AuthService).login(user.email, 'irrelevant').subscribe();
  mock.expectOne('/api/auth/login').flush(user);
}

/**
 * Der Wizard als Seite: Einbettung von Einkommens-Card und Fixkosten-Abschnitt, die Beschriftung
 * und Wirkung des Abschluss-Buttons. Tabelle, Bearbeiten, Löschen und der Dialog selbst sind in
 * `fixed-cost-section.spec.ts` bzw. `fixed-cost-create-dialog.spec.ts` belegt — hier nur, dass der
 * Wizard sie tatsächlich bekommt (FE-FC-13).
 */
describe('FixedCostWizard', () => {
  let fixture: ComponentFixture<FixedCostWizard>;
  let component: FixedCostWizard;
  let httpMock: HttpTestingController;
  let navigate: ReturnType<typeof vi.spyOn>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [FixedCostWizard],
      providers: [
        provideHttpClient(),
        provideHttpClientTesting(),
        provideRouter([]),
        { provide: LOCALE_ID, useValue: 'de-CH' },
      ],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    loginAs(httpMock, LARA);

    fixture = TestBed.createComponent(FixedCostWizard);
    component = fixture.componentInstance;
    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
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

  /** Beantwortet das `GET /api/fixed-costs`, mit dem der Fixkosten-Abschnitt startet. */
  function flushFixedCosts(fixedCosts: FixedCostDetail[]): void {
    httpMock.expectOne({ method: 'GET', url: '/api/fixed-costs' }).flush(summaryOf(fixedCosts));
    fixture.detectChanges();
  }

  /** Bezeichnungen in der Fixkosten-Tabelle, in DOM-Reihenfolge. */
  function tableRows(): string[] {
    return Array.from(root().querySelectorAll('app-fixed-cost-section tbody > tr .name')).map(
      (cell) => cell.firstChild?.textContent?.trim() ?? '',
    );
  }

  function newPositionButton(): HTMLButtonElement {
    return root().querySelector<HTMLButtonElement>(
      'app-fixed-cost-section .section-header button',
    )!;
  }

  /** Füllt den offenen Dialog «Neue Position» aus und klickt «Speichern». */
  function fillDialogAndSave(bezeichnung: string, betrag: string, intervall: string): void {
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
    const buttons = root().querySelectorAll<HTMLButtonElement>(
      'app-fixed-cost-create-dialog .modal__actions button',
    );
    buttons[buttons.length - 1].click();
    fixture.detectChanges();
  }

  function finishButtonText(): string {
    return root().querySelector('.wizard__finish button')?.textContent?.trim() ?? '';
  }

  // --- FE-FC-13: Tabelle + Dialog statt Inline-Formular ---

  // FE-FC-09 (#360): die Einkommens-Card ist dieselbe Komponente wie auf der Budget-Seite; ihr
  // eigenes Verhalten deckt `income-card.spec.ts` ab, hier geht es nur um die Einbettung.
  it('rendert die Einkommens-Card oberhalb des Fixkosten-Abschnitts', () => {
    flushFixedCosts([MIETE]);

    const income = root().querySelector('app-income-card');
    const section = root().querySelector('app-fixed-cost-section');
    expect(income).not.toBeNull();
    expect(section).not.toBeNull();
    expect(
      income!.compareDocumentPosition(section!) & Node.DOCUMENT_POSITION_FOLLOWING,
    ).toBeTruthy();
  });

  it('zeigt den Abschnitt unter der Zwischenüberschrift «Fixkosten» als h2, gleichrangig mit «Einkommen»', () => {
    flushFixedCosts([]);

    const h2s = Array.from(root().querySelectorAll('h2')).map((h) => h.textContent?.trim());
    expect(h2s).toEqual(['Einkommen', 'Fixkosten']);
  });

  it('zeigt die erfassten Fixkosten als Tabelle mit Monatsbetrag und Total', () => {
    flushFixedCosts([MIETE, KRANKENKASSE]);

    expect(tableRows()).toEqual(['Miete', 'Krankenkasse']);
    expect(root().querySelector('thead')?.textContent).toContain('Monatsbetrag');
    const total = Array.from(root().querySelectorAll('tfoot tr > *')).map((cell) =>
      cell.textContent?.replace(/\s+/g, ' ').trim(),
    );
    expect(total).toEqual(['Total', 'CHF 1’600.00', '']);
  });

  it('führt kein eigenes Erfassungsformular mehr auf der Seite', () => {
    flushFixedCosts([MIETE]);

    // Das einzige Formular ausserhalb eines Dialogs ist das der Einkommens-Card.
    const forms = Array.from(root().querySelectorAll('form')).filter(
      (form) => !form.closest('app-income-card'),
    );
    expect(forms).toHaveLength(0);
    expect(root().querySelector('#bezeichnung')).toBeNull();
    expect(root().textContent).not.toContain('Fixkosten speichern');
  });

  it('öffnet über «+ Neue Position» denselben Dialog wie auf /budget', () => {
    flushFixedCosts([MIETE]);
    expect(root().querySelector('app-fixed-cost-create-dialog')).toBeNull();

    expect(newPositionButton().textContent?.trim()).toBe('+ Neue Position');
    newPositionButton().click();
    fixture.detectChanges();

    expect(root().querySelector('app-fixed-cost-create-dialog')).not.toBeNull();
  });

  // AC «Test» aus #376: die über den Dialog angelegte Position erscheint ohne Seitenwechsel.
  it('zeigt eine über den Dialog angelegte Position sofort in der Tabelle — ohne Reload', () => {
    flushFixedCosts([MIETE]);
    newPositionButton().click();
    fixture.detectChanges();

    fillDialogAndSave('Krankenkasse', '1200', 'quartalsweise');
    const post = httpMock.expectOne({ method: 'POST', url: '/api/fixed-costs' });
    expect(post.request.body).toEqual({
      bezeichnung: 'Krankenkasse',
      betrag: 1200,
      intervall: 'quartalsweise',
    });
    post.flush({ id: 2, bezeichnung: 'Krankenkasse', betrag: 1200, intervall: 'quartalsweise' });
    fixture.detectChanges();

    // Der Abschnitt lädt nach dem Anlegen selbst neu — kein Navigieren, keine neue Komponente.
    flushFixedCosts([MIETE, KRANKENKASSE]);

    expect(root().querySelector('app-fixed-cost-create-dialog')).toBeNull();
    expect(tableRows()).toEqual(['Miete', 'Krankenkasse']);
    expect(navigate).not.toHaveBeenCalled();
    expect(finishButtonText()).toBe('Fertig — weiter zum Dashboard');
  });

  it('bietet Bearbeiten und Löschen für bereits erfasste Positionen an', () => {
    flushFixedCosts([MIETE]);

    const labels = Array.from(root().querySelectorAll('tbody > tr button')).map((btn) =>
      btn.getAttribute('aria-label'),
    );
    expect(labels).toEqual(['Bearbeiten: Miete', 'Löschen: Miete']);
  });

  it('lädt die Fixkosten neu, sobald die Einkommens-Card gespeichert hat', () => {
    flushFixedCosts([MIETE]);

    const income = fixture.debugElement.query(By.directive(IncomeCard))
      .componentInstance as IncomeCard;
    income.incomeForm.controls.betrag.setValue(3800);
    income.submitIncome();
    httpMock.expectOne('/api/users/me/income').flush({ ...LARA, monthlyIncome: 3800 });

    // Die Warnung «Fixkosten übersteigen Einkommen» hängt am Fixkosten-Request.
    flushFixedCosts([MIETE]);
  });

  // --- FE-FC-02: Onboarding abschliessen ---

  it('beschriftet den Button mit «Später erfassen», solange die Tabelle leer ist', () => {
    flushFixedCosts([]);

    expect(finishButtonText()).toBe('Später erfassen — weiter zum Dashboard');
    expect(component.hasEnteredData()).toBe(false);
  });

  it('beschriftet den Button mit «Fertig», sobald die Tabelle Positionen enthält', () => {
    // Auch nach einem Reload mitten im Onboarding: die Positionen kommen vom Server, nicht aus
    // einem Sitzungs-Flag.
    flushFixedCosts([MIETE]);

    expect(finishButtonText()).toBe('Fertig — weiter zum Dashboard');
    expect(component.hasEnteredData()).toBe(true);
  });

  it('beschriftet den Button wieder mit «Später erfassen», wenn die letzte Position gelöscht ist', () => {
    flushFixedCosts([MIETE]);
    const deleteButton = root().querySelector<HTMLButtonElement>(
      'tbody > tr button[aria-label="Löschen: Miete"]',
    )!;
    deleteButton.click();
    fixture.detectChanges();
    const confirm = Array.from(
      root().querySelectorAll<HTMLButtonElement>('app-modal .modal__actions button'),
    ).find((btn) => btn.textContent?.trim() === 'Löschen')!;
    confirm.click();
    httpMock
      .expectOne({ method: 'DELETE', url: '/api/fixed-costs/1' })
      .flush(null, { status: 204, statusText: 'No Content' });
    flushFixedCosts([]);

    expect(finishButtonText()).toBe('Später erfassen — weiter zum Dashboard');
  });

  // FE-FC-09 (#360): die Einkommens-Card zählt genauso wie eine Fixkosten-Position — wer nur
  // sein Einkommen erfasst, hat nicht «nichts» getan, der alte Text «Keine Fixkosten» wäre hier
  // irreführend gewesen.
  it('beschriftet den Button um, sobald nur das Einkommen gespeichert wurde (ohne Fixkosten)', () => {
    flushFixedCosts([]);

    const income = fixture.debugElement.query(By.directive(IncomeCard))
      .componentInstance as IncomeCard;
    income.incomeForm.controls.betrag.setValue(3800);
    income.submitIncome();
    httpMock.expectOne('/api/users/me/income').flush({ ...LARA, monthlyIncome: 3800 });
    flushFixedCosts([]);

    expect(component.hasEnteredData()).toBe(true);
    expect(finishButtonText()).toBe('Fertig — weiter zum Dashboard');
  });

  it('schliesst das Onboarding ab und navigiert aufs Dashboard', () => {
    flushFixedCosts([]);
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
    flushFixedCosts([]);
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

  it('schliesst auch nach gespeicherter Position ueber denselben Request ab', () => {
    // US-03 laesst beide Wege aus dem Wizard heraus: ohne Eingabe bestaetigen ODER mindestens
    // etwas gespeichert (Fixkosten oder Einkommen, FE-FC-09). Ohne diesen Pfad sperrte der
    // onboardingGuard genau die Nutzer ein, die ihre Fixkosten korrekt erfasst haben.
    flushFixedCosts([MIETE]);

    component.finishOnboarding();
    httpMock.expectOne('/api/users/me/onboarding-complete').flush(LARA_ONBOARDED);

    expect(navigate).toHaveBeenCalledWith(['/dashboard']);
  });

  it('bleibt im Wizard und meldet den Fehler, wenn der Abschluss scheitert', () => {
    flushFixedCosts([]);
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
    flushFixedCosts([]);
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
