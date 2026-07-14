import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map } from 'rxjs';

import { AuthService } from '../../auth/auth.service';

/**
 * Schützt Routes vor nicht eingeloggten Nutzern (US-01, FE-AUTH-04).
 *
 * <p>Ist der State bereits authentifiziert, wird der Zugriff sofort gewährt. Sonst
 * versucht der Guard, den State über {@link AuthService.loadCurrentUser} (`GET
 * /users/me`) wiederherzustellen — so erreicht ein eingeloggter Nutzer geschützte
 * Routes auch nach einem Reload ohne erneuten Login. Schlägt das fehl (kein/abgelaufenes
 * Cookie), wird auf `/login` umgeleitet.
 */
export const authGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  if (auth.isAuthenticated()) {
    return true;
  }

  return auth
    .loadCurrentUser()
    .pipe(map((user) => (user !== null ? true : router.createUrlTree(['/login']))));
};
