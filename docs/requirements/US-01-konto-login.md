# US-01: Konto erstellen und einloggen

**Persona:** Lara  
**MoSCoW:** Should  
**Story:** Als Lara möchte ich ein Konto erstellen und mich einloggen können, damit meine Daten sicher gespeichert sind und ich von verschiedenen Geräten darauf zugreifen kann.

---

## Acceptance Criteria

**Given** eine gültige E-Mail und ein Passwort mit mindestens 8 Zeichen, **When** ich mich registriere, **Then** wird das Konto angelegt und das Passwort als bcrypt-Hash gespeichert — Klartext-Passwörter sind in der DB nicht auffindbar.

**Given** eine bereits registrierte E-Mail, **When** ich mich registriere, **Then** wird die Registrierung mit "E-Mail bereits vergeben" abgelehnt.

**Given** korrekte Zugangsdaten, **When** ich mich einlogge, **Then** sehe ich mein Dashboard.

**Given** falsche Zugangsdaten, **When** ich mich einlogge, **Then** erhalte ich die Meldung "E-Mail oder Passwort falsch".

**Given** ich eingeloggt bin, **When** ich auf "Abmelden" klicke, **Then** wird meine Session serverseitig ungültig gemacht und ich werde zur Login-Seite weitergeleitet — ein Zugriff auf geschützte Seiten ohne erneuten Login ist nicht möglich.

---

## Post-MVP (nicht im Scope dieses Semesters)

- E-Mail-Verifikation
- Passwort-Reset per E-Mail
- Rate-Limiting / Login-Sperre
- PLZ-Validierung gegen offizielle Schweizer PLZ-Liste
- Automatischer Session-Ablauf nach Inaktivität
