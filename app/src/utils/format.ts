const chfFormatter = new Intl.NumberFormat('de-CH', {
  minimumFractionDigits: 2,
  maximumFractionDigits: 2,
});

const chfFormatter0 = new Intl.NumberFormat('de-CH', {
  maximumFractionDigits: 0,
});

export function formatChf(value: number): string {
  return chfFormatter.format(value);
}

export function formatChf0(value: number): string {
  return chfFormatter0.format(value);
}
