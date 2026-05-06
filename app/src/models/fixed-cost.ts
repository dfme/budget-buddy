import type { Category } from './category';

export type Interval = 'monthly' | 'quarterly' | 'yearly';

export interface FixedCost {
  id: string;
  name: string;
  amount: number;
  interval: Interval;
  category: Category;
}
