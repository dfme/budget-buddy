import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, ElementRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router } from '@angular/router';
import { filter } from 'rxjs';

import { NotificationResponse, RECURRING_EXPENSE_DETECTED } from './notification.model';
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
    this.reload();

    this.router.events
      .pipe(
        filter((event): event is NavigationEnd => event instanceof NavigationEnd),
        takeUntilDestroyed(),
      )
      .subscribe(() => this.reload());
  }

  /**
   * Lädt neu (Mount/Login und jede Navigation, siehe Klassen-Doc). Ein Fehler bleibt bewusst
   * still: die Glocke zeigt dann weiterhin den zuletzt bekannten Stand statt einer Fehlermeldung
   * für eine sekundäre Funktion — dieselbe Abwägung wie bei `Dashboard.loadUncertainCount`.
   */
  private reload(): void {
    this.notificationService.load().subscribe({
      error: (_err: HttpErrorResponse) => {
        // Siehe Methoden-Doc: bewusst ohne Meldung.
      },
    });
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
   *
   * <p>Ein Fehler bleibt bewusst still: die Benachrichtigung zeigt dann weiterhin als ungelesen
   * — der sichere Fallback, ein erneuter Klick versucht es wieder.
   *
   * <p><strong>Abo-Benachrichtigung (FE-REC-01).</strong> Trägt sie den Typ
   * {@link RECURRING_EXPENSE_DETECTED}, führt der Klick zusätzlich in die Abo-Übersicht und
   * schliesst das Dropdown — dort steht der Eintrag, und das ist die Antwort auf die Meldung.
   * Navigiert wird sofort, der Gelesen-Call läuft parallel im Hintergrund weiter: Die Übersicht
   * leitet ihr «Neu»-Label aus genau dieser Benachrichtigung ab (BE-REC-02) — würde erst auf den
   * Abschluss des Gelesen-Calls gewartet, wäre der Eintrag beim Eintreffen in der Übersicht
   * bereits als gelesen markiert und das Label liefe leer, obwohl US-08 AC2 es genau für diesen
   * Weg verlangt. Die Glocke selbst markiert trotzdem als gelesen — der Klick ist die
   * Kenntnisnahme der Benachrichtigung, nicht der Grund, warum der Eintrag in der Übersicht sein
   * «Neu» behält.
   *
   * <p><strong>Der `ref`-Parameter (FE-NOTIF-03).</strong> Die Übersicht zeigt nur
   * `DETECTED`-Einträge — wurde der Eintrag zwischenzeitlich per «Kein Abo» verneint, führte der
   * Klick auf eine Seite, auf der er fehlt, ohne jeden Hinweis warum. Mitgegeben wird deshalb die
   * `referenceId`, die auf die Zeile in `recurring_expenses` zeigt; die Übersicht erkennt daran,
   * dass der gemeinte Eintrag nicht (mehr) in ihrer Liste steht, und sagt es.
   *
   * <p>Nicht über `notification.read` entschieden: Der Gelesen-Status ist kein Stellvertreter für
   * `DISMISSED`. Diese Methode markiert jede angeklickte Benachrichtigung als gelesen, ein
   * intaktes Abo wäre nach dem ersten Klick also ebenfalls «gelesen» — ein Navigationsverbot
   * daran festzumachen bräche den Normalfall.
   */
  protected select(notification: NotificationResponse): void {
    if (notification.type === RECURRING_EXPENSE_DETECTED) {
      this.close();
      // Ohne `referenceId` gibt es nichts zu referenzieren — dann bleibt es beim blossen Ziel,
      // statt `ref=null` in die URL zu schreiben.
      void this.router.navigate(['/abos'], {
        queryParams: notification.referenceId === null ? {} : { ref: notification.referenceId },
      });
    }

    if (notification.read) {
      return;
    }
    this.notificationService.markAsRead(notification.id).subscribe({
      error: (_err: HttpErrorResponse) => {
        // Siehe Methoden-Doc: bewusst ohne Meldung.
      },
    });
  }
}
