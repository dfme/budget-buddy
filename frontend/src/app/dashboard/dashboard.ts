import { HttpErrorResponse } from '@angular/common/http';
import { ChangeDetectionStrategy, Component, computed, inject, signal } from '@angular/core';

import { Amount } from '../shared/amount/amount';
import { Card } from '../shared/card/card';
import { Notice } from '../shared/notice/notice';
import { SafeToSpendResponse } from './safe-to-spend.model';
import { SafeToSpendService } from './safe-to-spend.service';

/**
 * Dashboard mit dem Safe-to-Spend-Widget (FE-STS-01, US-06).
 *
 * <p>Zeigt den wöchentlichen Safe-to-Spend-Betrag gross und zentral, zusammen mit
 * dem Wochen-Label ("noch N Wochen im Monat"). Das Negativ-Banner (FE-STS-02) und
 * der No-Income-Hinweis mit Einkommens-Vorschlag (FE-STS-03) sind eigene, auf
 * diesem Widget aufbauende Issues — hier wird der `amount: null`-Fall nur so weit
 * behandelt, dass kein `NaN`/`CHF 0.00` als irreführender Platzhalter erscheint.
 *
 * <p>OnPush + Signals wie im übrigen Frontend; der HTTP-Zugriff liegt im
 * zustandslosen {@link SafeToSpendService}.
 */
@Component({
  selector: 'app-dashboard',
  imports: [Card, Amount, Notice],
  templateUrl: './dashboard.html',
  styleUrl: './dashboard.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
})
export class Dashboard {
  private readonly safeToSpendService = inject(SafeToSpendService);

  /** Geladene Antwort oder `null`, solange nichts geladen ist. */
  readonly data = signal<SafeToSpendResponse | null>(null);

  /** `true`, solange der Request läuft. */
  readonly loading = signal(false);

  /** Fehlermeldung oder `null`, wenn kein Fehler vorliegt. */
  readonly errorMessage = signal<string | null>(null);

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

  constructor() {
    this.load();
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
