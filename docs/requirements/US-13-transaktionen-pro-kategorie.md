# US-13: Einzeltransaktionen einer Kategorie einsehen

**Persona:** Lara  
**MoSCoW:** Should  
**Story:** Als Lara möchte ich die Einzeltransaktionen einer Kategorie einsehen können, damit ich genau nachvollziehen kann, wofür ich Geld ausgegeben habe.

---

## Acceptance Criteria

**Given** ich die Kategorienübersicht öffne, **When** ich auf eine Kategorie klicke, **Then** sehe ich alle zugehörigen Transaktionen mit Datum, Betrag und Empfänger — sortiert nach Datum absteigend.

**Given** eine Kategorie mehr als 20 Transaktionen enthält, **When** die Liste geladen wird, **Then** werden initial 20 Einträge angezeigt mit einem "Weitere laden"-Button — kein ungepaginierter Vollload.
