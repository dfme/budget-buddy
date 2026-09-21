import type { Locator, Page } from '@playwright/test';

/**
 * Die Glocke der App-Shell (FE-NOTIF-01).
 *
 * <p>Sie ist zweimal im DOM (mobile Topbar, Desktop-Sidebar — `shell.html`), sichtbar ist je
 * Viewport nur eine. `filter({ visible: true })` statt `first()`: welche der beiden das ist,
 * hängt am Projekt-Viewport und soll den Test nicht interessieren.
 *
 * <p>Hier statt in einer Spec, weil inzwischen zwei Stories über die Glocke einsteigen — die
 * Abo-Erkennung (US-08) und der Import-Abschluss (US-04, FE-NOTIF-05). Derselbe Grund, aus dem
 * INFRA-45 den Poll-Helfer aus drei Specs nach `support/import.ts` gezogen hat.
 */
export function bell(page: Page): Locator {
  return page.getByRole('button', { name: 'Benachrichtigungen' }).filter({ visible: true });
}
