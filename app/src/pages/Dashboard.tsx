import { Link } from 'react-router-dom';
import { CATEGORY_COLORS, type Category } from '../models/category';
import { useBudget } from '../services/budget-context';
import { formatChf, formatChf0 } from '../utils/format';
import './Dashboard.css';

function colorFor(category: Category): string {
  return CATEGORY_COLORS[category] ?? '#6b7280';
}

export function Dashboard() {
  const {
    transactions,
    fixedCosts,
    savingsGoals,
    monthlyIncome,
    totalMonthlyFixedCosts,
    currentMonthSpent,
    currentMonthIncome,
    remainingWeeks,
    safeToSpendWeekly,
    categoryTotals,
    loadDemoData,
    resetAll,
  } = useBudget();

  const isEmpty = transactions.length === 0 && fixedCosts.length === 0;
  const income = monthlyIncome || currentMonthIncome;
  const recentTransactions = transactions.slice(0, 5);
  const maxCategoryAmount = categoryTotals.length === 0 ? 1 : categoryTotals[0].amount;

  const ym = new Date().toISOString().slice(0, 7);
  const savedAmount = transactions
    .filter((t) => t.category === 'Sparen' && t.date.startsWith(ym))
    .reduce((sum, t) => sum + Math.abs(t.amount), 0);

  if (isEmpty) {
    return (
      <section className="empty">
        <h1>Willkommen bei BudgetBuddy</h1>
        <p>Starte mit Demo-Daten oder lade deinen ersten Kontoauszug hoch.</p>
        <div className="cta-row">
          <button type="button" className="btn-primary" onClick={loadDemoData}>
            Demo-Daten laden
          </button>
          <Link className="btn-secondary" to="/onboarding">
            Fixkosten erfassen
          </Link>
          <Link className="btn-secondary" to="/upload">
            PDF hochladen
          </Link>
        </div>
      </section>
    );
  }

  const sts = safeToSpendWeekly;
  const overBudget = sts !== null && sts < 0;

  return (
    <>
      <section className={`hero${overBudget ? ' warn' : ''}`}>
        <div className="hero-label">Safe-to-Spend diese Woche</div>
        {sts === null ? (
          <>
            <div className="hero-value muted">— CHF</div>
            <div className="hero-sub">Erfasse Einkommen oder Transaktionen</div>
          </>
        ) : overBudget ? (
          <>
            <div className="hero-value">{formatChf0(sts)} CHF</div>
            <div className="hero-sub warn-text">⚠ Achtung: Dein Budget für diese Woche ist überzogen</div>
          </>
        ) : (
          <>
            <div className="hero-value">{formatChf0(sts)} CHF</div>
            <div className="hero-sub">{remainingWeeks} Wochen bis Monatsende</div>
          </>
        )}
      </section>

      <section className="stats">
        <div className="stat">
          <div className="stat-label">Einkommen</div>
          <div className="stat-value">{formatChf0(income)} CHF</div>
        </div>
        <div className="stat">
          <div className="stat-label">Fixkosten / Monat</div>
          <div className="stat-value">−{formatChf0(totalMonthlyFixedCosts)} CHF</div>
        </div>
        <div className="stat">
          <div className="stat-label">Ausgegeben (Monat)</div>
          <div className="stat-value">−{formatChf0(currentMonthSpent)} CHF</div>
        </div>
      </section>

      <div className="grid">
        <section className="panel">
          <header className="panel-head">
            <h2>Top-Kategorien</h2>
            <Link to="/transactions">Alle Transaktionen</Link>
          </header>
          {categoryTotals.length === 0 ? (
            <p className="muted">Noch keine Ausgaben in diesem Monat.</p>
          ) : (
            <ul className="cat-list">
              {categoryTotals.slice(0, 6).map((entry) => (
                <li key={entry.category}>
                  <div className="cat-row">
                    <span className="dot" style={{ background: colorFor(entry.category) }} />
                    <span className="cat-name">{entry.category}</span>
                    <span className="cat-amount">{formatChf(entry.amount)} CHF</span>
                  </div>
                  <div className="bar">
                    <div
                      className="bar-fill"
                      style={{
                        width: `${(entry.amount / maxCategoryAmount) * 100}%`,
                        background: colorFor(entry.category),
                      }}
                    />
                  </div>
                </li>
              ))}
            </ul>
          )}
        </section>

        <section className="panel">
          <header className="panel-head">
            <h2>Letzte Transaktionen</h2>
            <Link to="/transactions">Mehr</Link>
          </header>
          {recentTransactions.length === 0 ? (
            <p className="muted">Noch keine Transaktionen.</p>
          ) : (
            <ul className="tx-list">
              {recentTransactions.map((tx) => (
                <li key={tx.id}>
                  <div className="tx-main">
                    <div className="tx-desc">{tx.description}</div>
                    <div className="tx-meta">
                      <span
                        className="tag"
                        style={{
                          background: colorFor(tx.category) + '22',
                          color: colorFor(tx.category),
                        }}
                      >
                        {tx.category}
                      </span>
                      <span>{tx.date}</span>
                    </div>
                  </div>
                  <div className={`tx-amount${tx.amount < 0 ? ' neg' : ''}`}>
                    {formatChf(tx.amount)} CHF
                  </div>
                </li>
              ))}
            </ul>
          )}
        </section>

        {savingsGoals.length > 0 && (
          <section className="panel">
            <header className="panel-head">
              <h2>Sparziele</h2>
            </header>
            <ul className="goal-list">
              {savingsGoals.map((goal) => (
                <li key={goal.id}>
                  <div className="goal-row">
                    <span className="goal-name">{goal.name}</span>
                    <span className="goal-amount">
                      {formatChf0(savedAmount)} / {formatChf0(goal.targetAmount)} CHF
                    </span>
                  </div>
                  <div className="bar">
                    <div
                      className="bar-fill"
                      style={{
                        width: `${Math.min(100, (savedAmount / goal.targetAmount) * 100)}%`,
                        background: '#3b82f6',
                      }}
                    />
                  </div>
                  <div className="goal-meta">Ziel bis {goal.targetDate}</div>
                </li>
              ))}
            </ul>
          </section>
        )}
      </div>

      <footer className="actions">
        <button
          type="button"
          className="btn-ghost"
          onClick={() => {
            if (confirm('Alle Daten wirklich löschen?')) resetAll();
          }}
        >
          Alle Daten zurücksetzen
        </button>
      </footer>
    </>
  );
}
