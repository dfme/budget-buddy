/**
 * BudgetBuddy — Design-Variante A: Chart-Konfiguration.
 *
 * Demo-Daten, keine echte API. Die Struktur entspricht bewusst dem, was
 * ng2-charts später als `[data]` / `[options]` erhält — der Port nach Angular
 * ist damit ein Copy-Paste in ein Signal.
 *
 * Chart.js liegt lokal im Repo (design/vendor/chart.umd.min.js) und wird in
 * index.html vor dieser Datei eingebunden. Grund: htmlpreview.github.io führt
 * nur GitHub-gehostete Scripts aus — ein externes CDN-Script bliebe wirkungslos
 * und die Charts leer. Sollte Chart.js wider Erwarten fehlen, greift die
 * Schutzabfrage unten und der Rest des Prototyps bleibt intakt.
 *
 * Theme-fähig: Alle Farben werden aus den CSS Custom Properties gelesen (dem-
 * selben Token-System wie das übrige UI). Ein Canvas kennt keine CSS-Variablen,
 * deshalb werden die Charts bei jedem `themechange` (aus theme.js) zerstört und
 * mit den Farben des neuen Themes neu aufgebaut.
 */
(function () {
  'use strict';

  if (typeof Chart === 'undefined') {
    console.warn('[BudgetBuddy Design A] Chart.js nicht geladen — Charts bleiben leer.');
    return;
  }

  var root = document.documentElement;

  /** Liest eine CSS Custom Property vom <html>-Element (aktuelles Theme). */
  function cssVar(name) {
    return getComputedStyle(root).getPropertyValue(name).trim();
  }

  var CATEGORY_KEYS = [
    'wohnen', 'lebensmittel', 'transport', 'versicherung', 'telekom',
    'gesundheit', 'freizeit', 'restaurant', 'shopping', 'bildung',
    'einkommen', 'sparen', 'sonstiges',
  ];

  /** CHF im Schweizer Format: 1'234.56 */
  function chf(value) {
    return value.toLocaleString('de-CH', {
      minimumFractionDigits: 2,
      maximumFractionDigits: 2,
    });
  }

  // Ausgaben Juli 2026 nach Kategorie — identisch zur Legende im HTML.
  var spendingByCategory = [
    ['Wohnen', 'wohnen', 980.0],
    ['Lebensmittel', 'lebensmittel', 412.65],
    ['Transport', 'transport', 185.0],
    ['Versicherung', 'versicherung', 168.4],
    ['Restaurant', 'restaurant', 142.8],
    ['Gesundheit', 'gesundheit', 108.0],
    ['Freizeit', 'freizeit', 96.5],
    ['Shopping', 'shopping', 78.9],
    ['Telekom', 'telekom', 59.0],
    ['Sonstiges', 'sonstiges', 34.15],
  ];

  // Ausgaben der letzten 6 Monate (Monatsvergleich, US-10).
  var monthlySpending = [
    ['Feb', 2340.1], ['Mär', 2512.75], ['Apr', 2198.4],
    ['Mai', 2640.2], ['Jun', 2405.6], ['Jul', 2265.4],
  ];

  var charts = [];

  function destroyCharts() {
    for (var i = 0; i < charts.length; i++) charts[i].destroy();
    charts = [];
  }

  function buildCharts() {
    destroyCharts();

    // Farben je nach aktuellem Theme frisch auslesen.
    var ink = cssVar('--c-ink');
    var surface = cssVar('--c-surface');
    var line = cssVar('--c-line');
    var accent = cssVar('--c-accent');

    Chart.defaults.font.family =
      'system-ui, -apple-system, "Segoe UI", Roboto, sans-serif';
    Chart.defaults.font.size = 12;
    Chart.defaults.color = cssVar('--c-ink-2');

    // Tooltip: Grund = Ink, Text = Surface (Gegenfarbe) — funktioniert so in
    // beiden Themes ohne Extra-Tokens.
    var tooltip = {
      backgroundColor: ink,
      titleColor: surface,
      bodyColor: surface,
      padding: 10,
      cornerRadius: 8,
      displayColors: false,
    };

    var donutCanvas = document.getElementById('chart-categories');
    if (donutCanvas) {
      charts.push(new Chart(donutCanvas, {
        type: 'doughnut',
        data: {
          labels: spendingByCategory.map(function (e) { return e[0]; }),
          datasets: [{
            data: spendingByCategory.map(function (e) { return e[2]; }),
            backgroundColor: spendingByCategory.map(function (e) {
              return cssVar('--cat-' + e[1]);
            }),
            // Trennlinie in der Kartenfarbe — in beiden Themes sauber getrennt.
            borderColor: surface,
            borderWidth: 2,
          }],
        },
        options: {
          responsive: true,
          maintainAspectRatio: false,
          // Dünner Ring — die Zahl in der Mitte ist die Hauptaussage.
          cutout: '72%',
          plugins: {
            legend: { display: false },
            tooltip: Object.assign({}, tooltip, {
              callbacks: { label: function (ctx) { return ctx.label + ': CHF ' + chf(ctx.parsed); } },
            }),
          },
        },
      }));
    }

    var barCanvas = document.getElementById('chart-months');
    if (barCanvas) {
      var lineStrong = cssVar('--c-line-strong');
      charts.push(new Chart(barCanvas, {
        type: 'bar',
        data: {
          labels: monthlySpending.map(function (e) { return e[0]; }),
          datasets: [{
            data: monthlySpending.map(function (e) { return e[1]; }),
            // Laufender Monat im Akzent, Historie neutral.
            backgroundColor: monthlySpending.map(function (e, i) {
              return i === monthlySpending.length - 1 ? accent : lineStrong;
            }),
            borderRadius: 4,
            borderSkipped: false,
            barPercentage: 0.62,
          }],
        },
        options: {
          responsive: true,
          maintainAspectRatio: false,
          plugins: {
            legend: { display: false },
            tooltip: Object.assign({}, tooltip, {
              callbacks: { label: function (ctx) { return 'CHF ' + chf(ctx.parsed.y); } },
            }),
          },
          scales: {
            x: { grid: { display: false }, border: { color: line } },
            y: {
              beginAtZero: true,
              border: { display: false },
              grid: { color: line },
              ticks: { maxTicksLimit: 4, callback: function (v) { return v / 1000 + 'k'; } },
            },
          },
        },
      }));
    }
  }

  buildCharts();
  // Bei Theme-Wechsel Charts mit den neuen Token-Farben neu aufbauen.
  document.addEventListener('themechange', buildCharts);
})();
