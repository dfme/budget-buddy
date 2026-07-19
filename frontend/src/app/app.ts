import { ChangeDetectionStrategy, Component, inject, signal } from '@angular/core';
import { Router, RouterLink, RouterOutlet } from '@angular/router';

import { AuthService } from './auth/auth.service';

/**
 * App-Shell mit Header/Nav und Router-Outlet. Der Header trägt den Logout-Button
 * (US-01, FE-AUTH-05), der nur im eingeloggten Zustand sichtbar ist.
 */
@Component({
  selector: 'app-root',
  imports: [RouterOutlet, RouterLink],
  templateUrl: './app.html',
  styleUrl: './app.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class App {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);

  protected readonly title = signal('BudgetBuddy');

  /** Steuert die Sichtbarkeit des Logout-Buttons. */
  protected readonly isAuthenticated = this.auth.isAuthenticated;

  /**
   * Loggt aus (`POST /auth/logout`), leert den Auth-State und leitet auf `/login`.
   * Auch bei einem fehlgeschlagenen Backend-Call wird der lokale State geleert und
   * umgeleitet — so bleibt der Nutzer nie in einem scheinbar eingeloggten Zustand.
   */
  logout(): void {
    this.auth.logout().subscribe({
      next: () => this.router.navigate(['/login']),
      error: () => {
        this.auth.resetState();
        this.router.navigate(['/login']);
      },
    });
  }
}
