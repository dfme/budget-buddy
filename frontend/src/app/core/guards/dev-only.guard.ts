import { inject, InjectionToken, isDevMode } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';

/**
 * Ob die App im Entwicklungs-Build läuft. Als Token gekapselt, damit der Wert im Test
 * deterministisch überschrieben werden kann (statt `isDevMode()` zu mocken). Der
 * Default-Faktor liefert im echten Build das korrekte `isDevMode()`.
 */
export const IS_DEV_MODE = new InjectionToken<boolean>('IS_DEV_MODE', {
  providedIn: 'root',
  factory: () => isDevMode(),
});

/**
 * Lässt eine Route nur im Entwicklungs-Build zu (FE-UI-03, `/styleguide`).
 *
 * <p>Im Produktions-Build wird auf `/dashboard` umgeleitet, damit interne Dev-Seiten wie der
 * Styleguide nicht Teil der Nutzer-App sind. Die Seite bleibt bewusst nur per Direkt-URL
 * erreichbar (nicht in der Navigation verlinkt).
 */
export const devOnlyGuard: CanActivateFn = () => {
  const router = inject(Router);
  return inject(IS_DEV_MODE) ? true : router.createUrlTree(['/dashboard']);
};
