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

  it('register posts credentials and sets the auth state', () => {
    service.register('lara@example.ch', 'supersecret').subscribe();

    const req = httpMock.expectOne('/auth/register');
    expect(req.request.method).toBe('POST');
    expect(req.request.body).toEqual({
      email: 'lara@example.ch',
      password: 'supersecret',
    });
    req.flush(LARA);

    expect(service.currentUser()).toEqual(LARA);
    expect(service.isAuthenticated()).toBe(true);
  });

  it('login posts credentials and sets the auth state', () => {
    service.login('lara@example.ch', 'supersecret').subscribe();

    const req = httpMock.expectOne('/auth/login');
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
    httpMock.expectOne('/auth/login').flush(LARA);
    expect(service.isAuthenticated()).toBe(true);

    service.logout().subscribe();
    const req = httpMock.expectOne('/auth/logout');
    expect(req.request.method).toBe('POST');
    req.flush(null);

    expect(service.currentUser()).toBeNull();
    expect(service.isAuthenticated()).toBe(false);
  });

  it('loadCurrentUser restores the state from GET /users/me', () => {
    let emitted: User | null | undefined;
    service.loadCurrentUser().subscribe((user) => (emitted = user));

    const req = httpMock.expectOne('/users/me');
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

    httpMock.expectOne('/users/me').flush(null, { status: 401, statusText: 'Unauthorized' });

    expect(errored).toBe(false);
    expect(emitted).toBeNull();
    expect(service.currentUser()).toBeNull();
    expect(service.isAuthenticated()).toBe(false);
  });
});
