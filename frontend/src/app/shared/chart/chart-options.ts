import { FontSpec, TooltipOptions } from 'chart.js';

import { formatSwissAmount } from '../format';
import { ChartPalette } from './chart-theme';

/** Schriftgrösse in Charts — eine Stufe unter dem Fliesstext, wie im Prototyp. */
const CHART_FONT_SIZE = 12;

/**
 * Schrift für Achsen, Ticks und Tooltips aus der Palette.
 *
 * <p>Ist die Familie (noch) nicht ermittelbar — etwa im Test ohne geladenes Stylesheet —
 * bleibt sie undefiniert, sodass Chart.js seinen eigenen Default nutzt statt einer leeren
 * `font-family`.
 */
export function chartFont(palette: ChartPalette): Partial<FontSpec> {
  return { family: palette.fontFamily || undefined, size: CHART_FONT_SIZE };
}

/**
 * Tooltip-Look der Variante A: Grund in `--c-ink`, Text in `--c-surface`.
 *
 * <p>Die Gegenfarbe als Text funktioniert in beiden Themes, ohne dafür eigene Tokens
 * einzuführen (Begründung aus `design/variant-a/charts.js`).
 */
export function tooltipOptions(palette: ChartPalette): Partial<TooltipOptions> {
  return {
    backgroundColor: palette.ink,
    titleColor: palette.surface,
    bodyColor: palette.surface,
    padding: 10,
    cornerRadius: 8,
    displayColors: false,
    titleFont: chartFont(palette),
    bodyFont: chartFont(palette),
  };
}

/** Formatiert einen Betrag als Tooltip-Text, z. B. `CHF 1'234.56`. */
export function chfTooltip(value: number): string {
  return `CHF ${formatSwissAmount(value)}`;
}

/**
 * Achsenbeschriftung in Tausenderschritten, z. B. `2.5k`.
 *
 * <p>Die Null bleibt `0` statt `0k` — der Prototyp schreibt dort `0k`, was als
 * Achsen-Nullpunkt schlicht falsch aussieht.
 */
export function thousandsTick(value: number): string {
  return value === 0 ? '0' : `${value / 1000}k`;
}
