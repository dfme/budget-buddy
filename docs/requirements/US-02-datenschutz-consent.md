# US-02: Datenschutz-Consent

**Persona:** Marc  
**MoSCoW:** Should  
**Story:** Als Marc möchte ich einen Datenschutz-Consent-Schritt durchlaufen, bevor meine Daten verarbeitet werden, damit ich weiss, was mit meinen sensiblen Finanzdaten passiert und ihnen bewusst zustimme.

---

## Acceptance Criteria

**Given** ich die App zum ersten Mal nutze, **When** ich Daten importieren möchte, **Then** werde ich zuerst über die Datenschutzerklärung informiert und muss aktiv zustimmen.

**Given** ich meine Einwilligung nicht erteile, **When** ich den Consent-Schritt abbreche, **Then** werden keine Daten gespeichert und ich kann die App ohne Datenspeicherung nicht weiternutzen.

**Given** das nDSG (Schweizer Datenschutzgesetz) ein Recht auf Löschung vorschreibt, **When** ich mein Konto lösche, **Then** werden alle personenbezogenen Daten (Profil, Transaktionen, Einstellungen) aus der Produktionsdatenbank gelöscht; ein erneuter Login mit denselben Zugangsdaten schlägt fehl; ein Datenbankadmin findet keinen Eintrag mit der gelöschten User-ID.

_(Backups werden gemäss Datenschutzerklärung nach spätestens 30 Tagen überschrieben — ausserhalb des Scope dieses Tests.)_
