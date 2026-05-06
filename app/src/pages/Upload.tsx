import { useState } from 'react';
import { useNavigate } from 'react-router-dom';
import { useBudget } from '../services/budget-context';
import { formatChf } from '../utils/format';
import './Upload.css';

interface ParsedTx {
  date: string;
  description: string;
  amount: number;
}

type State = 'idle' | 'parsing' | 'preview' | 'imported' | 'error';

export function Upload() {
  const navigate = useNavigate();
  const { importTransactions } = useBudget();

  const [state, setState] = useState<State>('idle');
  const [fileName, setFileName] = useState('');
  const [parsed, setParsed] = useState<ParsedTx[]>([]);
  const [importedCount, setImportedCount] = useState(0);
  const [errorMsg, setErrorMsg] = useState('');

  function reset() {
    setState('idle');
    setParsed([]);
    setFileName('');
    setErrorMsg('');
  }

  function handleFile(event: React.ChangeEvent<HTMLInputElement>) {
    const file = event.target.files?.[0];
    if (!file) return;
    setFileName(file.name);
    if (file.type !== 'application/pdf' && !file.name.toLowerCase().endsWith('.pdf')) {
      setState('error');
      setErrorMsg('Nur PDF-Dateien von Schweizer Banken werden unterstützt.');
      return;
    }
    if (file.size > 10 * 1024 * 1024) {
      setState('error');
      setErrorMsg('Datei zu gross (max. 10 MB).');
      return;
    }
    setState('parsing');
    setTimeout(() => {
      const today = new Date();
      const ago = (days: number) => {
        const d = new Date(today.getFullYear(), today.getMonth(), today.getDate() - days);
        return d.toISOString().slice(0, 10);
      };
      const mock: ParsedTx[] = [
        { date: ago(2), description: 'Migros Bahnhof Bern', amount: -38.5 },
        { date: ago(3), description: 'SBB Tageskarte', amount: -52.0 },
        { date: ago(4), description: 'Sunrise Mobile', amount: -39.9 },
        { date: ago(5), description: 'Spotify Premium', amount: -12.95 },
        { date: ago(6), description: 'Coop Bern', amount: -47.2 },
        { date: ago(7), description: 'McDonalds Zürich', amount: -16.8 },
        { date: ago(9), description: 'Lohn Adcubum AG', amount: 4200.0 },
      ];
      setParsed(mock);
      setState('preview');
    }, 800);
  }

  function confirmImport() {
    const count = importTransactions(parsed);
    setImportedCount(count);
    setState('imported');
  }

  return (
    <section className="page">
      <h1>Kontoauszug hochladen</h1>
      <p className="lead">
        Lade einen PDF-Kontoauszug deiner Bank hoch. Unterstützte Banken (MVP):{' '}
        <strong>UBS, PostFinance, Raiffeisen, ZKB, Migros Bank</strong>.
      </p>

      {state === 'idle' && (
        <>
          <label className="dropzone">
            <input type="file" accept="application/pdf,.pdf" onChange={handleFile} hidden />
            <div className="dz-icon">📄</div>
            <div className="dz-text">
              <strong>PDF auswählen</strong>
              <span>oder hierher ziehen (max. 10 MB)</span>
            </div>
          </label>
          <p className="hint">
            Tipp: Du hast keinen Auszug zur Hand? Es ist ein Mock-Parser eingebaut — wähle
            irgendein PDF und es werden Beispiel-Transaktionen erzeugt.
          </p>
        </>
      )}

      {state === 'parsing' && (
        <div className="status">
          <div className="spinner" />
          <p>Analysiere {fileName} …</p>
        </div>
      )}

      {state === 'preview' && (
        <div className="panel">
          <h2>Vorschau — {parsed.length} Transaktionen erkannt</h2>
          <table>
            <thead>
              <tr>
                <th>Datum</th>
                <th>Beschreibung</th>
                <th className="right">Betrag</th>
              </tr>
            </thead>
            <tbody>
              {parsed.map((tx, idx) => (
                <tr key={`${tx.date}-${idx}`}>
                  <td>{tx.date}</td>
                  <td>{tx.description}</td>
                  <td className={`right${tx.amount < 0 ? ' neg' : ''}`}>
                    {formatChf(tx.amount)} CHF
                  </td>
                </tr>
              ))}
            </tbody>
          </table>
          <div className="actions">
            <button type="button" className="btn-secondary" onClick={reset}>
              Abbrechen
            </button>
            <button type="button" className="btn-primary" onClick={confirmImport}>
              Importieren
            </button>
          </div>
        </div>
      )}

      {state === 'imported' && (
        <div className="status success">
          <div className="status-icon">✓</div>
          <h2>{importedCount} Transaktionen importiert</h2>
          <p>
            Duplikate wurden automatisch übersprungen. Schau dir jetzt dein aktualisiertes
            Dashboard an.
          </p>
          <div className="actions">
            <button type="button" className="btn-secondary" onClick={reset}>
              Weiteres PDF
            </button>
            <button type="button" className="btn-primary" onClick={() => navigate('/')}>
              Zum Dashboard
            </button>
          </div>
        </div>
      )}

      {state === 'error' && (
        <div className="status error">
          <div className="status-icon">⚠</div>
          <h2>Upload fehlgeschlagen</h2>
          <p>{errorMsg}</p>
          <div className="actions">
            <button type="button" className="btn-primary" onClick={reset}>
              Erneut versuchen
            </button>
          </div>
        </div>
      )}
    </section>
  );
}
