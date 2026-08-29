import { TestBed } from '@angular/core/testing';
import { provideHttpClient } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';

import { AuthService } from './auth.service';
import { User } from './user.model';

const LARA: User = {
  id: 1,
  email: 'lara@example.ch',
  monthlyIncome: null,
  onboardingCompleted: false,
  firstName: null,
  lastName: null,
};

describe('AuthService', () => {
  let service: AuthService;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [provideHttpClient(), provideHttpClientTesting()],
    });
    service = TestBed.inject(AuthService);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('starts unauthenticated', () => {
    expect(service.currentUser()).toBeNull();
    expect(service.isAuthenticated()).toBe(false);
  });

  it('register posts credentials without a name and sets the auth state', () => {
    service.register('lara@example.ch', 'supersecret').subscribe();

    const req = httpMock.expectOne('/api/auth/register');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      email: 'lara@example.ch',
      password: 'supersecret',
      firstName: null,
      lastName: null,
    });
    req.flush(LARA);

    expect(service.currentUser()).toEqual(LARA);
    expect(service.isAuthenticated()).toBe(true);
  });

  // BE-AUTH-05 (#114): firstName/lastName sind optional, gehen aber immer im Body mit —
  // der Feldname ist Teil des Vertrags, wie bei den übrigen Feldern dieses Service.
  it('register posts firstName and lastName when provided', () => {
    service.register('lara@example.ch', 'supersecret', 'Lara', 'Meier').subscribe();

    const req = httpMock.expectOne('/api/auth/register');
    expect(req.request.body).toEqual({
      email: 'lara@example.ch',
      password: 'supersecret',
      firstName: 'Lara',
      lastName: 'Meier',
    });
    req.flush({ ...LARA, firstName: 'Lara', lastName: 'Meier' });

    expect(service.currentUser()?.firstName).toBe('Lara');
    expect(service.currentUser()?.lastName).toBe('Meier');
  });

  it('login posts credentials and sets the auth state', () => {
    service.login('lara@example.ch', 'supersecret').subscribe();

    const req = httpMock.expectOne('/api/auth/login');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      email: 'lara@example.ch',
      password: 'supersecret',
    });
    req.flush(LARA);

    expect(service.currentUser()).toEqual(LARA);
    expect(service.isAuthenticated()).toBe(true);
  });

  it('logout hits the endpoint and clears the auth state', () => {
    service.login('lara@example.ch', 'supersecret').subscribe();
    httpMock.expectOne('/api/auth/login').flush(LARA);
    expect(service.isAuthenticated()).toBe(true);

    service.logout().subscribe();
    const req = httpMock.expectOne('/api/auth/logout');
    expect(req.request.method).toBe('POST');
    req.flush(null);

    expect(service.currentUser()).toBeNull();
    expect(service.isAuthenticated()).toBe(false);
  });

  it('loadCurrentUser restores the state from GET /api/users/me', () => {
    let emitted: User | null | undefined;
    service.loadCurrentUser().subscribe((user) => (emitted = user));

    const req = httpMock.expectOne('/api/users/me');
    expect(req.request.method).toBe('GET');
    req.flush(LARA);

    expect(emitted).toEqual(LARA);
    expect(service.currentUser()).toEqual(LARA);
    expect(service.isAuthenticated()).toBe(true);
  });

  it('loadCurrentUser stays anonymous on 401 without throwing', () => {
    let emitted: User | null | undefined;
    let errored = false;
    service.loadCurrentUser().subscribe({
      next: (user) => (emitted = user),
      error: () => (errored = true),
    });

    httpMock.expectOne('/api/users/me').flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(errored).toBe(false);
    expect(emitted).toBeNull();
    expect(service.currentUser()).toBeNull();
    expect(service.isAuthenticated()).toBe(false);
  });

  it('ensureCurrentUser returns the loaded profile without a request', () => {
    service.login('lara@example.ch', 'supersecret').subscribe();
    httpMock.expectOne('/api/auth/login').flush(LARA);

    let emitted: User | null | undefined;
    service.ensureCurrentUser().subscribe((user) => (emitted = user));

    expect(emitted).toEqual(LARA);
    httpMock.expectNone('/api/users/me');
  });

  it('ensureCurrentUser falls back to GET /api/users/me when the state is empty', () => {
    let emitted: User | null | undefined;
    service.ensureCurrentUser().subscribe((user) => (emitted = user));

    const req = httpMock.expectOne('/api/users/me');
    expect(req.request.method).toBe('GET');
    req.flush(LARA);

    expect(emitted).toEqual(LARA);
    expect(service.currentUser()).toEqual(LARA);
  });

  it('completeOnboarding posts to the endpoint and updates the state', () => {
    service.login('lara@example.ch', 'supersecret').subscribe();
    httpMock.expectOne('/api/auth/login').flush(LARA);
    expect(service.currentUser()?.onboardingCompleted).toBe(false);

    let emitted: User | undefined;
    service.completeOnboarding().subscribe((user) => (emitted = user));

    const req = httpMock.expectOne('/api/users/me/onboarding-complete');
    expect(req.request.method).toBe('POST');
    req.flush({ ...LARA, onboardingCompleted: true });

    // Ohne dieses State-Update wuerde der onboardingGuard die anschliessende Navigation
    // aufs Dashboard sofort wieder in den Wizard zurueckdrehen.
    expect(emitted?.onboardingCompleted).toBe(true);
    expect(service.currentUser()?.onboardingCompleted).toBe(true);
  });

  it('updateIncome puts the amount and updates the state (FE-STS-03)', () => {
    service.login('lara@example.ch', 'supersecret').subscribe();
    httpMock.expectOne('/api/auth/login').flush(LARA);
    expect(service.currentUser()?.monthlyIncome).toBeNull();

    let emitted: User | undefined;
    service.updateIncome(3800).subscribe((user) => (emitted = user));

    const req = httpMock.expectOne('/api/users/me/income');
    expect(req.request.method).toBe('PUT');
    // Der Feldname ist Teil des Vertrags: das Backend bindet auf UpdateIncomeRequest.betrag.
    expect(req.request.body).toEqual({ betrag: 3800 });
    req.flush({ ...LARA, monthlyIncome: 3800 });

    // Ohne das State-Update truege der Auth-State weiterhin monthlyIncome: null, waehrend
    // das Backend laengst ein Einkommen kennt.
    expect(emitted?.monthlyIncome).toBe(3800);
    expect(service.currentUser()?.monthlyIncome).toBe(3800);
  });

  it('changePassword puts the German field names the backend expects (FE-SET-02)', () => {
    let completed = false;
    service.changePassword('altesPasswort', 'neuesPasswort123').subscribe(() => (completed = true));

    const req = httpMock.expectOne('/api/users/me/password');
    expect(req.request.method).toBe('PUT');
    // Der Feldname ist Teil des Vertrags: das Backend bindet auf ChangePasswordRequest.
    expect(req.request.body).toEqual({
      aktuellesPasswort: 'altesPasswort',
      neuesPasswort: 'neuesPasswort123',
    });
    req.flush(null);

    expect(completed).toBe(true);
  });
});
