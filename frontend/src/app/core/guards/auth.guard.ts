import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map } from 'rxjs';

import { AuthService } from '../../auth/auth.service';

/**
 * Schützt Routes vor nicht eingeloggten Nutzern (US-01, FE-AUTH-04).
 *
 * <p>Das Profil kommt über {@link AuthService.ensureCurrentUser}: liegt es im State, wird
 * der Zugriff ohne Request gewährt, sonst wird es per `GET /users/me` nachgeladen — so
 * erreicht ein eingeloggter Nutzer geschützte Routes auch nach einem Reload ohne erneuten
 * Login. Schlägt das fehl (kein/abgelaufenes Cookie), wird auf `/login` umgeleitet.
 */
export const authGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  return auth
    .ensureCurrentUser()
    .pipe(map((user) => (user !== null ? true : router.createUrlTree(['/login']))));
};
