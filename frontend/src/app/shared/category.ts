/**
 * Die 13 Kategorien der Hybrid-Kategorisierung als Frontend-Liste.
 *
 * <p>Spiegel des Backend-Enums
 * [`Category.java`](../../../../backend/src/main/java/com/budgetbuddy/categorization/Category.java)
 * (gleiche Reihenfolge). Der {@link CategoryMeta.slug} ist die kleingeschriebene Form des
 * Enum-Namens und zugleich der Schlüssel der `$categories`-Map in `styles/_tokens.scss` —
 * die Farben kommen ausschliesslich aus den `--cat-<slug>`-Tokens, nicht aus dieser Datei.
 * Diese Liste liefert nur Slug + deutsches Label für Iteration (Badges, Showcase, Tests).
 */
export interface CategoryMeta {
  /** Kleingeschriebener Enum-Name, z. B. `"lebensmittel"`. Schlüssel der `--cat-*`-Tokens. */
  readonly slug: string;
  /** Deutsches Anzeige-Label, exakt wie {@code Category.getLabel()}, z. B. `"Lebensmittel"`. */
  readonly label: string;
}

export const CATEGORIES: readonly CategoryMeta[] = [
  { slug: 'wohnen', label: 'Wohnen' },
  { slug: 'lebensmittel', label: 'Lebensmittel' },
  { slug: 'transport', label: 'Transport' },
  { slug: 'versicherung', label: 'Versicherung' },
  { slug: 'telekom', label: 'Telekom' },
  { slug: 'gesundheit', label: 'Gesundheit' },
  { slug: 'freizeit', label: 'Freizeit' },
  { slug: 'restaurant', label: 'Restaurant' },
  { slug: 'shopping', label: 'Shopping' },
  { slug: 'bildung', label: 'Bildung' },
  { slug: 'einkommen', label: 'Einkommen' },
  { slug: 'sparen', label: 'Sparen' },
  { slug: 'sonstiges', label: 'Sonstiges' },
] as const;

/** Nur die Slugs — praktisch für Validierung und Iteration. */
export const CATEGORY_SLUGS: readonly string[] = CATEGORIES.map((c) => c.slug);
