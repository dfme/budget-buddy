import { Location } from '@angular/common';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { By } from '@angular/platform-browser';
import { Router, provideRouter } from '@angular/router';

import {
  installMatchMedia,
  restoreMatchMedia,
  setSystemDark,
} from '../../testing/prefers-color-scheme';
import { App } from '../app';
import { routes } from '../app.routes';
import { AuthService } from '../auth/auth.service';
import { User } from '../auth/user.model';
import { authGuard } from '../core/guards/auth.guard';
import { onboardingGuard } from '../core/guards/onboarding.guard';
import { THEME_STORAGE_KEY, Theme } from '../core/theme/theme';
import { Dashboard } from '../dashboard/dashboard';
import { SafeToSpendResponse } from '../dashboard/safe-to-spend.model';
import { Settings } from './settings';

const LARA: User = {
  id: 1,
  email: 'lara@example.ch',
  monthlyIncome: 3000,
  onboardingCompleted: true,
  firstName: null,
  lastName: null,
};

const LARA_NO_INCOME: User = { ...LARA, monthlyIncome: null };

const NO_INCOME_WITH_SUGGESTION: SafeToSpendResponse = {
  amount: null,
  weeksLeft: 3,
  negative: false,
  noIncome: true,
  incomeSuggestion: 3800,
  status: 'OPEN',
};

const NO_INCOME_WITHOUT_SUGGESTION: SafeToSpendResponse = {
  amount: null,
  weeksLeft: 3,
  negative: false,
  noIncome: true,
  incomeSuggestion: null,
  status: 'OPEN',
};

/** Loggt via `AuthService.login()` ein, damit `currentUser()` synchron befüllt ist. */
function loginAs(httpMock: HttpTestingController, user: User): void {
  TestBed.inject(AuthService).login(user.email, 'irrelevant').subscribe();
  httpMock.expectOne('/api/auth/login').flush(user);
}

describe('Settings', () => {
  let fixture: ComponentFixture<Settings>;
  let component: Settings;
  let httpMock: HttpTestingController;
  /**
   * Dieses describe läuft mit `provideRouter([])` — ohne Routen. `confirmDelete` navigiert nach
   * Erfolg auf `/login`, was hier in einem NG04002 als Unhandled Rejection endete und die
   * Testumgebung mitriss. Der Spy hält die Navigation an und macht sie zugleich prüfbar; dass
   * die echte Navigation samt Login-Aktivierung funktioniert, deckt der Integrationstest in
   * «Route /einstellungen» ab.
   */
  let navigate: ReturnType<typeof vi.spyOn>;

  beforeEach(async () => {
    localStorage.removeItem(THEME_STORAGE_KEY);
    installMatchMedia(false);

    await TestBed.configureTestingModule({
      imports: [Settings],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    loginAs(httpMock, LARA);

    // Der Constructor überspringt den Vorschlags-Call für LARA (siehe settings.ts): sie hat
    // bereits ein Einkommen, das Backend liefert für diesen Fall laut SafeToSpendResponse-Doku
    // ohnehin immer `incomeSuggestion: null`. `afterEach`s `httpMock.verify()` deckt einen
    // ungewollten Call auf, falls die Optimierung doch einmal bricht.
    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);

    fixture = TestBed.createComponent(Settings);
    component = fixture.componentInstance;
    fixture.detectChanges();
  });

  // Der Screen hat keinen Ladepfad — die Theme-Wahl liegt client-only im localStorage
  // (US-14, Scope-Entscheid). `verify()` deckt auf, falls doch einmal ein Request
  // abgesetzt würde, statt das nur an einem Textinhalt zu vermuten.
  afterEach(() => {
    httpMock.verify();
    restoreMatchMedia();
    localStorage.removeItem(THEME_STORAGE_KEY);
    document.documentElement.removeAttribute('data-theme');
  });

  /** Die drei Buttons des Erscheinungsbild-Umschalters. */
  function themeButtons(): HTMLButtonElement[] {
    return Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll<HTMLButtonElement>(
        'app-segment button',
      ),
    );
  }

  /** Klickt die Option mit der gegebenen Beschriftung an. */
  function clickTheme(label: string): void {
    themeButtons()
      .find((button) => button.textContent?.trim() === label)!
      .click();
    TestBed.tick();
    fixture.detectChanges();
  }

  // --- AC (FE-SET-01): Überschrift und drei Abschnitte als Cards ---

  it('rendert die Überschrift "Einstellungen"', () => {
    expect(fixture.nativeElement.querySelector('h1')?.textContent?.trim()).toBe('Einstellungen');
  });

  it('rendert die vier Abschnitte "Passwort", "Einkommen", "Erscheinungsbild" und "Konto löschen" als Cards', () => {
    const cardTitles = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('app-card .card__title'),
    ).map((el) => el.textContent?.trim());

    // „Konto löschen" steht bewusst zuletzt (FE-SET-05): die zerstörerische Aktion gehört
    // ans Ende, nicht zwischen zwei alltägliche Einstellungen.
    expect(cardTitles).toEqual(['Passwort', 'Einkommen', 'Erscheinungsbild', 'Konto löschen']);
  });

  it('lässt an keiner Card das globale title-Attribut am Host stehen (FE-UI-08)', () => {
    // Alle app-card-Aufrufe in settings.html schreiben `title="…"` statisch — genau die
    // Konstellation, die Angular ohne das Host-Binding in `Card` zusätzlich als DOM-Attribut
    // stehen liesse. Die Anzahl der Cards sichert der Test „rendert die vier Abschnitte …"
    // bereits ab; hier geht es nur um das Attribut, nicht um den Zähler.
    const hosts = Array.from((fixture.nativeElement as HTMLElement).querySelectorAll('app-card'));

    expect(hosts.length).toBeGreaterThan(0);
    expect(hosts.every((host) => !host.hasAttribute('title'))).toBe(true);
  });

  // --- FE-SET-02: Passwort ändern ---

  it('sperrt den Submit-Button, solange das Formular ungültig ist', () => {
    const button = fixture.nativeElement.querySelector(
      'button[type="submit"]',
    ) as HTMLButtonElement;
    expect(button.disabled).toBe(true);
  });

  it('deaktiviert den Submit-Button, solange der zu kurze neue Passwort clientseitig abgelehnt wird', () => {
    component.passwordForm.setValue({ aktuellesPasswort: 'altesPasswort', neuesPasswort: 'kurz' });

    component.submitPassword();

    httpMock.expectNone('/api/users/me/password');
    expect(component.passwordForm.invalid).toBe(true);
    expect(component.neuesPasswortError()).toBe('Passwort muss mindestens 8 Zeichen lang sein.');
  });

  it('ändert das Passwort und zeigt eine In-App-Bestätigung, Felder werden geleert', () => {
    component.passwordForm.setValue({
      aktuellesPasswort: 'altesPasswort',
      neuesPasswort: 'neuesPasswort123',
    });

    component.submitPassword();

    const req = httpMock.expectOne('/api/users/me/password');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({
      aktuellesPasswort: 'altesPasswort',
      neuesPasswort: 'neuesPasswort123',
    });
    req.flush(null);
    fixture.detectChanges();

    expect(component.passwordForm.controls.aktuellesPasswort.value).toBe('');
    expect(component.passwordForm.controls.neuesPasswort.value).toBe('');
    expect(component.passwordSubmitting()).toBe(false);
    expect(component.passwordErrorMessage()).toBeNull();

    const notice: HTMLElement = fixture.nativeElement.querySelector('app-notice');
    expect(notice.getAttribute('role')).toBe('status');
    expect(notice.querySelector('.notice__body')?.textContent?.trim()).toBe('Passwort geändert.');
  });

  it('lehnt ein falsches aktuelles Passwort mit "Aktuelles Passwort falsch" ab, der User bleibt eingeloggt', () => {
    component.passwordForm.setValue({
      aktuellesPasswort: 'falschesPasswort',
      neuesPasswort: 'neuesPasswort123',
    });

    component.submitPassword();

    httpMock
      .expectOne('/api/users/me/password')
      .flush({ message: 'Aktuelles Passwort falsch' }, { status: 400, statusText: 'Bad Request' });
    fixture.detectChanges();

    expect(component.passwordErrorMessage()).toBe('Aktuelles Passwort falsch');
    expect(component.passwordSaved()).toBe(false);
    expect(component.passwordSubmitting()).toBe(false);

    const notice: HTMLElement = fixture.nativeElement.querySelector('app-notice');
    expect(notice.getAttribute('role')).toBe('alert');
    expect(notice.querySelector('.notice__body')?.textContent?.trim()).toBe(
      'Aktuelles Passwort falsch',
    );
  });

  it('zeigt die Backend-Meldung bei einem 400 wegen ungültigem neuen Passwort, nicht "Aktuelles Passwort falsch"', () => {
    component.passwordForm.setValue({
      aktuellesPasswort: 'altesPasswort',
      neuesPasswort: '        ',
    });

    component.submitPassword();

    httpMock
      .expectOne('/api/users/me/password')
      .flush(
        { message: 'Neues Passwort ist erforderlich.' },
        { status: 400, statusText: 'Bad Request' },
      );
    fixture.detectChanges();

    expect(component.passwordErrorMessage()).toBe('Neues Passwort ist erforderlich.');
  });

  it('setzt die alte Erfolgsmeldung vor einem neuen Versuch zurück', () => {
    component.passwordForm.setValue({
      aktuellesPasswort: 'altesPasswort',
      neuesPasswort: 'neuesPasswort123',
    });
    component.submitPassword();
    httpMock.expectOne('/api/users/me/password').flush(null);
    expect(component.passwordSaved()).toBe(true);

    component.passwordForm.setValue({
      aktuellesPasswort: 'falschesPasswort',
      neuesPasswort: 'neuesPasswort123',
    });
    component.submitPassword();

    expect(component.passwordSaved()).toBe(false);
    httpMock
      .expectOne('/api/users/me/password')
      .flush(null, { status: 400, statusText: 'Bad Request' });
  });

  // --- AC2: Ist ein Einkommen erfasst, steht der aktuelle Wert im Feld ---

  it('belegt das Betragsfeld mit dem aktuellen Monatseinkommen vor', () => {
    expect(fixture.componentInstance.incomeForm.controls.betrag.value).toBe(3000);

    const input = (fixture.nativeElement as HTMLElement).querySelector(
      '#monthlyIncome',
    ) as HTMLInputElement;
    expect(input.value).toBe('3000');
  });

  // --- AC1: Das Feld ist im UI als optional gekennzeichnet ---

  it('weist im Hinweistext darauf hin, dass das Betragsfeld optional ist', () => {
    const hint = (fixture.nativeElement as HTMLElement).querySelector('form .settings__hint');

    expect(hint?.textContent).toContain('Optional');
  });

  // --- AC3: Kein Vorschlag, solange bereits ein Einkommen erfasst ist ---

  it('zeigt keine Vorschlags-Notice, wenn bereits ein Einkommen erfasst ist', () => {
    expect(fixture.componentInstance.incomeSuggestionText()).toBeNull();
    expect((fixture.nativeElement as HTMLElement).querySelector('.income-suggestion')).toBeNull();
  });

  it('lädt den Einkommens-Vorschlag nicht, wenn bereits ein Einkommen erfasst ist', () => {
    httpMock.expectNone('/api/budget/safe-to-spend');
  });

  // --- AC1/AC4: Betrag ist optional, Beträge <= 0 werden clientseitig abgefangen ---

  it.each([0, -5, -0.01])('lehnt den Betrag %s ab und sendet nicht', (betrag) => {
    fixture.componentInstance.incomeForm.controls.betrag.setValue(betrag);

    fixture.componentInstance.submitIncome();

    httpMock.expectNone('/api/users/me/income');
    expect(fixture.componentInstance.incomeForm.controls.betrag.hasError('min')).toBe(true);
    expect(fixture.componentInstance.incomeError()).toBe('Betrag muss grösser als 0 sein.');
  });

  it('zeigt bei einem zu niedrigen Betrag mit zu vielen Nachkommastellen die Nachkommastellen-Meldung', () => {
    // 0.005 verletzt beide Regeln (< 0.01 und > 2 Nachkommastellen) — "muss grösser als 0 sein"
    // wäre hier irreführend, die eigentliche Verletzung ist die Nachkommastellen-Regel.
    fixture.componentInstance.incomeForm.controls.betrag.setValue(0.005);

    fixture.componentInstance.submitIncome();

    expect(fixture.componentInstance.incomeError()).toBe(
      'Betrag darf höchstens zwei Nachkommastellen haben.',
    );
  });

  it.each([10.999, 0.015, 3000.123])(
    'lehnt den Betrag %s mit mehr als zwei Nachkommastellen ab',
    (betrag) => {
      fixture.componentInstance.incomeForm.controls.betrag.setValue(betrag);

      fixture.componentInstance.submitIncome();

      httpMock.expectNone('/api/users/me/income');
      expect(fixture.componentInstance.incomeForm.controls.betrag.hasError('maxDecimals')).toBe(
        true,
      );
      expect(fixture.componentInstance.incomeError()).toBe(
        'Betrag darf höchstens zwei Nachkommastellen haben.',
      );
    },
  );

  it('sendet keinen Request, wenn das Feld geleert wird — leer lassen ist erlaubt', () => {
    fixture.componentInstance.incomeForm.controls.betrag.setValue(null);

    fixture.componentInstance.submitIncome();

    httpMock.expectNone('/api/users/me/income');
    expect(fixture.componentInstance.incomeForm.valid).toBe(true);
  });

  // --- AC4: Speichern ruft PUT /users/me/income auf ---

  it('speichert einen gültigen Betrag und zeigt eine Bestätigung', () => {
    fixture.componentInstance.incomeForm.controls.betrag.setValue(3800);

    fixture.componentInstance.submitIncome();

    const req = httpMock.expectOne('/api/users/me/income');
    expect(req.request.method).toBe('PUT');
    expect(req.request.body).toEqual({ betrag: 3800 });
    req.flush({ ...LARA, monthlyIncome: 3800 });
    fixture.detectChanges();

    expect(fixture.componentInstance.incomeSaved()).toBe(true);
    expect(fixture.componentInstance.incomeSubmitting()).toBe(false);
    expect((fixture.nativeElement as HTMLElement).textContent).toContain('Einkommen gespeichert.');
  });

  it('zeigt die Fehlermeldung aus dem Backend bei einer 400-Antwort direkt an', () => {
    fixture.componentInstance.incomeForm.controls.betrag.setValue(100_000_000);

    fixture.componentInstance.submitIncome();

    httpMock
      .expectOne('/api/users/me/income')
      .flush(
        { field: 'betrag', message: "Betrag darf 99'999'999.99 nicht überschreiten." },
        { status: 400, statusText: 'Bad Request' },
      );
    fixture.detectChanges();

    expect(fixture.componentInstance.incomeErrorMessage()).toBe(
      "Betrag darf 99'999'999.99 nicht überschreiten.",
    );
    expect(fixture.componentInstance.incomeSaved()).toBe(false);
  });

  it('zeigt eine generische Meldung bei einem Serverfehler', () => {
    fixture.componentInstance.incomeForm.controls.betrag.setValue(3800);

    fixture.componentInstance.submitIncome();

    httpMock
      .expectOne('/api/users/me/income')
      .flush('boom', { status: 500, statusText: 'Internal Server Error' });
    fixture.detectChanges();

    expect(fixture.componentInstance.incomeErrorMessage()).toBe(
      'Einkommen konnte nicht gespeichert werden. Bitte versuche es später erneut.',
    );
  });

  it('räumt die alte Erfolgsmeldung weg, bevor der nächste Versuch läuft', () => {
    fixture.componentInstance.incomeForm.controls.betrag.setValue(3800);
    fixture.componentInstance.submitIncome();
    httpMock.expectOne('/api/users/me/income').flush({ ...LARA, monthlyIncome: 3800 });
    expect(fixture.componentInstance.incomeSaved()).toBe(true);

    fixture.componentInstance.incomeForm.controls.betrag.setValue(4000);
    fixture.componentInstance.submitIncome();

    expect(fixture.componentInstance.incomeSaved()).toBe(false);
    expect(fixture.componentInstance.incomeSubmitting()).toBe(true);
    httpMock.expectOne('/api/users/me/income').flush({ ...LARA, monthlyIncome: 4000 });
  });

  // --- FE-SET-04: Erscheinungsbild ---

  // --- AC1: Auswahl Hell / Dunkel / System, sofort und ohne Reload ---

  it('bietet genau die drei Optionen "Hell", "Dunkel" und "System" an', () => {
    expect(themeButtons().map((button) => button.textContent?.trim())).toEqual([
      'Hell',
      'Dunkel',
      'System',
    ]);
  });

  it('stellt bei Klick sofort um und markiert die aktive Option', () => {
    clickTheme('Dunkel');

    expect(document.documentElement.getAttribute('data-theme')).toBe('dark');
    expect(TestBed.inject(Theme).preference()).toBe('dark');
    expect(
      themeButtons().find((button) => button.getAttribute('aria-pressed') === 'true')?.textContent,
    ).toContain('Dunkel');
  });

  // --- AC6: Persistenz über localStorage ---

  it('merkt sich die Wahl im localStorage', () => {
    clickTheme('Dunkel');

    expect(localStorage.getItem(THEME_STORAGE_KEY)).toBe('dark');
  });

  // --- AC3: "System" ist der Ausgangszustand und folgt dem Betriebssystem ---

  it('startet auf "System" und folgt einem Wechsel im Betriebssystem', () => {
    expect(
      themeButtons().find((button) => button.getAttribute('aria-pressed') === 'true')?.textContent,
    ).toContain('System');

    setSystemDark(true);
    TestBed.tick();

    expect(document.documentElement.getAttribute('data-theme')).toBe('dark');
  });

  // --- AC5: Der Hinweis benennt die Reichweite der Wahl ---

  it('weist darauf hin, dass die Wahl nur in diesem Browser gilt', () => {
    const hint = (fixture.nativeElement as HTMLElement).querySelector(
      'app-segment + .settings__hint',
    );

    expect(hint?.textContent).toContain('nur in diesem Browser');
  });

  // --- FE-SET-05 / US-02: Konto löschen ---

  /** Der Button der Lösch-Card — nicht der gleichnamige im Dialog. */
  function openDeleteButton(): HTMLButtonElement {
    const card = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('app-card'),
    ).find((el) => el.querySelector('.card__title')?.textContent?.trim() === 'Konto löschen');
    return card!.querySelector('button')!;
  }

  /** Ein Button im Aktionsbereich des Dialogs, über seine Beschriftung gesucht. */
  function dialogButton(label: string): HTMLButtonElement | undefined {
    return Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll<HTMLButtonElement>(
        'app-modal .modal__actions button',
      ),
    ).find((btn) => btn.textContent?.trim() === label);
  }

  function dialog(): HTMLElement | null {
    return (fixture.nativeElement as HTMLElement).querySelector('app-modal');
  }

  /** Öffnet den Dialog über den Button der Card und tippt optional ein Passwort ein. */
  function openDialog(passwort?: string): void {
    openDeleteButton().click();
    fixture.detectChanges();
    if (passwort !== undefined) {
      component.deleteForm.controls.passwort.setValue(passwort);
      fixture.detectChanges();
    }
  }

  // --- AC1: Aktion öffnet einen Bestätigungsdialog mit Passwort-Eingabe ---

  it('zeigt die Aktion "Konto löschen" und öffnet den Dialog erst auf Klick', () => {
    expect(openDeleteButton().textContent?.trim()).toBe('Konto löschen');
    expect(dialog()).toBeNull();

    openDialog();

    expect(dialog()).not.toBeNull();
    expect(dialog()!.textContent).toContain('Konto endgültig löschen?');
  });

  it('enthält im Dialog ein Passwortfeld', () => {
    openDialog();

    const input = (fixture.nativeElement as HTMLElement).querySelector<HTMLInputElement>(
      'app-modal input#deletePasswort',
    );
    expect(input).not.toBeNull();
    expect(input!.type).toBe('password');
  });

  it('sperrt den Submit, solange das Passwortfeld leer ist', () => {
    openDialog();

    expect(component.deleteDisabled()).toBe(true);
    expect(dialogButton('Konto löschen')!.disabled).toBe(true);

    component.deleteForm.controls.passwort.setValue('supersecret');
    fixture.detectChanges();

    expect(component.deleteDisabled()).toBe(false);
    expect(dialogButton('Konto löschen')!.disabled).toBe(false);
  });

  it('sperrt den Submit, solange der Request läuft', () => {
    openDialog('supersecret');

    dialogButton('Konto löschen')!.click();
    fixture.detectChanges();

    expect(component.deleteSubmitting()).toBe(true);
    expect(dialogButton('Konto löschen')!.disabled).toBe(true);

    // Ein zweiter Klick darf keinen zweiten Request auslösen — `httpMock.verify()` im
    // afterEach deckte einen unbeantworteten auf.
    dialogButton('Konto löschen')!.click();

    httpMock.expectOne('/api/users/me').flush(null, { status: 204, statusText: 'No Content' });
  });

  it('bestätigt per Enter im Passwortfeld wie über den Button (Review #304)', () => {
    openDialog('supersecret');

    (fixture.nativeElement as HTMLElement)
      .querySelector<HTMLFormElement>('app-modal form')!
      .dispatchEvent(new Event('submit'));

    httpMock.expectOne('/api/users/me').flush(null, { status: 204, statusText: 'No Content' });
  });

  it('löst per Enter bei leerem Passwortfeld keinen Request aus', () => {
    openDialog();

    // Der Submit-Button ist gesperrt, das Formular selbst aber nicht — der Guard sitzt in
    // confirmDelete(), sonst käme Enter am gesperrten Button vorbei.
    (fixture.nativeElement as HTMLElement)
      .querySelector<HTMLFormElement>('app-modal form')!
      .dispatchEvent(new Event('submit'));

    httpMock.expectNone('/api/users/me');
  });

  // --- AC2: DELETE /api/users/me mit dem Passwort im Body ---

  it('ruft DELETE /api/users/me mit dem eingegebenen Passwort im Body', () => {
    openDialog('supersecret');

    dialogButton('Konto löschen')!.click();

    const req = httpMock.expectOne('/api/users/me');
    expect(req.request.method).toBe('DELETE');
    expect(req.request.body).toEqual({ passwort: 'supersecret' });
    req.flush(null, { status: 204, statusText: 'No Content' });
  });

  // --- AC3: Erfolgreiche Löschung navigiert auf /login ---

  it('navigiert nach erfolgreicher Löschung auf /login und meldet die Löschung dorthin', () => {
    openDialog('supersecret');

    dialogButton('Konto löschen')!.click();
    httpMock.expectOne('/api/users/me').flush(null, { status: 204, statusText: 'No Content' });
    fixture.detectChanges();

    expect(navigate).toHaveBeenCalledWith(['/login'], { state: { accountDeleted: true } });
    // Der Dialog ist weg und der Auth-State geleert — der Service macht das im `tap`.
    expect(dialog()).toBeNull();
    expect(TestBed.inject(AuthService).currentUser()).toBeNull();
  });

  // --- AC4: 400 → "Passwort falsch", User bleibt eingeloggt ---

  it('zeigt "Passwort falsch" im Dialog und hält den User eingeloggt, wenn das Backend 400 liefert', () => {
    openDialog('falsch');

    dialogButton('Konto löschen')!.click();
    httpMock
      .expectOne('/api/users/me')
      .flush({ message: 'Aktuelles Passwort falsch' }, { status: 400, statusText: 'Bad Request' });
    fixture.detectChanges();

    // Bewusst der feste Text, nicht die Backend-`message`: im Löschdialog gibt es kein
    // „neues" Passwort, zu dem „aktuelles" den Gegensatz bildete (AC4).
    expect(component.deleteErrorMessage()).toBe('Passwort falsch');
    expect(dialog()!.querySelector('app-notice')?.textContent).toContain('Passwort falsch');

    // Der User bleibt eingeloggt und im Einstellungs-Screen.
    expect(dialog()).not.toBeNull();
    expect(navigate).not.toHaveBeenCalled();
    expect(TestBed.inject(AuthService).currentUser()).toEqual(LARA);
  });

  it('erlaubt einen zweiten Versuch nach falschem Passwort', () => {
    openDialog('falsch');
    dialogButton('Konto löschen')!.click();
    httpMock
      .expectOne('/api/users/me')
      .flush({ message: 'Aktuelles Passwort falsch' }, { status: 400, statusText: 'Bad Request' });
    fixture.detectChanges();

    expect(component.deleteSubmitting()).toBe(false);
    expect(dialogButton('Konto löschen')!.disabled).toBe(false);

    component.deleteForm.controls.passwort.setValue('supersecret');
    fixture.detectChanges();
    dialogButton('Konto löschen')!.click();

    const req = httpMock.expectOne('/api/users/me');
    expect(req.request.body).toEqual({ passwort: 'supersecret' });
    req.flush(null, { status: 204, statusText: 'No Content' });
  });

  // --- Abbrechen ---

  it('schliesst den Dialog per Abbrechen und leert das Passwortfeld', () => {
    openDialog('supersecret');

    dialogButton('Abbrechen')!.click();
    fixture.detectChanges();

    expect(dialog()).toBeNull();
    expect(component.deleteForm.controls.passwort.value).toBe('');
    httpMock.expectNone('/api/users/me');
  });

  it('ignoriert Abbrechen, solange der Request läuft', () => {
    openDialog('supersecret');
    dialogButton('Konto löschen')!.click();
    fixture.detectChanges();

    // Das DELETE liesse sich nicht mehr zurücknehmen — ein geschlossener Dialog täuschte
    // einen Abbruch vor, den es nicht gibt.
    dialogButton('Abbrechen')!.click();
    fixture.detectChanges();

    expect(dialog()).not.toBeNull();
    httpMock.expectOne('/api/users/me').flush(null, { status: 204, statusText: 'No Content' });
  });

  // --- AC5: Das Passwort erreicht keinen Browser-Speicher ---

  it('schreibt das Passwort weder in localStorage noch in sessionStorage (ADR-7)', () => {
    const localSetItem = vi.spyOn(Storage.prototype, 'setItem');

    openDialog('supersecret');
    dialogButton('Konto löschen')!.click();
    httpMock.expectOne('/api/users/me').flush(null, { status: 204, statusText: 'No Content' });
    fixture.detectChanges();

    // Der Spy fängt beide Storages ab — `localStorage` und `sessionStorage` teilen sich
    // `Storage.prototype`. Geprüft wird jeder Aufruf, nicht nur die Summe: der Theme-Service
    // darf schreiben, das Passwort darf in keinem Wert vorkommen.
    for (const [key, value] of localSetItem.mock.calls) {
      expect(String(key)).not.toContain('supersecret');
      expect(String(value)).not.toContain('supersecret');
    }
    expect(localStorage.getItem('deletePasswort')).toBeNull();
    expect(sessionStorage.length).toBe(0);

    localSetItem.mockRestore();
  });
});

describe('Einkommen-Formular ohne erfasstes Einkommen', () => {
  let fixture: ComponentFixture<Settings>;
  let httpMock: HttpTestingController;

  async function createWith(response: SafeToSpendResponse): Promise<void> {
    await TestBed.configureTestingModule({
      imports: [Settings],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    httpMock = TestBed.inject(HttpTestingController);
    loginAs(httpMock, LARA_NO_INCOME);

    fixture = TestBed.createComponent(Settings);
    httpMock.expectOne('/api/budget/safe-to-spend').flush(response);
    fixture.detectChanges();
  }

  afterEach(() => httpMock.verify());

  it('lässt das Betragsfeld leer, solange kein Einkommen erfasst ist', async () => {
    await createWith(NO_INCOME_WITHOUT_SUGGESTION);

    expect(fixture.componentInstance.incomeForm.controls.betrag.value).toBeNull();
  });

  it('zeigt keine Vorschlags-Notice, wenn die Heuristik nichts gefunden hat', async () => {
    await createWith(NO_INCOME_WITHOUT_SUGGESTION);

    expect((fixture.nativeElement as HTMLElement).querySelector('.income-suggestion')).toBeNull();
  });

  // --- AC3: Vorschlag erscheint am Feld ---

  it('zeigt den Vorschlagssatz mit dem erkannten Betrag', async () => {
    await createWith(NO_INCOME_WITH_SUGGESTION);

    expect(fixture.componentInstance.incomeSuggestionText()).toBe(
      "Regelmässige Gutschrift von 3'800.00 CHF erkannt — als Monatseinkommen übernehmen?",
    );
    const notice = (fixture.nativeElement as HTMLElement).querySelector('.income-suggestion');
    expect(notice?.textContent).toContain("3'800.00 CHF erkannt");
  });

  it('übernimmt den Vorschlag per Klick und speichert ihn sofort', async () => {
    await createWith(NO_INCOME_WITH_SUGGESTION);

    const button = Array.from(
      (fixture.nativeElement as HTMLElement).querySelectorAll('button'),
    ).find((candidate) => candidate.textContent?.trim() === 'Übernehmen');
    expect(button).toBeDefined();

    button!.click();

    expect(fixture.componentInstance.incomeForm.controls.betrag.value).toBe(3800);
    const req = httpMock.expectOne('/api/users/me/income');
    expect(req.request.body).toEqual({ betrag: 3800 });
    req.flush({ ...LARA_NO_INCOME, monthlyIncome: 3800 });
    fixture.detectChanges();

    expect(fixture.componentInstance.incomeSaved()).toBe(true);
  });
});

describe('Route /einstellungen', () => {
  let httpMock: HttpTestingController;
  let router: Router;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter(routes)],
    });
    httpMock = TestBed.inject(HttpTestingController);
    router = TestBed.inject(Router);
  });

  // Die geladene Settings-Komponente zieht den Theme-Service mit und setzt dabei
  // `data-theme` — nach den Routing-Tests wieder abräumen.
  afterEach(() => {
    httpMock.verify();
    document.documentElement.removeAttribute('data-theme');
  });

  /** Beantwortet das `GET /api/users/me` des Guards. */
  async function answerProfile(user: User | null): Promise<void> {
    await new Promise((resolve) => setTimeout(resolve, 0));
    const req = httpMock.expectOne('/api/users/me');
    if (user === null) {
      req.flush(null, { status: 401, statusText: 'Unauthorized' });
    } else {
      req.flush(user);
    }
  }

  // --- AC4: Anonymer Aufruf landet auf /login ---

  it('leitet einen anonymen Aufruf von /einstellungen auf /login um', async () => {
    const navigation = router.navigateByUrl('/einstellungen');
    await answerProfile(null);
    await navigation;

    expect(router.url).toBe('/login');
  });

  // --- AC5: Route ist erreichbar ---

  it('erreicht /einstellungen als eingeloggter, onboardeter User', async () => {
    // Ohne gerendertes RouterOutlet in diesem Test wird die Ziel-Komponente nicht
    // instanziiert — der Guard-Erfolg (Navigation landet auf /einstellungen) ist unabhängig
    // davon, dass Settings selbst noch GET /api/budget/safe-to-spend nachlädt (siehe AC5-Test
    // unten, der App als Root-Fixture nutzt).
    const navigation = router.navigateByUrl('/einstellungen');
    await answerProfile(LARA);
    await navigation;

    expect(router.url).toBe('/einstellungen');
  });

  it('hängt authGuard und onboardingGuard an /einstellungen wie bei den übrigen geschützten Routes', () => {
    const route = routes.find((candidate) => candidate.path === 'einstellungen');
    expect(route, "Route 'einstellungen' fehlt").toBeDefined();
    expect(route!.canActivate ?? []).toEqual([authGuard, onboardingGuard]);
  });

  // --- AC5: Dashboard zeigt den neuen Betrag ohne Reload der App ---

  /**
   * FE-STS-04: Das Dashboard lädt beim Aufbau daneben die Monatsliste — sie speist Dropdown und
   * Keine-Daten-Hinweis. Der laufende Monat trägt hier Daten, damit das Dashboard in seinem
   * Normalzustand steht und der Hinweis wegbleibt.
   */
  function flushDashboardMonths() {
    const now = new Date();
    const month = `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
    httpMock.expectOne('/api/transactions/months').flush([month]);
  }

  it('zeigt auf dem Dashboard den neuen Safe-to-Spend-Betrag, nachdem das Einkommen in den Einstellungen gespeichert wurde — ohne Reload', async () => {
    const root = TestBed.createComponent(App);
    root.detectChanges();

    const firstNavigation = router.navigateByUrl('/einstellungen');
    await answerProfile(LARA_NO_INCOME);
    await firstNavigation;
    // Dieser Request gehört Settings selbst, nicht dem Dashboard — hier ist noch keine
    // Monatsliste im Spiel.
    httpMock.expectOne('/api/budget/safe-to-spend').flush(NO_INCOME_WITHOUT_SUGGESTION);
    root.detectChanges();
    // FE-NOTIF-01: Sobald isAuthenticated() kippt, rendert die Shell app-notification-bell
    // (Topbar + Sidebar) und beide Instanzen laden beim Erstellen — gebündelt auf einen Request.
    // Die Komponenten selbst entstehen erst mit diesem detectChanges(), anders als Settings, das
    // der Router bereits während der Navigation erzeugt.
    httpMock.expectOne('/api/notifications').flush([]);

    const settings = root.debugElement.query(By.directive(Settings)).componentInstance as Settings;
    settings.incomeForm.controls.betrag.setValue(3800);
    settings.submitIncome();
    httpMock.expectOne('/api/users/me/income').flush({ ...LARA_NO_INCOME, monthlyIncome: 3800 });
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

  // --- FE-SET-05 / AC3: Löschen landet auf /login samt Bestätigung ---

  it('landet nach dem Löschen auf /login und zeigt dort die Bestätigung — ohne Reload', async () => {
    const root = TestBed.createComponent(App);
    root.detectChanges();

    const firstNavigation = router.navigateByUrl('/einstellungen');
    await answerProfile(LARA);
    await firstNavigation;
    root.detectChanges();
    httpMock.expectOne('/api/notifications').flush([]);

    const settings = root.debugElement.query(By.directive(Settings)).componentInstance as Settings;
    settings.deleteForm.controls.passwort.setValue('supersecret');
    settings.confirmDelete();
    httpMock.expectOne('/api/users/me').flush(null, { status: 204, statusText: 'No Content' });

    // Echte Router-Navigation statt eines Spies: nur so wird Login tatsächlich aktiviert und
    // liest den Navigation-State, den confirmDelete mitgibt.
    await root.whenStable();
    root.detectChanges();

    // FE-NOTIF-01: Die Glocke lädt bei jedem NavigationEnd neu (`notification-bell.ts:55`) —
    // auch bei dieser letzten Navigation, genau wie beim Logout. Ob sie noch dazu kommt, hängt
    // davon ab, ob sie vor oder nach dem Kippen von isAuthenticated() zerstört wird; `match`
    // nimmt beide Fälle, `expectOne` nur einen und liesse `verify()` sonst stolpern.
    httpMock.match('/api/notifications').forEach((req) => req.flush([]));

    expect(router.url).toBe('/login');
    expect((root.nativeElement as HTMLElement).textContent).toContain('Dein Konto wurde gelöscht');
    // Der authGuard hat die Navigation nicht zurückgedreht — der Auth-State ist leer.
    expect(TestBed.inject(AuthService).currentUser()).toBeNull();
  });

  // --- FE-SET-05 / AC3: Ein Reload nach der Löschung zeigt die Bestätigung nicht erneut ---

  it('löscht den Navigation-State aus der History, damit ein Reload die Bestätigung nicht wiederholt', async () => {
    const root = TestBed.createComponent(App);
    root.detectChanges();

    const firstNavigation = router.navigateByUrl('/einstellungen');
    await answerProfile(LARA);
    await firstNavigation;
    root.detectChanges();
    httpMock.expectOne('/api/notifications').flush([]);

    const settings = root.debugElement.query(By.directive(Settings)).componentInstance as Settings;
    settings.deleteForm.controls.passwort.setValue('supersecret');
    settings.confirmDelete();
    httpMock.expectOne('/api/users/me').flush(null, { status: 204, statusText: 'No Content' });

    await root.whenStable();
    root.detectChanges();
    httpMock.match('/api/notifications').forEach((req) => req.flush([]));

    // `Location.getState()` ist genau das, was ein Reload restaurieren würde (siehe login.ts,
    // readAccountDeleted): bliebe `accountDeleted` hier stehen, käme die Bestätigung nach F5
    // zurück — das war der in Review #304 gefundene Defekt.
    const state = TestBed.inject(Location).getState() as Record<string, unknown> | null;
    expect(state?.['accountDeleted']).toBeUndefined();
  });
});
