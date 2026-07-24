/**
 * BudgetBuddy — Design-Variante A: Chart-Konfiguration.
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
    console.warn('[BudgetBuddy Design A] Chart.js nicht geladen — Charts bleiben leer.');
    return;
  }

  // Muss mit $categories in styles.scss übereinstimmen.
  const CATEGORY_COLORS = {
    Wohnen: '#0f6b5f',
    Lebensmittel: '#6a9b3f',
    Transport: '#2f6f9f',
    Versicherung: '#7a5fa3',
    Telekom: '#3f8f9f',
    Gesundheit: '#b5566b',
    Freizeit: '#c2803a',
    Restaurant: '#a34f3a',
    Shopping: '#9b5f7f',
    Bildung: '#4f6f5f',
    Einkommen: '#2f8f5f',
    Sparen: '#5f6f8f',
    Sonstiges: '#8b9694',
  };

  const INK = '#101413';
  const INK_2 = '#5b6462';
  const LINE = '#e3e8e6';
  const ACCENT = '#0f6b5f';

  Chart.defaults.font.family =
    'system-ui, -apple-system, "Segoe UI", Roboto, sans-serif';
  Chart.defaults.font.size = 12;
  Chart.defaults.color = INK_2;

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
            borderColor: '#ffffff',
            borderWidth: 2,
          },
        ],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        // Dünner Ring — die Zahl in der Mitte ist die Hauptaussage,
        // der Ring nur die Verteilung.
        cutout: '72%',
        plugins: {
          // Eigene Legende im HTML: als Grid mit Beträgen lesbarer und
          // auf 375px zuverlässiger umbrechend als die Chart.js-Legende.
          legend: { display: false },
          tooltip: {
            backgroundColor: INK,
            padding: 10,
            cornerRadius: 8,
            displayColors: false,
            callbacks: {
              label: (ctx) => `${ctx.label}: CHF ${chf(ctx.parsed)}`,
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
              index === monthlySpending.length - 1 ? ACCENT : '#dfe6e4'
            ),
            borderRadius: 4,
            borderSkipped: false,
            barPercentage: 0.62,
          },
        ],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        plugins: {
          legend: { display: false },
          tooltip: {
            backgroundColor: INK,
            padding: 10,
            cornerRadius: 8,
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
          },
          y: {
            beginAtZero: true,
            border: { display: false },
            grid: { color: LINE },
            ticks: {
              maxTicksLimit: 4,
              callback: (value) => `${value / 1000}k`,
            },
          },
        },
      },
    });
  }
})();
