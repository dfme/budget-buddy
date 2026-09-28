import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map } from 'rxjs';

import { AuthService } from '../../auth/auth.service';

/**
 * Erzwingt den Fixkosten-Wizard, solange das Onboarding nicht abgeschlossen ist
 * (US-03, FE-FC-02).
 *
 * <p>Der Status kommt aus `onboardingCompleted` des Profils, das
 * {@link AuthService.ensureCurrentUser} aus dem State liefert oder per `GET /api/users/me`
 * nachlädt. Ist er `false`, wird auf `/onboarding` umgeleitet — der Safe-to-Spend-Betrag
 * ist ohne Fixkosten wertlos, und Lara soll die Zahl nicht sehen, bevor sie stimmt.
 *
 * <p>Für anonyme Nutzer gibt dieser Guard `true` zurück, statt selbst umzuleiten: wer nicht
 * eingeloggt ist, ist die Entscheidung des {@link authGuard}, der am selben `canActivate`-Array
 * hängt und `/login` liefert. Verlassen wird sich dabei nicht auf eine Reihenfolge — Angular
 * führt die Guards eines Arrays <em>nebenläufig</em> aus (belegt in `onboarding.guard.spec.ts`
 * durch den Navigationstest ohne Login). Weil dieser Guard im Anonymfall gar keine eigene
 * Meinung äussert, bleibt der `UrlTree` des `authGuard` der einzige und gewinnt unabhängig
 * davon, wer zuerst fertig ist.
 *
 * <p>Bewusst <em>nicht</em> an `/onboarding` selbst gehängt: das wäre eine Endlosschleife.
 * Die Gegenrichtung — ein onboardeter User ruft den Wizard per Direkt-Link auf — deckt
 * {@link onboardingPendingGuard} ab.
 */
export const onboardingGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  return auth
    .ensureCurrentUser()
    .pipe(
      map((user) =>
        user === null || user.onboardingCompleted ? true : router.createUrlTree(['/onboarding']),
      ),
    );
};

/**
 * Sperrt den Wizard, sobald das Onboarding abgeschlossen ist (FE-FC-12, #375) — die Umkehrung
 * von {@link onboardingGuard}, darum an `/onboarding` statt an den übrigen Routes.
 *
 * <p>Ein onboardeter User wird auf `/budget` umgeleitet. Nachträgliche Änderungen an den
 * Fixkosten laufen dort über `FixedCostList`, die seit FE-FC-10 (#372) auch neue Positionen im
 * Overlay-Dialog anlegt. Ein zweiter Weg über den Wizard wäre nicht nur doppelt, sondern würde
 * am Ende auch `finishOnboarding()` erneut anbieten, das für diesen User nicht mehr gedacht ist.
 *
 * <p>Anonyme Nutzer und solche mit offenem Onboarding lässt der Guard durch. Den Anonymfall
 * entscheidet wie beim {@link onboardingGuard} der {@link authGuard} im selben
 * `canActivate`-Array, ohne dass es auf die Ausführungsreihenfolge ankäme.
 */
export const onboardingPendingGuard: CanActivateFn = () => {
  const auth = inject(AuthService);
  const router = inject(Router);

  return auth
    .ensureCurrentUser()
    .pipe(map((user) => (user?.onboardingCompleted ? router.createUrlTree(['/budget']) : true)));
};
