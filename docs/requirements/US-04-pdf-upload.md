# US-04: Kontoauszug als PDF hochladen

**Persona:** Lara  
**MoSCoW:** Must  
**Story:** Als Lara möchte ich einen Kontoauszug als PDF hochladen, damit meine Transaktionen automatisch eingelesen werden.

---

## Acceptance Criteria

**Given** ein gültiges PDF, **When** ich es hochlade, **Then** werden Datum, Betrag und Empfänger von mindestens 95% der Transaktionen korrekt extrahiert und angezeigt — validiert anhand eines definierten Test-Sets aus PDFs von UBS, Raiffeisen und PostFinance.

**Given** ein unlesbares/falsches Format, **When** ich es hochlade, **Then** erhalte ich eine Fehlermeldung mit dem Hinweis, welches Format erwartet wird (z.B. "Nur PDF-Dateien von Schweizer Banken werden unterstützt").

**Given** ein passwortgeschütztes PDF, **When** ich es hochlade, **Then** erhalte ich die Fehlermeldung "Das PDF ist passwortgeschützt — bitte entferne den Schutz vor dem Upload".

**Given** ein PDF grösser als 10 MB, **When** ich es hochlade, **Then** wird der Upload vor der Verarbeitung abgelehnt mit dem Hinweis "Maximale Dateigrösse: 10 MB".

**Given** ich ein PDF hochlade, dessen SHA-256-Hash bereits gespeichert ist, **When** die Duplikaterkennung anschlägt, **Then** erhalte ich die Warnung "Dieser Kontoauszug wurde bereits importiert" mit den Optionen "Trotzdem importieren" und "Abbrechen" — ohne explizite Bestätigung werden keine Dubletten gespeichert.

**Given** die PDF-Verarbeitung länger als 30 Sekunden dauert, **When** das Timeout erreicht wird, **Then** wird die Verarbeitung abgebrochen und ich erhalte die Meldung "Verarbeitung fehlgeschlagen — bitte versuche es erneut".

**Given** ein PDF erfolgreich verarbeitet wurde, **When** die Verarbeitung abgeschlossen ist, **Then** wird die Anzahl extrahierter Transaktionen angezeigt (z.B. "42 Transaktionen erkannt"), damit ich die Vollständigkeit prüfen kann.

**Given** ich mehrere PDFs nacheinander hochlade (z.B. verschiedene Monate oder Konten), **When** jedes PDF verarbeitet wird, **Then** werden die enthaltenen Transaktionen den bestehenden Daten hinzugefügt — beliebig viele PDFs können sukzessive hochgeladen werden.

**Given** ein PDF hochgeladen und verarbeitet wurde, **When** die Extraktion abgeschlossen ist, **Then** wird das PDF nicht auf dem Server gespeichert — ausschliesslich die extrahierten Transaktionsdaten werden persistiert; ein Datenbankadmin findet keine PDF-Dateien auf dem Server.
