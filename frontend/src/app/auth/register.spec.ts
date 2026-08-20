import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';

import { Register } from './register';
import { User } from './user.model';

const LARA: User = {
  id: 1,
  email: 'lara@example.ch',
  monthlyIncome: null,
  onboardingCompleted: false,
};

const LARA_ONBOARDED: User = { ...LARA, onboardingCompleted: true };

describe('Register', () => {
  let fixture: ComponentFixture<Register>;
  let component: Register;
  let httpMock: HttpTestingController;
  let navigate: ReturnType<typeof vi.spyOn>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Register],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(Register);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    fixture.detectChanges();
  });

  afterEach(() => httpMock.verify());

  it('does not call the backend when the form is empty', () => {
    component.submit();

    httpMock.expectNone('/api/auth/register');
    expect(component.form.invalid).toBe(true);
    expect(navigate).not.toHaveBeenCalled();
  });

  it('does not call the backend when the password is shorter than 8 characters', () => {
    component.form.setValue({ email: 'lara@example.ch', password: 'short' });

    component.submit();

    httpMock.expectNone('/api/auth/register');
    expect(component.form.controls.password.hasError('minlength')).toBe(true);
    expect(navigate).not.toHaveBeenCalled();
  });

  it('registers and redirects to the onboarding wizard for a fresh account', () => {
    // Ein frisch registriertes Konto hat onboardingCompleted = false (User-Konstruktor,
    // BE-AUTH-03). Der direkte Sprung erspart den Umweg über den onboardingGuard-Redirect
    // (FE-FC-02), landet aber am selben Ziel wie dieser.
    component.form.setValue({ email: 'lara@example.ch', password: 'supersecret' });

    component.submit();

    const req = httpMock.expectOne('/api/auth/register');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      email: 'lara@example.ch',
      password: 'supersecret',
    });
    req.flush(LARA);

    expect(navigate).toHaveBeenCalledWith(['/onboarding']);
    expect(component.errorMessage()).toBeNull();
    expect(component.submitting()).toBe(false);
  });

  it('redirects to the dashboard if the account is already onboarded', () => {
    // Praktisch nicht der Regelfall bei einer Neuregistrierung, aber die Weiche entscheidet
    // strikt nach der Serverantwort — kein Sonderfall für „gerade registriert".
    component.form.setValue({ email: 'lara@example.ch', password: 'supersecret' });

    component.submit();
    httpMock.expectOne('/api/auth/register').flush(LARA_ONBOARDED);

    expect(navigate).toHaveBeenCalledWith(['/dashboard']);
  });

  it('shows a specific error and does not redirect on 409', () => {
    component.form.setValue({ email: 'lara@example.ch', password: 'supersecret' });

    component.submit();

    httpMock.expectOne('/api/auth/register').flush(null, { status: 409, statusText: 'Conflict' });

    expect(component.errorMessage()).toBe('E-Mail bereits vergeben');
    expect(navigate).not.toHaveBeenCalled();
    expect(component.submitting()).toBe(false);
  });

  // Deckt den gerenderten Fehler ab, nicht nur das Signal: ein Umbau auf eine Komponente
  // mit `role="status"` liesse den Screenreader-Fehler sonst still verschwinden.
  it('kündigt den Formular-Fehler assertiv an (role=alert)', () => {
    component.form.setValue({ email: 'lara@example.ch', password: 'supersecret' });

    component.submit();
    httpMock.expectOne('/api/auth/register').flush(null, { status: 409, statusText: 'Conflict' });
    fixture.detectChanges();

    const notice: HTMLElement = fixture.nativeElement.querySelector('app-notice');
    expect(notice.textContent?.trim()).toBe('E-Mail bereits vergeben');
    expect(notice.getAttribute('role')).toBe('alert');
  });
});
