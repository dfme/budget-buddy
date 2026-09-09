import { DatePipe } from '@angular/common';
import { ChangeDetectionStrategy, Component, ElementRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router } from '@angular/router';
import { filter } from 'rxjs';

import { NotificationResponse } from './notification.model';
import { NotificationService } from './notification.service';

let nextId = 0;

/**
 * Glocke mit Ungelesen-Badge und Dropdown-Liste in der App-Shell (FE-NOTIF-01).
 *
 * <p>Wird zweimal gerendert — mobile Topbar und Desktop-Sidebar-Konto-Block —, analog zu
 * Avatar/Initialen in `Shell`: beide Stellen sind laut `shell.scss` nie gleichzeitig sichtbar.
 * Deshalb ein instanzweiter, eindeutiger `listId` statt eines festen `id`-Attributs — sonst gäbe
 * es doppelte IDs im DOM.
 *
 * <p>Lädt einmal im Konstruktor und bei jeder Navigation ({@code NavigationEnd}) neu — deckt
 * „Laden beim Login/Navigation, kein Polling" ab. {@link NotificationService.load} bündelt
 * gleichzeitige Aufrufer beider Instanzen auf einen Request.
 *
 * <p>Popover-Verhalten (Klick aussen/Escape schliesst) analog zum Konto-Popover in `Shell`;
 * Disclosure, kein WAI-ARIA-Menu — dieselbe Begründung wie dort.
 */
@Component({
  selector: 'app-notification-bell',
  imports: [DatePipe],
  templateUrl: './notification-bell.html',
  styleUrl: './notification-bell.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  host: {
    '(document:click)': 'onDocumentClick($event)',
    '(document:keydown.escape)': 'close()',
  },
})
export class NotificationBell {
  private readonly notificationService = inject(NotificationService);
  private readonly router = inject(Router);
  private readonly host = inject<ElementRef<HTMLElement>>(ElementRef);

  protected readonly listId = `notification-list-${nextId++}`;

  protected readonly notifications = this.notificationService.notifications;
  protected readonly unreadCount = this.notificationService.unreadCount;

  /** Offen/zu des Dropdowns. */
  protected readonly open = signal(false);

  constructor() {
    this.notificationService.load().subscribe();

    this.router.events
      .pipe(
        filter((event): event is NavigationEnd => event instanceof NavigationEnd),
        takeUntilDestroyed(),
      )
      .subscribe(() => this.notificationService.load().subscribe());
  }

  protected toggle(): void {
    this.open.update((isOpen) => !isOpen);
  }

  protected close(): void {
    this.open.set(false);
  }

  protected onDocumentClick(event: MouseEvent): void {
    if (!this.open()) {
      return;
    }
    if (!this.host.nativeElement.contains(event.target as Node)) {
      this.open.set(false);
    }
  }

  /**
   * Markiert eine ungelesene Benachrichtigung als gelesen. Bereits gelesene lösen keinen
   * weiteren Call aus — das Backend ist zwar idempotent, ein Call ohne Wirkung ist trotzdem
   * unnötig. Das Dropdown bleibt bewusst offen, damit der Wechsel auf «gelesen» sichtbar wird.
   */
  protected select(notification: NotificationResponse): void {
    if (!notification.read) {
      this.notificationService.markAsRead(notification.id).subscribe();
    }
  }
}
