import { Provider } from '@angular/core';
import { provideCharts, withDefaultRegisterables } from 'ng2-charts';

/**
 * Chart.js-Registrierung für die Chart-Komponenten (FE-UI-05).
 *
 * <p>Bewusst **komponentenlokal** statt in `app.config.ts`: ein Provider dort würde
 * Chart.js in jeden Start der App ziehen (+208 kB im Initial-Bundle, auch für Nutzer, die
 * nie ein Chart sehen). Als Provider der Chart-Komponenten wandert die Library in die
 * Lazy-Chunks der Feature-Routes, die tatsächlich Charts zeigen.
 *
 * <p>Die Default-Registerables decken Donut und Bar ab. Eine engere Auswahl müsste bei
 * jedem neuen Chart-Typ nachgezogen werden und liesse das Chart sonst still leer.
 */
export const CHART_PROVIDERS: Provider[] = [provideCharts(withDefaultRegisterables())];
