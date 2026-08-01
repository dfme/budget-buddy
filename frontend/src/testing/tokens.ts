/**
 * Setzt Design-Tokens für Tests direkt als Inline-Custom-Properties auf `<html>`.
 *
 * <p>Im Unit-Test ist `styles.scss` nicht geladen, `getComputedStyle` liefert für die
 * `--c-*`/`--cat-*`-Tokens also leere Strings. Statt die echten Farbwerte in den Specs zu
 * wiederholen (und damit ein zweites Mal zu pflegen) setzen die Tests hier bewusst
 * erfundene, gut unterscheidbare Werte — geprüft wird, dass die Komponenten *aus den
 * Tokens* lesen, nicht welche Farbe Variante A gerade hat.
 */
export function setTokens(tokens: Readonly<Record<string, string>>): void {
  for (const [name, value] of Object.entries(tokens)) {
    document.documentElement.style.setProperty(name, value);
  }
}

/** Entfernt alle per {@link setTokens} gesetzten Werte und das `data-theme`-Attribut. */
export function clearTokens(): void {
  document.documentElement.removeAttribute('style');
  document.documentElement.removeAttribute('data-theme');
}
