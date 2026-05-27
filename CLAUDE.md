# BudgetBuddy — Projektstatus

**Stand:** 2026-05-06  
**Kurs:** CAS Application Development with AI (ADAI) 2026 · BFH Biel · Ilja Rasin

---

## Projektidee

**BudgetBuddy** ist eine Web-App für in der Schweiz wohnhafte Studenten und Berufseinsteiger, die ihnen durch das einfache Einlesen von Kontoauszügen einen klaren Überblick über ihre monatlichen Ausgaben gibt. Die App kategorisiert Transaktionen automatisch und zeigt einen wöchentlichen "Safe-to-Spend"-Betrag an — damit Nutzer jederzeit wissen, wie viel sie noch ausgeben können. Durch gezielte, lebensnahe Sparvorschläge hilft BudgetBuddy jungen Menschen, finanzielle Kontrolle zu gewinnen und erste Rücklagen aufzubauen.

---

## Zielgruppe (Personas)

### Persona 1 — Lara (22), Studentin (Bern)

- Studium Soziale Arbeit, arbeitet 20% in einer Bar, wohnhaft in der Schweiz
- **Problem:** Verliert Mitte des Monats den Überblick, ob das Geld noch für Miete und Lebensmittel reicht
- **Frustration:** Mühsame Excel-Tabellen, die sie nie aktualisiert; scrollt panisch durch Banking-App
- **Ziel:** "Safe-to-Spend"-Betrag pro Woche
- **Hürde:** Aufschieberitis — wenn der erste PDF-Upload zu kompliziert ist, bricht sie sofort ab

### Persona 2 — Marc (25), Junior-Verkäufer (Zürich)

- Hat gerade Lehre abgeschlossen, arbeitet im Detailhandel, wohnhaft in der Schweiz
- **Problem:** 0 CHF übrig am Monatsende trotz Vollzeitjob ("Kleinvieh" frisst Budget auf)
- **Frustration:** Bank warnt ihn nicht proaktiv vor unnötigen Ausgaben
- **Ziel:** Ersten Notgroschen von 1.000 CHF ansparen
- **Hürde:** Datenschutz-Skepsis — warum private Daten einer Web-App anvertrauen?

---

## Key Decisions

| Entscheid                  | Status                                                           |
| -------------------------- | ---------------------------------------------------------------- |
| OpenBanking-Anbindung      | Nice-to-Have (nicht MVP)                                         |
| Fokus                      | Zahlungskonten                                                   |
| Kategorisierung            | Automatisch + manuelle Korrektur als Feature                     |
| Nutzer                     | Nur Kunden mit Wohnsitz in der Schweiz (kein B2B / Berater-Tool) |
| Geografische Einschränkung | Schweiz (kein internationaler Rollout im MVP)                    |

---

## Technische Entscheide

### Tech Stack

| Schicht | Technologie | Begründung |
|---|---|---|
| Frontend | Angular (TypeScript) | Component-basiert, Two-Way-Binding, gut für Forms |
| Backend | Java 25 + Spring Boot 3.x | Typsicher, breites Ökosystem, Industriestandard |
| API-Dokumentation | OpenAPI 3 (Springdoc) | Automatisch generierte Doku, Contract-First möglich |
| Datenbank | SQLite | Einfach, kein separater DB-Server nötig — ideal für MVP |
| KI | Claude API (Anthropic Java SDK) | Kategorisierung + KI-Monatsbericht |

> **Hinweis SQLite:** Für das MVP ausreichend. Bei gleichzeitigen Schreibzugriffen mehrerer User kann SQLite zum Bottleneck werden — Migration zu PostgreSQL möglich, wenn nötig.

---

### Transaktions-Kategorisierung: Hybrid-Ansatz

| Schritt                     | Methode                                  | Begründung                                                                    |
| --------------------------- | ---------------------------------------- | ----------------------------------------------------------------------------- |
| 1. Bekannte Händler         | Lookup-Tabelle (Händlername → Kategorie) | Schnell, kostenlos, deterministisch — deckt ~70–80% der Transaktionen ab      |
| 2. Unbekannte Transaktionen | Claude API (LLM)                         | Flexibel für unbekannte/mehrdeutige Einträge; reduziert API-Calls auf ~20–30% |
| 3. Manuelle Korrekturen     | Lookup-Tabelle wird erweitert            | User-Korrekturen trainieren das System — Lerneffekt ohne Retraining           |

**Fallback-Kategorie:** `Sonstiges` (wenn LLM unsicher oder API nicht erreichbar)

**Beispiel-Prompt an Claude API:**

```
Kategorisiere diese Transaktion in genau eine der folgenden Kategorien:
[Wohnen, Lebensmittel, Transport, Versicherung, Telekom, Gesundheit,
 Freizeit, Restaurant, Shopping, Bildung, Einkommen, Sparen, Sonstiges]

Transaktion: "DIGITEC GALAXUS AG 044 913 2323"
Antwort (nur Kategoriename):
```

---

## 3 Grösste Risiken

1. **Churn-Falle** — manueller PDF-Import + Kategorisierung führt zu Nutzungsabbruch nach erstem Aha-Effekt
2. **Liability & Compliance** — sensible Transaktionsdaten = Hacking-Ziel; ein Datenleck ist existenzbedrohend
3. **Feature-Lücke der Banken** — UBS, Raiffeisen etc. bauen eigene PFM-Tools; Business Case kann über Nacht wegfallen

---

## User Stories

| #   | User Story                                                                                                                                                                                              | MoSCoW | Acceptance Criteria                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                               |
| --- | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- | ------ | ------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------------- |
| 1   | Als **Lara** möchte ich ein Konto erstellen und mich einloggen können, damit meine Daten sicher gespeichert sind und ich von verschiedenen Geräten darauf zugreifen kann.                               | Should | **Given** eine gültige E-Mail und ein Passwort mit mindestens 8 Zeichen, **When** ich mich registriere, **Then** wird das Konto angelegt und das Passwort als bcrypt-Hash gespeichert — Klartext-Passwörter sind in der DB nicht auffindbar.<br>**Given** eine bereits registrierte E-Mail, **When** ich mich registriere, **Then** wird die Registrierung mit "E-Mail bereits vergeben" abgelehnt.<br>**Given** korrekte Zugangsdaten, **When** ich mich einlogge, **Then** sehe ich mein Dashboard.<br>**Given** falsche Zugangsdaten, **When** ich mich einlogge, **Then** erhalte ich die Meldung "E-Mail oder Passwort falsch".<br>**Given** ich eingeloggt bin, **When** ich auf "Abmelden" klicke, **Then** wird meine Session serverseitig ungültig gemacht und ich werde zur Login-Seite weitergeleitet — ein Zugriff auf geschützte Seiten ohne erneuten Login ist nicht möglich.<br><br>_Post-MVP (nicht im Scope dieses Semesters): E-Mail-Verifikation, Passwort-Reset per E-Mail, Rate-Limiting / Login-Sperre, PLZ-Validierung gegen offizielle Schweizer PLZ-Liste, automatischer Session-Ablauf nach Inaktivität._ |
| 2   | Als **Marc** möchte ich einen Datenschutz-Consent-Schritt durchlaufen, bevor meine Daten verarbeitet werden, damit ich weiss, was mit meinen sensiblen Finanzdaten passiert und ihnen bewusst zustimme. | Should | **Given** ich die App zum ersten Mal nutze, **When** ich Daten importieren möchte, **Then** werde ich zuerst über die Datenschutzerklärung informiert und muss aktiv zustimmen.<br>**Given** ich meine Einwilligung nicht erteile, **When** ich den Consent-Schritt abbreche, **Then** werden keine Daten gespeichert und ich kann die App ohne Datenspeicherung nicht weiternutzen.<br>**Given** das nDSG (Schweizer Datenschutzgesetz) ein Recht auf Löschung vorschreibt, **When** ich mein Konto lösche, **Then** werden alle personenbezogenen Daten (Profil, Transaktionen, Einstellungen) aus der Produktionsdatenbank gelöscht; ein erneuter Login mit denselben Zugangsdaten schlägt fehl; ein Datenbankadmin findet keinen Eintrag mit der gelöschten User-ID. _(Backups werden gemäss Datenschutzerklärung nach spätestens 30 Tagen überschrieben — ausserhalb des Scope dieses Tests.)_                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                               |
| 3   | Als **Lara** möchte ich beim ersten Einrichten meine monatlichen Fixkosten (z.B. Miete, Krankenkasse, Handy) erfassen können, damit der Safe-to-Spend-Betrag von Anfang an realistisch berechnet wird.  | Must   | **Given** ich starte die App zum ersten Mal, **When** das Onboarding beginnt, **Then** wird ein Fixkosten-Wizard angezeigt, der nicht übersprungen werden kann, bis mindestens ein Eintrag gespeichert oder explizit "Keine Fixkosten" bestätigt wurde.<br>**Given** ich erfasse einen Fixkosten-Eintrag, **When** ich speichere, **Then** muss er die Pflichtfelder `Bezeichnung` (nicht leer), `Betrag in CHF > 0` und `Intervall ∈ {monatlich, quartalsweise, jährlich}` enthalten — andernfalls wird das Speichern mit einer feldspezifischen Fehlermeldung abgelehnt.<br>**Given** ich habe z.B. Miete 1.200 CHF (monatlich) und Krankenkasse 300 CHF (monatlich) erfasst, **When** ich das Dashboard öffne, **Then** werden in der Safe-to-Spend-Berechnung 1.500 CHF/Monat abgezogen — verifizierbar durch Vergleich der Werte vor/nach Erfassung.<br>**Given** ein bestehender Fixkosten-Eintrag, **When** ich ihn ändere oder lösche, **Then** wird der nächste Safe-to-Spend-Wert sofort entsprechend neu berechnet (auf den Rappen genau).<br>**Given** ich habe das Onboarding einmal abgeschlossen, **When** ich die App erneut öffne, **Then** wird der Wizard nicht mehr angezeigt.<br>**Given** ein Fixkosten-Eintrag mit Intervall `quartalsweise` (z.B. 300 CHF), **When** die Safe-to-Spend-Formel ausgeführt wird, **Then** wird der Monatsbetrag als 300 ÷ 3 = 100 CHF/Monat berechnet; bei `jährlich` (z.B. 1200 CHF) als 1200 ÷ 12 = 100 CHF/Monat — verifizierbar mit je einem Test-Szenario pro Intervall.<br>**Given** die Summe aller Fixkosten (auf Monatsbasis) ≥ Monatseinkommen, **When** ich speichere oder das Dashboard öffne, **Then** wird die Warnung "Deine Fixkosten übersteigen dein Einkommen — Safe-to-Spend kann nicht berechnet werden" angezeigt und der Safe-to-Spend-Wert als "–" dargestellt.<br>**Given** mindestens ein Monat importierter Transaktionen vorliegt, **When** ich den Fixkosten-Wizard oder die Fixkosten-Einstellungen öffne, **Then** schlägt das System wiederkehrende Belastungen (gleicher Empfänger, gleicher Betrag ±2%, mind. 2 aufeinanderfolgende Monate) automatisch als Fixkosten-Vorschläge vor — ich kann jeden Vorschlag einzeln bestätigen oder ablehnen.                                                                                                                                                                                                                                                                                                  |
| 4   | Als **Lara** möchte ich einen Kontoauszug als PDF hochladen, damit meine Transaktionen automatisch eingelesen werden.                                                                                   | Must   | **Given** ein gültiges PDF, **When** ich es hochlade, **Then** werden Datum, Betrag und Empfänger von mindestens 95% der Transaktionen korrekt extrahiert und angezeigt — validiert anhand eines definierten Test-Sets aus PDFs von UBS, Raiffeisen und PostFinance.<br>**Given** ein unlesbares/falsches Format, **When** ich es hochlade, **Then** erhalte ich eine Fehlermeldung mit dem Hinweis, welches Format erwartet wird (z.B. "Nur PDF-Dateien von Schweizer Banken werden unterstützt").<br>**Given** ein passwortgeschütztes PDF, **When** ich es hochlade, **Then** erhalte ich die Fehlermeldung "Das PDF ist passwortgeschützt — bitte entferne den Schutz vor dem Upload".<br>**Given** ein PDF grösser als 10 MB, **When** ich es hochlade, **Then** wird der Upload vor der Verarbeitung abgelehnt mit dem Hinweis "Maximale Dateigrösse: 10 MB".<br>**Given** ich ein PDF hochlade, dessen SHA-256-Hash bereits gespeichert ist, **When** die Duplikaterkennung anschlägt, **Then** erhalte ich die Warnung "Dieser Kontoauszug wurde bereits importiert" mit den Optionen "Trotzdem importieren" und "Abbrechen" — ohne explizite Bestätigung werden keine Dubletten gespeichert.<br>**Given** die PDF-Verarbeitung länger als 30 Sekunden dauert, **When** das Timeout erreicht wird, **Then** wird die Verarbeitung abgebrochen und ich erhalte die Meldung "Verarbeitung fehlgeschlagen — bitte versuche es erneut".<br>**Given** ein PDF erfolgreich verarbeitet wurde, **When** die Verarbeitung abgeschlossen ist, **Then** wird die Anzahl extrahierter Transaktionen angezeigt (z.B. "42 Transaktionen erkannt"), damit ich die Vollständigkeit prüfen kann.<br>**Given** ich mehrere PDFs nacheinander hochlade (z.B. verschiedene Monate oder Konten), **When** jedes PDF verarbeitet wird, **Then** werden die enthaltenen Transaktionen den bestehenden Daten hinzugefügt — beliebig viele PDFs können sukzessive hochgeladen werden.<br>**Given** ein PDF hochgeladen und verarbeitet wurde, **When** die Extraktion abgeschlossen ist, **Then** wird das PDF nicht auf dem Server gespeichert — ausschliesslich die extrahierten Transaktionsdaten werden persistiert; ein Datenbankadmin findet keine PDF-Dateien auf dem Server.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                               |
| 5   | Als **Lara** möchte ich meine Transaktionen in Kategorien sehen, damit ich weiss, wofür ich wie viel ausgebe.                                                                                           | Must   | **Given** importierte Transaktionen, **When** ich die Übersicht öffne, **Then** ist jede Transaktion genau einer Kategorie aus einer fest definierten Taxonomie zugeordnet (Wohnen, Lebensmittel, Transport, Versicherung, Telekom, Gesundheit, Freizeit, Restaurant, Shopping, Bildung, Einkommen, Sparen, Sonstiges) — Default bei Unsicherheit: "Sonstiges".<br>**Given** ein definiertes Test-Set von mindestens 200 manuell gelabelten Transaktionen, **When** der Auto-Kategorisierer evaluiert wird, **Then** sind mindestens 80% der Transaktionen korrekt zugeordnet.<br>**Given** eine Transaktion, **When** ich die Kategorie manuell ändere, **Then** wird die Änderung persistiert und die Kategorie-Totals der Übersicht aktualisieren sich innerhalb von 1 Sekunde — Summen pro Kategorie stimmen auf den Rappen mit der Summe der zugeordneten Transaktionen überein.<br>**Given** ich habe Transaktionen desselben Empfängers (z.B. "Migros") manuell umkategorisiert, **When** zukünftige Transaktionen desselben Empfängers importiert werden, **Then** übernimmt das System die zuletzt vom User gewählte Kategorie für diesen Empfänger.<br>**Given** die Übersichtsseite, **When** sie geladen wird, **Then** zeigt sie pro Kategorie: Summe in CHF, Anzahl Transaktionen und prozentualer Anteil am Gesamtmonat.<br>**Given** eine Transaktion importiert wird, **When** die Kategorisierung stattfindet, **Then** wird zunächst die statische Lookup-Tabelle (Händlername → Kategorie) geprüft — ausschliesslich für Transaktionen ohne Treffer wird die Claude API aufgerufen; bekannte Händler werden nie unnötig an die API gesendet.                                                                                                                                                           |
| 6   | Als **Lara** möchte ich einen wöchentlichen "Safe-to-Spend"-Betrag sehen, damit ich ohne schlechtes Gewissen Geld ausgeben kann.                                                                        | Must   | **Given** importierte Transaktionen und bekannte Fixkosten, **When** ich das Dashboard öffne, **Then** wird der Safe-to-Spend-Betrag nach folgender Formel berechnet und angezeigt: `(Einkommen − Fixkosten − bisherige Ausgaben im laufenden Monat) ÷ verbleibende Wochen im Monat` — bei einem Einkommen von 2000 CHF, Fixkosten von 800 CHF und bisherigen Ausgaben von 400 CHF in Woche 1 zeigt das Dashboard 200 CHF/Woche für die verbleibenden 3 Wochen.<br>**Given** der Betrag ist negativ, **When** ich das Dashboard öffne, **Then** wird ein rot hinterlegtes Banner mit dem Text "Achtung: Dein Budget für diese Woche ist überzogen" am oberen Rand des Dashboards angezeigt.<br>**Given** kein Monatseinkommen erfasst ist, **When** ich das Dashboard öffne, **Then** wird statt des Safe-to-Spend-Betrags der Hinweis "Bitte erfasse dein Monatseinkommen in den Einstellungen" angezeigt — keine Division wird ausgeführt.<br>**Given** weniger als 7 Tage im laufenden Monat verbleiben, **When** der Safe-to-Spend berechnet wird, **Then** wird als Divisor mindestens 1 (volle Woche) verwendet, um Division durch 0 oder unrealistische Beträge zu vermeiden — der Wert wird mit dem Hinweis "Letzte Woche des Monats" angezeigt.<br>**Given** importierte Transaktionen mindestens 2 Monate umfassen und eine regelmässige Gutschrift desselben Absenders mit gleichem Betrag (±5%) erkennbar ist, **When** das Dashboard geladen wird und kein Einkommen manuell erfasst ist, **Then** schlägt das System diesen Betrag automatisch als Monatseinkommen vor mit dem Hinweis "Regelmässige Gutschrift von X CHF erkannt — als Monatseinkommen übernehmen?" — eine manuelle Eingabe in den Einstellungen bleibt jederzeit möglich und überschreibt die automatische Schätzung.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                       |
| 7   | Als **Marc** möchte ich ein Sparziel definieren können, damit ich meinen Fortschritt in Richtung Notgroschen verfolgen kann.                                                                            | Could | **Given** ich lege ein Sparziel mit `Betrag in CHF > 0` und `Zieldatum in der Zukunft` an, **When** ich speichere, **Then** wird das Ziel persistiert und ist im Dashboard sichtbar — andernfalls wird das Speichern mit einer Validierungsmeldung abgelehnt.<br>**Given** ein Sparziel von 1.000 CHF und bisher 250 CHF gespart, **When** ich das Dashboard öffne, **Then** sehe ich exakt "250 CHF / 1.000 CHF (25%)" — "bisher gespart" = Summe aller Transaktionen der Kategorie "Sparen" seit Erstellung des Ziels — verifizierbar mit definierten Test-Szenarien (0%, 50%, 100%, >100%).<br>**Given** das Zieldatum ist überschritten und das Ziel noch nicht erreicht, **When** ich das Dashboard öffne, **Then** wird ein Banner "Ziel verpasst" mit dem fehlenden Betrag angezeigt.<br>**Given** ich habe in einem Monat nichts gespart, **When** der Monat endet, **Then** erhalte ich einen Hinweis, der mindestens eine Ausgabenkategorie und einen CHF-Betrag nennt (z.B. "Du hast diesen Monat 90 CHF für Takeaway ausgegeben — 2x weniger bestellen spart dir 45 CHF").                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            |
| 8   | Als **Marc** möchte ich wiederkehrende Ausgaben (Abos, Ratenzahlungen) auf einen Blick sehen, damit ich versteckte Kosten erkennen kann.                                                                | Should | **Given** importierte Transaktionen, **When** ich die Abo-Übersicht öffne, **Then** werden alle Transaktionen gruppiert angezeigt, die vom selben Empfänger in mindestens 2 aufeinanderfolgenden Monaten mit demselben Betrag (Toleranz ±2%) verbucht wurden.<br>**Given** eine Transaktion zum ersten Mal als wiederkehrend erkannt wird, **When** sie in der Abo-Übersicht erscheint, **Then** wird sie mit einem "Neu"-Label markiert und ich erhalte eine In-App-Benachrichtigung.<br>**Given** eine Transaktion fälschlicherweise als wiederkehrend markiert ist, **When** ich auf "Kein Abo" klicke, **Then** wird sie aus der Abo-Übersicht entfernt und künftige Transaktionen desselben Empfängers werden nicht mehr automatisch als wiederkehrend erkannt.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                            |
| 9   | Als **Lara** möchte ich einmal im Monat einen KI-generierten Monatsbericht erhalten, damit ich automatisch einen Überblick über mein Finanzverhalten und gezielte Sparvorschläge bekomme.               | Should | **Given** Transaktionsdaten aus dem vergangenen Monat liegen vor, **When** ein neuer Monat beginnt, **Then** wird automatisch ein KI-generierter Bericht erstellt, der mindestens enthält: Gesamtausgaben des Monats in CHF, die 3 grössten Ausgabenkategorien mit je Betrag und Anteil in Prozent, sowie mindestens einen Sparvorschlag mit konkretem CHF-Betrag — alles bezogen auf die tatsächlichen Transaktionen des Nutzers. _(Qualitätsprüfung per manuellem Review durch PO vor Release.)_<br>**Given** der Bericht wurde generiert, **When** ich ihn öffne, **Then** enthält er keine Fachbegriffe ohne Erklärung und referenziert die tatsächlichen Transaktionsdaten des Nutzers (z.B. konkrete Kategorien und Beträge statt Platzhalter).<br>**Given** weniger als 28 Tage an Transaktionsdaten vorliegen oder der aktuelle Monat noch nicht abgeschlossen ist, **When** der Bericht generiert werden soll, **Then** erhalte ich einen Hinweis, dass noch zu wenig Daten vorhanden sind.<br>**Given** der Bericht generiert wurde, **When** ich ihn öffne, **Then** ist er in der App unter "KI-Bericht" sichtbar; zusätzlich erhalte ich eine In-App-Benachrichtigung — ein optionaler E-Mail-Versand kann in den Einstellungen aktiviert werden.<br>**Given** die Claude API beim Generieren nicht erreichbar ist oder einen Fehler zurückgibt, **When** der Monatswechsel eintritt, **Then** wird der letzte erfolgreich generierte Bericht weiterhin angezeigt mit dem Hinweis "Aktueller Bericht konnte nicht erstellt werden — zeige Bericht vom [Datum]" und einem "Erneut versuchen"-Button.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                              |
| 10  | Als **Lara** möchte ich meine Ausgaben des aktuellen Monats mit dem Vormonat vergleichen, damit ich Trends in meinem Verhalten erkenne.                                                                 | Could  | **Given** Daten aus mindestens zwei vollständigen Monaten, **When** ich die Vergleichsansicht öffne, **Then** sehe ich pro Kategorie die Werte `aktueller Monat`, `Vormonat`, `Differenz in CHF` und `Differenz in Prozent` — Summen exakt auf den Rappen (z.B. Restaurant: 320 CHF vs. 250 CHF → +70 CHF / +28%).<br>**Given** weniger als zwei vollständige Monate Daten, **When** ich die Ansicht öffne, **Then** wird stattdessen der Hinweis "Vergleich ab zwei vollständigen Monaten verfügbar" angezeigt.<br>**Given** eine Kategorie mit Steigerung > 20% UND aktuellem Betrag ≥ 50 CHF (Mindestschwelle gegen Rauschen), **When** der Vergleich geladen wird, **Then** wird diese Kategorie visuell hervorgehoben (z.B. roter Balken oder Warnsymbol).<br>**Given** eine Kategorie hatte im Vormonat 0 CHF, **When** der Vergleich gerechnet wird, **Then** wird die Differenz in Prozent als "Neu" gekennzeichnet (keine Division durch Null, kein "∞%").                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                               |
| 11  | Als **Lara** möchte ich mein Bankkonto über eine OpenBanking-Schnittstelle direkt verbinden, damit meine Transaktionen automatisch und ohne manuellen PDF-Upload aktuell gehalten werden.               | Could  | **Given** ich verbinde mein Konto via OpenBanking (z.B. Swiss Open Banking / SIX API), **When** die Verbindung erfolgreich ist, **Then** werden Transaktionen beim App-Start sowie alle 24 Stunden automatisch synchronisiert; das Datum und die Uhrzeit der letzten Synchronisation sind im Dashboard sichtbar.<br>**Given** die Verbindung fehlschlägt oder die Bank nicht unterstützt wird, **When** ich die Verbindung einrichten möchte, **Then** erhalte ich eine Fehlermeldung mit Angabe des Grundes (z.B. "Bank nicht unterstützt") und dem Hinweis, stattdessen einen PDF-Upload zu nutzen.                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                                             |
| 12  | Als **Lara** möchte ich zwischen verschiedenen Monaten wechseln können, damit ich meine historischen Ausgaben einsehen kann. | Should | **Given** PDFs für mehrere Monate importiert wurden, **When** ich das Dashboard öffne, **Then** wird standardmässig der aktuellste Monat angezeigt.<br>**Given** ich einen anderen Monat auswähle, **When** der Monat geladen wird, **Then** werden alle Ansichten (Dashboard, Kategorien, Safe-to-Spend) für den gewählten Monat aktualisiert — Safe-to-Spend wird nur für den laufenden Monat berechnet, für vergangene Monate wird stattdessen "Abgeschlossen" angezeigt.<br>**Given** kein PDF für einen gewählten Monat vorhanden ist, **When** ich diesen Monat auswähle, **Then** wird der Hinweis "Keine Daten für [Monat Jahr] — PDF hochladen?" angezeigt. |
| 13  | Als **Lara** möchte ich die Einzeltransaktionen einer Kategorie einsehen können, damit ich genau nachvollziehen kann, wofür ich Geld ausgegeben habe. | Should | **Given** ich die Kategorienübersicht öffne, **When** ich auf eine Kategorie klicke, **Then** sehe ich alle zugehörigen Transaktionen mit Datum, Betrag und Empfänger — sortiert nach Datum absteigend.<br>**Given** eine Kategorie mehr als 20 Transaktionen enthält, **When** die Liste geladen wird, **Then** werden initial 20 Einträge angezeigt mit einem "Weitere laden"-Button — kein ungepaginierter Vollload. |
| 14  | Als **Marc** möchte ich mein Passwort und mein Einkommen in den Einstellungen anpassen können, damit ich mein Konto aktuell halten kann. | Should | **Given** ich eingeloggt bin, **When** ich unter "Einstellungen > Passwort ändern" das aktuelle und ein neues Passwort (min. 8 Zeichen) eingebe und bestätige, **Then** wird das neue Passwort gespeichert und ich erhalte eine In-App-Bestätigung.<br>**Given** das eingegebene aktuelle Passwort ist falsch, **When** ich speichere, **Then** wird die Änderung abgelehnt mit "Aktuelles Passwort falsch".<br>**Given** ich mein Monatseinkommen in den Einstellungen ändere, **When** ich speichere, **Then** wird der Safe-to-Spend-Betrag auf dem Dashboard sofort mit dem neuen Wert neu berechnet.<br>**Given** kein Monatseinkommen manuell erfasst ist, **When** ich die Einstellungen öffne, **Then** wird das Einkommensfeld als optional gekennzeichnet; wurde ein Einkommen automatisch geschätzt (US-6), erscheint der Schätzwert als Vorschlag — das Feld kann leer bleiben, wenn die automatische Schätzung verwendet werden soll. |

<!-- GSD:project-start source:PROJECT.md -->
## Project

**BudgetBuddy**

BudgetBuddy is a web app for students and young professionals living in Switzerland that ingests bank statement PDFs, automatically categorizes transactions, and displays a weekly "Safe-to-Spend" budget — so users always know how much they can spend without worry. Built with Angular (frontend), Spring Boot 3.x (backend), SQLite (database), and Claude API (AI categorization + monthly reports).

**Core Value:** A weekly Safe-to-Spend number users can trust — calculated from real transaction data, not manual entry.

### Constraints

- **Tech Stack**: Angular (frontend), Java 25 + Spring Boot 3.x (backend), SQLite (MVP DB), Claude API via Anthropic Java SDK, OpenAPI 3 / Springdoc — locked in
- **Database**: SQLite for MVP; migration path to PostgreSQL exists if concurrent writes become bottleneck
- **Geography**: Switzerland only — CHF, Swiss banks (UBS, Raiffeisen, PostFinance), nDSG
- **Privacy**: Sensitive financial data — security is existential; compliance with Swiss nDSG required (including right to deletion)
- **Timeline**: No hard deadline; MVP-first mentality — validate core safe-to-spend concept, then iterate
<!-- GSD:project-end -->

<!-- GSD:stack-start source:research/STACK.md -->
## Technology Stack

## Confidence: HIGH on framework/library choices, MEDIUM on a few version patches
## Recommended Stack
### Backend
| Layer | Technology | Version | Rationale |
|-------|-----------|---------|-----------|
| Runtime | Java | 25 (LTS) | Project-locked |
| Framework | Spring Boot | 3.5.3 | Project-locked; latest 3.x stable |
| Web layer | Spring Web MVC | (bundled) | Synchronous; correct for blocking SQLite JDBC |
| Security | Spring Security | 6.5.x | Stateless JWT resource server pattern |
| ORM | Spring Data JPA + Hibernate | (bundled) | Repository pattern; needs community dialect for SQLite |
| DB | SQLite | 3.x | Project-locked for MVP |
| JDBC driver | org.xerial:sqlite-jdbc | 3.49.x | Only production JDBC driver for SQLite |
| Dialect | org.hibernate.orm:hibernate-community-dialects | (Hibernate version) | Provides `SQLiteDialect` |
| Migrations | Flyway | 10.x | SQLite-confirmed; essential for team schema sync |
| JWT | io.jsonwebtoken:jjwt-* | 0.12.x | HS256 signing, fluent builder API |
| API docs | Springdoc OpenAPI | 2.8.17 | Spring Boot 3.5 compatible; zero-config Swagger UI |
| AI | com.anthropic:anthropic-java | 2.31.0 | Official Anthropic SDK |
| PDF parsing | org.apache.pdfbox:pdfbox | 3.0.x | Apache-licensed; correct for text-layer Swiss bank PDFs |
### Frontend
| Layer | Technology | Version | Rationale |
|-------|-----------|---------|-----------|
| Framework | Angular | 19.x | Project-locked; standalone components, Signals |
| State | Angular Signals + Services | (bundled) | No NgRx needed for MVP scope |
| Forms | Reactive Forms (FormGroup) | (bundled) | Stable; Signal Forms still experimental |
| HTTP auth | Functional HTTP interceptor | (bundled) | Inject JWT Bearer token per request |
| Charts | Chart.js + ng2-charts | 4.x / 6.x | Lightweight, Angular-native wrapper for pie/bar |
| Change detection | OnPush everywhere | (bundled) | Required for Signals to work correctly |
### AI/ML
- **Categorization model**: `claude-haiku-3-5-20241022` — fast (~200ms), cheap, single-label output
- **Monthly AI report model**: `claude-sonnet-4-20250514` (latest Sonnet) — richer language, called once/user/month
- **Fallback**: catch `AnthropicException`, return `"Sonstiges"` — Claude unavailability must never block import flow
## Swiss Bank PDF Specifics
- Columns: Buchungsdatum | Valuta | Text | Belastungen CHF | Gutschriften CHF | Saldo CHF
- Date format: `dd.MM.yyyy`
- Amount format: `1'234.56` (apostrophe thousands separator — requires `replace("'", "")` before `BigDecimal` parse)
- Text field can include multiline wrapping — use Saldo column as row anchor when splitting
## Auth Decision: JWT (Stateless, HS256)
| Factor | JWT (stateless) | Session (server-side) |
|--------|----------------|----------------------|
| SQLite write pressure | None — no session table | Every login/request writes to sessions table |
| Angular SPA integration | Clean Bearer header | Requires cookie + CORS + SameSite config |
| Spring Security support | First-class `oauth2ResourceServer().jwt()` | Also supported but adds Spring Session dep |
| Logout invalidation | Client deletes token (MVP acceptable) | Instant server-side invalidation |
| MVP scope fit | Excellent | Overengineered |
## SQLite + Spring Boot Gotchas (Critical)
## What NOT to Use
| Technology | Why Not |
|-----------|---------|
| Spring Boot 4 | Explicit project risk decision — milestone releases only |
| Spring WebFlux | SQLite JDBC is blocking; reactive wrapping adds complexity with no benefit |
| iText 7 | AGPL license — requires open-sourcing or commercial license |
| Tabula-java | Designed for scanned PDFs; Swiss bank PDFs have a text layer |
| NgRx | Over-engineered for 2-3 person course project with simple state |
| D3.js | Steep learning curve, no Angular integration, overkill for pie + bar |
| Highcharts | Commercial license for non-personal projects |
| Redis + Spring Session | Unnecessary infrastructure when using stateless JWT |
| H2 in-memory (for testing) | Dialect mismatch vs SQLite; use `jdbc:sqlite::memory:` in tests instead |
| PDFBox 2.x | Deprecated API (`PDDocument.load()`); use 3.x `Loader.loadPDF()` from the start |
| `double`/`float` for money | Binary floating point cannot represent CHF amounts exactly |
## Open Questions
## Sources
| Claim | Confidence |
|-------|------------|
| Anthropic Java SDK v2.31.0 | HIGH — GitHub releases |
| Spring Boot 3.5 / Spring Security 6.5 | HIGH — official Spring docs |
| Springdoc 2.8.17 Spring Boot 3.5 compat | HIGH — springdoc.org |
| PDFBox text extraction + password detection | HIGH — Apache PDFBox repo |
| JJWT 0.12.x API | MEDIUM — version patch unverified |
| Raiffeisen PDF layout | HIGH — direct fixture inspection |
<!-- GSD:stack-end -->

<!-- GSD:conventions-start source:CONVENTIONS.md -->
## Conventions

Conventions not yet established. Will populate as patterns emerge during development.
<!-- GSD:conventions-end -->

<!-- GSD:architecture-start source:ARCHITECTURE.md -->
## Architecture

Architecture not yet mapped. Follow existing patterns found in the codebase.
<!-- GSD:architecture-end -->

### Architecture Decision Records

Vollständige ADRs: [docs/adr/README.md](docs/adr/README.md)

| ADR | Entscheid | Abgelehnte Alternativen |
|-----|-----------|------------------------|
| [ADR-0](docs/adr/ADR-0-frontend-backend-separation.md) | SPA + REST API (Angular ↔ Spring Boot, JWT in Header) | SSR (Next.js/Thymeleaf), Monolith mit JSP |
| [ADR-1](docs/adr/ADR-1-java-spring-boot-backend.md) | Java 25 + Spring Boot 3.5.x | Node.js/Express, Python/FastAPI, Go, .NET 8 |
| [ADR-2](docs/adr/ADR-2-angular-frontend.md) | Angular 19.x (Standalone Components, Signals, Reactive Forms) | React, Vue 3, Svelte, Astro |
| [ADR-3](docs/adr/ADR-3-rest-vs-graphql.md) | REST API + OpenAPI 3 (Springdoc) | GraphQL (Overkill, kein nativer File-Upload), gRPC |
| [ADR-4](docs/adr/ADR-4-monolith-vs-microservices.md) | Single Spring Boot JAR (Monolith) | Microservices/K8s (zu komplex), Serverless (JVM Cold-Start) |
| [ADR-5](docs/adr/ADR-5-sqlite-mvp-database.md) | SQLite für MVP; Migration zu PostgreSQL möglich | PostgreSQL from Day One (Overkill), MongoDB (nicht relational) |
| [ADR-6](docs/adr/ADR-6-hybrid-categorization.md) | Hybrid: Lookup-Tabelle zuerst, Claude API nur für unbekannte Tx | LLM-Only ($750/Monat, zu teuer), Fine-tuned ML Model (kein Trainingsdata) |
| [ADR-7](docs/adr/ADR-7-jwt-authentication.md) | JWT HS256, bcrypt-Passwörter, stateless; Logout = Client löscht Token | Server-Side Sessions (DB-Schreibdruck), OAuth 2.0 (Overkill für MVP) |
| [ADR-8](docs/adr/ADR-8-apache-pdfbox.md) | Apache PDFBox 3.x (`Loader.loadPDF()`) | iText 7 (AGPL-Lizenz!), Tabula-java (langsam, kein Text-Layer), pdfplumber (Python) |
| [ADR-9](docs/adr/ADR-9-bigdecimal-money.md) | `BigDecimal` für alle CHF-Beträge, `DECIMAL(10,2)` in DB | `double`/`float` (Rundungsfehler!), `long` (Cent-Speicherung), Joda-Money |

<!-- GSD:skills-start source:skills/ -->
## Project Skills

No project skills found. Add skills to any of: `.claude/skills/`, `.agents/skills/`, `.cursor/skills/`, `.github/skills/`, or `.codex/skills/` with a `SKILL.md` index file.
<!-- GSD:skills-end -->

<!-- GSD:workflow-start source:GSD defaults -->
## GSD Workflow Enforcement

Before using Edit, Write, or other file-changing tools, start work through a GSD command so planning artifacts and execution context stay in sync.

Use these entry points:
- `/gsd-quick` for small fixes, doc updates, and ad-hoc tasks
- `/gsd-debug` for investigation and bug fixing
- `/gsd-execute-phase` for planned phase work

Do not make direct repo edits outside a GSD workflow unless the user explicitly asks to bypass it.
<!-- GSD:workflow-end -->

<!-- GSD:profile-start -->
## Developer Profile

> Profile not yet configured. Run `/gsd-profile-user` to generate your developer profile.
> This section is managed by `generate-claude-profile` -- do not edit manually.
<!-- GSD:profile-end -->
