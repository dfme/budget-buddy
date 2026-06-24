import { TestBed } from '@angular/core/testing';
import {
  HttpClient,
  provideHttpClient,
  withInterceptors,
} from '@angular/common/http';
import {
  HttpTestingController,
  provideHttpClientTesting,
} from '@angular/common/http/testing';

import { credentialsInterceptor } from './credentials.interceptor';

describe('credentialsInterceptor', () => {
  let http: HttpClient;
  let httpMock: HttpTestingController;

  beforeEach(() => {
    TestBed.configureTestingModule({
      providers: [
        provideHttpClient(withInterceptors([credentialsInterceptor])),
        provideHttpClientTesting(),
      ],
    });
    http = TestBed.inject(HttpClient);
    httpMock = TestBed.inject(HttpTestingController);
  });

  afterEach(() => httpMock.verify());

  it('sets withCredentials=true on outgoing requests', () => {
    http.get('/api/ping').subscribe();

    const req = httpMock.expectOne('/api/ping');
    expect(req.request.withCredentials).toBe(true);
    req.flush({});
  });

  it('sets withCredentials=true on POST requests too', () => {
    http.post('/api/login', { user: 'lara' }).subscribe();

    const req = httpMock.expectOne('/api/login');
    expect(req.request.withCredentials).toBe(true);
    req.flush({});
  });
});
