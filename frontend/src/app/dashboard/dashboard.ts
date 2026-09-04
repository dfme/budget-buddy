import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';

import { AuthService } from '../auth/auth.service';
import { Amount } from '../shared/amount/amount';
import { Button } from '../shared/button/button';
import { Card } from '../shared/card/card';
import { formatSwissAmount } from '../shared/format';
import { Notice } from '../shared/notice/notice';
import { TransactionService } from '../transactions/transaction.service';
import { SafeToSpendResponse } from './safe-to-spend.model';
import { SafeToSpendService } from './safe-to-spend.service';

/** Aktueller Monat als `YYYY-MM` — das Fenster, für das Safe-to-Spend gilt. */
function currentMonth(): string {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
}

/**
 * Dashboard mit dem Safe-to-Spend-Widget (FE-STS-01/02/03, US-06).
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
 * <p>OnPush + Signals wie im übrigen Frontend; der lesende HTTP-Zugriff liegt im
 * zustandslosen {@link SafeToSpendService}, der schreibende im {@link AuthService},
 * dem `/api/users/me` und der `User`-State gehören.
 */
@Component({
  selector: 'app-dashboard',
  imports: [Card, Amount, Notice, Button],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Dashboard {
  private readonly safeToSpendService = inject(SafeToSpendService);
  private readonly authService = inject(AuthService);
  private readonly transactionService = inject(TransactionService);

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
   * einen Zusammenhang, den es nicht gibt.
   */
  readonly uncertainCount = signal(0);

  /** Geladene Antwort oder `null`, solange nichts geladen ist. */
  readonly data = signal<SafeToSpendResponse | null>(null);

  /** `true`, solange der Request läuft. */
  readonly loading = signal(false);

  /** Fehlermeldung oder `null`, wenn kein Fehler vorliegt. */
  readonly errorMessage = signal<string | null>(null);

  /** `true`, solange das Übernehmen des Vorschlags läuft. */
  readonly saving = signal(false);

  /** Fehlermeldung des Übernehmen-Requests, oder `null`. */
  readonly saveErrorMessage = signal<string | null>(null);

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

  constructor() {
    this.load();
    this.loadUncertainCount();
  }

  /**
   * Lädt die Zahl der ungeprüften Buchungsrichtungen des laufenden Monats.
   *
   * <p>Eigener Request neben dem Safe-to-Spend statt eines Felds in dessen Antwort: Der
   * `SafeToSpendService` liegt im `budget`-Modul und liest ausschliesslich über schmale Ports,
   * über die laut deren Javadoc <em>Beträge</em> gehen und keine Buchungseigenschaften. Einen
   * Zähler dort durchzureichen hiesse, diese Kante für eine reine Anzeigefrage aufzuweiten.
   *
   * <p>Ein Fehler bleibt bewusst still: Der Safe-to-Spend daneben ist korrekt geladen, und eine
   * rote Meldung für einen ausgefallenen Zusatzhinweis stünde in keinem Verhältnis — dieselbe
   * Abwägung wie beim Monats-Dropdown der Kategorie-Übersicht. Der Hinweis erscheint dann nicht;
   * die Prüfliste selbst bleibt über die Kategorie-Übersicht erreichbar.
   */
  private loadUncertainCount(): void {
    this.transactionService.uncertainDirections(currentMonth()).subscribe({
      next: (transactions) => this.uncertainCount.set(transactions.length),
      error: (_err: HttpErrorResponse) => {
        // Siehe Javadoc: bewusst ohne Meldung.
      },
    });
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

  private load(): void {
    this.loading.set(true);
    this.errorMessage.set(null);

    this.safeToSpendService.getSafeToSpend().subscribe({
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
  }
}
