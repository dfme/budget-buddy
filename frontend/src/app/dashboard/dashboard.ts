import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';
import { takeUntilDestroyed } from '@angular/core/rxjs-interop';
import { ActivatedRoute, ParamMap, Router, RouterLink } from '@angular/router';
import { Subscription } from 'rxjs';

import { AuthService } from '../auth/auth.service';
import { Amount } from '../shared/amount/amount';
import { Button } from '../shared/button/button';
import { Card } from '../shared/card/card';
import { formatSwissAmount } from '../shared/format';
import { MonthNav, MonthOption } from '../shared/month-nav/month-nav';
import { Notice } from '../shared/notice/notice';
import { TransactionService } from '../transactions/transaction.service';
import { SafeToSpendResponse } from './safe-to-spend.model';
import { SafeToSpendService } from './safe-to-spend.service';

/**
 * Zulässiger `month`-Query-Parameter. Derselbe Ausdruck wie in `category-overview.ts`: Ein
 * unbrauchbarer Wert darf nicht bis {@link formatMonth} durchkommen, sonst stünde dort ein
 * «Invalid Date» als Monatslabel.
 */
const MONTH_PATTERN = /^\d{4}-(0[1-9]|1[0-2])$/;

/** Aktueller Monat als `YYYY-MM` — die Obergrenze dessen, was angezeigt werden darf. */
function currentMonth(): string {
  const now = new Date();
  return toMonthString(now.getFullYear(), now.getMonth() + 1);
}

/** Verschiebt `YYYY-MM` um `delta` Monate; der Jahreswechsel fällt dabei aus `Date` selbst. */
function shiftMonth(month: string, delta: number): string {
  const [year, monthNumber] = month.split('-').map(Number);
  const shifted = new Date(year, monthNumber - 1 + delta, 1);
  return toMonthString(shifted.getFullYear(), shifted.getMonth() + 1);
}

/** `2026`, `7` → `"2026-07"`. */
function toMonthString(year: number, monthNumber: number): string {
  return `${year}-${String(monthNumber).padStart(2, '0')}`;
}

/** `"2026-07"` → `"Juli 2026"`. */
function formatMonth(month: string): string {
  const [year, monthNumber] = month.split('-').map(Number);
  const date = new Date(year, monthNumber - 1, 1);
  return new Intl.DateTimeFormat('de-CH', { month: 'long', year: 'numeric' }).format(date);
}

/**
 * Dashboard mit dem Safe-to-Spend-Widget (FE-STS-01/02/03/04, US-06 und US-12).
 *
 * <p>Zeigt den wöchentlichen Safe-to-Spend-Betrag gross und zentral, zusammen mit
 * dem Wochen-Label ("noch N Wochen im Monat"). Ist das Budget überzogen (`negative`),
 * steht darüber das rote Warn-Banner aus FE-STS-02 — der Text stammt wörtlich aus
 * US-06.
 *
 * <p>Ist kein Einkommen erfasst (`noIncome`), tritt an die Stelle des Betrags ein
 * Platzhalter, und darüber steht der Hinweis aus FE-STS-03. Hat die Heuristik aus
 * BE-STS-02 ein wiederkehrendes Muster gefunden (`incomeSuggestion`), bietet der
 * Hinweis den Betrag zur Übernahme an. Übernommen wird nie still: US-06 formuliert
 * das ausdrücklich als Rückfrage, und der `IncomeSuggestionService` nennt genau diese
 * Rückfrage als einzige Entschärfung dafür, dass eine monatliche Eigenübertragung
 * vom Sparkonto nicht von einem Lohneingang zu unterscheiden ist.
 *
 * <p><strong>Monatswechsel (FE-STS-04).</strong> Der angezeigte Monat steht im
 * Query-Parameter `month` und wird über dieselbe Mechanik gehalten wie in der
 * Kategorie-Übersicht: ein Signal, das aus der URL gespeist wird und zurück in sie
 * schreibt. Damit funktionieren Deep-Link, Browser-Zurück und eine von Hand editierte
 * Adresse ohne Sonderfall. Ein vergangener Monat liefert `status: 'CLOSED'` und wird
 * als «Abgeschlossen» angezeigt statt gerechnet (US-12); Zukunftsmonate bietet die
 * Oberfläche gar nicht erst an, weil das Backend sie mit HTTP 400 ablehnt. Ohne Parameter
 * steht die Seite auf dem laufenden Monat — die Monatsliste speist nur Dropdown und
 * Keine-Daten-Hinweis.
 *
 * <p>OnPush + Signals wie im übrigen Frontend; der lesende HTTP-Zugriff liegt im
 * zustandslosen {@link SafeToSpendService}, der schreibende im {@link AuthService},
 * dem `/api/users/me` und der `User`-State gehören.
 */
@Component({
  selector: 'app-dashboard',
  imports: [Card, Amount, Notice, Button, MonthNav, RouterLink],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Dashboard {
  private readonly safeToSpendService = inject(SafeToSpendService);
  private readonly authService = inject(AuthService);
  private readonly transactionService = inject(TransactionService);
  private readonly route = inject(ActivatedRoute);
  private readonly router = inject(Router);

  /** Aktuell angezeigter Monat im Format `YYYY-MM`. */
  readonly month = signal(currentMonth());

  /**
   * Anzahl Buchungen des laufenden Monats, deren Richtung der PDF-Parser nur angenommen hat
   * (BE-PDF-10, US-04). `0`, solange nichts geladen ist — der Normalfall.
   *
   * <p>Der Hinweis steht hier und nicht nur auf der Kategorie-Übersicht, weil der Schaden hier
   * eintritt: Eine als Belastung übernommene Gutschrift drückt genau diese Zahl. Ein Nutzer, der
   * die Übersicht nie öffnet, sähe einen Hinweis, der nur dort steht, nie — und der Bug wäre für
   * ihn unverändert stumm.
   *
   * <p>Auf den laufenden Monat begrenzt, wie der Safe-to-Spend selbst: Eine unsichere Buchung aus
   * dem März trägt zu dieser Zahl nichts bei, und ein Banner, das ihretwegen erschiene, behauptete
   * einen Zusammenhang, den es nicht gibt. Seit FE-STS-04 ist das keine Annahme mehr, sondern
   * folgt aus dem Backend: Ein Monat, für den gerechnet wird, *ist* der laufende — jeder frühere
   * kommt als `CLOSED` zurück, jeder spätere als HTTP 400.
   */
  readonly uncertainCount = signal(0);

  /** Geladene Antwort oder `null`, solange nichts geladen ist. */
  readonly data = signal<SafeToSpendResponse | null>(null);

  /** `true`, solange ein Request läuft. */
  readonly loading = signal(false);

  /** Fehlermeldung oder `null`, wenn kein Fehler vorliegt. */
  readonly errorMessage = signal<string | null>(null);

  /** `true`, solange das Übernehmen des Vorschlags läuft. */
  readonly saving = signal(false);

  /** Fehlermeldung des Übernehmen-Requests, oder `null`. */
  readonly saveErrorMessage = signal<string | null>(null);

  /** Menschlich lesbares Monatslabel, z. B. `"Juli 2026"`. */
  readonly monthLabel = computed(() => formatMonth(this.month()));

  /** `true`, wenn der angezeigte Monat der aktuelle Monat ist — sperrt "›". */
  readonly isCurrentMonth = computed(() => this.month() >= currentMonth());

  /**
   * Monate mit Ausgaben aus `GET /api/transactions/months`, roh wie geliefert (neuester zuerst).
   * Leer, solange nichts geladen ist oder der Request fehlgeschlagen ist.
   */
  private readonly loadedMonths = signal<readonly string[]>([]);

  /**
   * `true`, sobald die Monatsliste **erfolgreich** geladen ist.
   *
   * <p>Getrennt von {@link loadedMonths}, weil eine leere Liste zwei völlig verschiedene Dinge
   * bedeuten kann: ein Konto ohne Buchungen (dann stimmt «Keine Daten») oder ein
   * fehlgeschlagener Request (dann wäre «Keine Daten» eine Behauptung über etwas, das die Seite
   * nicht weiss).
   */
  private readonly monthsLoaded = signal(false);

  /**
   * Die Monate des Direktsprung-Dropdowns, neuester zuerst — dieselben zwei Regeln wie in der
   * Kategorie-Übersicht: der angezeigte Monat ist immer dabei, auch wenn er keine Ausgaben hat,
   * und Zukunftsmonate fallen raus, weil der Stepper sie ebenfalls sperrt.
   */
  readonly monthOptions = computed<readonly MonthOption[]>(() => {
    const current = currentMonth();
    const values = new Set(this.loadedMonths().filter((month) => month <= current));
    values.add(this.month());
    return [...values]
      .sort()
      .reverse()
      .map((value) => ({ value, label: formatMonth(value) }));
  });

  /**
   * `true`, wenn für den angezeigten Monat keine Ausgaben vorliegen (FE-CAT-08-Hinweis, US-12).
   *
   * <p>Grundlage ist `GET /api/transactions/months`, das laut seiner OpenAPI-Beschreibung Monate
   * **mit Ausgaben** liefert; ein Monat mit ausschliesslich Gutschriften gilt hier also als leer.
   * Dieselbe Grundlage, auf der der Hinweis in der Kategorie-Übersicht steht — die beiden Seiten
   * sollen für denselben Monat nicht Verschiedenes behaupten.
   */
  readonly noData = computed(
    () => this.monthsLoaded() && !this.loadedMonths().includes(this.month()),
  );

  /**
   * `true`, wenn das «Abgeschlossen»-Banner zu zeigen ist.
   *
   * <p>Der Keine-Daten-Hinweis verdrängt es: Für einen vergangenen Monat ohne jede Buchung sagt
   * «Abgeschlossen» nichts, woran der Nutzer etwas ändern könnte, «PDF hochladen?» dagegen schon.
   * Im laufenden Monat stehen beide nie zusammen zur Debatte, weil der nie `CLOSED` ist.
   */
  readonly closed = computed(() => this.data()?.status === 'CLOSED' && !this.noData());

  /** Wochen-Label, z. B. "noch 1 Woche im Monat" bzw. "noch 3 Wochen im Monat". */
  readonly weekLabel = computed(() => {
    const current = this.data();
    if (current === null) {
      return '';
    }
    return current.weeksLeft === 1
      ? 'noch 1 Woche im Monat'
      : `noch ${current.weeksLeft} Wochen im Monat`;
  });

  /**
   * `true` in der letzten Woche des Monats — dann verlangt US-06 zusätzlich zum Betrag den
   * Hinweis "Letzte Woche des Monats".
   *
   * <p>Hängt allein an `weeksLeft === 1` und gilt deshalb auch ohne erfasstes Einkommen: der
   * Wert kommt laut `SafeToSpendResponse` rein aus dem Datum, und das Wochen-Label steht im
   * No-Income-Fall ebenfalls da. Der Hinweis ersetzt das Label nicht, er tritt daneben —
   * "noch 1 Woche im Monat" sagt, wie lange, der Hinweis sagt, warum der Divisor nicht
   * weiter sinkt.
   */
  readonly lastWeek = computed(() => this.data()?.weeksLeft === 1);

  /**
   * Der Vorschlagssatz aus US-06, oder `null`, wenn es nichts vorzuschlagen gibt.
   *
   * <p>Der Wortlaut ist wörtlich aus der Story übernommen; der Betrag steht dort **vor** der
   * Währung ("von X CHF erkannt"), weshalb hier {@link formatSwissAmount} zum Zug kommt und
   * nicht die `app-amount`-Komponente, die "CHF" voranstellt.
   */
  readonly suggestionText = computed(() => {
    const suggestion = this.data()?.incomeSuggestion;
    if (suggestion === null || suggestion === undefined) {
      return null;
    }
    return `Regelmässige Gutschrift von ${formatSwissAmount(suggestion)} CHF erkannt — als Monatseinkommen übernehmen?`;
  });

  /**
   * Der Hinweistext zur ungeprüften Buchungsrichtung, oder `null`, wenn nichts offen ist.
   *
   * <p>Sagt ausdrücklich, in welche Richtung die Zahl falsch sein kann («zu tief»). Ein blosses
   * «N Buchungen prüfen» liesse offen, ob der Betrag zu hoch oder zu tief ist — und damit auch,
   * ob der Nutzer heute vorsichtiger sein sollte oder nicht.
   */
  readonly uncertainText = computed(() => {
    const count = this.uncertainCount();
    if (count === 0) {
      return null;
    }
    return count === 1
      ? 'Bei 1 Buchung dieses Monats ist unklar, ob sie eine Ausgabe oder eine Gutschrift war. Dein Safe-to-Spend kann deshalb zu tief sein.'
      : `Bei ${count} Buchungen dieses Monats ist unklar, ob sie Ausgaben oder Gutschriften waren. Dein Safe-to-Spend kann deshalb zu tief sein.`;
  });

  /**
   * `false`, bis die erste URL-Auswertung gelaufen ist. Ohne diese Unterscheidung würde die
   * Gleichheits-Wache in {@link syncFromUrl} das Erstladen verschlucken, sobald die URL keinen
   * Parameter trägt — der ausgelesene Monat ist dann von Anfang an derselbe wie der angezeigte.
   */
  private initialLoadDone = false;

  /**
   * Subscription des zuletzt gestarteten Safe-to-Spend-Requests, um ihn beim Monatswechsel zu
   * canceln.
   *
   * <p>Ohne das Canceln überschreibt eine spät eintreffende Antwort des alten Monats die des
   * neuen — und weil die Card ihr Monatslabel als `meta` trägt, stünde der Betrag dann unter der
   * Überschrift eines anderen Monats. Dieselbe Absicherung wie in `category-overview.ts`.
   */
  private pendingRequest: Subscription | undefined;

  /**
   * Subscription des zuletzt gestarteten Prüflisten-Requests (BE-PDF-10).
   *
   * <p>Eigenes Feld und nicht dasselbe wie {@link pendingRequest}: Für einen vergangenen Monat
   * unterbleibt der Folge-Request ganz, es gibt also keine neue Antwort, die eine veraltete später
   * überschriebe. Abgeräumt wird deshalb vor dem frühen `return`.
   */
  private pendingUncertainRequest: Subscription | undefined;

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
    this.goTo(shiftMonth(this.month(), -1));
  }

  /** Einen Monat vor. */
  nextMonth(): void {
    this.goTo(shiftMonth(this.month(), 1));
  }

  /** Springt direkt auf einen Monat (Dropdown der {@link MonthNav}). */
  selectMonth(month: string): void {
    if (month === this.month()) {
      return;
    }
    this.goTo(month);
  }

  /**
   * Übernimmt den Einkommens-Vorschlag als Monatseinkommen (`PUT /api/users/me/income`).
   *
   * <p>Nach Erfolg wird Safe-to-Spend neu geladen: der dann erscheinende Betrag ist die
   * Bestätigung, dass die Übernahme gewirkt hat. Eine blosse Erfolgsmeldung liesse den
   * Nutzer mit dem Platzhalter zurück, den er gerade loswerden wollte.
   */
  applySuggestion(): void {
    const suggestion = this.data()?.incomeSuggestion;
    if (suggestion === null || suggestion === undefined || this.saving()) {
      return;
    }

    this.saving.set(true);
    this.saveErrorMessage.set(null);

    this.authService.updateIncome(suggestion).subscribe({
      next: () => {
        this.saving.set(false);
        this.load();
      },
      error: (_err: HttpErrorResponse) => {
        this.saveErrorMessage.set('Das Einkommen konnte nicht gespeichert werden.');
        this.saving.set(false);
      },
    });
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
   * <p>Ohne brauchbaren Parameter gilt der laufende Monat, genau wie in der Kategorie-Übersicht.
   * Die Monatsliste speist deshalb allein Dropdown und Keine-Daten-Hinweis, nie den Default: Ein
   * Default aus der Liste stellte die beiden Schwesteransichten am selben Tag auf verschiedene
   * Monate, und weil Kontoauszüge erst nach Monatsende kommen, wäre das der Regelfall — die
   * Startseite zeigte dann statt der Kernzahl das «Abgeschlossen»-Banner des Vormonats.
   *
   * <p>Die Gleichheits-Wache unten ist der Grund, warum Stepper und Sprung synchron laden dürfen,
   * ohne dass ein zweiter Request folgt.
   */
  private syncFromUrl(params: ParamMap): void {
    const raw = params.get('month');
    // Ein Zukunftsmonat ist hier genauso unbrauchbar wie ein kaputtes Format: Das Backend
    // beantwortet ihn mit 400, und der Stepper führt ohnehin nicht dorthin.
    const valid = raw !== null && MONTH_PATTERN.test(raw) && raw <= currentMonth();
    const month = valid ? raw : currentMonth();

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
   * Lädt die Monate für Dropdown und Keine-Daten-Hinweis.
   *
   * <p>Einmal beim Aufbau der Seite: die Liste ändert sich nur durch einen Import, und der führt
   * ohnehin über eine andere Seite hierher zurück.
   */
  private loadAvailableMonths(): void {
    this.transactionService.availableMonths().subscribe({
      next: (months) => {
        this.loadedMonths.set(months);
        this.monthsLoaded.set(true);
      },
      error: (_err: HttpErrorResponse) => {
        // Bewusst ohne Meldung: Das Dropdown fällt auf den angezeigten Monat zurück und der
        // Keine-Daten-Hinweis bleibt weg, statt etwas zu behaupten. Safe-to-Spend selbst
        // funktioniert unverändert — er hängt nicht an dieser Liste — und eine rote Meldung
        // stünde in keinem Verhältnis zur Einschränkung.
      },
    });
  }

  private load(): void {
    // Einen noch laufenden Request canceln, bevor ein neuer startet — sonst kann bei schneller
    // Monat-Navigation die spätere Antwort von der früheren überschrieben werden (Race Condition).
    // Dasselbe Muster wie in `category-overview.ts`.
    this.pendingRequest?.unsubscribe();
    this.loading.set(true);
    this.errorMessage.set(null);

    const month = this.month();
    this.pendingRequest = this.safeToSpendService.getSafeToSpend(month).subscribe({
      next: (response) => {
        this.data.set(response);
        this.loading.set(false);
      },
      error: (_err: HttpErrorResponse) => {
        this.data.set(null);
        this.errorMessage.set('Der Safe-to-Spend-Betrag konnte nicht geladen werden.');
        this.loading.set(false);
      },
    });
    this.loadUncertainCount(month);
  }

  /**
   * Lädt die Zahl der ungeprüften Buchungsrichtungen — nur für den laufenden Monat.
   *
   * <p>Eigener Request neben dem Safe-to-Spend statt eines Felds in dessen Antwort: Der
   * `SafeToSpendService` liegt im `budget`-Modul und liest ausschliesslich über schmale Ports,
   * über die laut deren Javadoc <em>Beträge</em> gehen und keine Buchungseigenschaften. Einen
   * Zähler dort durchzureichen hiesse, diese Kante für eine reine Anzeigefrage aufzuweiten.
   *
   * <p>Für einen vergangenen Monat unterbleibt der Request ganz: Dort wird kein Safe-to-Spend
   * berechnet, und der Hinweis begründet sich allein darüber, dass die Zahl daneben zu tief sein
   * könnte. Ohne Zahl trüge er nichts — und ein Request für eine Ausgabe, die niemand sieht,
   * wäre reine Last.
   *
   * <p>Ein Fehler bleibt bewusst still: Der Safe-to-Spend daneben ist korrekt geladen, und eine
   * rote Meldung für einen ausgefallenen Zusatzhinweis stünde in keinem Verhältnis — dieselbe
   * Abwägung wie beim Monats-Dropdown der Kategorie-Übersicht. Der Hinweis erscheint dann nicht;
   * die Prüfliste selbst bleibt über die Kategorie-Übersicht erreichbar.
   */
  private loadUncertainCount(month: string): void {
    // Vor dem frühen `return` und nicht danach: Ein noch offener Request des laufenden Monats
    // würde sonst beim Wechsel in einen vergangenen Monat weiterlaufen und den Hinweis über
    // dessen «Abgeschlossen»-Banner setzen — für Buchungen, die zu einem anderen Monat gehören.
    // `uncertainCount.set(0)` räumt nur den Zustand ab, nicht die Subscription.
    this.pendingUncertainRequest?.unsubscribe();
    this.uncertainCount.set(0);
    if (month !== currentMonth()) {
      return;
    }

    this.pendingUncertainRequest = this.transactionService.uncertainDirections(month).subscribe({
      next: (transactions) => this.uncertainCount.set(transactions.length),
      error: (_err: HttpErrorResponse) => {
        // Siehe Javadoc: bewusst ohne Meldung.
      },
    });
  }
}
