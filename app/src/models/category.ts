export const CATEGORIES = [
  'Wohnen',
  'Lebensmittel',
  'Transport',
  'Versicherung',
  'Telekom',
  'Gesundheit',
  'Freizeit',
  'Restaurant',
  'Shopping',
  'Bildung',
  'Einkommen',
  'Sparen',
  'Sonstiges',
] as const;

export type Category = (typeof CATEGORIES)[number];

export const CATEGORY_COLORS: Record<Category, string> = {
  Wohnen: '#4f46e5',
  Lebensmittel: '#16a34a',
  Transport: '#0891b2',
  Versicherung: '#7c3aed',
  Telekom: '#0ea5e9',
  Gesundheit: '#db2777',
  Freizeit: '#f59e0b',
  Restaurant: '#ea580c',
  Shopping: '#e11d48',
  Bildung: '#0d9488',
  Einkommen: '#22c55e',
  Sparen: '#3b82f6',
  Sonstiges: '#6b7280',
};
