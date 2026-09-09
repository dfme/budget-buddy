import { afterEach, describe, expect, it, vi } from 'vitest';

import {
  MONTH_PATTERN,
  currentMonth,
  formatMonth,
  isValidMonth,
  shiftMonth,
  toMonthString,
} from './month';

describe('month', () => {
  afterEach(() => vi.useRealTimers());

  /** Friert die Uhr ein, damit die Erwartungen nicht vom Ausführungstag abhängen. */
  function freezeAt(iso: string) {
    vi.useFakeTimers();
    vi.setSystemTime(new Date(iso));
  }

  describe('toMonthString', () => {
    it('pads a single-digit month to two digits', () => {
      expect(toMonthString(2026, 7)).toBe('2026-07');
      expect(toMonthString(2026, 12)).toBe('2026-12');
    });
  });

  describe('currentMonth', () => {
    it('returns the running month in YYYY-MM', () => {
      freezeAt('2026-09-09T12:00:00');
      expect(currentMonth()).toBe('2026-09');
    });

    it('pads the month for January', () => {
      freezeAt('2026-01-31T23:00:00');
      expect(currentMonth()).toBe('2026-01');
    });
  });

  describe('shiftMonth', () => {
    it('steps back and forth within a year', () => {
      expect(shiftMonth('2026-07', -1)).toBe('2026-06');
      expect(shiftMonth('2026-07', 1)).toBe('2026-08');
      expect(shiftMonth('2026-07', 0)).toBe('2026-07');
    });

    it('crosses the year boundary backwards', () => {
      expect(shiftMonth('2026-01', -1)).toBe('2025-12');
      expect(shiftMonth('2026-01', -2)).toBe('2025-11');
    });

    it('crosses the year boundary forwards', () => {
      expect(shiftMonth('2025-12', 1)).toBe('2026-01');
      expect(shiftMonth('2025-11', 2)).toBe('2026-01');
    });

    it('spans more than a year', () => {
      expect(shiftMonth('2026-07', -12)).toBe('2025-07');
      expect(shiftMonth('2026-07', 13)).toBe('2027-08');
    });

    it('does not get stuck on a month end', () => {
      // Der 31. Januar minus einen Monat ist kein 31. Februar. shiftMonth rechnet deshalb auf
      // Tag 1 und liefert schlicht den Vormonat.
      expect(shiftMonth('2026-01', 1)).toBe('2026-02');
      expect(shiftMonth('2026-03', -1)).toBe('2026-02');
    });
  });

  describe('MONTH_PATTERN', () => {
    it('accepts a well-formed month', () => {
      expect(MONTH_PATTERN.test('2026-01')).toBe(true);
      expect(MONTH_PATTERN.test('2026-12')).toBe(true);
    });

    it('rejects an out-of-range month number', () => {
      expect(MONTH_PATTERN.test('2026-13')).toBe(false);
      expect(MONTH_PATTERN.test('2026-00')).toBe(false);
    });

    it('rejects a single-digit month', () => {
      // Das Backend erwartet YYYY-MM und legte 2026-1 anders aus.
      expect(MONTH_PATTERN.test('2026-1')).toBe(false);
    });

    it('rejects garbage that would become an Invalid Date', () => {
      expect(MONTH_PATTERN.test('')).toBe(false);
      expect(MONTH_PATTERN.test('juli')).toBe(false);
      expect(MONTH_PATTERN.test('2026')).toBe(false);
      expect(MONTH_PATTERN.test('2026-07-15')).toBe(false);
    });
  });

  describe('formatMonth', () => {
    it('renders the de-CH label', () => {
      expect(formatMonth('2026-07')).toBe('Juli 2026');
      expect(formatMonth('2026-01')).toBe('Januar 2026');
      expect(formatMonth('2025-12')).toBe('Dezember 2025');
    });
  });

  describe('isValidMonth', () => {
    it('accepts the running month and anything before it', () => {
      freezeAt('2026-09-09T12:00:00');
      expect(isValidMonth('2026-09')).toBe(true);
      expect(isValidMonth('2026-08')).toBe(true);
      expect(isValidMonth('2019-01')).toBe(true);
    });

    it('rejects a future month', () => {
      // Dort gibt es keine Buchungen, und GET /api/budget/safe-to-spend antwortet mit 400 —
      // der Client darf nicht hinlaufen.
      freezeAt('2026-09-09T12:00:00');
      expect(isValidMonth('2026-10')).toBe(false);
      expect(isValidMonth('2027-01')).toBe(false);
    });

    it('rejects a malformed value', () => {
      freezeAt('2026-09-09T12:00:00');
      expect(isValidMonth('2026-13')).toBe(false);
      expect(isValidMonth('juli')).toBe(false);
      expect(isValidMonth('')).toBe(false);
    });

    it('rejects null and undefined', () => {
      expect(isValidMonth(null)).toBe(false);
      expect(isValidMonth(undefined)).toBe(false);
    });

    it('orders lexicographically the same way it orders chronologically', () => {
      // Die Zusage, auf der isValidMonth und das .sort() der Dropdown-Liste beide beruhen.
      expect('2025-12' < '2026-01').toBe(true);
      expect('2026-09' < '2026-10').toBe(true);
    });
  });
});
