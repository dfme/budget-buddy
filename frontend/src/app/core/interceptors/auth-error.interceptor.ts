import { HttpErrorResponse, HttpInterceptorFn } from '@angular/common/http';
import { inject } from '@angular/core';
import { Router } from '@angular/router';
import { catchError, throwError } from 'rxjs';

import { AuthService } from '../../auth/auth.service';

/**
 * Endpoints, deren 401 NICHT zu einem Redirect führt:
 * - `/api/auth/login`, `/api/auth/register`: Ein 401 bedeutet hier "falsche Credentials"
 *   und wird von den Formular-Komponenten selbst behandelt.
 * - `/api/users/me`: Bootstrap-Call von {@link AuthService.loadCurrentUser}, dem
 *   `authGuard` und dem `onboardingGuard`. Ein Redirect hier würde eine
 *   Doppel-Navigation/Loop auslösen.
 *
 * <p>Verglichen wird der <strong>ganze</strong> Pfad, nicht sein Anfang: unter `/api/users/me`
 * hängen inzwischen normale geschützte Aufrufe (`POST /api/users/me/onboarding-complete`,
 * `PUT /api/users/me/income`), und die sind keine Bootstrap-Calls. Ein Teilstring-Vergleich
 * nähme sie mit aus und liesse den Nutzer bei abgelaufenem Cookie mit einer irreführenden
 * Fehlermeldung im Formular sitzen, statt ihn zum Login zu schicken.
 */
const AUTH_BOOTSTRAP_PATHS = ['/api/auth/login', '/api/auth/register', '/api/users/me'];

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
  // Query-String abschneiden, damit der exakte Vergleich nicht daran scheitert.
  const path = url.split('?')[0];
  return AUTH_BOOTSTRAP_PATHS.includes(path);
}
