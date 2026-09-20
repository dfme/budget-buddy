/**
 * Die 17 Kategorien der Hybrid-Kategorisierung als Frontend-Liste.
 *
 * <p>Spiegel des Backend-Enums
 * [`Category.java`](../../../../backend/src/main/java/com/budgetbuddy/categorization/Category.java)
 * (gleiche Reihenfolge). Der {@link CategoryMeta.slug} ist die kleingeschriebene Form des
 * Enum-Namens und zugleich der Schlüssel der `$categories`-Map in `styles/_tokens.scss` —
 * die Farben kommen ausschliesslich aus den `--cat-<slug>`-Tokens, nicht aus dieser Datei.
 * Diese Liste liefert Slug, deutsches Label und Icon für Iteration (Badges, Showcase, Tests).
 *
 * <p>Das **Icon** kam mit BE-CAT-10 dazu und ist bewusst nur hier zuhause, nicht im Backend-Enum:
 * Es trägt keine fachliche Bedeutung, sondern ist Anzeige. Die DB speichert weiterhin das Label,
 * und der Claude-Call kennt nur die Enum-Namen.
 */
export interface CategoryMeta {
  /** Kleingeschriebener Enum-Name, z. B. `"lebensmittel"`. Schlüssel der `--cat-*`-Tokens. */
  readonly slug: string;
  /** Deutsches Anzeige-Label, exakt wie {@code Category.getLabel()}, z. B. `"Lebensmittel"`. */
  readonly label: string;
  /**
   * Unicode-Glyph der Kategorie, z. B. `"🛒"` — eindeutig über alle 17 Kategorien.
   *
   * <p>Farb-Emoji statt monochromer Textglyphe: Für 17 unterscheidbare Kategorien gibt es kein
   * brauchbares monochromes Vokabular. Der Preis ist, dass das Icon in hellem und dunklem Theme
   * dieselben festen Farben behält — die Kategoriefarbe trägt daneben weiterhin der Text.
   * Glyphen, die ohne Variantenselektor als Textzeichen rendern würden (🛡️ ⚕️ 🍽️ 🗂️ ✈️),
   * tragen ein angehängtes U+FE0F.
   */
  readonly icon: string;
}

export const CATEGORIES: readonly CategoryMeta[] = [
  { slug: 'wohnen', label: 'Wohnen', icon: '🏠' },
  { slug: 'lebensmittel', label: 'Lebensmittel', icon: '🛒' },
  { slug: 'transport', label: 'Transport', icon: '🚆' },
  { slug: 'versicherung', label: 'Versicherung', icon: '🛡️' },
  { slug: 'telekom', label: 'Telekom', icon: '📱' },
  { slug: 'gesundheit', label: 'Gesundheit', icon: '⚕️' },
  { slug: 'freizeit', label: 'Freizeit', icon: '🎭' },
  { slug: 'restaurant', label: 'Restaurant', icon: '🍽️' },
  { slug: 'shopping', label: 'Shopping', icon: '🛍️' },
  { slug: 'bildung', label: 'Bildung', icon: '🎓' },
  { slug: 'einkommen', label: 'Einkommen', icon: '💵' },
  { slug: 'sparen', label: 'Sparen', icon: '🐷' },
  { slug: 'persoenliches', label: 'Persönliches', icon: '👤' },
  { slug: 'steuern', label: 'Steuern', icon: '🧾' },
  { slug: 'bargeldbezug', label: 'Bargeldbezug', icon: '🏧' },
  { slug: 'reisen', label: 'Reisen', icon: '✈️' },
  { slug: 'sonstiges', label: 'Sonstiges', icon: '🗂️' },
] as const;

/** Nur die Slugs — praktisch für Validierung und Iteration. */
export const CATEGORY_SLUGS: readonly string[] = CATEGORIES.map((c) => c.slug);

/** Slug → Eintrag. Einmal gebaut statt bei jedem Lookup durch die Liste zu laufen. */
const BY_SLUG: ReadonlyMap<string, CategoryMeta> = new Map(CATEGORIES.map((c) => [c.slug, c]));

/**
 * Icon zu einem Kategorie-Slug, oder `undefined` für einen unbekannten bzw. fehlenden Slug.
 *
 * <p>`undefined` ist hier kein Fehlerfall: Die Kategorie kommt als deutsches Label aus der API,
 * und ein Label, das keine der 17 Kategorien trifft, ist genau das, was `badge` mit dem neutralen
 * Punkt statt eines Icons darstellt. Ein Platzhalter-Glyph wäre die schlechtere Antwort — er
 * behauptete eine Kategorie, die es nicht gibt.
 */
export function categoryIcon(slug: string | undefined): string | undefined {
  return slug === undefined ? undefined : BY_SLUG.get(slug)?.icon;
}
