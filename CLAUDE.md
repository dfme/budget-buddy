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

| Entscheid | Status |
|---|---|
| OpenBanking-Anbindung | Nice-to-Have (nicht MVP) |
| Fokus | Zahlungskonten |
| Kategorisierung | Automatisch + manuelle Korrektur als Feature |
| Nutzer | Nur Kunden mit Wohnsitz in der Schweiz (kein B2B / Berater-Tool) |
| Geografische Einschränkung | Schweiz (kein internationaler Rollout im MVP) |

---

## 3 Grösste Risiken

1. **Churn-Falle** — manueller PDF-Import + Kategorisierung führt zu Nutzungsabbruch nach erstem Aha-Effekt
2. **Liability & Compliance** — sensible Transaktionsdaten = Hacking-Ziel; ein Datenleck ist existenzbedrohend
3. **Feature-Lücke der Banken** — UBS, Raiffeisen etc. bauen eigene PFM-Tools; Business Case kann über Nacht wegfallen

---

## User Stories

| # | User Story | MoSCoW | Acceptance Criteria |
|---|---|---|---|
| 1 | Als **Lara** möchte ich ein Konto erstellen und mich einloggen können, damit meine Daten sicher gespeichert sind und ich von verschiedenen Geräten darauf zugreifen kann. | Must | **Given** ich die App zum ersten Mal öffne, **When** ich mich registriere, **Then** kann ich ein Konto mit E-Mail und Passwort erstellen.<br>**Given** ich mich registriere, **When** ich meine Adresse eingebe, **Then** muss ich eine gültige Schweizer Postleitzahl (PLZ) angeben — andernfalls wird die Registrierung abgelehnt mit dem Hinweis, dass BudgetBuddy nur für Personen mit Wohnsitz in der Schweiz verfügbar ist.<br>**Given** ich bereits ein Konto habe, **When** ich mich einlogge, **Then** sehe ich meine gespeicherten Transaktionen und Einstellungen.<br>**Given** ich mein Passwort vergessen habe, **When** ich auf "Passwort vergessen" klicke, **Then** erhalte ich einen Reset-Link per E-Mail. |
| 2 | Als **Marc** möchte ich einen Datenschutz-Consent-Schritt durchlaufen, bevor meine Daten verarbeitet werden, damit ich weiss, was mit meinen sensiblen Finanzdaten passiert und ihnen bewusst zustimme. | Must | **Given** ich die App zum ersten Mal nutze, **When** ich Daten importieren möchte, **Then** werde ich zuerst über die Datenschutzerklärung informiert und muss aktiv zustimmen.<br>**Given** ich meine Einwilligung nicht erteile, **When** ich den Consent-Schritt abbreche, **Then** werden keine Daten gespeichert und ich kann die App ohne Datenspeicherung nicht weiternutzen.<br>**Given** das nDSG (Schweizer Datenschutzgesetz) ein Recht auf Löschung vorschreibt, **When** ich mein Konto lösche, **Then** werden alle meine Daten vollständig und unwiderruflich entfernt. |
| 3 | Als **Lara** möchte ich beim ersten Einrichten meine monatlichen Fixkosten (z.B. Miete, Krankenkasse, Handy) erfassen können, damit der Safe-to-Spend-Betrag von Anfang an realistisch berechnet wird. | Must | **Given** ich starte die App zum ersten Mal, **When** das Onboarding beginnt, **Then** werde ich aufgefordert, meine Fixkosten einzutragen.<br>**Given** ich habe Fixkosten erfasst, **When** ich das Dashboard öffne, **Then** werden diese bei der Safe-to-Spend-Berechnung automatisch abgezogen.<br>**Given** ich möchte Fixkosten nachträglich anpassen, **When** ich die Einstellungen öffne, **Then** kann ich bestehende Einträge bearbeiten oder löschen. |
| 4 | Als **Lara** möchte ich einen Kontoauszug als PDF hochladen, damit meine Transaktionen automatisch eingelesen werden. | Must | **Given** ein gültiges PDF, **When** ich es hochlade, **Then** werden alle Transaktionen korrekt erkannt und angezeigt.<br>**Given** ein unlesbares/falsches Format, **When** ich es hochlade, **Then** erhalte ich eine verständliche Fehlermeldung. |
| 5 | Als **Lara** möchte ich meine Transaktionen in Kategorien sehen, damit ich weiss, wofür ich wie viel ausgebe. | Must | **Given** importierte Transaktionen, **When** ich die Übersicht öffne, **Then** ist jede Transaktion einer Kategorie zugewiesen.<br>**Given** eine falsch kategorisierte Transaktion, **When** ich die Kategorie manuell ändere, **Then** wird die Änderung gespeichert und die Totals aktualisiert. |
| 6 | Als **Lara** möchte ich einen wöchentlichen "Safe-to-Spend"-Betrag sehen, damit ich ohne schlechtes Gewissen Geld ausgeben kann. | Must | **Given** importierte Transaktionen und bekannte Fixkosten, **When** ich das Dashboard öffne, **Then** wird ein realistischer Safe-to-Spend-Betrag für die laufende Woche angezeigt.<br>**Given** der Betrag ist negativ, **When** ich das Dashboard öffne, **Then** erhalte ich eine deutliche Warnung. |
| 7 | Als **Marc** möchte ich ein Sparziel definieren können, damit ich meinen Fortschritt in Richtung Notgroschen verfolgen kann. | Should | **Given** ich setze ein Sparziel (z.B. 1.000 CHF), **When** ich das Dashboard öffne, **Then** sehe ich den aktuellen Fortschritt in Prozent und CHF.<br>**Given** ich habe in einem Monat nichts gespart, **When** der Monat endet, **Then** erhalte ich einen Hinweis mit einem konkreten Sparvorschlag. |
| 8 | Als **Marc** möchte ich wiederkehrende Ausgaben (Abos, Ratenzahlungen) auf einen Blick sehen, damit ich versteckte Kosten erkennen kann. | Should | **Given** importierte Transaktionen, **When** ich die Abo-Übersicht öffne, **Then** werden alle monatlich wiederkehrenden Beträge gruppiert angezeigt.<br>**Given** ein neues Abo wird erkannt, **When** es zum ersten Mal erscheint, **Then** werde ich darauf hingewiesen. |
| 9 | Als **Lara** möchte ich einmal im Monat einen KI-generierten Monatsbericht erhalten, damit ich automatisch einen Überblick über mein Finanzverhalten und gezielte Sparvorschläge bekomme. | Should | **Given** Transaktionsdaten aus dem vergangenen Monat liegen vor, **When** ein neuer Monat beginnt, **Then** wird automatisch ein KI-generierter Bericht mit Ausgabenzusammenfassung, auffälligen Trends und konkreten Sparvorschlägen erstellt und angezeigt.<br>**Given** der Bericht wurde generiert, **When** ich ihn öffne, **Then** sehe ich eine verständliche, personalisierte Analyse in einfacher Sprache.<br>**Given** es liegen noch keine Daten aus einem vollständigen Monat vor, **When** der Bericht generiert werden soll, **Then** erhalte ich einen Hinweis, dass noch zu wenig Daten vorhanden sind. |
| 10 | Als **Lara** möchte ich meine Ausgaben des aktuellen Monats mit dem Vormonat vergleichen, damit ich Trends in meinem Verhalten erkenne. | Could | **Given** Daten aus mindestens zwei Monaten, **When** ich die Vergleichsansicht öffne, **Then** sehe ich die Differenz pro Kategorie in CHF und Prozent.<br>**Given** eine Kategorie hat sich um mehr als 20% erhöht, **When** der Vergleich geladen wird, **Then** wird diese Kategorie visuell hervorgehoben. |
| 11 | Als **Lara** möchte ich mein Bankkonto über eine OpenBanking-Schnittstelle direkt verbinden, damit meine Transaktionen automatisch und ohne manuellen PDF-Upload aktuell gehalten werden. | Could | **Given** ich verbinde mein Konto via OpenBanking (z.B. Swiss Open Banking / SIX API), **When** die Verbindung erfolgreich ist, **Then** werden meine Transaktionen automatisch importiert und aktuell gehalten.<br>**Given** die Verbindung fehlschlägt oder die Bank nicht unterstützt wird, **When** ich die Verbindung einrichten möchte, **Then** erhalte ich eine verständliche Fehlermeldung und den Hinweis, stattdessen einen PDF-Upload zu nutzen. |

---

## Noch offen

- [ ] Projekt-Canvas ausfüllen
- [ ] Präsentation vorbereiten

---

## Präsentation (nächste Session)

- Projektname & Idee in einem Satz
- 3 Must-have User Stories
- Grösstes Risiko — und Plan B
- Wo setzen wir AI konkret ein?
- Was bleibt noch offen?
