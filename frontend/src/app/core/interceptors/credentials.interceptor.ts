import { HttpInterceptorFn } from '@angular/common/http';

/**
 * Sets `withCredentials: true` on every outgoing request so the httpOnly auth
 * cookie (JWT, SameSite=Strict) is sent automatically (ADR-7).
 *
 * Angular's `provideHttpClient` has no global `withCredentials` option, so a
 * functional interceptor is the standard way to apply it to all requests.
 * This is NOT a Bearer-token interceptor — the cookie carries the token.
 */
export const credentialsInterceptor: HttpInterceptorFn = (req, next) =>
  next(req.clone({ withCredentials: true }));
