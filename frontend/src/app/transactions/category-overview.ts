import { CurrencyPipe, DatePipe, DecimalPipe } from '@angular/common';
import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, ParamMap, Router } from '@angular/router';
import { Subscription } from 'rxjs';

import { Badge } from '../shared/badge/badge';
import { Button } from '../shared/button/button';
import { Card } from '../shared/card/card';
import { CATEGORIES } from '../shared/category';
import { DonutChart, DonutSlice } from '../shared/chart/donut-chart';
import { Input } from '../shared/input/input';
import { MonthNav, MonthOption } from '../shared/month-nav/month-nav';
import { Notice } from '../shared/notice/notice';
import { CategorySummary } from './category-summary.model';
import { Transaction } from './transaction.model';
import {
  MAX_TRANSACTION_PAGE_SIZE,
  TRANSACTION_PAGE_SIZE,
  TransactionService,
} from './transaction.service';
import { TransactionSummaryService } from './transaction-summary.service';

/** Meldung, wenn die Buchungen einer aufgeklappten Kategorie nicht geladen werden konnten. */
const FAILED_TO_LOAD = 'Die Buchungen konnten nicht geladen werden.';

/** Meldung, wenn das Nachladen weiterer Buchungen fehlschlägt — die geladenen bleiben stehen. */
const FAILED_TO_LOAD_MORE = 'Weitere Buchungen konnten nicht geladen werden.';

/** Meldung, wenn die Liste nach einer Kategorie-Korrektur nicht aktualisiert werden konnte. */
const FAILED_TO_REFRESH = 'Die Liste konnte nicht aktualisiert werden.';

/**
 * Meldung, wenn die Prüfliste der unsicheren Buchungsrichtungen nicht geladen werden konnte
 * (BE-PDF-10).
 *
 * <p>Anders als bei den Monaten des Dropdowns wird der Ausfall hier sichtbar gemeldet: Bleibt die
 * Liste still leer, sieht der Nutzer «alles in Ordnung», obwohl niemand nachgesehen hat — und
 * genau diese stumme Zusicherung ist der Bug, den BE-PDF-10 behebt.
 */
const FAILED_TO_LOAD_UNCERTAIN = 'Die Buchungen mit unsicherer Richtung konnten nicht geladen werden.';

/** Meldung, wenn eine Richtungskorrektur nicht gespeichert werden konnte. */
const FAILED_TO_SAVE_DIRECTION = 'Die Buchungsrichtung konnte nicht gespeichert werden.';

/**
 * Wie viele Seiten am Stück nachgeladen werden können, bevor das Backend das `size`-Limit
 * ablehnt. Grenze des Fensters, das nach einer Kategorie-Korrektur neu geladen wird.
 */
const MAX_PAGES_PER_REQUEST = MAX_TRANSACTION_PAGE_SIZE / TRANSACTION_PAGE_SIZE;

/**
 * Zulässiger `month`-Query-Parameter. Ein unbrauchbarer Wert darf nicht bis `formatMonth()`
 * durchkommen — `new Date(NaN)` erzeugte dort ein «Invalid Date» als Überschrift.
 */
const MONTH_PATTERN = /^\d{4}-(0[1-9]|1[0-2])$/;

/** Zustand der aktuell aufgeklappten Kategorie. `null`, solange keine offen ist. */
interface Drilldown {
  /** Deutsches Kategorie-Label, z. B. `"Lebensmittel"`. */
  readonly category: string;
  /** Die bereits geladenen Buchungen dieser Kategorie im angezeigten Monat. */
  readonly transactions: readonly Transaction[];
  /** `true`, solange die Liste erstmals geladen wird. */
  readonly loading: boolean;
  /** `true`, solange eine weitere Seite nachgeladen wird — die bisherigen bleiben sichtbar. */
  readonly loadingMore: boolean;
  /** Fehlermeldung des Ladevorgangs oder `null`. */
  readonly error: string | null;
  /** `true`, wenn hinter den geladenen Buchungen weitere folgen (Backend-Signal `hasMore`). */
  readonly hasMore: boolean;
  /**
   * Anzahl angeforderter Seiten. Nicht dasselbe wie {@link transactions}`.length / 20`: nach einer
   * Korrektur kann das Fenster weniger Einträge enthalten, als es Seiten umfasst. Der Wert
   * bestimmt, ab welchem Offset die nächste Seite beginnt — er darf deshalb nur wachsen, wenn eine
   * Seite tatsächlich angefordert wurde.
   */
  readonly pagesLoaded: number;
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
 * jede mit einem Dropdown zur Korrektur der Kategorie (FE-CAT-03). Die Liste beginnt mit 20
 * Buchungen und wächst über «Weitere laden» seitenweise (FE-CAT-05, US-13).
 *
 * <p>OnPush + Signals wie im übrigen Frontend; der HTTP-Zugriff liegt in den
 * zustandslosen Services {@link TransactionSummaryService} und {@link TransactionService}.
 */
@Component({
  selector: 'app-category-overview',
  imports: [
    CurrencyPipe,
    DatePipe,
    DecimalPipe,
    MonthNav,
    Card,
    Badge,
    Button,
    DonutChart,
    Input,
    Notice,
  ],
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
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

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

  /**
   * Monate mit Ausgaben aus `GET /api/transactions/months`, roh wie geliefert. Leer, solange nichts
   * geladen ist oder der Request fehlgeschlagen ist.
   */
  private readonly loadedMonths = signal<readonly string[]>([]);

  /**
   * Die Monate des Direktsprung-Dropdowns (FE-CAT-04), neuester zuerst.
   *
   * <p>Zwei Regeln über der geladenen Liste:
   *
   * <ul>
   *   <li>Der angezeigte Monat ist immer dabei, auch wenn er keine Ausgaben hat. Sonst stünde das
   *       Dropdown bei leerem Konto oder einem per Deep-Link geöffneten leeren Monat auf einem
   *       fremden Wert.
   *   <li>Zukunftsmonate fallen raus — dieselbe Regel, die {@link isCurrentMonth} am Stepper
   *       durchsetzt. Ein Dropdown, das sie anböte, hebelte die Sperre aus.
   * </ul>
   */
  readonly monthOptions = computed<readonly MonthOption[]>(() => {
    const current = CategoryOverview.currentMonth();
    const values = new Set(this.loadedMonths().filter((month) => month <= current));
    values.add(this.month());
    return [...values]
      .sort()
      .reverse()
      .map((value) => ({ value, label: CategoryOverview.formatMonth(value) }));
  });

  /** Aufgeklappte Kategorie samt ihren Buchungen, oder `null`, wenn keine offen ist. */
  readonly drilldown = signal<Drilldown | null>(null);

  /**
   * Die Buchungen des Monats, deren Richtung der PDF-Parser nur angenommen hat (BE-PDF-10, US-04).
   *
   * <p>Steht als eigene Karte über der Übersicht, nicht bloss als Marker in den Kategoriezeilen:
   * Die betroffenen Buchungen verteilen sich über beliebige Kategorien, und wer sie nur dort
   * markiert, verlangt vom Nutzer, jede Kategorie aufzuklappen, um sie zu finden. Der Marker in
   * der Zeile kommt zusätzlich — er beantwortet die Frage «warum steht diese Buchung oben?» dort,
   * wo sie auftaucht.
   */
  readonly uncertain = signal<readonly Transaction[]>([]);

  /** Fehlermeldung der Prüfliste, oder `null`. */
  readonly uncertainErrorMessage = signal<string | null>(null);

  /**
   * IDs der Buchungen, deren Richtungskorrektur gerade läuft — sperrt deren Auswahl.
   *
   * <p>Bewusst <em>nicht</em> optimistisch wie die Kategorie-Korrektur nebenan. Der Unterschied
   * liegt in der Wirkung: Eine Kategorie ändert einen Wert in einer Zeile, die stehen bleibt; eine
   * bestätigte Richtung <em>entfernt</em> die Zeile aus dieser Liste. Eine optimistisch entfernte
   * Zeile bei einem Fehler wieder an ihrer alten Stelle einzusetzen ist mehr Mechanik, als die
   * gesparte Round-Trip-Zeit auf einer Liste wert ist, die im Normalfall leer und im schlechten
   * Fall eine Handvoll Einträge lang ist.
   */
  readonly savingDirections = signal<ReadonlySet<number>>(new Set());

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

  /** Subscription der zuletzt geladenen Prüfliste (BE-PDF-10). */
  private pendingUncertainRequest: Subscription | undefined;

  /**
   * Noch laufende Kategorie-Korrekturen (PUT).
   *
   * <p>Als Menge und nicht als einzelnes Feld, weil mehrere Korrekturen gleichzeitig offen sein
   * können — für zwei Klicks innerhalb einer Round-Trip-Zeit braucht es nicht viel. Sie steuert
   * zwei Dinge: das Nachladen wartet, bis die letzte fertig ist ({@link refreshAfterCorrection}),
   * und beim Monatswechsel werden sie abgebrochen, damit keine späte Antwort mehr in eine Anzeige
   * schreibt, zu der sie nicht mehr gehört.
   */
  private readonly pendingSaves = new Set<Subscription>();

  /**
   * `false`, bis die erste URL-Auswertung gelaufen ist. Ohne diese Unterscheidung würde die
   * Gleichheits-Wache in {@link syncFromUrl} das Erstladen verschlucken, sobald die URL keinen
   * Parameter trägt — der ausgelesene Monat wäre dann von Anfang an derselbe wie der angezeigte.
   */
  private initialLoadDone = false;

  constructor() {
    // `queryParamMap` liefert den aktuellen Stand sofort und danach jede Änderung. Beides läuft
    // durch dieselbe Methode: das Erstladen (auch per Deep-Link) und später Browser-Zurück,
    // -Vorwärts oder eine von Hand editierte Adresse.
    this.route.queryParamMap
      .pipe(takeUntilDestroyed())
      .subscribe((params) => this.syncFromUrl(params));
    this.loadAvailableMonths();
  }

  /** Einen Monat zurück. */
  previousMonth(): void {
    this.goTo(CategoryOverview.shiftMonth(this.month(), -1));
  }

  /** Einen Monat vor. */
  nextMonth(): void {
    this.goTo(CategoryOverview.shiftMonth(this.month(), 1));
  }

  /**
   * Springt direkt auf einen Monat (FE-CAT-04, US-12) — unabhängig davon, wie weit er entfernt
   * ist, und mit genau einem Request statt einem pro übersprungenem Monat.
   */
  selectMonth(month: string): void {
    if (month === this.month()) {
      return;
    }
    this.goTo(month);
  }

  /**
   * Wechselt den angezeigten Monat und zieht die URL nach.
   *
   * <p>Geladen wird sofort, nicht erst wenn die Navigation gelandet ist: die Anzeige soll nicht
   * auf den Router warten. Die URL folgt anschliessend, und die Wache in {@link syncFromUrl}
   * verwirft die Rückmeldung — sonst liefe jeder Wechsel zweimal.
   */
  private goTo(month: string): void {
    this.month.set(month);
    this.load();
    void this.router.navigate([], {
      relativeTo: this.route,
      queryParams: { month },
      queryParamsHandling: 'merge',
    });
  }

  /**
   * Übernimmt den Monat aus der URL — beim Erstladen und bei jeder äusseren Änderung.
   *
   * <p>Die Gleichheits-Wache unten ist der Grund, warum Stepper und Sprung synchron laden dürfen,
   * ohne dass ein zweiter Request folgt. Wer sie entfernt, bekommt pro Wechsel zwei Requests —
   * der Test «schreibt den Monat in die URL, ohne ein zweites Mal zu laden» wird dann rot.
   */
  private syncFromUrl(params: ParamMap): void {
    const raw = params.get('month');
    // Ein Zukunftsmonat ist hier genauso unbrauchbar wie ein kaputtes Format: dort gibt es keine
    // Buchungen, und der Stepper verbietet den Weg dorthin ohnehin. Ihn erst weiter unten aus dem
    // Dropdown zu filtern, hiesse Entscheid 5 zu brechen — das <select> stünde dann auf einem
    // Wert, den seine eigene Liste nicht enthält. Hier abgefangen, halten beide Regeln.
    const valid = raw !== null && MONTH_PATTERN.test(raw) && raw <= CategoryOverview.currentMonth();
    const month = valid ? raw : CategoryOverview.currentMonth();

    if (raw !== null && !valid) {
      // Unbrauchbarer Parameter: die Adresse auf den tatsächlich angezeigten Monat zurechtrücken,
      // statt eine URL stehen zu lassen, die etwas anderes behauptet als die Seite. `replaceUrl`,
      // damit die kaputte Adresse nicht im Verlauf liegenbleibt und «Zurück» sie wieder aufruft.
      void this.router.navigate([], {
        relativeTo: this.route,
        queryParams: { month },
        queryParamsHandling: 'merge',
        replaceUrl: true,
      });
    }

    if (this.initialLoadDone && month === this.month()) {
      return;
    }
    this.initialLoadDone = true;
    this.month.set(month);
    this.load();
  }

  /**
   * Lädt die Monate für das Direktsprung-Dropdown.
   *
   * <p>Einmal beim Aufbau der Seite: die Liste ändert sich nur durch einen Import, und der führt
   * ohnehin über eine andere Seite hierher zurück.
   */
  private loadAvailableMonths(): void {
    this.transactionService.availableMonths().subscribe({
      next: (months) => this.loadedMonths.set(months),
      error: (_err: HttpErrorResponse) => {
        // Bewusst ohne Meldung: {@link monthOptions} fällt auf den angezeigten Monat zurück, und
        // Stepper wie Übersicht funktionieren unverändert weiter. Eine rote Meldung für ein
        // ausgefallenes Komfort-Element stünde in keinem Verhältnis zur Einschränkung.
      },
    });
  }

  /** `true`, wenn die Buchungen dieser Kategorie gerade aufgeklappt sind. */
  isExpanded(category: string): boolean {
    return this.drilldown()?.category === category;
  }

  /**
   * ID der aufgeklappten Buchungszeile — Ziel des `aria-controls` am Toggle. `null`, solange die
   * Kategorie zu ist: die Zeile existiert dann nicht, und ein IDREF ins Leere ist ungültig.
   */
  drilldownId(category: string): string | null {
    return this.isExpanded(category) ? `drilldown-${category}` : null;
  }

  /** Klappt die Buchungen einer Kategorie auf — oder wieder zu, wenn sie schon offen sind. */
  toggleCategory(category: string): void {
    if (this.isExpanded(category)) {
      this.pendingDrilldownRequest?.unsubscribe();
      this.drilldown.set(null);
      return;
    }
    this.saveErrorMessage.set(null);
    this.drilldown.set({
      category,
      transactions: [],
      loading: true,
      loadingMore: false,
      error: null,
      hasMore: false,
      pagesLoaded: 0,
    });
    this.loadPage(category, 0);
  }

  /**
   * Lädt die nächste Seite Buchungen und hängt sie an (FE-CAT-05, US-13).
   *
   * <p>Der Offset kommt aus {@link Drilldown#pagesLoaded}, nicht aus der Anzahl sichtbarer
   * Buchungen: nach einer Kategorie-Korrektur kann das Fenster einen Eintrag weniger enthalten,
   * als es Seiten umfasst — die nächste Seite beginnt trotzdem an der Seitengrenze.
   */
  loadMore(): void {
    const open = this.drilldown();
    if (open === null || !open.hasMore || open.loading || open.loadingMore) {
      return;
    }
    this.drilldown.set({ ...open, loadingMore: true, error: null });
    this.loadPage(open.category, open.pagesLoaded);
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

    // Der Eintrag steht vor dem subscribe in der Menge, damit ein zeitgleich laufender zweiter
    // PUT ihn schon sieht — und damit die Callbacks unten ihn sicher referenzieren können.
    const save = new Subscription();
    this.pendingSaves.add(save);

    save.add(
      this.transactionService.updateCategory(transaction.id, category).subscribe({
        next: () => {
          this.pendingSaves.delete(save);
          this.refreshAfterCorrection();
        },
        error: (_err: HttpErrorResponse) => {
          this.pendingSaves.delete(save);
          this.applyCategory(transaction.id, previous);
          this.saveErrorMessage.set('Die Kategorie konnte nicht gespeichert werden.');
        },
      }),
    );
  }

  /** `true`, solange die Richtungskorrektur dieser Buchung noch läuft — sperrt ihre Auswahl. */
  isSavingDirection(transactionId: number): boolean {
    return this.savingDirections().has(transactionId);
  }

  /**
   * Setzt die Buchungsrichtung einer unsicher markierten Buchung (BE-PDF-10, AC 2 und 3).
   *
   * <p>Beide Antworten sind eine Entscheidung und räumen die Markierung ab: «Gutschrift» dreht das
   * Vorzeichen, «Ausgabe» bestätigt die Annahme des Parsers. Ohne die zweite Möglichkeit stünde
   * eine richtig geratene Buchung für immer in der Prüfliste.
   *
   * <p>Danach wird nachgeladen, weil eine auf Gutschrift gesetzte Buchung die Ausgabenseite
   * verlässt: Summen, Anteile und Donut stimmen sonst nicht mehr zu den Zeilen darunter — und der
   * Safe-to-Spend auf dem Dashboard hat sich mit derselben Korrektur ebenfalls verändert.
   */
  correctDirection(transaction: Transaction, income: boolean): void {
    if (this.isSavingDirection(transaction.id)) {
      return;
    }

    this.saveErrorMessage.set(null);
    this.markSavingDirection(transaction.id, true);

    // In dieselbe Menge wie die Kategorie-Korrekturen: Der Nachlade-Schritt wartet damit auch auf
    // eine parallel laufende Richtungskorrektur, statt eine Liste zu ziehen, die deren Ergebnis
    // noch nicht enthält.
    const save = new Subscription();
    this.pendingSaves.add(save);

    save.add(
      this.transactionService.updateDirection(transaction.id, income).subscribe({
        next: () => {
          this.pendingSaves.delete(save);
          this.markSavingDirection(transaction.id, false);
          this.refreshAfterCorrection();
        },
        error: (_err: HttpErrorResponse) => {
          this.pendingSaves.delete(save);
          this.markSavingDirection(transaction.id, false);
          this.saveErrorMessage.set(FAILED_TO_SAVE_DIRECTION);
        },
      }),
    );
  }

  /** Setzt oder entfernt die Sperre einer laufenden Richtungskorrektur (neue Menge wegen OnPush). */
  private markSavingDirection(transactionId: number, saving: boolean): void {
    this.savingDirections.update((current) => {
      const next = new Set(current);
      if (saving) {
        next.add(transactionId);
      } else {
        next.delete(transactionId);
      }
      return next;
    });
  }

  /** Bricht alle noch offenen Kategorie-Korrekturen ab und leert die Menge. */
  private cancelPendingSaves(): void {
    for (const save of this.pendingSaves) {
      save.unsubscribe();
    }
    this.pendingSaves.clear();
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
   * Lädt Summary, Prüfliste und offene Kategorie nach einer erfolgreichen Korrektur neu.
   *
   * <p>Nötig, weil jede Korrektur die Aggregate verändert: Bei der Kategorie wandert der Betrag in
   * eine andere Zeile, bei der Richtung verlässt er womöglich die Ausgabenseite ganz. Summen,
   * Anteile und Donut stimmen sonst nicht mehr zu den Zeilen darunter. Die Requests laufen bewusst
   * ohne Ladezustand — die korrigierte Zeile steht bereits richtig da, ein Zurückfallen auf
   * «Lädt …» wäre nur Unruhe.
   *
   * <p>Gemeinsam für beide Korrekturarten (BE-PDF-10): Sie unterscheiden sich darin, <em>was</em>
   * sie ändern, nicht darin, was danach nicht mehr stimmt.
   */
  private refreshAfterCorrection(): void {
    if (this.pendingSaves.size > 0) {
      // Eine zweite Korrektur läuft noch. Ihre Zeile steht optimistisch schon auf dem neuen Wert,
      // der Server kennt ihn aber noch nicht — die nachgeladene Liste trüge dort den alten Wert
      // und würde die Auswahl sichtbar zurückwerfen. Der zuletzt fertige PUT lädt für alle nach.
      return;
    }

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

    // Die Prüfliste kann durch eine Richtungskorrektur geschrumpft sein — und durch eine
    // Kategorie-Korrektur ändert sich das Kategorie-Label der dort gezeigten Buchungen.
    this.loadUncertain();

    const open = this.drilldown();
    if (open !== null) {
      this.reloadWindow(open);
    }
  }

  /**
   * Lädt eine Seite Buchungen einer Kategorie im aktuellen Monat.
   *
   * <p>Seite 0 ersetzt die Liste (Erstladen), jede weitere hängt an — sonst würde «Weitere laden»
   * die bereits sichtbaren Buchungen neu setzen, was AC 5 ausschliesst.
   */
  private loadPage(category: string, page: number): void {
    this.pendingDrilldownRequest?.unsubscribe();

    this.pendingDrilldownRequest = this.transactionService
      .list(this.month(), category, page)
      .subscribe({
        next: (result) =>
          this.drilldown.update((current) =>
            // Nach dem Monatswechsel oder Zuklappen gehört die Antwort nicht mehr zur Anzeige.
            current?.category === category
              ? {
                  ...current,
                  transactions:
                    page === 0
                      ? result.transactions
                      : [...current.transactions, ...result.transactions],
                  pagesLoaded: page + 1,
                  hasMore: result.hasMore,
                  loading: false,
                  loadingMore: false,
                  error: null,
                }
              : current,
          ),
        error: (_err: HttpErrorResponse) =>
          this.drilldown.update((current) => {
            if (current?.category !== category) {
              return current;
            }
            // Beim Erstladen gibt es nichts zu behalten; beim Nachladen dagegen stehen bereits
            // Buchungen da, und die gehören nicht wegen einer gescheiterten Folgeseite entfernt.
            // hasMore bleibt in dem Fall stehen, damit der Button einen zweiten Versuch erlaubt.
            return page === 0
              ? {
                  ...current,
                  transactions: [],
                  loading: false,
                  loadingMore: false,
                  hasMore: false,
                  error: FAILED_TO_LOAD,
                }
              : { ...current, loadingMore: false, error: FAILED_TO_LOAD_MORE };
          }),
      });
  }

  /**
   * Lädt nach einer Kategorie-Korrektur das gesamte geladene Fenster neu — in einem Request.
   *
   * <p>Ein blosses Nachladen von Seite 0 würde die Liste auf 20 Einträge zurückwerfen, sobald der
   * Nutzer «Weitere laden» benutzt hat. Die Zeile nur lokal zu entfernen wäre der andere
   * naheliegende Weg und ist falsch: die korrigierte Buchung verlässt serverseitig die Kategorie,
   * alle dahinter rücken einen Platz vor, und die nächste Seite begänne dann hinter einer Buchung,
   * die nie jemand gesehen hat. Das Fenster neu zu laden schliesst diese Lücke — entweder es
   * bleibt voll (die Offsets stimmen weiter) oder die Menge ist hineingeschrumpft und `hasMore`
   * wird `false`, womit es keine Folgeseite mehr gibt.
   *
   * <p>Ab {@link MAX_PAGES_PER_REQUEST} Seiten begrenzt das Backend die Anfrage. Dann wird das
   * Fenster auf diese Grösse gekürzt und der Button erscheint wieder: die Liste ist kürzer als
   * vorher, aber keine Buchung wird übersprungen.
   *
   * <p>Die <em>Untergrenze</em> von einer Seite sieht nach totem Code aus, ist es aber nicht:
   * {@code toggleCategory} setzt {@code pagesLoaded} beim Aufklappen auf 0, und eine Korrektur
   * überlebt das Zuklappen (abgebrochen werden Korrekturen nur beim Monatswechsel). Wer eine
   * Kategorie korrigiert, zuklappt und wieder aufklappt, bevor der PUT zurück ist, landet genau
   * hier mit 0 geladenen Seiten. Ohne die Untergrenze ginge die Anfrage mit {@code size=0} raus,
   * und das Backend antwortete mit 400. Festgehalten im Test «reloads a full page when a
   * correction lands after the category was reopened».
   */
  private reloadWindow(open: Drilldown): void {
    const pages = Math.min(Math.max(open.pagesLoaded, 1), MAX_PAGES_PER_REQUEST);
    this.pendingDrilldownRequest?.unsubscribe();

    this.pendingDrilldownRequest = this.transactionService
      .list(this.month(), open.category, 0, pages * TRANSACTION_PAGE_SIZE)
      .subscribe({
        next: (result) =>
          this.drilldown.update((current) =>
            current?.category === open.category
              ? {
                  ...current,
                  transactions: result.transactions,
                  pagesLoaded: pages,
                  hasMore: result.hasMore,
                  loading: false,
                  loadingMore: false,
                  error: null,
                }
              : current,
          ),
        error: (_err: HttpErrorResponse) =>
          this.drilldown.update((current) =>
            current?.category === open.category
              ? { ...current, loadingMore: false, error: FAILED_TO_REFRESH }
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

  /**
   * Buchung in einer Zeile, für den unsichtbaren Namen des Kategorie-Dropdowns (BE-PDF-07).
   *
   * <p>Ohne die Detailzeilen läse ein Screenreader zwölfmal «Kategorie von LASTSCHRIFT» — die
   * Dropdowns wären untereinander nicht unterscheidbar, also genau das Problem, das dieses Ticket
   * visuell löst. Die Zeilenumbrüche werden dabei zu Kommas: Der Name ist ein einzeiliges
   * Attribut, ein `\n` darin wäre für die Ausgabe bloss ein Leerzeichen ohne Pause.
   */
  transactionLabel(tx: Transaction): string {
    return tx.buchungsdetails
      ? `${tx.buchungstext}, ${tx.buchungsdetails.replaceAll('\n', ', ')}`
      : tx.buchungstext;
  }

  private load(): void {
    // Einen noch laufenden Request canceln, bevor ein neuer startet — sonst kann bei
    // schneller Monat-Navigation die spätere Antwort von der früheren überschrieben
    // werden (Race Condition).
    this.pendingRequest?.unsubscribe();
    // Eine offene Kategorie gehört zum alten Monat — sie zeigt sonst weiter dessen Buchungen.
    this.pendingDrilldownRequest?.unsubscribe();
    // Und eine noch offene Korrektur gehört ebenfalls dorthin: ihr Error-Handler würde sonst nach
    // dem Wechsel «konnte nicht gespeichert werden» über einen Monat schreiben, in dem gar nichts
    // geändert wurde. Der PUT selbst ist beim Server längst angekommen — abgebrochen wird nur die
    // Reaktion darauf, nicht die Speicherung.
    this.cancelPendingSaves();
    this.drilldown.set(null);
    this.saveErrorMessage.set(null);
    // Die Prüfliste gehört zum alten Monat und wird geleert, nicht bloss überschrieben: Sie steht
    // ausserhalb des Ladezustands der Tabelle und bliebe sonst sichtbar, bis die neue Antwort da
    // ist — mit Buchungen aus einem Monat, den die Seite nicht mehr zeigt.
    this.uncertain.set([]);
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

    this.loadUncertain();
  }

  /**
   * Lädt die Prüfliste der unsicheren Buchungsrichtungen für den angezeigten Monat (BE-PDF-10).
   *
   * <p>Eigener Request neben dem Summary statt eines Felds darin: Das Summary aggregiert Ausgaben
   * pro Kategorie, die Prüfliste ist eine Auswahl einzelner Buchungen quer über alle Kategorien.
   * Sie in dieselbe Antwort zu packen hiesse, zwei verschiedene Fragen an eine Zahl zu hängen —
   * und die Prüfliste müsste mitwachsen, wenn das Summary später einmal etwas anderes wird.
   */
  private loadUncertain(): void {
    this.pendingUncertainRequest?.unsubscribe();
    this.uncertainErrorMessage.set(null);

    this.pendingUncertainRequest = this.transactionService
      .uncertainDirections(this.month())
      .subscribe({
        next: (transactions) => this.uncertain.set(transactions),
        error: (_err: HttpErrorResponse) => {
          // Die Liste wird geleert, nicht stehen gelassen: Einträge aus dem vorigen Monat neben
          // einer Fehlermeldung wären schlimmer als gar keine.
          this.uncertain.set([]);
          this.uncertainErrorMessage.set(FAILED_TO_LOAD_UNCERTAIN);
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
