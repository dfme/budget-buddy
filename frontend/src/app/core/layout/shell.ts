import { NgTemplateOutlet } from '@angular/common';
import {
  ChangeDetectionStrategy,
  Component,
  ElementRef,
  computed,
  inject,
  signal,
  viewChild,
} from '@angular/core';
import { Router, RouterLink, RouterLinkActive } from '@angular/router';

import { AuthService } from '../../auth/auth.service';

/** Ein Ziel der Hauptnavigation. */
interface NavItem {
  /** Router-Pfad, absolut. */
  readonly path: string;
  /** Sichtbares Label in Tab-Bar und Sidebar. */
  readonly label: string;
  /** Dekoratives Zeichen; im Template `aria-hidden`, trägt keine Information. */
  readonly icon: string;
}

/**
 * App-Shell der Design-Variante A «Klarheit» (FE-UI-04, ADR-11).
 *
 * <p>Ein Markup, zwei Erscheinungsformen: auf Mobile eine Tab-Bar unten in der
 * Daumenzone plus Topbar mit Wortmarke, ab 900px eine vertikale Sidebar links,
 * die die Topbar ersetzt. Umgeschaltet wird ausschliesslich per Breakpoint im
 * SCSS — die Navigation bleibt im DOM vor dem Inhalt und damit für Screenreader
 * und Tastatur in der richtigen Reihenfolge.
 *
 * <p>Der Inhalt kommt per `<ng-content>` herein; den Router-Outlet hält das
 * Root-Component. Solange niemand eingeloggt ist, bleiben Topbar und Nav
 * ausgeblendet — Login und Registrierung sind vollflächige Screens.
 */
@Component({
  selector: 'app-shell',
  imports: [NgTemplateOutlet, RouterLink, RouterLinkActive],
  templateUrl: './shell.html',
  styleUrl: './shell.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    // Beide Listener hängen am Document, weil Klick und Escape auch ausserhalb
    // der Shell auftreten können, während das Konto-Popover offen ist.
    '(document:click)': 'onDocumentClick($event)',
    '(document:keydown.escape)': 'closeAccountMenu()',
  },
})
export class Shell {
  private readonly auth = inject(AuthService);
  private readonly router = inject(Router);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  private readonly avatarButton = viewChild<ElementRef<HTMLButtonElement>>('avatarButton');

  /** Steuert, ob Topbar, Navigation und Konto-Block überhaupt gerendert werden. */
  protected readonly isAuthenticated = this.auth.isAuthenticated;

  /** E-Mail des eingeloggten Users; leer, solange niemand eingeloggt ist. */
  protected readonly email = computed(() => this.auth.currentUser()?.email ?? '');

  /**
   * Initialen für den Avatar. Das Backend liefert keinen Namen (US-14 offen),
   * deshalb aus dem Local-Part der E-Mail: `lara.meier@…` → «LM», `lara@…` → «LA».
   */
  protected readonly initials = computed(() => initialsFromEmail(this.email()));

  /** Offen/zu des Konto-Popovers in der Topbar (nur Mobile sichtbar). */
  protected readonly accountMenuOpen = signal(false);

  /** Die Hauptziele. */
  protected readonly navItems: readonly NavItem[] = [
    { path: '/dashboard', label: 'Übersicht', icon: '◎' },
    { path: '/categories', label: 'Transaktionen', icon: '≡' },
    { path: '/import', label: 'Import', icon: '↑' },
    { path: '/fixkosten', label: 'Fixkosten', icon: '▦' },
    { path: '/einstellungen', label: 'Einstellungen', icon: '⚙' },
  ];

  protected toggleAccountMenu(): void {
    this.accountMenuOpen.update((open) => !open);
  }

  /**
   * Schliesst das Popover und gibt den Fokus an den Avatar-Button zurück —
   * sonst landet die Tastaturnavigation nach dem Schliessen am Dokumentanfang.
   */
  protected closeAccountMenu(): void {
    if (!this.accountMenuOpen()) {
      return;
    }
    this.accountMenuOpen.set(false);
    this.avatarButton()?.nativeElement.focus();
  }

  protected onDocumentClick(event: MouseEvent): void {
    if (!this.accountMenuOpen()) {
      return;
    }
    if (!this.host.nativeElement.contains(event.target as Node)) {
      this.accountMenuOpen.set(false);
    }
  }

  /**
   * Loggt aus (`POST /api/auth/logout`), leert den Auth-State und leitet auf `/login`.
   * Auch bei einem fehlgeschlagenen Backend-Call wird der lokale State geleert und
   * umgeleitet — so bleibt der Nutzer nie in einem scheinbar eingeloggten Zustand.
   */
  protected logout(): void {
    this.accountMenuOpen.set(false);
    this.auth.logout().subscribe({
      next: () => this.router.navigate(['/login']),
      error: () => {
        this.auth.resetState();
        this.router.navigate(['/login']);
      },
    });
  }
}

/**
 * Zwei Initialen aus einer E-Mail-Adresse. Mehrteilige Local-Parts liefern die
 * Anfangsbuchstaben der ersten beiden Teile (`lara.meier@…` → «LM»), einteilige
 * die ersten beiden Buchstaben (`lara@…` → «LA»). Trennzeichen sind `.`, `_`,
 * `-` und `+`. Ohne E-Mail bleibt der Avatar leer.
 */
function initialsFromEmail(email: string): string {
  const localPart = email.split('@')[0] ?? '';
  const parts = localPart.split(/[._+-]+/).filter((part) => part.length > 0);

  if (parts.length === 0) {
    return '';
  }
  const raw = parts.length >= 2 ? parts[0][0] + parts[1][0] : parts[0].slice(0, 2);
  return raw.toUpperCase();
}
