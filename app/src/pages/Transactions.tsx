import { useMemo, useState } from 'react';
import { CATEGORIES, CATEGORY_COLORS, type Category } from '../models/category';
import { useBudget } from '../services/budget-context';
import { formatChf } from '../utils/format';
import './Transactions.css';

export function Transactions() {
  const { transactions, changeCategory } = useBudget();
  const [filter, setFilter] = useState<'all' | Category>('all');
  const [search, setSearch] = useState('');

  const filtered = useMemo(() => {
    const q = search.toLowerCase().trim();
    return transactions.filter((t) => {
      if (filter !== 'all' && t.category !== filter) return false;
      if (q && !t.description.toLowerCase().includes(q)) return false;
      return true;
    });
  }, [transactions, filter, search]);

  return (
    <section className="page">
      <header className="head">
        <h1>Transaktionen</h1>
        <span className="count">
          {filtered.length} von {transactions.length}
        </span>
      </header>

      <div className="filters">
        <input
          type="search"
          placeholder="Suchen…"
          value={search}
          onChange={(e) => setSearch(e.target.value)}
        />
        <select value={filter} onChange={(e) => setFilter(e.target.value as 'all' | Category)}>
          <option value="all">Alle Kategorien</option>
          {CATEGORIES.map((cat) => (
            <option key={cat} value={cat}>
              {cat}
            </option>
          ))}
        </select>
      </div>

      {filtered.length === 0 ? (
        <p className="muted">Keine Transaktionen gefunden.</p>
      ) : (
        <ul className="tx-list">
          {filtered.map((tx) => (
            <li key={tx.id}>
              <div className="tx-info">
                <div className="tx-desc">{tx.description}</div>
                <div className="tx-date">{tx.date}</div>
              </div>
              <select
                className="cat-select"
                style={{
                  borderColor: CATEGORY_COLORS[tx.category],
                  color: CATEGORY_COLORS[tx.category],
                }}
                value={tx.category}
                onChange={(e) => changeCategory(tx.id, e.target.value as Category)}
              >
                {CATEGORIES.map((cat) => (
                  <option key={cat} value={cat}>
                    {cat}
                  </option>
                ))}
              </select>
              <div
                className={`tx-amount${tx.amount < 0 ? ' neg' : tx.amount > 0 ? ' pos' : ''}`}
              >
                {formatChf(tx.amount)} CHF
              </div>
            </li>
          ))}
        </ul>
      )}
    </section>
  );
}
