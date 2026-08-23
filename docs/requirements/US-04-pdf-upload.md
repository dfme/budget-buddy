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

**Given** ich ein PDF hochgeladen habe, **When** die Transaktionen kategorisiert werden, **Then** sehe ich einen Fortschrittsbalken mit dem Stand ("45 von 108 Transaktionen kategorisiert"), statt ohne Rückmeldung warten zu müssen.

**Given** die Kategorisierung dauert länger als das serverseitige Zeitbudget, **When** das Budget erreicht wird, **Then** wird der Import **nicht** verworfen — alle Transaktionen werden gespeichert, die verbleibenden unter "Sonstiges", und ich erhalte den Hinweis, dass ich die Kategorien von Hand korrigieren kann.

**Given** das Lesen des PDFs selbst dauert länger als 30 Sekunden, **When** das Timeout erreicht wird, **Then** wird der Upload abgebrochen und ich erhalte die Meldung "Der Import hat zu lange gedauert und wurde abgebrochen. Bitte versuche es erneut" — es wurde nichts gespeichert.

**Given** ein PDF erfolgreich verarbeitet wurde, **When** die Verarbeitung abgeschlossen ist, **Then** wird die Anzahl extrahierter Transaktionen angezeigt (z.B. "42 Transaktionen erkannt"), damit ich die Vollständigkeit prüfen kann.

**Given** ich mehrere PDFs nacheinander hochlade (z.B. verschiedene Monate oder Konten), **When** jedes PDF verarbeitet wird, **Then** werden die enthaltenen Transaktionen den bestehenden Daten hinzugefügt — beliebig viele PDFs können sukzessive hochgeladen werden.

**Given** ein PDF hochgeladen und verarbeitet wurde, **When** die Extraktion abgeschlossen ist, **Then** wird das PDF nicht auf dem Server gespeichert — ausschliesslich die extrahierten Transaktionsdaten werden persistiert; ein Datenbankadmin findet keine PDF-Dateien auf dem Server.

---

## Umsetzungshinweise

Der Import ist seit [ADR-14](../adr/ADR-14-asynchroner-pdf-import.md) (BE-PDF-09,
[#192](https://github.com/dfme/budget-buddy/issues/192)) zweistufig: Das PDF wird im Request
geparst, die Kategorisierung läuft danach als Hintergrund-Job, dessen Fortschritt das Frontend
abfragt. Die drei Timeout-Kriterien oben unterscheiden deshalb bewusst zwischen dem Lesen des
PDFs (Abbruch, nichts gespeichert) und der Kategorisierung (kein Abbruch, `Sonstiges`).
