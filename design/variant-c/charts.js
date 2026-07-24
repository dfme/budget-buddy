/**
 * BudgetBuddy — Design-Variante C: Chart-Konfiguration.
 *
 * Demo-Daten, keine echte API. Die Struktur entspricht bewusst dem, was
 * ng2-charts später als `[data]` / `[options]` erhält — der Port nach Angular
 * ist damit ein Copy-Paste in ein Signal.
 *
 * Chart.js kommt per CDN. Ohne Netzverbindung (z. B. beim Öffnen via file://)
 * bleiben die Chart-Flächen leer; der Rest des Prototyps ist davon nicht
 * betroffen.
 */
(function () {
  'use strict';

  if (typeof Chart === 'undefined') {
    console.warn('[BudgetBuddy Design C] Chart.js nicht geladen — Charts bleiben leer.');
    return;
  }

  // Muss mit $categories in styles.scss übereinstimmen.
  const CATEGORY_COLORS = {
    Wohnen: '#60a5fa',
    Lebensmittel: '#4ade80',
    Transport: '#38bdf8',
    Versicherung: '#a78bfa',
    Telekom: '#22d3ee',
    Gesundheit: '#fb7185',
    Freizeit: '#fbbf24',
    Restaurant: '#fb923c',
    Shopping: '#e879f9',
    Bildung: '#818cf8',
    Einkommen: '#34d399',
    Sparen: '#facc15',
    Sonstiges: '#94a3b8',
  };

  const SURFACE = '#151e2a';
  const SURFACE_2 = '#1b2634';
  const LINE = '#253143';
  const INK = '#e8eef6';
  const INK_3 = '#64748b';
  const ACCENT = '#4f8ff7';

  Chart.defaults.font.family =
    'system-ui, -apple-system, "Segoe UI", Roboto, sans-serif';
  Chart.defaults.font.size = 11;
  Chart.defaults.color = INK_3;

  /** CHF im Schweizer Format: 1'234.56 */
  function chf(value) {
    return value.toLocaleString('de-CH', {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    });
  }

  // Ausgaben Juli 2026 nach Kategorie — identisch zur Legende im HTML.
  const spendingByCategory = [
    ['Wohnen', 980.0],
    ['Lebensmittel', 412.65],
    ['Transport', 185.0],
    ['Versicherung', 168.4],
    ['Restaurant', 142.8],
    ['Gesundheit', 108.0],
    ['Freizeit', 96.5],
    ['Shopping', 78.9],
    ['Telekom', 59.0],
    ['Sonstiges', 34.15],
  ];

  const donutCanvas = document.getElementById('chart-categories');
  if (donutCanvas) {
    new Chart(donutCanvas, {
      type: 'doughnut',
      data: {
        labels: spendingByCategory.map((entry) => entry[0]),
        datasets: [
          {
            data: spendingByCategory.map((entry) => entry[1]),
            backgroundColor: spendingByCategory.map(
              (entry) => CATEGORY_COLORS[entry[0]]
            ),
            // Trennlinie in der Flächenfarbe der Karte statt in Weiss —
            // auf dunklem Grund wäre Weiss ein grelles Gitter.
            borderColor: SURFACE,
            borderWidth: 2,
          },
        ],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        // Sehr dünner Ring: hier ist der Donut Beleg, nicht Blickfang —
        // die eigentliche Auswertung steht in der Legende darunter.
        cutout: '78%',
        plugins: {
          // Eigene Legende im HTML: als Tabelle mit Betrag und Prozentanteil.
          // Die Chart.js-Legende kann keine zwei Werte pro Eintrag zeigen.
          legend: { display: false },
          tooltip: {
            backgroundColor: SURFACE_2,
            borderColor: LINE,
            borderWidth: 1,
            titleColor: INK,
            bodyColor: INK,
            padding: 8,
            cornerRadius: 3,
            displayColors: false,
            callbacks: {
              label: (ctx) => `CHF ${chf(ctx.parsed)}`,
            },
          },
        },
      },
    });
  }

  // Ausgaben der letzten 6 Monate (Monatsvergleich, US-10).
  const monthlySpending = [
    ['Feb', 2340.1],
    ['Mär', 2512.75],
    ['Apr', 2198.4],
    ['Mai', 2640.2],
    ['Jun', 2405.6],
    ['Jul', 2265.4],
  ];

  const barCanvas = document.getElementById('chart-months');
  if (barCanvas) {
    new Chart(barCanvas, {
      type: 'bar',
      data: {
        labels: monthlySpending.map((entry) => entry[0]),
        datasets: [
          {
            data: monthlySpending.map((entry) => entry[1]),
            // Der laufende Monat im Akzent, die Historie neutral —
            // so braucht es keine zusätzliche Beschriftung.
            backgroundColor: monthlySpending.map((entry, index) =>
              index === monthlySpending.length - 1 ? ACCENT : '#2c3a4e'
            ),
            // Kantig wie der Rest des Systems.
            borderRadius: 2,
            borderSkipped: false,
            barPercentage: 0.72,
          },
        ],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          legend: { display: false },
          tooltip: {
            backgroundColor: SURFACE_2,
            borderColor: LINE,
            borderWidth: 1,
            titleColor: INK,
            bodyColor: INK,
            padding: 8,
            cornerRadius: 3,
            displayColors: false,
            callbacks: {
              label: (ctx) => `CHF ${chf(ctx.parsed.y)}`,
            },
          },
        },
        scales: {
          x: {
            grid: { display: false },
            border: { color: LINE },
            ticks: { color: INK_3 },
          },
          y: {
            beginAtZero: true,
            border: { display: false },
            grid: { color: LINE },
            ticks: {
              maxTicksLimit: 5,
              color: INK_3,
              callback: (value) => value.toLocaleString('de-CH'),
            },
          },
        },
      },
    });
  }
})();
