import type { Category } from '../models/category';

const RULES: Array<{ pattern: RegExp; category: Category }> = [
  { pattern: /migros|coop|aldi|lidl|denner|volg|spar/i, category: 'Lebensmittel' },
  { pattern: /sbb|cff|postauto|zvv|bvb|libero|tnw/i, category: 'Transport' },
  { pattern: /helvetia|css|swica|sanitas|axa|zurich|allianz/i, category: 'Versicherung' },
  { pattern: /sunrise|swisscom|salt|wingo|yallo/i, category: 'Telekom' },
  { pattern: /apotheke|pharma|spital|arzt|hirslanden/i, category: 'Gesundheit' },
  { pattern: /netflix|spotify|disney|youtube|fitness|gym|kino|cinema/i, category: 'Freizeit' },
  { pattern: /mcdonald|burger|kfc|starbucks|restaurant|pizzeria|kebab|takeaway/i, category: 'Restaurant' },
  { pattern: /zalando|h&m|zara|digitec|galaxus|amazon|ikea/i, category: 'Shopping' },
  { pattern: /miete|rent|hauswart|stockwerk/i, category: 'Wohnen' },
  { pattern: /uni|hochschule|bfh|eth|fh\b|kurs|seminar/i, category: 'Bildung' },
  { pattern: /lohn|salär|salary|gehalt/i, category: 'Einkommen' },
  { pattern: /sparkonto|dauerauftrag sparen|säule 3a/i, category: 'Sparen' },
];

export function categorize(description: string): Category {
  for (const rule of RULES) {
    if (rule.pattern.test(description)) return rule.category;
  }
  return 'Sonstiges';
}
