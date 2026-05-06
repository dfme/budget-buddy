import { StrictMode } from 'react';
import { createRoot } from 'react-dom/client';
import { BrowserRouter } from 'react-router-dom';
import App from './App.tsx';
import { BudgetProvider } from './services/budget-context';
import './index.css';

createRoot(document.getElementById('root')!).render(
  <StrictMode>
    <BrowserRouter>
      <BudgetProvider>
        <App />
      </BudgetProvider>
    </BrowserRouter>
  </StrictMode>,
);
