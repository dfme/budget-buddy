/**
 * Formatiert einen Betrag im Schweizer Format `1'234.56` — Apostroph als
 * Tausendertrennung, Punkt als Dezimaltrenner, immer zwei Nachkommastellen.
 *
 * <p>Gibt den **Betrag** zurück (ohne Vorzeichen); das Vorzeichen tragen die aufrufenden
 * Stellen separat, damit positiv/negativ nicht nur über Farbe unterschieden wird (a11y).
 *
 * @param value Betrag; das Vorzeichen wird ignoriert (Absolutwert wird formatiert).
 */
export function formatSwissAmount(value: number): string {
  const [integer, decimals] = Math.abs(value).toFixed(2).split('.');
  const grouped = integer.replace(/\B(?=(\d{3})+(?!\d))/g, "'");
  return `${grouped}.${decimals}`;
}
