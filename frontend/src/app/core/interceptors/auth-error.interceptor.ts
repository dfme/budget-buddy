import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';

import { AuthService } from '../../auth/auth.service';

/**
 * Endpoints, deren 401 NICHT zu einem Redirect führt:
 * - `/auth/login`, `/auth/register`: Ein 401 bedeutet hier "falsche Credentials"
 *   und wird von den Formular-Komponenten selbst behandelt.
 * - `/users/me`: Bootstrap-Call von {@link AuthService.loadCurrentUser} und dem
 *   `authGuard`. Ein Redirect hier würde eine Doppel-Navigation/Loop auslösen.
 */
const AUTH_BOOTSTRAP_PATHS = ['/auth/login', '/auth/register', '/users/me'];

/**
 * Globales 401-Handling (US-01, FE-AUTH-04). Ein `401` auf einem geschützten Call
 * bedeutet ein abgelaufenes oder fehlendes Cookie: der Auth-State wird zurückgesetzt
 * und der Nutzer auf `/login` umgeleitet. Der Fehler wird trotzdem weiter-propagiert,
 * damit der auslösende Call sein eigenes Error-Handling behalten kann.
 *
 * <p>Auth-/Bootstrap-Endpoints (siehe {@link AUTH_BOOTSTRAP_PATHS}) sind bewusst
 * ausgenommen, um Loops und das Überschreiben erwarteter 401 zu vermeiden.
 */
export const authErrorInterceptor: HttpInterceptorFn = (req, next) => {
  const auth = inject(AuthService);
  const router = inject(Router);

  return next(req).pipe(
    catchError((error: unknown) => {
      if (error instanceof HttpErrorResponse && error.status === 401 && !isBootstrap(req.url)) {
        auth.resetState();
        router.navigate(['/login']);
      }
      return throwError(() => error);
    }),
  );
};

function isBootstrap(url: string): boolean {
  return AUTH_BOOTSTRAP_PATHS.some((path) => url.includes(path));
}
