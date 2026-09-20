import { CATEGORIES, CATEGORY_SLUGS, categoryIcon } from './category';

/**
 * Die Kategorienliste ist ein Spiegel von `Category.java` — geprüft wird deshalb nicht, dass sie
 * bestimmte Werte enthält (das stünde doppelt und liefe auseinander), sondern dass die Zusagen
 * halten, auf die sich Badge, Chip, Chart-Theme und die `--cat-*`-Tokens verlassen.
 */
describe('CATEGORIES', () => {
  it('hat 17 Einträge', () => {
    expect(CATEGORIES).toHaveLength(17);
  });

  it('hat paarweise eindeutige Slugs', () => {
    expect(new Set(CATEGORY_SLUGS).size).toBe(CATEGORIES.length);
  });

  it('hat paarweise eindeutige Labels', () => {
    const labels = CATEGORIES.map((c) => c.label);
    expect(new Set(labels).size).toBe(CATEGORIES.length);
  });

  // AC 3: «Jede der 17 Kategorien hat ein eindeutiges Unicode-Icon.» Zwei Kategorien mit
  // demselben Glyph wären im Badge nicht mehr auseinanderzuhalten — und genau das ist der
  // einzige Zweck, den das Icon hat.
  it('hat paarweise eindeutige Icons', () => {
    const icons = CATEGORIES.map((c) => c.icon);
    expect(new Set(icons).size).toBe(CATEGORIES.length);
  });

  it('hat für jede Kategorie ein nicht-leeres Icon', () => {
    for (const category of CATEGORIES) {
      expect(category.icon.length, `Icon fehlt für ${category.slug}`).toBeGreaterThan(0);
    }
  });

  // Der Slug ist der Schlüssel der `--cat-<slug>`-Tokens und der `$categories`-Map in
  // `_tokens.scss`. Ein Slug mit Umlaut, Leerzeichen oder Grossbuchstabe träfe dort nichts und
  // das Badge fiele lautlos auf den neutralen Punkt zurück.
  it('hat Slugs, die als CSS-Token-Schlüssel taugen', () => {
    for (const slug of CATEGORY_SLUGS) {
      expect(slug, slug).toMatch(/^[a-z]+$/);
    }
  });

  // Die Reihenfolge ist Teil des AC: Backend-Enum und Frontend-Liste spiegeln sich. `Sonstiges`
  // ist im Backend die Fallback-Kategorie und steht dort zuletzt — hier ebenso, weil beide
  // Listen dieselben Dropdowns füllen.
  it('führt Sonstiges als letzten Eintrag', () => {
    expect(CATEGORIES.at(-1)?.slug).toBe('sonstiges');
  });

  it('enthält die vier Kategorien aus BE-CAT-10 mit ihrem deutschen Label', () => {
    const byLabel = new Map(CATEGORIES.map((c) => [c.label, c.slug]));

    expect(byLabel.get('Persönliches')).toBe('persoenliches');
    expect(byLabel.get('Steuern')).toBe('steuern');
    expect(byLabel.get('Bargeldbezug')).toBe('bargeldbezug');
    expect(byLabel.get('Reisen')).toBe('reisen');
  });
});

describe('categoryIcon', () => {
  it('liefert den Glyph zu einem bekannten Slug', () => {
    expect(categoryIcon('lebensmittel')).toBe('🛒');
    expect(categoryIcon('bargeldbezug')).toBe('🏧');
  });

  it('liefert für jeden Slug der Liste dessen Icon', () => {
    for (const category of CATEGORIES) {
      expect(categoryIcon(category.slug)).toBe(category.icon);
    }
  });

  // Die Kategorie kommt als deutsches Label aus der API. Trifft es keine der 17 — etwa weil ein
  // Altbestand in der DB steht —, darf das kein Platzhalter-Icon werden: Das Badge zeigt dann
  // den neutralen Punkt, und dafür muss hier `undefined` ankommen.
  it('liefert undefined für unbekannten, leeren und fehlenden Slug', () => {
    expect(categoryIcon('weltraumtourismus')).toBeUndefined();
    expect(categoryIcon('')).toBeUndefined();
    expect(categoryIcon(undefined)).toBeUndefined();
  });
});
