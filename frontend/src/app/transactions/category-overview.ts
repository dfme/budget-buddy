import { CurrencyPipe, DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { Subscription } from 'rxjs';

import { CategorySummary } from './category-summary.model';
import { TransactionSummaryService } from './transaction-summary.service';

/**
 * Kategorie-Übersicht (FE-CAT-01, US-05).
 *
 * <p>Zeigt pro Kategorie CHF-Summe, Anzahl und Prozentanteil für den gewählten
 * Monat. Ein Prev/Next-Selector navigiert zwischen Monaten; jeder Wechsel lädt neu.
 * Ist der Monat leer, erscheint ein Leerzustand statt einer leeren Tabelle.
 *
 * <p>OnPush + Signals wie im übrigen Frontend; der HTTP-Zugriff liegt im
 * zustandslosen {@link TransactionSummaryService}.
 */
@Component({
  selector: 'app-category-overview',
  imports: [CurrencyPipe, DecimalPipe],
  templateUrl: './category-overview.html',
  styleUrl: './category-overview.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CategoryOverview {
  private readonly summaryService = inject(TransactionSummaryService);

  /** Aktuell angezeigter Monat im Format `YYYY-MM`. */
  readonly month = signal(CategoryOverview.currentMonth());

  /** Geladenes Summary oder `null`, solange nichts geladen ist. */
  readonly summary = signal<CategorySummary | null>(null);

  /** `true`, solange ein Request läuft. */
  readonly loading = signal(false);

  /** Fehlermeldung oder `null`, wenn kein Fehler vorliegt. */
  readonly errorMessage = signal<string | null>(null);

  /** Menschlich lesbares Monatslabel, z. B. `"Juli 2026"`. */
  readonly monthLabel = computed(() => CategoryOverview.formatMonth(this.month()));

  /** `true`, wenn geladen wurde und der Monat keine Ausgaben enthält. */
  readonly isEmpty = computed(() => {
    const current = this.summary();
    return current !== null && current.categories.length === 0;
  });

  /** `true`, wenn der angezeigte Monat der aktuelle Monat ist — sperrt "›". */
  readonly isCurrentMonth = computed(() => this.month() >= CategoryOverview.currentMonth());

  /** Subscription des zuletzt gestarteten Requests, um ihn bei Monatswechsel zu canceln. */
  private pendingRequest: Subscription | undefined;

  constructor() {
    this.load();
  }

  /** Einen Monat zurück. */
  previousMonth(): void {
    this.month.set(CategoryOverview.shiftMonth(this.month(), -1));
    this.load();
  }

  /** Einen Monat vor. */
  nextMonth(): void {
    this.month.set(CategoryOverview.shiftMonth(this.month(), 1));
    this.load();
  }

  private load(): void {
    // Einen noch laufenden Request canceln, bevor ein neuer startet — sonst kann bei
    // schneller Monat-Navigation die spätere Antwort von der früheren überschrieben
    // werden (Race Condition).
    this.pendingRequest?.unsubscribe();
    this.loading.set(true);
    this.errorMessage.set(null);

    this.pendingRequest = this.summaryService.getSummary(this.month()).subscribe({
      next: (summary) => {
        this.summary.set(summary);
        this.loading.set(false);
      },
      error: (_err: HttpErrorResponse) => {
        this.summary.set(null);
        this.errorMessage.set('Die Kategorie-Übersicht konnte nicht geladen werden.');
        this.loading.set(false);
      },
    });
  }

  /** Aktueller Monat als `YYYY-MM`. */
  private static currentMonth(): string {
    const now = new Date();
    return CategoryOverview.toMonthString(now.getFullYear(), now.getMonth() + 1);
  }

  /** Verschiebt einen `YYYY-MM`-String um `delta` Monate (jahresübergreifend). */
  private static shiftMonth(month: string, delta: number): string {
    const [year, monthNumber] = month.split('-').map(Number);
    // Date normalisiert Monats-Overflow/-Underflow (z. B. Monat 0 → Dezember Vorjahr).
    const shifted = new Date(year, monthNumber - 1 + delta, 1);
    return CategoryOverview.toMonthString(shifted.getFullYear(), shifted.getMonth() + 1);
  }

  /** Baut `YYYY-MM` aus Jahr und 1-basiertem Monat mit führender Null. */
  private static toMonthString(year: number, monthNumber: number): string {
    return `${year}-${String(monthNumber).padStart(2, '0')}`;
  }

  /** Formatiert `YYYY-MM` als `"Juli 2026"` (de-CH). */
  private static formatMonth(month: string): string {
    const [year, monthNumber] = month.split('-').map(Number);
    const date = new Date(year, monthNumber - 1, 1);
    return new Intl.DateTimeFormat('de-CH', { month: 'long', year: 'numeric' }).format(date);
  }
}
