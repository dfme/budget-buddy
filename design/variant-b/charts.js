/**
 * BudgetBuddy — Design-Variante B: Chart-Konfiguration.
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
    console.warn('[BudgetBuddy Design B] Chart.js nicht geladen — Charts bleiben leer.');
    return;
  }

  // Muss mit $categories in styles.scss übereinstimmen.
  const CATEGORY_COLORS = {
    Wohnen: '#6c4ef0',
    Lebensmittel: '#35c17a',
    Transport: '#2f9bef',
    Versicherung: '#00b3a4',
    Telekom: '#4fd1e0',
    Gesundheit: '#ff5d8f',
    Freizeit: '#ffa62b',
    Restaurant: '#ff7043',
    Shopping: '#e05fd8',
    Bildung: '#7a8cff',
    Einkommen: '#86c232',
    Sparen: '#f2b705',
    Sonstiges: '#a49db5',
  };

  const INK = '#2e2a3f';
  const INK_3 = '#a49db5';
  const LINE = '#f2e3dc';
  const VIOLET = '#6c4ef0';

  Chart.defaults.font.family =
    'system-ui, -apple-system, "Segoe UI", Roboto, sans-serif';
  Chart.defaults.font.size = 13;
  Chart.defaults.color = '#6b6480';

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
            borderWidth: 4,
            // Runde Segmentenden — dieselbe Formensprache wie die
            // Pill-Buttons und der Fortschrittsbalken.
            borderRadius: 10,
            spacing: 2,
            hoverOffset: 8,
          },
        ],
      },
      options: {
        responsive: true,
        maintainAspectRatio: false,
        // Dicker Ring: die Fläche ist hier Teil des Looks, nicht nur Datenträger.
        cutout: '58%',
        plugins: {
          // Eigene Legende im HTML: als Grid mit Beträgen lesbarer und
          // auf 375px zuverlässiger umbrechend als die Chart.js-Legende.
          legend: { display: false },
          tooltip: {
            backgroundColor: INK,
            padding: 12,
            cornerRadius: 14,
            displayColors: false,
            titleFont: { weight: '700' },
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
            // Der laufende Monat in Violett, die Historie in einem
            // gedämpften Lila — so braucht es keine zusätzliche Beschriftung.
            backgroundColor: monthlySpending.map((entry, index) =>
              index === monthlySpending.length - 1 ? VIOLET : '#e6dffb'
            ),
            // Vollständig gerundete Säulenköpfe — das prägendste Chart-Merkmal
            // dieser Variante.
            borderRadius: 999,
            borderSkipped: false,
            barPercentage: 0.5,
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
            padding: 12,
            cornerRadius: 14,
            displayColors: false,
            callbacks: {
              label: (ctx) => `CHF ${chf(ctx.parsed.y)}`,
            },
          },
        },
        scales: {
          x: {
            grid: { display: false },
            border: { display: false },
            ticks: { color: INK_3 },
          },
          y: {
            beginAtZero: true,
            border: { display: false },
            grid: { color: LINE },
            ticks: {
              maxTicksLimit: 4,
              color: INK_3,
              callback: (value) => `${value / 1000}k`,
            },
          },
        },
      },
    });
  }
})();
