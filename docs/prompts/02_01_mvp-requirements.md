# BudgetBuddy — MVP Requirements

**Version:** 1.0
**Stand:** 2026-05-20
**Status:** Draft — drei Klärungen offen (siehe §5)
**Scope:** 3-Monats-MVP, Team 2–3 Personen, gemischter Tech-Background

---

## 1. Projektzusammenfassung

BudgetBuddy ist eine Web-App für in der Schweiz wohnhafte Studenten und Berufseinsteiger, die hochgeladene Kontoauszug-PDFs verarbeitet, Transaktionen automatisch kategorisiert und daraus einen wöchentlichen Safe-to-Spend-Betrag berechnet. Der MVP läuft auf Angular (Frontend), Spring Boot 3.x mit SQLite (Backend) und nutzt einen Hybrid aus Lookup-Tabelle und Claude API für die Kategorisierung. Geografischer Scope ist ausschliesslich die Schweiz; OpenBanking, Multi-Konto-Szenarien und E-Mail-Workflows liegen ausserhalb des MVP-Scopes.

---

## 2. Personas

### Lara — Studentin
- **Job:** Studium Soziale Arbeit (Bern), 20%-Job in einer Bar
- **Problem:** Verliert Mitte des Monats den Überblick, ob das Geld noch für Miete und Lebensmittel reicht
- **Ziel:** Wöchentlicher Safe-to-Spend-Betrag, der ohne manuelle Excel-Pflege aktuell bleibt

### Marc — Junior-Verkäufer
- **Job:** Abgeschlossene Lehre, Vollzeit im Detailhandel (Zürich)
- **Problem:** 0 CHF am Monatsende trotz Vollzeitjob — "Kleinvieh" frisst das Budget auf
- **Ziel:** Ersten Notgroschen von 1.000 CHF aufbauen, ohne sensible Daten unkontrolliert aus der Hand zu geben

---

## 3. User Stories mit Acceptance Criteria

### US-1 — Konto erstellen und einloggen (Lara)

> Als Lara möchte ich ein Konto erstellen und mich einloggen können, damit meine Daten sicher gespeichert sind und ich von verschiedenen Geräten darauf zugreifen kann.

- **Given** eine gültige E-Mail und ein Passwort mit mindestens 8 Zeichen, **When** ich mich registriere, **Then** wird das Konto angelegt und das Passwort als bcrypt-Hash gespeichert — Klartext-Passwörter sind in der DB nicht auffindbar.
- **Given** eine bereits registrierte E-Mail, **When** ich mich registriere, **Then** wird die Registrierung mit "E-Mail bereits vergeben" abgelehnt.
- **Given** korrekte Zugangsdaten, **When** ich mich einlogge, **Then** sehe ich mein Dashboard.
- **Given** falsche Zugangsdaten, **When** ich mich einlogge, **Then** erhalte ich die Meldung "E-Mail oder Passwort falsch".
- **Given** ich eingeloggt bin, **When** ich auf "Abmelden" klicke, **Then** wird meine Session serverseitig ungültig gemacht und ich werde zur Login-Seite weitergeleitet.

**Post-MVP (out of scope):** E-Mail-Verifikation, Passwort-Reset per E-Mail, Rate-Limiting / Login-Sperre, PLZ-Validierung, automatischer Session-Ablauf nach Inaktivität.

---

### US-2 — Datenschutz-Consent (Marc)

> Als Marc möchte ich einen Datenschutz-Consent-Schritt durchlaufen, bevor meine Daten verarbeitet werden, damit ich weiss, was mit meinen sensiblen Finanzdaten passiert und ihnen bewusst zustimme.

- **Given** ich die App zum ersten Mal nutze, **When** ich Daten importieren möchte, **Then** werde ich zuerst über die Datenschutzerklärung informiert und muss aktiv zustimmen.
- **Given** ich meine Einwilligung nicht erteile, **When** ich den Consent-Schritt abbreche, **Then** werden keine Daten gespeichert und ich kann die App ohne Datenspeicherung nicht weiternutzen.
- **Given** das nDSG ein Recht auf Löschung vorschreibt, **When** ich mein Konto lösche, **Then** werden alle personenbezogenen Daten (Profil, Transaktionen, Einstellungen) aus der Produktionsdatenbank gelöscht; ein erneuter Login mit denselben Zugangsdaten schlägt fehl; ein DB-Admin findet keinen Eintrag mit der gelöschten User-ID. (Backups werden gemäss Datenschutzerklärung nach max. 30 Tagen überschrieben — ausserhalb dieses Tests.)

---

### US-3 — Fixkosten erfassen (Lara)

> Als Lara möchte ich beim ersten Einrichten meine monatlichen Fixkosten erfassen können, damit der Safe-to-Spend-Betrag von Anfang an realistisch berechnet wird.

- **Given** ich starte die App zum ersten Mal, **When** das Onboarding beginnt, **Then** wird ein Fixkosten-Wizard angezeigt, der nicht übersprungen werden kann, bis mindestens ein Eintrag gespeichert oder explizit "Keine Fixkosten" bestätigt wurde.
- **Given** ich erfasse einen Eintrag, **When** ich speichere, **Then** muss er `Bezeichnung` (nicht leer), `Betrag in CHF > 0` und `Intervall ∈ {monatlich, quartalsweise, jährlich}` enthalten — andernfalls feldspezifische Fehlermeldung.
- **Given** Miete 1.200 CHF (monatlich) + Krankenkasse 300 CHF (monatlich), **When** ich das Dashboard öffne, **Then** werden in der Safe-to-Spend-Berechnung 1.500 CHF/Monat abgezogen.
- **Given** ein bestehender Eintrag, **When** ich ihn ändere oder lösche, **Then** wird der nächste Safe-to-Spend-Wert sofort neu berechnet (rappen-genau).
- **Given** ich habe das Onboarding abgeschlossen, **When** ich die App erneut öffne, **Then** wird der Wizard nicht mehr angezeigt.
- **Given** Intervall `quartalsweise` (300 CHF), **When** S2S rechnet, **Then** 300 ÷ 3 = 100 CHF/Monat. **Given** `jährlich` (1200 CHF), **Then** 1200 ÷ 12 = 100 CHF/Monat — je 1 Testfall pro Intervall.
- **Given** Summe Fixkosten (monatlich) ≥ Monatseinkommen, **When** ich speichere oder Dashboard öffne, **Then** Warnung "Deine Fixkosten übersteigen dein Einkommen — Safe-to-Spend kann nicht berechnet werden" + Wert als "–".

**Erweiterung gegenüber Original (siehe §5, Risiko 1):** Der Wizard erfasst zusätzlich das Monatseinkommen als Pflichtfeld (Betrag in CHF > 0). Begründung: US-6 setzt Einkommen voraus, ohne dass eine eigene Erfassungs-Story existiert.

---

### US-4 — PDF-Upload (Lara)

> Als Lara möchte ich einen Kontoauszug als PDF hochladen, damit meine Transaktionen automatisch eingelesen werden.

- **Given** ein gültiges PDF, **When** ich es hochlade, **Then** werden Datum, Betrag und Empfänger von mindestens 95% der Transaktionen korrekt extrahiert — validiert anhand eines Test-Sets aus PDFs von UBS, Raiffeisen und PostFinance.
- **Given** unlesbares/falsches Format, **When** ich hochlade, **Then** Fehlermeldung mit erwartetem Format (z. B. "Nur PDF-Dateien von Schweizer Banken werden unterstützt").
- **Given** passwortgeschütztes PDF, **When** ich hochlade, **Then** "Das PDF ist passwortgeschützt — bitte entferne den Schutz vor dem Upload".
- **Given** PDF > 10 MB, **When** ich hochlade, **Then** Upload vor Verarbeitung abgelehnt ("Maximale Dateigrösse: 10 MB").
- **Given** Zeitraum + Bank des PDFs sind bereits importiert, **When** Duplikaterkennung anschlägt, **Then** Warnung "Dieser Kontoauszug wurde bereits importiert" mit Optionen "Trotzdem importieren" / "Abbrechen" — ohne Bestätigung keine Dubletten.
- **Given** Verarbeitung > 30 s, **When** Timeout eintritt, **Then** Abbruch + "Verarbeitung fehlgeschlagen — bitte versuche es erneut".

---

### US-5 — Transaktionen in Kategorien (Lara)

> Als Lara möchte ich meine Transaktionen in Kategorien sehen, damit ich weiss, wofür ich wie viel ausgebe.

- **Given** importierte Transaktionen, **When** ich die Übersicht öffne, **Then** ist jede Transaktion genau einer Kategorie aus der Taxonomie zugeordnet (Wohnen, Lebensmittel, Transport, Versicherung, Telekom, Gesundheit, Freizeit, Restaurant, Shopping, Bildung, Einkommen, Sparen, Sonstiges) — Default: "Sonstiges".
- **Given** ein Test-Set von ≥ 200 manuell gelabelten Transaktionen, **When** der Auto-Kategorisierer evaluiert wird, **Then** sind ≥ 80% korrekt zugeordnet.
- **Given** eine Transaktion, **When** ich die Kategorie manuell ändere, **Then** wird die Änderung persistiert und Kategorie-Totals aktualisieren sich innerhalb 1 s — rappen-genau zur Summe der Transaktionen.
- **Given** ich habe Transaktionen desselben Empfängers (z. B. "Migros") manuell umkategorisiert, **When** zukünftige Transaktionen desselben Empfängers importiert werden, **Then** übernimmt das System die zuletzt gewählte Kategorie.
- **Given** Übersichtsseite, **When** sie geladen wird, **Then** zeigt sie pro Kategorie: Summe CHF, Anzahl Transaktionen, prozentualer Anteil am Gesamtmonat.

---

### US-6 — Safe-to-Spend (Lara)

> Als Lara möchte ich einen wöchentlichen Safe-to-Spend-Betrag sehen, damit ich ohne schlechtes Gewissen Geld ausgeben kann.

- **Given** importierte Transaktionen und bekannte Fixkosten, **When** ich das Dashboard öffne, **Then** wird S2S berechnet: `(Einkommen − Fixkosten − bisherige variable Ausgaben im laufenden Monat) ÷ verbleibende Wochen im Monat`. Beispiel: Einkommen 2000 CHF, Fixkosten 800 CHF, bisherige Ausgaben 400 CHF in Woche 1 → 200 CHF/Woche für 3 verbleibende Wochen.
- **Given** Betrag ist negativ, **When** ich Dashboard öffne, **Then** rotes Banner "Achtung: Dein Budget für diese Woche ist überzogen" am oberen Rand.
- **Given** kein Monatseinkommen erfasst, **When** Dashboard öffnen, **Then** Hinweis "Bitte erfasse dein Monatseinkommen in den Einstellungen" — keine Division.
- **Given** < 7 Tage im laufenden Monat verbleiben, **When** S2S berechnet wird, **Then** Divisor ≥ 1 (volle Woche), Wert mit Hinweis "Letzte Woche des Monats".

**Offen (siehe §5, Risiko 2):** Definition "variable Ausgaben" — sind PDF-Transaktionen, die einer Fixkosten-Bezeichnung entsprechen, von den variablen Ausgaben auszuschliessen? Anderenfalls werden Fixkosten doppelt subtrahiert.

---

### US-7 — Sparziel definieren (Marc)

> Als Marc möchte ich ein Sparziel definieren können, damit ich meinen Fortschritt in Richtung Notgroschen verfolgen kann.

- **Given** Sparziel mit `Betrag CHF > 0` und `Zieldatum in der Zukunft`, **When** ich speichere, **Then** wird das Ziel persistiert und im Dashboard sichtbar — sonst Validierungsfehler.
- **Given** Ziel 1.000 CHF und 250 CHF gespart, **When** Dashboard öffnen, **Then** "250 CHF / 1.000 CHF (25%)". "Gespart" = Summe Transaktionen der Kategorie "Sparen" seit Erstellung des Ziels — Testfälle 0%, 50%, 100%, >100%.
- **Given** Zieldatum überschritten und Ziel nicht erreicht, **When** Dashboard öffnen, **Then** Banner "Ziel verpasst" mit fehlendem Betrag.
- **Given** ich habe in einem Monat nichts gespart, **When** Monat endet, **Then** Hinweis mit ≥ 1 Ausgabenkategorie + CHF-Betrag (z. B. "90 CHF für Takeaway — 2× weniger bestellen spart 45 CHF").

**Anmerkung:** Letztes Kriterium (Sparvorschlag) überlappt inhaltlich mit US-9 (KI-Monatsbericht). Empfehlung beim Refinement: in US-9 verschieben oder gemeinsame Logik definieren.

---

### US-8 — Wiederkehrende Ausgaben (Marc)

> Als Marc möchte ich wiederkehrende Ausgaben auf einen Blick sehen, damit ich versteckte Kosten erkennen kann.

- **Given** importierte Transaktionen, **When** Abo-Übersicht öffnen, **Then** werden alle Transaktionen gruppiert, die vom selben Empfänger in ≥ 2 aufeinanderfolgenden Monaten mit demselben Betrag (Toleranz ±2%) verbucht wurden.
- **Given** Transaktion zum ersten Mal als wiederkehrend erkannt, **When** sie erscheint, **Then** "Neu"-Label + In-App-Benachrichtigung.
- **Given** fälschlich als wiederkehrend markiert, **When** ich "Kein Abo" klicke, **Then** wird sie entfernt und künftige Transaktionen desselben Empfängers nicht mehr automatisch als wiederkehrend erkannt.

---

### US-9 — KI-Monatsbericht (Lara)

> Als Lara möchte ich einmal im Monat einen KI-generierten Monatsbericht erhalten, damit ich automatisch einen Überblick über mein Finanzverhalten und gezielte Sparvorschläge bekomme.

- **Given** Transaktionsdaten aus Vormonat liegen vor, **When** neuer Monat beginnt, **Then** wird ein Bericht generiert mit mindestens: Gesamtausgaben CHF, 3 grösste Kategorien (Betrag + Anteil %), ≥ 1 Sparvorschlag mit konkretem CHF-Betrag — bezogen auf reale Transaktionen. (Qualitätsprüfung manuell durch PO vor Release.)
- **Given** Bericht generiert, **When** ich ihn öffne, **Then** keine Fachbegriffe ohne Erklärung; reale Transaktionsdaten referenziert (konkrete Kategorien + Beträge, keine Platzhalter).
- **Given** < 28 Tage Daten oder Monat nicht abgeschlossen, **When** Bericht generieren, **Then** Hinweis "noch zu wenig Daten".
- **Given** Bericht generiert, **When** ich öffne, **Then** sichtbar unter "KI-Bericht" + In-App-Benachrichtigung; optionaler E-Mail-Versand in Einstellungen aktivierbar.
- **Given** Claude API nicht erreichbar oder Fehler, **When** Monatswechsel, **Then** letzter erfolgreicher Bericht weiterhin angezeigt mit Hinweis "Aktueller Bericht konnte nicht erstellt werden — zeige Bericht vom [Datum]" + "Erneut versuchen"-Button.

---

### US-10 — Monatsvergleich (Lara)

> Als Lara möchte ich meine Ausgaben des aktuellen Monats mit dem Vormonat vergleichen, damit ich Trends in meinem Verhalten erkenne.

- **Given** ≥ 2 vollständige Monate Daten, **When** Vergleichsansicht öffnen, **Then** pro Kategorie: `aktueller Monat`, `Vormonat`, `Differenz CHF`, `Differenz %` — rappen-genau (z. B. Restaurant: 320 vs. 250 → +70 / +28%).
- **Given** < 2 vollständige Monate Daten, **When** Ansicht öffnen, **Then** Hinweis "Vergleich ab zwei vollständigen Monaten verfügbar".
- **Given** Kategorie mit Steigerung > 20% UND aktueller Betrag ≥ 50 CHF, **When** Vergleich laden, **Then** visuelle Hervorhebung (roter Balken / Warnsymbol).
- **Given** Kategorie hatte im Vormonat 0 CHF, **When** Vergleich rechnen, **Then** Differenz % als "Neu" (keine Division durch 0).

---

### US-11 — OpenBanking-Verbindung (Lara)

> Als Lara möchte ich mein Bankkonto über OpenBanking verbinden, damit meine Transaktionen automatisch aktuell gehalten werden.

- **Given** Verbindung via OpenBanking (Swiss Open Banking / SIX API), **When** erfolgreich, **Then** Sync beim App-Start + alle 24 h; letzte Synchronisation als Datum/Uhrzeit im Dashboard sichtbar.
- **Given** Verbindung fehlschlägt oder Bank nicht unterstützt, **When** ich einrichten möchte, **Then** Fehlermeldung mit Grund + Hinweis auf PDF-Upload als Alternative.

---

### US-12 — Monate wechseln (Lara)

> Als Lara möchte ich zwischen verschiedenen Monaten wechseln können, damit ich meine historischen Ausgaben einsehen kann.

- **Given** PDFs für mehrere Monate importiert, **When** Dashboard öffnen, **Then** standardmässig aktuellster Monat.
- **Given** ich anderen Monat wähle, **When** Monat laden, **Then** alle Ansichten aktualisieren sich; S2S nur für laufenden Monat, vergangene Monate zeigen "Abgeschlossen".
- **Given** kein PDF für gewählten Monat, **When** wählen, **Then** Hinweis "Keine Daten für [Monat Jahr] — PDF hochladen?".

---

### US-13 — Einzeltransaktionen einer Kategorie (Lara)

> Als Lara möchte ich die Einzeltransaktionen einer Kategorie einsehen, damit ich nachvollziehen kann, wofür ich Geld ausgegeben habe.

- **Given** Kategorienübersicht öffnen, **When** ich Kategorie anklicke, **Then** sehe ich alle Transaktionen mit Datum, Betrag, Empfänger — sortiert Datum absteigend.
- **Given** Kategorie > 20 Transaktionen, **When** Liste laden, **Then** initial 20 + "Weitere laden"-Button — kein ungepaginierter Vollload.

---

### US-14 — Passwort und Einkommen anpassen (Marc)

> Als Marc möchte ich Passwort und Einkommen in den Einstellungen anpassen können, damit ich mein Konto aktuell halten kann.

- **Given** eingeloggt, **When** unter "Einstellungen > Passwort ändern" aktuelles + neues Passwort (min. 8 Zeichen) bestätigen, **Then** wird gespeichert + In-App-Bestätigung.
- **Given** aktuelles Passwort falsch, **When** speichern, **Then** Ablehnung "Aktuelles Passwort falsch".
- **Given** Einkommen in Einstellungen ändern, **When** speichern, **Then** S2S sofort mit neuem Wert neu berechnet.

**Abhängigkeit:** Diese Story hängt an US-1. Wenn US-1 aus dem Sprint fällt, fällt US-14 implizit mit (siehe §5, Risiko-Hinweis am Ende).

---

## 4. MoSCoW-Priorisierung

Maximal 4 Stories im Must — Fokus auf die Core-Value-Pipeline (Daten rein → Verständnis → Safe-to-Spend).

| # | Story | Kategorie | Begründung |
|---|---|---|---|
| 3 | Fixkosten + Einkommen erfassen | **Must** | Pflicht-Input für die S2S-Formel — ohne diese Werte kein Kernwert. |
| 4 | PDF-Upload | **Must** | Einzige Datenquelle im MVP — ohne PDF gibt es nichts zu rechnen. |
| 5 | Transaktionen in Kategorien | **Must** | Voraussetzung für jede Auswertung; macht aus Rohdaten Information. |
| 6 | Safe-to-Spend | **Must** | Die Kern-Value-Proposition — entfällt sie, gibt es kein BudgetBuddy. |
| 1 | Konto + Login | **Should** | Für realen Einsatz unverzichtbar, im Demo-Modus mit Single-User überbrückbar. |
| 2 | Datenschutz-Consent | **Should** | nDSG-pflichtig für echte User-Daten; als statische Zustimmungsseite günstig nachrüstbar. |
| 12 | Monate wechseln | **Should** | Wird ab dem 2. importierten PDF nötig — sonst keine Navigation der Daten. |
| 13 | Einzeltransaktionen je Kategorie | **Should** | Drill-Down ist Voraussetzung dafür, dass Nutzer der Auto-Kategorisierung vertrauen. |
| 14 | Passwort + Einkommen ändern | **Should** | Hängt an US-1; Einkommen-Edit kritisch für US-6. Fällt mit US-1 weg. |
| 7 | Sparziel | **Could** | Adressiert Marc, aber Kernwert (S2S) funktioniert ohne. |
| 8 | Wiederkehrende Ausgaben | **Could** | Braucht ≥ 2 Monate Daten + Erkennungslogik — Aufwand/Nutzen passt nicht ins MVP-Fenster. |
| 9 | KI-Monatsbericht | **Could** | Differenzierungs-Feature, aber Sonnet-Integration + Quality-Review teuer für 3 Monate. |
| 10 | Monatsvergleich | **Could** | Liefert frühestens am MVP-Ende Wert (braucht 2 vollständige Monate). |
| 11 | OpenBanking | **Won't** | Out of MVP. Wird relevant nach Product-Market-Fit (FINMA-Klärung, SIX b.Link-Vertrag, OAuth2/FAPI). Realistisch frühestens v2.0, 6+ Monate nach MVP-Launch. |

---

## 5. Top 3 offene Risiken zur Klärung

### Risiko 1 — Einkommen-Erfassung ist nicht zugeordnet

Keine bestehende Story beschreibt, wo das Monatseinkommen erstmalig erfasst wird. US-6 setzt es als Input voraus, US-14 erlaubt nur das Ändern. Eine implizite Annahme "wir extrahieren das aus dem PDF" trägt nicht: Lohneingänge sind manuell als Einkommen zu identifizieren (US-5 ordnet sie zwar der Kategorie "Einkommen" zu, aber ein zuverlässiges automatisches Monatseinkommen lässt sich daraus nicht ableiten — Splitting-Konten, mehrere Quellen, variable Beträge).

**Vorgeschlagene Lösung (im Doc als Annahme dokumentiert):** Einkommen als Pflichtfeld in den Fixkosten-Wizard (US-3). Editierbar in Einstellungen (US-14).
**Blockt:** US-3, US-6 Umsetzung. **Klärung nötig vor Sprint 1.**

### Risiko 2 — Fixkosten doppelt gezählt?

US-3 erfasst Fixkosten manuell als Eingabe in die S2S-Formel. US-5 kategorisiert dieselben Transaktionen aus dem PDF (Miete → "Wohnen", Krankenkasse → "Versicherung"). Wenn die S2S-Formel `Einkommen − Fixkosten − variable Ausgaben` rechnet und "variable Ausgaben" einfach die Summe aller PDF-Transaktionen ist, werden Fixkosten doppelt subtrahiert.

**Vorgeschlagene Lösung (im Doc als Annahme dokumentiert):** Manuelle Fixkosten sind die Quelle für die Subtraktion. PDF-Transaktionen, die einem Fixkosten-Eintrag matchen (Empfänger + Betragsbereich), werden in der Kategorien-Übersicht angezeigt, aber aus dem Summanden "variable Ausgaben" ausgenommen.
**Alternative:** Nur S2S-relevant ist die Differenz; Fixkosten werden nicht manuell erfasst, sondern aus dem PDF abgeleitet (würde US-3 entfallen lassen).
**Blockt:** Konsistente Implementierung von US-3 und US-6. **Klärung nötig vor US-3/US-6.**

### Risiko 3 — PDF-Datenhaltung & nDSG-Compliance

Es gibt keine Story und keine Architekturentscheidung dazu, wo hochgeladene PDFs gespeichert werden, wie lange, und wer Zugriff hat. Ohne dokumentierte Antwort ist die App unter dem nDSG nicht legal mit echten Nutzerdaten betreibbar. Für eine Course-Demo mit Testdaten unkritisch, für jeden Pilot-Betrieb blockierend.

**Mögliche Optionen:**
- PDFs werden nur im RAM verarbeitet, nicht persistiert; nur extrahierte Transaktionen landen in der DB.
- PDFs werden persistiert (z. B. für Re-Processing bei Bugfixes) mit definierter Aufbewahrungsfrist (z. B. 30 Tage) und Verschlüsselung at rest.

**Blockt:** Production-Launch (nicht den Course-MVP). **Klärung vor Pilot/Live-Schaltung.**

### Zusätzlicher Risiko-Hinweis: US-1 ↔ US-14 Kopplung

US-14 ist nur sinnvoll, wenn US-1 implementiert ist. Beide sind hier als "Should" eingestuft. Wenn US-1 im Sprint gestrichen wird, muss US-14 automatisch ebenfalls gestrichen werden — diese Kopplung ist explizit zu modellieren (z. B. als Abhängigkeit im Sprint-Tool).

---

## 6. Out of Scope (MVP)

Folgende Punkte sind im MVP **explizit nicht** enthalten und dürfen weder priorisiert noch implementiert werden:

### Funktional
- **OpenBanking-Anbindung** (US-11) — siehe Won't.
- **E-Mail-Verifikation** bei Registrierung.
- **Passwort-Reset per E-Mail.**
- **Rate-Limiting / Login-Sperre** bei wiederholten Fehleingaben.
- **PLZ-Validierung** gegen offizielle Schweizer PLZ-Liste.
- **Automatischer Session-Ablauf** nach Inaktivität.
- **E-Mail-Versand** des Monatsberichts (nur In-App-Benachrichtigung im MVP).
- **Multi-Account-Unterstützung** — Privat- + Geschäftskonto + Partnerkonto + Sparkonto. Datenmodell setzt 1 User = 1 Datenquelle voraus.
- **Automatische Erkennung des Monatseinkommens** aus PDF-Transaktionen.
- **Mobile-native App** (iOS/Android) — Web-App, responsive Design.

### Geografisch / Marktlich
- **Internationaler Rollout** — Schweiz only (CHF, Schweizer Banken, nDSG).
- **B2B / Berater-Tool** — kein Multi-Tenant, keine Berater-Rolle.

### Banken
- Banken ausserhalb **UBS, Raiffeisen, PostFinance** — keine Garantie für korrekte Extraktion. Fehlermeldung beim Upload.

### Technisch
- **PostgreSQL-Migration** — SQLite für MVP. Migration ist vorgesehen, aber nicht Teil des MVP-Scopes.
- **Spring Boot 4 / WebFlux** — explizit ausgeschlossen.

---

## Versionierung

| Version | Datum | Änderung |
|---|---|---|
| 1.0 | 2026-05-20 | Initial — basiert auf 14 User Stories aus CLAUDE.md, MoSCoW-Refinement (max 4 Must), 3 offene Risiken |
