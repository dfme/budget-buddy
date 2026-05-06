import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { CATEGORIES, type Category } from '../models/category';
import type { Interval } from '../services/budget-context';
import { useBudget } from '../services/budget-context';
import { formatChf } from '../utils/format';
import './Onboarding.css';

interface NewCostForm {
  name: string;
  amount: number;
  interval: Interval;
  category: Category;
}

const INITIAL_FORM: NewCostForm = {
  name: '',
  amount: 0,
  interval: 'monthly',
  category: 'Wohnen',
};

export function Onboarding() {
  const navigate = useNavigate();
  const {
    monthlyIncome,
    fixedCosts,
    setIncome,
    addFixedCost,
    removeFixedCost,
    completeOnboarding,
  } = useBudget();

  const [form, setForm] = useState<NewCostForm>(INITIAL_FORM);
  const [error, setError] = useState<string | null>(null);

  function handleAdd(e: React.FormEvent) {
    e.preventDefault();
    if (!form.name.trim()) {
      setError('Bezeichnung darf nicht leer sein');
      return;
    }
    if (!(form.amount > 0)) {
      setError('Betrag muss grösser als 0 sein');
      return;
    }
    addFixedCost({
      name: form.name.trim(),
      amount: Number(form.amount),
      interval: form.interval,
      category: form.category,
    });
    setForm(INITIAL_FORM);
    setError(null);
  }

  function finish() {
    completeOnboarding();
    navigate('/');
  }

  return (
    <section className="page">
      <h1>Fixkosten erfassen</h1>
      <p className="lead">
        Damit dein Safe-to-Spend realistisch berechnet wird, brauchen wir deine monatlichen
        Fixkosten.
      </p>

      <div className="panel">
        <h2>Monatliches Einkommen</h2>
        <div className="row">
          <input
            type="number"
            min={0}
            step={50}
            placeholder="z.B. 4200"
            value={monthlyIncome || ''}
            onChange={(e) => setIncome(Number(e.target.value))}
          />
          <span className="suffix">CHF / Monat</span>
        </div>
      </div>

      <div className="panel">
        <h2>Fixkosten</h2>

        {fixedCosts.length === 0 ? (
          <p className="muted">Noch keine Fixkosten erfasst.</p>
        ) : (
          <ul className="cost-list">
            {fixedCosts.map((c) => (
              <li key={c.id}>
                <div className="cost-info">
                  <strong>{c.name}</strong>
                  <span className="badge">{c.category}</span>
                  <span className="muted">{c.interval}</span>
                </div>
                <div className="cost-amount">{formatChf(c.amount)} CHF</div>
                <button
                  type="button"
                  className="btn-icon"
                  onClick={() => removeFixedCost(c.id)}
                  aria-label="Löschen"
                >
                  ×
                </button>
              </li>
            ))}
          </ul>
        )}

        <form className="cost-form" onSubmit={handleAdd}>
          <input
            type="text"
            placeholder="Bezeichnung (z.B. Miete)"
            value={form.name}
            onChange={(e) => setForm((f) => ({ ...f, name: e.target.value }))}
          />
          <input
            type="number"
            min={0}
            step={0.05}
            placeholder="Betrag"
            value={form.amount || ''}
            onChange={(e) => setForm((f) => ({ ...f, amount: Number(e.target.value) }))}
          />
          <select
            value={form.interval}
            onChange={(e) => setForm((f) => ({ ...f, interval: e.target.value as Interval }))}
          >
            <option value="monthly">monatlich</option>
            <option value="quarterly">quartalsweise</option>
            <option value="yearly">jährlich</option>
          </select>
          <select
            value={form.category}
            onChange={(e) => setForm((f) => ({ ...f, category: e.target.value as Category }))}
          >
            {CATEGORIES.map((cat) => (
              <option key={cat} value={cat}>
                {cat}
              </option>
            ))}
          </select>
          <button type="submit" className="btn-primary">
            Hinzufügen
          </button>
        </form>

        {error && <div className="error">{error}</div>}
      </div>

      <div className="page-actions">
        <button type="button" className="btn-primary" onClick={finish}>
          Onboarding abschliessen
        </button>
      </div>
    </section>
  );
}
