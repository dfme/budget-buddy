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
    setProperties.add(name);
  }
}

/** Namen aller in diesem Testlauf gesetzten Properties — Grundlage für {@link clearTokens}. */
const setProperties = new Set<string>();

/**
 * Entfernt die per {@link setTokens} gesetzten Werte und das `data-theme`-Attribut.
 *
 * <p>Bewusst eigenschaftsweise statt `removeAttribute('style')`: das Attribut komplett zu
 * löschen würde auch Inline-Styles mitnehmen, die der Test gar nicht gesetzt hat.
 */
export function clearTokens(): void {
  for (const name of setProperties) {
    document.documentElement.style.removeProperty(name);
  }
  setProperties.clear();
  document.documentElement.removeAttribute('data-theme');
}
