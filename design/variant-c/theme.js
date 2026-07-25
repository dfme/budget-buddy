/**
 * BudgetBuddy — Theme-Umschalter für die Design-Prototypen.
 *
 * Schaltet `data-theme` auf <html> zwischen "light" und "dark" um. Die Farb-
 * Werte selbst liegen als CSS Custom Properties in styles.scss (:root /
 * [data-theme]) — hier wird nur das Attribut gesetzt.
 *
 * Startzustand und Speicher-Key kommen aus dem <html>-Element:
 *   <html data-theme="light" data-theme-key="variant-a">
 * So bleibt dieselbe Datei für alle Varianten identisch; nur das Default-Theme
 * (A hell, C dunkel) und der localStorage-Key unterscheiden sich.
 *
 * Beim Umschalten wird ein `themechange`-Event ausgelöst, damit charts.js die
 * Chart.js-Instanzen mit den neuen Farben neu aufbaut (Canvas kennt keine CSS-
 * Variablen).
 */
(function () {
  'use strict';

  var root = document.documentElement;
  var native = root.getAttribute('data-theme') || 'light';
  var storageKey = 'bb-theme-' + (root.getAttribute('data-theme-key') || 'default');

  // Zuvor gewählte Variante wiederherstellen (pro Prototyp eigener Key), damit
  // die Wahl beim Wechsel index <-> transactions erhalten bleibt.
  try {
    var saved = localStorage.getItem(storageKey);
    if (saved === 'light' || saved === 'dark') {
      root.setAttribute('data-theme', saved);
    }
  } catch (e) {
    /* localStorage kann in manchen Kontexten blockiert sein — dann native. */
  }

  function currentTheme() {
    return root.getAttribute('data-theme') || native;
  }

  function syncButtons(theme) {
    var dark = theme === 'dark';
    var buttons = document.querySelectorAll('[data-theme-toggle]');
    for (var i = 0; i < buttons.length; i++) {
      var icon = buttons[i].querySelector('[data-theme-icon]');
      var text = buttons[i].querySelector('[data-theme-text]');
      // Button zeigt das Ziel des Klicks, nicht den aktuellen Zustand.
      if (icon) icon.textContent = dark ? '☀' : '☾'; // ☀ / ☾
      if (text) text.textContent = dark ? 'Hell' : 'Dunkel';
      buttons[i].setAttribute(
        'aria-label',
        dark ? 'Zu hellem Design wechseln' : 'Zu dunklem Design wechseln'
      );
    }
  }

  function applyTheme(theme) {
    root.setAttribute('data-theme', theme);
    try {
      localStorage.setItem(storageKey, theme);
    } catch (e) {
      /* ignorieren */
    }
    syncButtons(theme);
    document.dispatchEvent(
      new CustomEvent('themechange', { detail: { theme: theme } })
    );
  }

  document.addEventListener('click', function (event) {
    var target = event.target;
    var button = target && target.closest ? target.closest('[data-theme-toggle]') : null;
    if (!button) return;
    applyTheme(currentTheme() === 'dark' ? 'light' : 'dark');
  });

  syncButtons(currentTheme());
})();
