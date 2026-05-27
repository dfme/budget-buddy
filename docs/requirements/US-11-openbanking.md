# US-11: OpenBanking-Anbindung

**Persona:** Lara  
**MoSCoW:** Could  
**Story:** Als Lara möchte ich mein Bankkonto über eine OpenBanking-Schnittstelle direkt verbinden, damit meine Transaktionen automatisch und ohne manuellen PDF-Upload aktuell gehalten werden.

---

## Acceptance Criteria

**Given** ich verbinde mein Konto via OpenBanking (z.B. Swiss Open Banking / SIX API), **When** die Verbindung erfolgreich ist, **Then** werden Transaktionen beim App-Start sowie alle 24 Stunden automatisch synchronisiert; das Datum und die Uhrzeit der letzten Synchronisation sind im Dashboard sichtbar.

**Given** die Verbindung fehlschlägt oder die Bank nicht unterstützt wird, **When** ich die Verbindung einrichten möchte, **Then** erhalte ich eine Fehlermeldung mit Angabe des Grundes (z.B. "Bank nicht unterstützt") und dem Hinweis, stattdessen einen PDF-Upload zu nutzen.

---

## Hinweis

OpenBanking ist als Nice-to-Have eingestuft und nicht Teil des MVP. Implementierung erst nach Abschluss aller Must- und Should-Stories.
