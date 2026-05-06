import { NavLink, Outlet, Route, Routes } from 'react-router-dom';
import { Dashboard } from './pages/Dashboard';
import { Onboarding } from './pages/Onboarding';
import { Transactions } from './pages/Transactions';
import { Upload } from './pages/Upload';
import './App.css';

function Shell() {
  return (
    <div className="shell">
      <header className="topbar">
        <NavLink to="/" className="brand">
          <span className="brand-mark">💰</span>
          <span className="brand-name">BudgetBuddy</span>
        </NavLink>
        <nav className="nav">
          <NavLink to="/" end>
            Dashboard
          </NavLink>
          <NavLink to="/transactions">Transaktionen</NavLink>
          <NavLink to="/upload">Upload</NavLink>
          <NavLink to="/onboarding">Fixkosten</NavLink>
        </nav>
      </header>
      <main className="content">
        <Outlet />
      </main>
    </div>
  );
}

export default function App() {
  return (
    <Routes>
      <Route element={<Shell />}>
        <Route index element={<Dashboard />} />
        <Route path="transactions" element={<Transactions />} />
        <Route path="upload" element={<Upload />} />
        <Route path="onboarding" element={<Onboarding />} />
        <Route path="*" element={<Dashboard />} />
      </Route>
    </Routes>
  );
}
