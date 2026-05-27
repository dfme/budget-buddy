# US-12: Zwischen Monaten wechseln

**Persona:** Lara  
**MoSCoW:** Should  
**Story:** Als Lara möchte ich zwischen verschiedenen Monaten wechseln können, damit ich meine historischen Ausgaben einsehen kann.

---

## Acceptance Criteria

**Given** PDFs für mehrere Monate importiert wurden, **When** ich das Dashboard öffne, **Then** wird standardmässig der aktuellste Monat angezeigt.

**Given** ich einen anderen Monat auswähle, **When** der Monat geladen wird, **Then** werden alle Ansichten (Dashboard, Kategorien, Safe-to-Spend) für den gewählten Monat aktualisiert — Safe-to-Spend wird nur für den laufenden Monat berechnet, für vergangene Monate wird stattdessen "Abgeschlossen" angezeigt.

**Given** kein PDF für einen gewählten Monat vorhanden ist, **When** ich diesen Monat auswähle, **Then** wird der Hinweis "Keine Daten für [Monat Jahr] — PDF hochladen?" angezeigt.
