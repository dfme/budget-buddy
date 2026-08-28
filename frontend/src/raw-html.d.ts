/**
 * Erlaubt den Import von `.html`-Dateien als Text.
 *
 * <p>Gegenstück zur `loader`-Option in `angular.json`. Genutzt von
 * `app/core/theme/theme-boot.spec.ts`, das `index.html` einliest, um das Pre-Paint-Script
 * gegen die Konstanten in `app/core/theme/theme.ts` zu prüfen. Angular-Templates laufen
 * nicht hierüber — die löst der Compiler aus `templateUrl` auf, nicht per `import`.
 */
declare module '*.html' {
  const content: string;
  export default content;
}
