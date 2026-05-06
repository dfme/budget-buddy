import type { Category } from './category';

export interface Transaction {
  id: string;
  date: string;
  description: string;
  amount: number;
  category: Category;
}
