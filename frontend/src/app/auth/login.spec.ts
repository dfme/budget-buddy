import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { ComponentFixture, TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';

import { Login } from './login';
import { User } from './user.model';

const LARA: User = {
  id: 1,
  email: 'lara@example.ch',
  monthlyIncome: null,
  onboardingCompleted: false,
};

const LARA_ONBOARDED: User = { ...LARA, onboardingCompleted: true };

describe('Login', () => {
  let fixture: ComponentFixture<Login>;
  let component: Login;
  let httpMock: HttpTestingController;
  let navigate: ReturnType<typeof vi.spyOn>;

  beforeEach(async () => {
    await TestBed.configureTestingModule({
      imports: [Login],
      providers: [provideHttpClient(), provideHttpClientTesting(), provideRouter([])],
    }).compileComponents();

    fixture = TestBed.createComponent(Login);
    component = fixture.componentInstance;
    httpMock = TestBed.inject(HttpTestingController);
    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    fixture.detectChanges();
  });

  afterEach(() => httpMock.verify());

  it('does not call the backend when the form is empty', () => {
    component.submit();

    httpMock.expectNone('/auth/login');
    expect(component.form.invalid).toBe(true);
    expect(navigate).not.toHaveBeenCalled();
  });

  it('logs in and redirects to the onboarding wizard for a non-onboarded account', () => {
    // Der direkte Sprung erspart den Umweg über den onboardingGuard-Redirect (FE-FC-02),
    // landet aber am selben Ziel wie dieser.
    component.form.setValue({ email: 'lara@example.ch', password: 'supersecret' });

    component.submit();

    const req = httpMock.expectOne('/auth/login');
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

  it('logs in and redirects to the dashboard for an onboarded account', () => {
    component.form.setValue({ email: 'lara@example.ch', password: 'supersecret' });

    component.submit();
    httpMock.expectOne('/auth/login').flush(LARA_ONBOARDED);

    expect(navigate).toHaveBeenCalledWith(['/dashboard']);
  });

  it('shows a neutral error and does not redirect on 401', () => {
    component.form.setValue({ email: 'lara@example.ch', password: 'wrong-password' });

    component.submit();

    httpMock.expectOne('/auth/login').flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(component.errorMessage()).toBe('E-Mail oder Passwort falsch');
    expect(navigate).not.toHaveBeenCalled();
    expect(component.submitting()).toBe(false);
  });

  // Deckt den gerenderten Fehler ab, nicht nur das Signal: ein Umbau auf eine Komponente
  // mit `role="status"` liesse den Screenreader-Fehler sonst still verschwinden.
  it('kündigt den Formular-Fehler assertiv an (role=alert)', () => {
    component.form.setValue({ email: 'lara@example.ch', password: 'wrong-password' });

    component.submit();
    httpMock.expectOne('/auth/login').flush(null, { status: 401, statusText: 'Unauthorized' });
    fixture.detectChanges();

    const notice: HTMLElement = fixture.nativeElement.querySelector('app-notice');
    expect(notice.textContent?.trim()).toBe('E-Mail oder Passwort falsch');
    expect(notice.getAttribute('role')).toBe('alert');
  });
});
