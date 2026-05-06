import {
  createContext,
  useCallback,
  useContext,
  useEffect,
  useMemo,
  useState,
  type ReactNode,
} from 'react';
import type { Category } from '../models/category';
import type { FixedCost, Interval } from '../models/fixed-cost';
import type { SavingsGoal } from '../models/savings-goal';
import type { Transaction } from '../models/transaction';
import { categorize } from './categorizer';

interface PersistedState {
  transactions: Transaction[];
  fixedCosts: FixedCost[];
  savingsGoals: SavingsGoal[];
  monthlyIncome: number;
  onboardingCompleted: boolean;
}

const STORAGE_KEY = 'budgetbuddy.v1';

const DEFAULT_STATE: PersistedState = {
  transactions: [],
  fixedCosts: [],
  savingsGoals: [],
  monthlyIncome: 0,
  onboardingCompleted: false,
};

function loadState(): PersistedState {
  if (typeof localStorage === 'undefined') return DEFAULT_STATE;
  const raw = localStorage.getItem(STORAGE_KEY);
  if (!raw) return DEFAULT_STATE;
  try {
    return { ...DEFAULT_STATE, ...JSON.parse(raw) } as PersistedState;
  } catch {
    return DEFAULT_STATE;
  }
}

function uid(): string {
  return Math.random().toString(36).slice(2, 10);
}

function monthlyAmount(cost: FixedCost): number {
  switch (cost.interval) {
    case 'monthly':
      return cost.amount;
    case 'quarterly':
      return cost.amount / 3;
    case 'yearly':
      return cost.amount / 12;
  }
}

function currentYearMonth(): string {
  const now = new Date();
  return `${now.getFullYear()}-${String(now.getMonth() + 1).padStart(2, '0')}`;
}

function remainingWeeksInMonth(): number {
  const now = new Date();
  const lastDay = new Date(now.getFullYear(), now.getMonth() + 1, 0).getDate();
  const daysLeft = lastDay - now.getDate() + 1;
  return Math.max(1, Math.ceil(daysLeft / 7));
}

interface BudgetContextValue {
  transactions: Transaction[];
  fixedCosts: FixedCost[];
  savingsGoals: SavingsGoal[];
  monthlyIncome: number;
  onboardingCompleted: boolean;

  totalMonthlyFixedCosts: number;
  currentMonthSpent: number;
  currentMonthIncome: number;
  remainingWeeks: number;
  safeToSpendWeekly: number | null;
  categoryTotals: Array<{ category: Category; amount: number; count: number }>;

  setIncome: (amount: number) => void;
  addFixedCost: (input: Omit<FixedCost, 'id'>) => void;
  removeFixedCost: (id: string) => void;
  changeCategory: (transactionId: string, category: Category) => void;
  importTransactions: (
    items: Array<Omit<Transaction, 'id' | 'category'>>,
  ) => number;
  addSavingsGoal: (name: string, targetAmount: number, targetDate: string) => void;
  removeSavingsGoal: (id: string) => void;
  completeOnboarding: () => void;
  resetAll: () => void;
  loadDemoData: () => void;
}

const BudgetContext = createContext<BudgetContextValue | null>(null);

export function BudgetProvider({ children }: { children: ReactNode }) {
  const [state, setState] = useState<PersistedState>(loadState);

  useEffect(() => {
    localStorage.setItem(STORAGE_KEY, JSON.stringify(state));
  }, [state]);

  const totalMonthlyFixedCosts = useMemo(
    () => state.fixedCosts.reduce((sum, c) => sum + monthlyAmount(c), 0),
    [state.fixedCosts],
  );

  const currentMonthSpent = useMemo(() => {
    const ym = currentYearMonth();
    return state.transactions
      .filter((t) => t.date.startsWith(ym) && t.amount < 0)
      .reduce((sum, t) => sum + Math.abs(t.amount), 0);
  }, [state.transactions]);

  const currentMonthIncome = useMemo(() => {
    const ym = currentYearMonth();
    return state.transactions
      .filter((t) => t.date.startsWith(ym) && t.amount > 0)
      .reduce((sum, t) => sum + t.amount, 0);
  }, [state.transactions]);

  const remainingWeeks = useMemo(() => remainingWeeksInMonth(), []);

  const safeToSpendWeekly = useMemo(() => {
    const income = state.monthlyIncome || currentMonthIncome;
    const fixed = totalMonthlyFixedCosts;
    const spent = currentMonthSpent;
    if (income === 0 && fixed === 0 && spent === 0) return null;
    return (income - fixed - spent) / remainingWeeks;
  }, [
    state.monthlyIncome,
    currentMonthIncome,
    totalMonthlyFixedCosts,
    currentMonthSpent,
    remainingWeeks,
  ]);

  const categoryTotals = useMemo(() => {
    const totals = new Map<Category, { amount: number; count: number }>();
    const ym = currentYearMonth();
    for (const t of state.transactions) {
      if (!t.date.startsWith(ym) || t.amount >= 0) continue;
      const entry = totals.get(t.category) ?? { amount: 0, count: 0 };
      entry.amount += Math.abs(t.amount);
      entry.count += 1;
      totals.set(t.category, entry);
    }
    return Array.from(totals.entries())
      .map(([category, data]) => ({ category, ...data }))
      .sort((a, b) => b.amount - a.amount);
  }, [state.transactions]);

  const setIncome = useCallback((amount: number) => {
    setState((s) => ({ ...s, monthlyIncome: amount }));
  }, []);

  const addFixedCost = useCallback((input: Omit<FixedCost, 'id'>) => {
    setState((s) => ({ ...s, fixedCosts: [...s.fixedCosts, { id: uid(), ...input }] }));
  }, []);

  const removeFixedCost = useCallback((id: string) => {
    setState((s) => ({ ...s, fixedCosts: s.fixedCosts.filter((c) => c.id !== id) }));
  }, []);

  const changeCategory = useCallback((transactionId: string, category: Category) => {
    setState((s) => ({
      ...s,
      transactions: s.transactions.map((t) =>
        t.id === transactionId ? { ...t, category } : t,
      ),
    }));
  }, []);

  const importTransactions = useCallback(
    (items: Array<Omit<Transaction, 'id' | 'category'>>): number => {
      let added = 0;
      setState((s) => {
        const existingKey = (t: Transaction) => `${t.date}|${t.amount}|${t.description}`;
        const existing = new Set(s.transactions.map(existingKey));
        const incoming: Transaction[] = items
          .map((i) => ({
            id: uid(),
            date: i.date,
            description: i.description,
            amount: i.amount,
            category: categorize(i.description),
          }))
          .filter((t) => !existing.has(existingKey(t)));
        added = incoming.length;
        if (incoming.length === 0) return s;
        return {
          ...s,
          transactions: [...incoming, ...s.transactions].sort((a, b) =>
            b.date.localeCompare(a.date),
          ),
        };
      });
      return added;
    },
    [],
  );

  const addSavingsGoal = useCallback(
    (name: string, targetAmount: number, targetDate: string) => {
      setState((s) => ({
        ...s,
        savingsGoals: [...s.savingsGoals, { id: uid(), name, targetAmount, targetDate }],
      }));
    },
    [],
  );

  const removeSavingsGoal = useCallback((id: string) => {
    setState((s) => ({ ...s, savingsGoals: s.savingsGoals.filter((g) => g.id !== id) }));
  }, []);

  const completeOnboarding = useCallback(() => {
    setState((s) => ({ ...s, onboardingCompleted: true }));
  }, []);

  const resetAll = useCallback(() => {
    setState(DEFAULT_STATE);
  }, []);

  const loadDemoData = useCallback(() => {
    const today = new Date();
    const ago = (days: number) => {
      const d = new Date(today.getFullYear(), today.getMonth(), today.getDate() - days);
      return d.toISOString().slice(0, 10);
    };
    const demoTx: Array<Omit<Transaction, 'id' | 'category'>> = [
      { date: ago(1), description: 'Migros Bern', amount: -42.5 },
      { date: ago(2), description: 'SBB Halbtax', amount: -185.0 },
      { date: ago(3), description: 'Sunrise Mobile', amount: -39.9 },
      { date: ago(4), description: 'Coop Pronto', amount: -12.3 },
      { date: ago(5), description: 'Netflix', amount: -19.9 },
      { date: ago(6), description: 'Migros', amount: -67.85 },
      { date: ago(7), description: 'McDonalds Zürich HB', amount: -14.5 },
      { date: ago(8), description: 'Lohn Adcubum AG', amount: 4200.0 },
      { date: ago(9), description: 'Helvetia Versicherung', amount: -310.0 },
      { date: ago(10), description: 'Spotify', amount: -12.95 },
      { date: ago(12), description: 'Coop Bern', amount: -56.4 },
      { date: ago(14), description: 'Restaurant Della Casa', amount: -68.0 },
      { date: ago(16), description: 'Digitec', amount: -129.0 },
      { date: ago(18), description: 'Apotheke Bahnhof', amount: -28.4 },
      { date: ago(20), description: 'Coiffeur Top Cut', amount: -45.0 },
    ];
    const demoFixedCosts: Array<Omit<FixedCost, 'id'>> = [
      { name: 'Miete', amount: 1200, interval: 'monthly', category: 'Wohnen' },
      { name: 'Krankenkasse', amount: 320, interval: 'monthly', category: 'Versicherung' },
      { name: 'Handy-Abo', amount: 39.9, interval: 'monthly', category: 'Telekom' },
    ];
    setState({
      monthlyIncome: 4200,
      fixedCosts: demoFixedCosts.map((c) => ({ id: uid(), ...c })),
      transactions: demoTx
        .map((t) => ({
          id: uid(),
          date: t.date,
          description: t.description,
          amount: t.amount,
          category: categorize(t.description),
        }))
        .sort((a, b) => b.date.localeCompare(a.date)),
      savingsGoals: [
        {
          id: uid(),
          name: 'Notgroschen',
          targetAmount: 1000,
          targetDate: new Date(today.getFullYear() + 1, today.getMonth(), 1)
            .toISOString()
            .slice(0, 10),
        },
      ],
      onboardingCompleted: true,
    });
  }, []);

  const value: BudgetContextValue = {
    transactions: state.transactions,
    fixedCosts: state.fixedCosts,
    savingsGoals: state.savingsGoals,
    monthlyIncome: state.monthlyIncome,
    onboardingCompleted: state.onboardingCompleted,
    totalMonthlyFixedCosts,
    currentMonthSpent,
    currentMonthIncome,
    remainingWeeks,
    safeToSpendWeekly,
    categoryTotals,
    setIncome,
    addFixedCost,
    removeFixedCost,
    changeCategory,
    importTransactions,
    addSavingsGoal,
    removeSavingsGoal,
    completeOnboarding,
    resetAll,
    loadDemoData,
  };

  return <BudgetContext.Provider value={value}>{children}</BudgetContext.Provider>;
}

export function useBudget(): BudgetContextValue {
  const ctx = useContext(BudgetContext);
  if (!ctx) throw new Error('useBudget must be used within BudgetProvider');
  return ctx;
}

export type { Interval };
