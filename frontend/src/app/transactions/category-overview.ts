import { CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { Subscription } from 'rxjs';

import { Badge } from '../shared/badge/badge';
import { Card } from '../shared/card/card';
import { CATEGORIES } from '../shared/category';
import { DonutChart, DonutSlice } from '../shared/chart/donut-chart';
import { Input } from '../shared/input/input';
import { MonthNav } from '../shared/month-nav/month-nav';
import { Notice } from '../shared/notice/notice';
import { CategorySummary } from './category-summary.model';
import { Transaction } from './transaction.model';
import { TransactionService } from './transaction.service';
import { TransactionSummaryService } from './transaction-summary.service';

/** Meldung, wenn die Buchungen einer aufgeklappten Kategorie nicht geladen werden konnten. */
const FAILED_TO_LOAD = 'Die Buchungen konnten nicht geladen werden.';

/** Zustand der aktuell aufgeklappten Kategorie. `null`, solange keine offen ist. */
interface Drilldown {
  /** Deutsches Kategorie-Label, z. B. `"Lebensmittel"`. */
  readonly category: string;
  /** Die Buchungen dieser Kategorie im angezeigten Monat. */
  readonly transactions: readonly Transaction[];
  /** `true`, solange die Liste erstmals geladen wird. */
  readonly loading: boolean;
  /** Fehlermeldung des Ladevorgangs oder `null`. */
  readonly error: string | null;
}

/**
 * Kategorie-Übersicht (FE-CAT-01, US-05).
 *
 * <p>Zeigt pro Kategorie CHF-Summe, Anzahl und Prozentanteil für den gewählten
 * Monat. Ein Prev/Next-Selector navigiert zwischen Monaten; jeder Wechsel lädt neu.
 * Ist der Monat leer, erscheint ein Leerzustand statt einer leeren Tabelle.
 *
 * <p>Über der Tabelle visualisiert ein {@link DonutChart} dieselben Zahlen als
 * Ausgabenverteilung (FE-CAT-02).
 *
 * <p>Jede Kategorie-Zeile lässt sich aufklappen und zeigt dann die Buchungen dahinter,
 * jede mit einem Dropdown zur Korrektur der Kategorie (FE-CAT-03).
 *
 * <p>OnPush + Signals wie im übrigen Frontend; der HTTP-Zugriff liegt in den
 * zustandslosen Services {@link TransactionSummaryService} und {@link TransactionService}.
 */
@Component({
  selector: 'app-category-overview',
  imports: [CurrencyPipe, DatePipe, DecimalPipe, MonthNav, Card, Badge, DonutChart, Input, Notice],
  templateUrl: './category-overview.html',
  styleUrl: './category-overview.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class CategoryOverview {
  /** Deutsches Label → Kategorie-Slug, um aus der API-Antwort das `app-badge`-Token zu treffen. */
  private static readonly SLUG_BY_LABEL = new Map(CATEGORIES.map((c) => [c.label, c.slug]));

  /** Die 13 Kategorien für das Korrektur-Dropdown (FE-CAT-03, AC 1). */
  protected readonly categories = CATEGORIES;

  private readonly summaryService = inject(TransactionSummaryService);
  private readonly transactionService = inject(TransactionService);

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

  /**
   * Segmente des Donut-Charts (FE-CAT-02) — dieselben Zahlen wie die Tabelle, in der
   * Reihenfolge der API-Antwort (absteigend nach Betrag).
   *
   * <p>Der Slug kommt aus derselben {@link SLUG_BY_LABEL}-Map wie das Badge der
   * Tabellenzeile: Segment, Legendenpunkt und Badge einer Kategorie ziehen damit
   * zwangsläufig dieselbe `--cat-<slug>`-Farbe. Ein unbekanntes Label fällt auf sich
   * selbst zurück — es trifft kein Token, die Komponente rendert das Segment darum
   * neutral grau statt in einer fremden Kategorie-Farbe.
   */
  readonly slices = computed<readonly DonutSlice[]>(
    () =>
      this.summary()?.categories.map((item) => ({
        slug: CategoryOverview.SLUG_BY_LABEL.get(item.category) ?? item.category,
        label: item.category,
        value: item.amount,
      })) ?? [],
  );

  /** `true`, wenn geladen wurde und der Monat keine Ausgaben enthält. */
  readonly isEmpty = computed(() => {
    const current = this.summary();
    return current !== null && current.categories.length === 0;
  });

  /** `true`, wenn der angezeigte Monat der aktuelle Monat ist — sperrt "›". */
  readonly isCurrentMonth = computed(() => this.month() >= CategoryOverview.currentMonth());

  /** Aufgeklappte Kategorie samt ihren Buchungen, oder `null`, wenn keine offen ist. */
  readonly drilldown = signal<Drilldown | null>(null);

  /**
   * Fehlermeldung einer fehlgeschlagenen Kategorie-Korrektur oder `null`.
   *
   * <p>Bewusst getrennt von {@link errorMessage}: die Tabelle steht ja korrekt da, nur das
   * Speichern hat nicht geklappt. Würde derselbe Fehlerzustand verwendet, verschwände die
   * Übersicht hinter einer Fehlermeldung.
   */
  readonly saveErrorMessage = signal<string | null>(null);

  /** Subscription des zuletzt gestarteten Requests, um ihn bei Monatswechsel zu canceln. */
  private pendingRequest: Subscription | undefined;

  /** Subscription der zuletzt geladenen Transaktionsliste. */
  private pendingDrilldownRequest: Subscription | undefined;

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

  /** `true`, wenn die Buchungen dieser Kategorie gerade aufgeklappt sind. */
  isExpanded(category: string): boolean {
    return this.drilldown()?.category === category;
  }

  /** Klappt die Buchungen einer Kategorie auf — oder wieder zu, wenn sie schon offen sind. */
  toggleCategory(category: string): void {
    if (this.isExpanded(category)) {
      this.pendingDrilldownRequest?.unsubscribe();
      this.drilldown.set(null);
      return;
    }
    this.saveErrorMessage.set(null);
    this.drilldown.set({ category, transactions: [], loading: true, error: null });
    this.loadDrilldown(category);
  }

  /**
   * Setzt die Kategorie einer Buchung (FE-CAT-03, AC 2 und 3).
   *
   * <p>Optimistisch: der neue Wert steht sofort im Signal und damit im DOM, noch bevor der
   * Server geantwortet hat. Erst danach läuft der PUT. Scheitert er, kommt der alte Wert
   * zurück und eine Fehlermeldung erscheint — die Anzeige behauptet nie einen Stand, den der
   * Server nicht hat.
   */
  changeCategory(transaction: Transaction, category: string): void {
    const previous = transaction.category;
    if (previous === category) {
      return;
    }

    this.saveErrorMessage.set(null);
    this.applyCategory(transaction.id, category);

    this.transactionService.updateCategory(transaction.id, category).subscribe({
      next: () => this.refreshAfterCategoryChange(),
      error: (_err: HttpErrorResponse) => {
        this.applyCategory(transaction.id, previous);
        this.saveErrorMessage.set('Die Kategorie konnte nicht gespeichert werden.');
      },
    });
  }

  /** Ersetzt die Kategorie einer Buchung im Drilldown-Signal (neue Objekte wegen OnPush). */
  private applyCategory(transactionId: number, category: string): void {
    this.drilldown.update((current) =>
      current === null
        ? current
        : {
            ...current,
            transactions: current.transactions.map((tx) =>
              tx.id === transactionId ? { ...tx, category } : tx,
            ),
          },
    );
  }

  /**
   * Lädt Summary und offene Liste nach einer erfolgreichen Korrektur neu.
   *
   * <p>Nötig, weil die Korrektur die Aggregate verändert: der Betrag wandert in eine andere
   * Kategorie, Summen, Anteile und Donut stimmen sonst nicht mehr zu den Zeilen darunter. Beide
   * Requests laufen bewusst ohne Ladezustand — die korrigierte Zeile steht bereits richtig da,
   * ein Zurückfallen auf «Lädt …» wäre nur Unruhe.
   */
  private refreshAfterCategoryChange(): void {
    this.pendingRequest?.unsubscribe();
    this.pendingRequest = this.summaryService.getSummary(this.month()).subscribe({
      next: (summary) => {
        this.summary.set(summary);
        // Dieser Request kann einen noch laufenden Monatswechsel abgebrochen haben (beide
        // teilen sich pendingRequest). Dessen Ladezustand bliebe sonst für immer stehen.
        this.loading.set(false);
      },
      error: (_err: HttpErrorResponse) => {
        this.loading.set(false);
        this.saveErrorMessage.set(
          'Die Kategorie wurde gespeichert, die Übersicht konnte aber nicht aktualisiert werden.',
        );
      },
    });

    const open = this.drilldown();
    if (open !== null) {
      this.loadDrilldown(open.category);
    }
  }

  /** Lädt die Buchungen einer Kategorie im aktuellen Monat. */
  private loadDrilldown(category: string): void {
    this.pendingDrilldownRequest?.unsubscribe();

    this.pendingDrilldownRequest = this.transactionService.list(this.month(), category).subscribe({
      next: (transactions) =>
        this.drilldown.update((current) =>
          // Nach dem Monatswechsel oder Zuklappen gehört die Antwort nicht mehr zur Anzeige.
          current?.category === category
            ? { ...current, transactions, loading: false, error: null }
            : current,
        ),
      error: (_err: HttpErrorResponse) =>
        this.drilldown.update((current) =>
          current?.category === category
            ? { ...current, transactions: [], loading: false, error: FAILED_TO_LOAD }
            : current,
        ),
    });
  }

  /**
   * Kategorie-Slug (z. B. `"wohnen"`) zum deutschen Label aus der API-Antwort, damit das
   * `app-badge` die passende `--cat-<slug>`-Farbe zieht. Unbekannte Labels → neutraler Punkt.
   */
  categorySlug(label: string): string | undefined {
    return CategoryOverview.SLUG_BY_LABEL.get(label);
  }

  private load(): void {
    // Einen noch laufenden Request canceln, bevor ein neuer startet — sonst kann bei
    // schneller Monat-Navigation die spätere Antwort von der früheren überschrieben
    // werden (Race Condition).
    this.pendingRequest?.unsubscribe();
    // Eine offene Kategorie gehört zum alten Monat — sie zeigt sonst weiter dessen Buchungen.
    this.pendingDrilldownRequest?.unsubscribe();
    this.drilldown.set(null);
    this.saveErrorMessage.set(null);
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
