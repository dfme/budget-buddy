import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { ChartData, ChartOptions } from 'chart.js';
import { BaseChartDirective } from 'ng2-charts';

import { chartFont, chfTooltip, thousandsTick, tooltipOptions } from './chart-options';
import { CHART_PROVIDERS } from './chart-providers';
import { ChartTheme } from './chart-theme';

/** Ein Balken — ein Monat mit seiner Ausgabensumme. */
export interface BarPoint {
  /** Kurzes Achsenlabel, z. B. `"Jul"`. */
  readonly label: string;
  /** Betrag in CHF. */
  readonly value: number;
}

/**
 * Bar-Chart «Ausgabenverlauf» der Design-Variante A (FE-UI-05).
 *
 * <p>Reiner Baustein ohne eigene Datenbeschaffung: die Balken kommen über {@link data}
 * herein (Monatsvergleich US-10). Der hervorgehobene Balken — standardmässig der letzte,
 * also der laufende Monat — trägt die Akzentfarbe, die übrigen die neutrale Linienfarbe.
 * Alle Farben stammen aus den Tokens und folgen dem Theme.
 *
 * <p>Die Hervorhebung steht zusätzlich in der Screenreader-Beschreibung: eine
 * Unterscheidung allein über Farbe wäre nicht zugänglich.
 */
@Component({
  selector: 'app-bar-chart',
  imports: [BaseChartDirective],
  templateUrl: './bar-chart.html',
  styleUrl: './bar-chart.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [CHART_PROVIDERS],
})
export class BarChart {
  private readonly theme = inject(ChartTheme);

  /** Balken in Anzeigereihenfolge (ältester zuerst). */
  readonly data = input.required<readonly BarPoint[]>();

  /**
   * Index des hervorgehobenen Balkens. Ohne Angabe ist es der letzte Eintrag — der
   * laufende Monat. Ein Wert ausserhalb des Bereichs hebt keinen Balken hervor.
   */
  readonly highlightIndex = input<number>();

  /**
   * Beschreibung für Screenreader. Ohne Angabe wird aus den Daten eine Aufzählung aller
   * Monate mit Beträgen erzeugt, inklusive Markierung des hervorgehobenen Balkens.
   */
  readonly ariaLabel = input<string>();

  /** Effektiv hervorgehobener Index (aufgelöster Default). */
  readonly highlighted = computed(() => this.highlightIndex() ?? this.data().length - 1);

  /** Effektive Screenreader-Beschreibung des Canvas. */
  readonly description = computed(() => {
    const explicit = this.ariaLabel();
    if (explicit) {
      return explicit;
    }
    const points = this.data();
    if (points.length === 0) {
      return 'Balkendiagramm der monatlichen Ausgaben: keine Daten vorhanden.';
    }
    const highlighted = this.highlighted();
    const parts = points.map((point, index) => {
      const amount = `${point.label} ${chfTooltip(point.value)}`;
      return index === highlighted ? `${amount} (hervorgehoben)` : amount;
    });
    return `Balkendiagramm der monatlichen Ausgaben: ${parts.join(', ')}.`;
  });

  /** Chart.js-Daten; Akzentfarbe nur für den hervorgehobenen Balken. */
  readonly chartData = computed<ChartData<'bar', number[], string>>(() => {
    const palette = this.theme.palette();
    const points = this.data();
    const highlighted = this.highlighted();
    return {
      labels: points.map((point) => point.label),
      datasets: [
        {
          data: points.map((point) => point.value),
          backgroundColor: points.map((_point, index) =>
            index === highlighted ? palette.accent : palette.lineStrong,
          ),
          borderRadius: 4,
          borderSkipped: false,
          barPercentage: 0.62,
        },
      ],
    };
  });

  /** Chart.js-Optionen; keine Chart.js-Legende, Gitter nur horizontal. */
  readonly chartOptions = computed<ChartOptions<'bar'>>(() => {
    const palette = this.theme.palette();
    const font = chartFont(palette);
    return {
      responsive: true,
      maintainAspectRatio: false,
      plugins: {
        legend: { display: false },
        tooltip: {
          ...tooltipOptions(palette),
          callbacks: { label: (ctx) => chfTooltip(ctx.parsed.y ?? 0) },
        },
      },
      scales: {
        x: {
          grid: { display: false },
          border: { color: palette.line },
          ticks: { color: palette.ink2, font },
        },
        y: {
          beginAtZero: true,
          border: { display: false },
          grid: { color: palette.line },
          ticks: {
            color: palette.ink2,
            font,
            maxTicksLimit: 4,
            callback: (value) => thousandsTick(Number(value)),
          },
        },
      },
    };
  });
}
