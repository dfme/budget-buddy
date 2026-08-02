import { ChangeDetectionStrategy, Component, computed, inject, input } from '@angular/core';
import { ChartData, ChartOptions } from 'chart.js';
import { BaseChartDirective } from 'ng2-charts';

import { formatSwissAmount } from '../format';
import { chfTooltip, tooltipOptions } from './chart-options';
import { CHART_PROVIDERS } from './chart-providers';
import { ChartTheme } from './chart-theme';

/** Ein Segment des Donuts — eine Kategorie mit ihrer Ausgabensumme. */
export interface DonutSlice {
  /** Kategorie-Slug, z. B. `"lebensmittel"` — bestimmt die Farbe über `--cat-<slug>`. */
  readonly slug: string;
  /** Deutsches Anzeige-Label, z. B. `"Lebensmittel"`. */
  readonly label: string;
  /** Betrag in CHF. */
  readonly value: number;
}

/**
 * Donut-Chart «Ausgaben nach Kategorie» der Design-Variante A (FE-UI-05).
 *
 * <p>Reiner Baustein ohne eigene Datenbeschaffung: die Segmente kommen über
 * {@link data} herein und lassen sich damit direkt aus einem Feature-Service speisen
 * (z. B. `CategorySummary` aus BE-CAT-05). Die Farben stammen ausschliesslich aus den
 * `--cat-<slug>`-Tokens; bei Theme-Wechsel baut sich die Konfiguration über
 * {@link ChartTheme} von selbst neu auf.
 *
 * <p>Die Legende ist bewusst HTML und nicht die Chart.js-Legende: sie bricht auf 375 px
 * zuverlässig um und zeigt die Beträge direkt mit (Entscheid aus dem Prototyp).
 */
@Component({
  selector: 'app-donut-chart',
  imports: [BaseChartDirective],
  templateUrl: './donut-chart.html',
  styleUrl: './donut-chart.scss',
  changeDetection: ChangeDetectionStrategy.OnPush,
  providers: [CHART_PROVIDERS],
})
export class DonutChart {
  private readonly theme = inject(ChartTheme);

  /** Segmente, absteigend nach Betrag erwartet (die Reihenfolge wird 1:1 übernommen). */
  readonly data = input.required<readonly DonutSlice[]>();

  /** Ob die HTML-Legende unter dem Chart erscheint. */
  readonly showLegend = input(true);

  /**
   * Beschreibung für Screenreader. Ohne Angabe wird aus den Daten eine Aufzählung
   * aller Kategorien mit Beträgen erzeugt.
   */
  readonly ariaLabel = input<string>();

  /** Effektive Screenreader-Beschreibung des Canvas. */
  readonly description = computed(() => {
    const explicit = this.ariaLabel();
    if (explicit) {
      return explicit;
    }
    const slices = this.data();
    if (slices.length === 0) {
      return 'Donut-Diagramm der Ausgaben nach Kategorie: keine Ausgaben vorhanden.';
    }
    const parts = slices.map((slice) => `${slice.label} ${chfTooltip(slice.value)}`);
    return `Donut-Diagramm der Ausgaben nach Kategorie: ${parts.join(', ')}.`;
  });

  /** Chart.js-Daten; Farben je Segment aus dem `--cat-<slug>`-Token. */
  readonly chartData = computed<ChartData<'doughnut', number[], string>>(() => {
    const palette = this.theme.palette();
    const slices = this.data();
    return {
      labels: slices.map((slice) => slice.label),
      datasets: [
        {
          data: slices.map((slice) => slice.value),
          // Unbekannter Slug → neutrale Linienfarbe statt Chart.js-Default, damit ein
          // Tippfehler im Slug als graues Segment auffällt und nicht als bunte Farbe.
          backgroundColor: slices.map(
            (slice) => palette.categories[slice.slug] || palette.lineStrong,
          ),
          // Trennlinie in der Kartenfarbe — in beiden Themes sauber getrennt.
          borderColor: palette.surface,
          borderWidth: 2,
        },
      ],
    };
  });

  /** Chart.js-Optionen; dünner Ring, keine Chart.js-Legende. */
  readonly chartOptions = computed<ChartOptions<'doughnut'>>(() => {
    const palette = this.theme.palette();
    return {
      responsive: true,
      maintainAspectRatio: false,
      // Dünner Ring — die Kategorie-Verteilung ist die Aussage, nicht die Fläche.
      cutout: '72%',
      // Keine Schrift auf Chart-Ebene: der Donut hat keine Skalen, die Chart.js-Legende
      // ist aus, und die Tooltip-Schrift setzt `tooltipOptions` selbst — ein `font` hier
      // hätte keinen Konsumenten.
      plugins: {
        legend: { display: false },
        tooltip: {
          ...tooltipOptions(palette),
          callbacks: { label: (ctx) => `${ctx.label}: ${chfTooltip(ctx.parsed)}` },
        },
      },
    };
  });

  /** Betrag im Schweizer Format für die HTML-Legende. */
  format(value: number): string {
    return formatSwissAmount(value);
  }
}
