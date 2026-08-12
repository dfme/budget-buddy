import { inject } from '@angular/core';
import { CanActivateFn, Router } from '@angular/router';
import { map } from 'rxjs';

import { AuthService } from '../../auth/auth.service';

/**
 * Erzwingt den Fixkosten-Wizard, solange das Onboarding nicht abgeschlossen ist
 * (US-03, FE-FC-02).
 *
 * <p>Der Status kommt aus `onboardingCompleted` des Profils, das
 * {@link AuthService.ensureCurrentUser} aus dem State liefert oder per `GET /users/me`
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
 * Die Route bleibt umgekehrt auch nach abgeschlossenem Onboarding per Direkt-Link
 * erreichbar — solange die Fixkosten-Liste (FE-FC-03, #26) fehlt, ist der Wizard der
 * einzige Weg, später eine Position nachzutragen.
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
