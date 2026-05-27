# US-09: KI-generierter Monatsbericht

**Persona:** Lara  
**MoSCoW:** Should  
**Story:** Als Lara möchte ich einmal im Monat einen KI-generierten Monatsbericht erhalten, damit ich automatisch einen Überblick über mein Finanzverhalten und gezielte Sparvorschläge bekomme.

---

## Acceptance Criteria

**Given** Transaktionsdaten aus dem vergangenen Monat liegen vor, **When** ein neuer Monat beginnt, **Then** wird automatisch ein KI-generierter Bericht erstellt, der mindestens enthält: Gesamtausgaben des Monats in CHF, die 3 grössten Ausgabenkategorien mit je Betrag und Anteil in Prozent, sowie mindestens einen Sparvorschlag mit konkretem CHF-Betrag — alles bezogen auf die tatsächlichen Transaktionen des Nutzers. _(Qualitätsprüfung per manuellem Review durch PO vor Release.)_

**Given** der Bericht wurde generiert, **When** ich ihn öffne, **Then** enthält er keine Fachbegriffe ohne Erklärung und referenziert die tatsächlichen Transaktionsdaten des Nutzers (z.B. konkrete Kategorien und Beträge statt Platzhalter).

**Given** weniger als 28 Tage an Transaktionsdaten vorliegen oder der aktuelle Monat noch nicht abgeschlossen ist, **When** der Bericht generiert werden soll, **Then** erhalte ich einen Hinweis, dass noch zu wenig Daten vorhanden sind.

**Given** der Bericht generiert wurde, **When** ich ihn öffne, **Then** ist er in der App unter "KI-Bericht" sichtbar; zusätzlich erhalte ich eine In-App-Benachrichtigung — ein optionaler E-Mail-Versand kann in den Einstellungen aktiviert werden.

**Given** die Claude API beim Generieren nicht erreichbar ist oder einen Fehler zurückgibt, **When** der Monatswechsel eintritt, **Then** wird der letzte erfolgreich generierte Bericht weiterhin angezeigt mit dem Hinweis "Aktueller Bericht konnte nicht erstellt werden — zeige Bericht vom [Datum]" und einem "Erneut versuchen"-Button.
