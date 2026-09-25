# [FE-SET-06] Konto löschen: Abstand zwischen Hinweistext und Button fehlt

- **Issue:** [#365](https://github.com/dfme/budget-buddy/issues/365)
- **Task-ID:** `FE-SET-06`
- **Branch:** `fix/FE-SET-06-abstand-loeschkarte`
- **Story:** — (kein us-*-Label)
- **Sprint:** Sprint 7
- **Bestätigt am:** 2026-09-25

## Problem

In der Card «Konto löschen» auf der Einstellungen-Seite ([settings.html:62-72](../../frontend/src/app/settings/settings.html#L62-L72))
folgt der Button «Konto löschen» direkt auf den Hinweistext (`.settings__hint--danger`), ohne
umschliessendes `form` — anders als bei der «Passwort»-Card, wo `form { gap: $sp-4 }`
([settings.scss:19-23](../../frontend/src/app/settings/settings.scss#L19-L23)) den Abstand
zwischen den Elementen liefert. `.settings__hint`
([settings.scss:25-29](../../frontend/src/app/settings/settings.scss#L25-L29)) hat nur
`margin: $sp-2 0 0` — Abstand nach oben, keinen nach unten. Text und Button wirken deshalb
visuell zusammengeklebt.

## Lösung

`.settings__hint--danger` bekommt zusätzlich `margin-bottom: $sp-4` — derselbe Spacing-Token,
den `.card__head` ([card.scss:12-18](../../frontend/src/app/shared/card/card.scss#L12-L18)) für
den Abstand zwischen Kopf und Inhalt einer Card verwendet, und den `form` im selben File für den
Abstand zwischen seinen Kindern nutzt. Keine Änderung an `settings.html` — kein neues Element,
keine neue Klasse.

## Betroffene Datei

- `frontend/src/app/settings/settings.scss` — `.settings__hint--danger` ergänzt um
  `margin-bottom: $sp-4`

## Test-Strategie

Rein visueller Fix ohne Verhaltensänderung am Lösch-Flow (siehe Hinweis im Issue). Kein
sinnvoller Unit-Test möglich — CSS-Margin wird von den bestehenden Angular-Tests nicht geprüft.
Verifikation erfolgt visuell (Vorher/Nachher-Screenshot der Einstellungen-Seite). Bestehende
Suite (`settings.spec.ts`) läuft zur Regressionskontrolle mit.

## Acceptance Criteria (aus dem Issue)

- Zwischen dem Hinweistext und dem Button in der Card «Konto löschen» ist ein konsistenter
  Abstand vorhanden, wie er auch bei anderen Cards auf der Einstellungen-Seite verwendet wird.
- Rein visueller Fix (Spacing), keine Verhaltensänderung am Lösch-Flow.
