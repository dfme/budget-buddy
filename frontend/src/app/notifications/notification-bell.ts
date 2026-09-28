import { DatePipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, ElementRef, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { NavigationEnd, Router } from '@angular/router';
import { filter } from 'rxjs';

import {
  IMPORT_NOTIFICATION_TYPES,
  NotificationResponse,
  RECURRING_EXPENSE_DETECTED,
} from './notification.model';
import { NotificationService } from './notification.service';

let nextId = 0;

/**
 * Glocke mit Ungelesen-Badge und Dropdown-Liste in der App-Shell (FE-NOTIF-01), darin
 * «Alle als gelesen» (FE-NOTIF-04).
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
   * {@link RECURRING_EXPENSE_DETECTED}, führt der Klick zusätzlich in die Abo-Übersicht — seit
   * FE-FC-05 der Abschnitt «Erkannte Abos» auf `/budget` (FE-FC-09; bis dahin `/ausgaben`) —
   * und schliesst das Dropdown: dort
   * steht der Eintrag, und das ist die Antwort auf die Meldung.
   * Navigiert wird sofort, der Gelesen-Call läuft parallel im Hintergrund weiter: Die Übersicht
   * leitet ihr «Neu»-Label aus genau dieser Benachrichtigung ab (BE-REC-02) — würde erst auf den
   * Abschluss des Gelesen-Calls gewartet, wäre der Eintrag beim Eintreffen in der Übersicht
   * bereits als gelesen markiert und das Label liefe leer, obwohl US-08 AC2 es genau für diesen
   * Weg verlangt. Die Glocke selbst markiert trotzdem als gelesen — der Klick ist die
   * Kenntnisnahme der Benachrichtigung, nicht der Grund, warum der Eintrag in der Übersicht sein
   * «Neu» behält.
   *
   * <p><strong>Import-Benachrichtigung (FE-NOTIF-05).</strong> Bei {@link IMPORT_NOTIFICATION_TYPES}
   * führt der Klick nach `/import?job=<referenceId>`: Die Import-Seite nimmt den Job über ihren
   * bestehenden Poll-Pfad auf und zeigt Erfolgsmeldung samt Buchungen — oder die Fehlermeldung
   * des Fehlschlags. Die Job-ID ist der `referenceId` aus BE-PDF-15; fehlt sie wider Erwarten,
   * bleibt die Import-Seite trotzdem das Ziel, nur ohne Parameter. Ob der Job dem Nutzer gehört,
   * entscheidet das Backend (404), nicht die Glocke. Gelesen-Call wie beim Abo-Sprung parallel.
   */
  protected select(notification: NotificationResponse): void {
    if (notification.type === RECURRING_EXPENSE_DETECTED) {
      this.close();
      void this.router.navigate(['/budget']);
    } else if (IMPORT_NOTIFICATION_TYPES.has(notification.type)) {
      this.close();
      void this.router.navigate(['/import'], {
        queryParams: notification.referenceId === null ? {} : { job: notification.referenceId },
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

  /**
   * Markiert alle Benachrichtigungen als gelesen (FE-NOTIF-04, #336) — eine Aktion statt N
   * Einzelklicks, wenn mehrere Importe je ein Bündel hinterlassen haben. Der Button ist nur
   * gerendert, solange etwas ungelesen ist; der Guard hier fängt den Klick zwischen zwei
   * Change-Detection-Läufen ab.
   *
   * <p>Das Dropdown bleibt offen, wie beim Einzelklick — der Wechsel auf «gelesen» soll sichtbar
   * sein. Navigiert wird nicht: anders als bei {@link select} gibt es kein einzelnes Ziel.
   *
   * <p>Nimmt als Nebeneffekt jedes «Neu»-Label auf `/budget`: das hängt am selben Gelesen-Zustand
   * (BE-REC-02). Das ist beabsichtigt und in US-08 AC2 festgehalten.
   *
   * <p>Ein Fehler bleibt bewusst still, wie bei {@link select}: das Badge zeigt dann weiterhin
   * den alten Stand, ein erneuter Klick versucht es wieder.
   */
  protected markAllAsRead(): void {
    if (this.unreadCount() === 0) {
      return;
    }
    this.notificationService.markAllAsRead().subscribe({
      error: (_err: HttpErrorResponse) => {
        // Siehe Methoden-Doc: bewusst ohne Meldung.
      },
    });
  }
}
