# US-14: Passwort und Einkommen in Einstellungen anpassen

**Persona:** Marc  
**MoSCoW:** Should  
**Story:** Als Marc möchte ich mein Passwort und mein Einkommen in den Einstellungen anpassen können, damit ich mein Konto aktuell halten kann.

---

## Acceptance Criteria

**Given** ich eingeloggt bin, **When** ich unter "Einstellungen > Passwort ändern" das aktuelle und ein neues Passwort (min. 8 Zeichen) eingebe und bestätige, **Then** wird das neue Passwort gespeichert und ich erhalte eine In-App-Bestätigung.

**Given** das eingegebene aktuelle Passwort ist falsch, **When** ich speichere, **Then** wird die Änderung abgelehnt mit "Aktuelles Passwort falsch".

**Given** ich mein Monatseinkommen in den Einstellungen ändere, **When** ich speichere, **Then** wird der Safe-to-Spend-Betrag auf dem Dashboard sofort mit dem neuen Wert neu berechnet.

**Given** kein Monatseinkommen manuell erfasst ist, **When** ich die Einstellungen öffne, **Then** wird das Einkommensfeld als optional gekennzeichnet; wurde ein Einkommen automatisch geschätzt (→ US-06), erscheint der Schätzwert als Vorschlag — das Feld kann leer bleiben, wenn die automatische Schätzung verwendet werden soll.
