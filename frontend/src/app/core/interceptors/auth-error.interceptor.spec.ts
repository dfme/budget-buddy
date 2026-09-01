import { HttpClient, provideHttpClient, withInterceptors } from '@angular/common/http';
import { HttpTestingController, provideHttpClientTesting } from '@angular/common/http/testing';
import { TestBed } from '@angular/core/testing';
import { Router, provideRouter } from '@angular/router';

import { AuthService } from '../../auth/auth.service';
import { authErrorInterceptor } from './auth-error.interceptor';

describe('authErrorInterceptor', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;
  let auth: AuthService;
  let navigate: ReturnType<typeof vi.spyOn>;
  let resetState: ReturnType<typeof vi.spyOn>;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([authErrorInterceptor])),
        provideHttpClientTesting(),
        provideRouter([]),
      ],
    });
    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
    auth = TestBed.inject(AuthService);
    navigate = vi.spyOn(TestBed.inject(Router), 'navigate').mockResolvedValue(true);
    resetState = vi.spyOn(auth, 'resetState');
  });

  afterEach(() => httpMock.verify());

  it('resets auth state and redirects to /login on 401 for a protected call', () => {
    let errored = false;
    http.get('/api/transactions').subscribe({ error: () => (errored = true) });

    httpMock
      .expectOne('/api/transactions')
      .flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(resetState).toHaveBeenCalledTimes(1);
    expect(navigate).toHaveBeenCalledWith(['/login']);
    // Fehler wird trotzdem weiter-propagiert
    expect(errored).toBe(true);
  });

  it('does not redirect on 401 from /api/auth/login (expected bad-credentials response)', () => {
    http.post('/api/auth/login', {}).subscribe({ error: () => {} });

    httpMock.expectOne('/api/auth/login').flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(resetState).not.toHaveBeenCalled();
    expect(navigate).not.toHaveBeenCalled();
  });

  it('does not redirect on 401 from /api/users/me (bootstrap call)', () => {
    http.get('/api/users/me').subscribe({ error: () => {} });

    httpMock.expectOne('/api/users/me').flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(resetState).not.toHaveBeenCalled();
    expect(navigate).not.toHaveBeenCalled();
  });

  it('redirects on 401 from /api/users/me/onboarding-complete despite the shared prefix', () => {
    // Der Aufruf liegt unter /api/users/me, ist aber kein Bootstrap-Call: ein 401 heisst hier
    // «Cookie abgelaufen». Mit einem Teilstring-Vergleich fiele er unter die Ausnahme und
    // der Nutzer bliebe mit «bitte spaeter erneut versuchen» im Wizard sitzen.
    http.post('/api/users/me/onboarding-complete', {}).subscribe({ error: () => {} });

    httpMock
      .expectOne('/api/users/me/onboarding-complete')
      .flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(resetState).toHaveBeenCalledTimes(1);
    expect(navigate).toHaveBeenCalledWith(['/login']);
  });

  it('does not redirect on 401 from /api/users/me with a query string', () => {
    // Der exakte Vergleich darf nicht an einem Query-String scheitern.
    http.get('/api/users/me', { params: { refresh: 'true' } }).subscribe({ error: () => {} });

    httpMock
      .expectOne((req) => req.url === '/api/users/me')
      .flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(resetState).not.toHaveBeenCalled();
    expect(navigate).not.toHaveBeenCalled();
  });

  it('passes non-401 errors through without redirecting', () => {
    let status: number | undefined;
    http.get('/api/transactions').subscribe({
      error: (err) => (status = err.status),
    });

    httpMock
      .expectOne('/api/transactions')
      .flush(null, { status: 500, statusText: 'Server Error' });

    expect(status).toBe(500);
    expect(resetState).not.toHaveBeenCalled();
    expect(navigate).not.toHaveBeenCalled();
  });
});
