# Playbook Abschlusspräsentation — Demo (B30–B34)

**Für:** Sergio, Vortragende:r Bereich 3 (Demo) der
[Abschlusspräsentation](../presentations/abschlusspraesentation.md) — Jason (Bereich 1,
Architektur) und Daniel (Bereich 2, Methodik) reden vor dir, insgesamt 30 Minuten, dein Anteil
**ca. 11–12 Minuten** (B30–B34 plus **B32b**, neu) — im Team abgesprochen, dass Bereich 3
gegenüber den ursprünglich geplanten 10 Minuten um **maximal 2 Minuten** wachsen darf, damit die
Aboerkennung Platz hat (siehe B32b unten); nicht so verkürzt wie bei der Testpräsentation.
**Wann:** Termin gemäss Kursplan (im Team noch zu bestätigen, siehe „Offene Punkte" unten).
**Ziel:** Der volle Flow, nicht die verkürzte Fassung — Live-Registrierung + Onboarding
(B30), PDF-Import (B31), Kategorisierung + 1 Korrektur (B32), **zweiter Import + automatische
Aboerkennung (B32b, neu)**, Safe-to-Spend (B33), **Konto löschen** (B34). Anders als die
[Testpräsentation](testpraesentation-playbook.md): `lara@demo.bb` existiert zu Beginn **nicht**
und wird live angelegt, nicht vorher geseedet.

Dieses Playbook übernimmt den B30–B34-Ablauf aus
[abschlusspraesentation.md](../presentations/abschlusspraesentation.md#bereich-3--demo--10-min),
verifiziert gegen den tatsächlichen Stand von `feature/abschlusspraesentation`
(Stand 28.09.2026) — Texte, Routen und Screens unten sind gegen den Code geprüft, nicht nur
aus dem alten Dokument übernommen. Wie bei der Testpräsentation als **Zielumgebung**
angenommen: die deployte Instanz `https://budgetbuddy-0myo.onrender.com` (Render + Neon), kein
lokales Setup — dieses Dokument nennt das nirgends explizit, aber `seed_demo_accounts.sh`
unterstützt `BUDGETBUDDY_API=<host>` genau für diesen Fall, und die Testpräsentation lief
erfolgreich so. Falls das Team stattdessen lokal demonstrieren will, bitte vor der Probe klären.

---

## 1. Wichtigster Unterschied zur Testpräsentation: die 20 Minuten davor

Bei der Testpräsentation wart ihr allein im Slot und konntet die App unmittelbar vor eurem
Auftritt aufwärmen. Hier reden **Jason und Daniel zusammen 20 Minuten**, bevor du anfängst — und
niemand berührt die App währenddessen. Zwei getrennte Cold-Start-Risiken schlagen in diesem
Fenster zu:

- **Neon Scale-to-Zero** nach 5 Minuten DB-Inaktivität ([ADR-12](../adr/ADR-12-datenpersistenz-produktion.md)).
- **Render-Spin-down** des Backends selbst nach 15 Minuten ohne Request (Free-Tier,
  [ADR-12](../adr/ADR-12-datenpersistenz-produktion.md)) — das ist **eigenständig** vom
  Neon-Risiko und dauert beim Aufwachen spürbar länger (Web-Service-Kaltstart plus DB-Kaltstart
  danach).

Ohne Gegenmassnahme ist das Backend bei Minute 20 (Start von B30) garantiert eingeschlafen, weil
20 > 15 Minuten. Das ist strukturell anders als bei der Testpräsentation, wo dieses Risiko nur
„Neon nach 5 Minuten" war und durch ein einmaliges Aufwärmen kurz vor dem Slot abgedeckt war.

**Gegenmassnahme:** jemand (nicht Jason oder Daniel selbst, die reden) schickt **einen Request
gegen die App etwa bei Minute 8 von Bereich 2** (Handy oder Zweitgerät reicht, einfach die
Login-Seite öffnen) — rechtzeitig, damit ein voller Kaltstart (Render + Neon) sicher abklingt,
bevor du bei Minute 20 übernimmst. Zusätzlich unmittelbar vor deinem Einstieg (siehe Abschnitt 2)
nochmal prüfen, nicht nur verlassen auf den einen Request 12 Minuten vorher.

---

## 2. Vorbereitung — vor der Präsentation

- [ ] **`lara@demo.bb` existiert auf der deployten Instanz nicht.** Nach der Testpräsentation
      wurde sie dort über „Konto löschen" (In-App) entfernt — trotzdem vorher einmal gezielt
      prüfen (z. B. Login-Versuch mit falschem Passwort: `401` statt einer Fehlermeldung wegen
      unbekannter E-Mail bestätigt, dass der Account existiert und noch nicht sauber weg ist).
      Falls sie doch noch existiert: manuell zurücksetzen, siehe
      [docs/demo/README.md → Zurücksetzen](README.md#zurücksetzen) (Achtung: das Skript dort
      zielt auf die lokale Docker-Postgres, gegen die deployte Instanz braucht es den
      entsprechenden Verbindungsstring).
- [ ] **`backend/tools/seed_demo_accounts.sh` NICHT für Lara laufen lassen** — legt sie sonst
      vorab an, B30 scheitert dann live mit `409`. (Für Marc unkritisch, falls der zweite Account
      ohnehin schon separat gepflegt wird.)
- [ ] `ANTHROPIC_API_KEY` auf der deployten Instanz gesetzt — sonst zeigt B32 nur Lookup +
      pauschal „Sonstiges" statt der echten Mischung.
- [ ] **Zwei** Kontoauszüge für Lara lokal gesichert, nicht nur einer:
      [`PostFinance_Kontoauszug_Lara_2026-09.pdf`](statements/PostFinance_Kontoauszug_Lara_2026-09.pdf)
      für B31 (derselbe, der schon für die Testpräsentation genutzt wurde) **und**
      [`PostFinance_Kontoauszug_Lara_2026-08.pdf`](statements/PostFinance_Kontoauszug_Lara_2026-08.pdf)
      für B32b (Aboerkennung braucht zwei aufeinanderfolgende Kalendermonate mit demselben
      Empfänger, siehe B32b unten). **Nur relevant, falls die Präsentation noch im September 2026
      stattfindet** — `SafeToSpendService` rechnet ausschliesslich für den laufenden Monat
      ([docs/demo/README.md](README.md#vor-der-präsentation-neu-generieren)); fällt der Termin in
      den Oktober, vorher `generate_demo_statements.py` neu laufen lassen und frische Auszüge für
      September **und** Oktober für Lara vorbereiten (die beiden dann jeweils als „laufender
      Monat" und „Vormonat" für B31/B32b verwenden).
- [ ] Laras Passwort im Team vereinbart, im Passwortmanager griffbereit (nicht live tippen).
- [ ] Laras Zahlen bereit, um sie beim Onboarding ohne Nachschlagen einzutippen — Einkommen
      **und** Fixkosten werden auf derselben `/onboarding`-Seite erfasst, kein Umweg über
      Einstellungen nötig (seit FE-FC-09 liegt die Einkommens-Card direkt im Onboarding):

  | Feld | Wert | Intervall |
  | --- | --- | --- |
  | Monatseinkommen | 1'900.– | — |
  | WG-Zimmer Länggasse | 650.– | monatlich |
  | Krankenkasse CSS | 180.– | monatlich |
  | Handy Salt | 25.– | monatlich |
  | Semestergebühr Uni Bern | 1'500.– | jährlich |

- [ ] Kurz geprüft, dass `feature/abschlusspraesentation` (Foliengrundlage) auf dem Stand von
      `main` ist — betrifft nur die Folien/Inhalte von Bereich 1/2, nicht die Demo selbst (die
      läuft ohnehin gegen die deployte Instanz, die aus `main` gebaut ist).

---

## 3. Unmittelbar vor deinem Einstieg (während Daniel zu Ende redet)

- **Zweite Aufwärm-Kontrolle:** falls möglich, auf deinem eigenen Gerät kurz `/login` öffnen,
  bevor du das Wort übernimmst — das fängt ab, falls die Minute-8-Massnahme aus Abschnitt 1 aus
  irgendeinem Grund nicht geklappt hat. Sichtbar für dich, nicht für das Publikum (zweiter Tab,
  nicht der Präsentationsbildschirm).
- **Tab-Reihenfolge:** ein Tab mit `/register` (nicht `/login` — B30 startet mit Live-
  Registrierung), Passwort griffbereit im Passwortmanager.
- **Screenshot-Fallback für B31** bereithalten (Fortschrittsanzeige + fertige Import-Liste) —
  der am längsten laufende Schritt, falls die Live-Kategorisierung hängt.
- **Screen-Handoff einplanen**, falls du nicht am Gerät sitzt, das Jason/Daniel benutzt haben.

---

## 4. Der Ablauf — Schritt für Schritt

| Nr | Min | Screen |
| --- | --- | --- |
| B30 | 0:00–2:00 | `/register` → `/onboarding` → `/dashboard` |
| B31 | 2:00–4:00 | `/import` |
| B32 | 4:00–6:00 | `/import` (gleiche Seite) |
| B32b | 6:00–8:00 | `/import` → Glocke → `/budget` |
| B33 | 8:00–9:00 | `/dashboard` |
| B34 | 9:00–11:00 | `/einstellungen` |

11 von maximal 12 Minuten verplant (1 Minute Puffer) — die ursprünglichen Zeiten für B30–B34
bleiben unverändert, B32b kommt als eigener Block dazwischen.

### B30 — Registrierung + Onboarding (0:00–2:00)

**Tust du:**
1. Browser zeigt `/register` (H1 „Registrieren"). Feld „E-Mail" → `lara@demo.bb`, Feld
   „Passwort" → aus dem Passwortmanager einfügen. Die Felder „Vorname (optional)" / „Nachname
   (optional)" kannst du auslassen — für die Demo nicht nötig. Button „Konto erstellen" klicken.
2. Die App setzt direkt ein JWT-Cookie (Auto-Login) und leitet automatisch auf `/onboarding`
   weiter — kein separater Login-Schritt.
3. Onboarding-Seite (H1 „Budget") zeigt **Einkommen und Fixkosten auf derselben Seite** — seit
   FE-FC-09 keine getrennten Schritte mehr, kein Umweg über Einstellungen nötig:
   - Card „Einkommen": Feld „Monatseinkommen (CHF)" → `1900`, Button „Einkommen speichern".
   - Darunter „Fixkosten": Felder „Bezeichnung" / „Betrag (CHF)" / „Intervall", Button
     „Fixkosten speichern" — die vier Positionen aus der Tabelle oben nacheinander eintippen und
     je einzeln speichern.
4. Nach der letzten Fixkosten-Position „Fertig — weiter zum Dashboard" klicken (Button heisst nur
   dann so, wenn schon Daten erfasst wurden — sonst „Später erfassen — weiter zum Dashboard").
5. Auf `/dashboard` zeigt die Safe-to-Spend-Card sofort einen Betrag — der
   „Kein Einkommen erfasst"-Banner erscheint in diesem Ablauf gar nicht, weil das Einkommen
   bereits im Onboarding gesetzt wurde.

**Sagst du (sinngemäss):**

> „Das ist Lara — 22, Studentin in Bern, eine unserer zwei Personas. Wir registrieren sie hier
> live, öffentlich, über genau denselben Endpoint, den jede echte Nutzerin auch durchläuft."
> *(Registrierung abschicken)* „Kein separater Login danach — das Konto setzt direkt ein
> JWT-Cookie." *(Onboarding, Einkommen eintippen)* „Ihr Einkommen — 1'900 Franken." *(Fixkosten
> eintippen)* „Und ihre Fixkosten: WG-Zimmer, Krankenkasse, Handy, anteilig die
> Semestergebühr. Beides auf einer Seite, weil beides gleichermassen in den Safe-to-Spend
> einfliesst." *(Dashboard zeigen)* „Und schon steht ein erster Betrag — noch ohne die echten
> Ausgaben. Genau die holen wir jetzt rein."

**Hinweis für die Foliengrundlage:** [abschlusspraesentation.md](../presentations/abschlusspraesentation.md)
beschreibt B30 noch mit dem alten Ablauf (Fixkosten-Wizard → Dashboard-Banner → Einkommen separat
in den Einstellungen) — das ist seit FE-FC-09 überholt. Falls Bereich 1/2 Screenshots oder
Formulierungen davon übernehmen, dem Team den neuen Ablauf hier zeigen.

**Zeitdruck-Ventil:** Läuft die Zeitprobe hier über 2 Minuten, eher auf 1–2 Fixkosten-Positionen
kürzen (z. B. nur WG-Zimmer + CSS) als B30 ganz zu streichen — das Team hat diesen Schritt bewusst
in die Demo aufgenommen, weil er den echten Erstnutzer-Flow zeigt.

---

### B31 — PDF-Import (2:00–4:00)

**Tust du:**
1. Über die Navigation „Import" (`/import`, H1 „Import", Untertitel „Kontoauszug als PDF
   hochladen — die Transaktionen werden automatisch eingelesen.").
2. „Datei wählen" (oder Drag & Drop) → `PostFinance_Kontoauszug_Lara_2026-09.pdf` auswählen.
3. Kurz „Kontoauszug wird gelesen …", danach Fortschrittsbalken „X von Y Transaktionen
   kategorisiert".
4. Erfolgsmeldung „N Transaktionen erkannt." plus die Liste der importierten Buchungen
   (Datum, Buchungstext/Gegenpartei, Betrag, vorausgewählte Kategorie).

**Sagst du:**

> „Lara bekommt von PostFinance monatlich einen Kontoauszug als PDF — genau den lädt sie hier
> hoch." *(Datei wählen)* „Das läuft asynchron im Hintergrund: ein voller Kontoauszug mit über
> hundert Buchungen lief früher synchron ins 30-Sekunden-Timeout und der komplette Import ging
> verloren. Heute parst das Backend in rund zwei Sekunden, die Kategorisierung läuft danach im
> Hintergrund mit Fortschrittsanzeige weiter." *(auf den Balken zeigen, warten)* „Fertig — und
> jede Buchung hat schon eine Kategorie."

---

### B32 — Kategorisierung + 1 Korrektur (4:00–6:00)

**Tust du:**
1. Auf `/import` zur importierten Liste scrollen.
2. Auf ein, zwei sinnvoll kategorisierte Zeilen zeigen (z. B. „Lebensmittel" bei Migros/Coop).
3. Eine Zeile mit Kategorie „Sonstiges" (🗂️) suchen.
4. Dropdown dieser Zeile öffnen, passende Kategorie wählen (z. B. „Restaurant"/„Freizeit").

**Sagst du:**

> „Die Kategorien kommen aus zwei Quellen: bekannte Händler wie Migros oder die SBB holt eine
> lokale Lookup-Tabelle sofort, ohne API-Call. Alles Unbekannte hat im Hintergrund Claude
> kategorisiert, gebündelt in einem einzigen Request." *(auf Sonstiges-Zeile zeigen)* „Diese hier
> ist bei 'Sonstiges' gelandet — der Fallback, wenn auch Claude sich nicht sicher war. Ich
> korrigiere das jetzt von Hand" *(Dropdown öffnen, Kategorie wählen)* „— und das merkt sich
> BudgetBuddy für Lara persönlich, pro Nutzer getrennt. Taucht derselbe Händler beim nächsten
> Import wieder auf, ist er sofort richtig kategorisiert, ganz ohne erneuten Claude-Call — und
> ohne dass Laras Korrektur die Vorschläge irgendeiner anderen Nutzerin beeinflusst."

**Falls keine „Sonstiges"-Zeile dabei ist:** keine Zeit mit Suchen verlieren — eine beliebige
Zeile korrigieren und sagen: „Das funktioniert für jede Zeile, falls Lara anderer Meinung ist als
die App."

---

### B32b — Zweiter Import + Aboerkennung (6:00–8:00, neu)

**Warum hier und nicht in B31/B32:** Die Aboerkennung braucht zwei aufeinanderfolgende
Kalendermonate mit demselben Empfänger und einem Betrag innerhalb ±2% Toleranz — mit nur einem
Import (B31) hat noch nichts zwei Treffer. Ein eigener Block hält B31 (Import-Mechanik/Async) und
B32 (Kategorisierung/Korrektur) sauber bei ihrem jeweiligen Punkt, statt einen dritten Punkt dort
hineinzumischen. Vor B33 platziert, weil `SafeToSpendService` nur den laufenden Monat
(September) rechnet — der zusätzliche August-Import verändert die Safe-to-Spend-Zahl in B33
nicht, es gibt also keine verwirrende Zahlenänderung direkt vor dem Finale.

**Tust du:**
1. Auf `/import` bleiben (oder erneut dorthin), zweite Datei hochladen:
   `PostFinance_Kontoauszug_Lara_2026-08.pdf`. Läuft wie in B31 (kurz „Kontoauszug wird
   gelesen …", Fortschrittsbalken, Erfolgsmeldung „N Transaktionen erkannt.") — die
   Async-Erklärung aus B31 hier nicht wiederholen.
2. Die Aboerkennung läuft automatisch am Ende desselben Import-Jobs, noch bevor der Import als
   fertig gemeldet wird — kein zusätzliches Warten nötig über den normalen Import-Fortschritt
   hinaus.
3. Sobald der Import fertig ist, zeigt die Benachrichtigungsglocke (Sidebar/Topbar, auf jeder
   Seite sichtbar) ein neues ungelesenes Badge — kurz draufzeigen, Dropdown öffnen: Text „N neues
   Abo erkannt: …" bzw. „N neue Abos erkannt: …" mit den Empfängernamen.
4. Über die Navigation zu „Budget" (`/budget`) wechseln, zum Abschnitt „Erkannte Abos" scrollen.
   Vier Positionen tragen jetzt ein „Neu"-Tag neben der „seit …"-Angabe (so lange die
   zugehörige Benachrichtigung ungelesen ist): „WG LAENGGASSE 42" (Miete), „CSS VERSICHERUNG AG",
   „SALT MOBILE SA" — und, als eigentliche Pointe, **„SPOTIFY AB" (12.90)**. Auf die Spotify-Zeile
   zeigen: die steht **nicht** in Laras Fixkosten aus B30.

**Sagst du:**

> „Noch ein zweiter Kontoauszug — der von August. Der zeigt eine weitere Fähigkeit: Taucht
> derselbe Empfänger in zwei aufeinanderfolgenden Monaten mit ähnlichem Betrag auf, erkennt
> BudgetBuddy das automatisch als Abo, ganz ohne dass Lara das irgendwo einträgt." *(auf die
> Glocke zeigen)* „Hier — vier neue Hinweise." *(zu Budget wechseln, „Erkannte Abos" zeigen)*
> „Miete, Krankenkasse, Handy-Abo — die kennt Lara zwar schon aus ihren eigenen Fixkosten, die
> App findet sie hier unabhängig davon in den echten Kontobewegungen wieder. Aber hier" *(auf
> Spotify zeigen)* „— Spotify hat sie nirgends eingetragen. Das findet die App ganz von selbst,
> allein daraus, dass derselbe Betrag zwei Monate in Folge abgebucht wurde."

**Falls die Erkennung aus irgendeinem Grund nicht sofort sichtbar ist:** `/budget` einmal neu
laden (F5) — die Anzeige liest beim Laden aus der DB, ein hängender Zustand ist unwahrscheinlich,
da die Erkennung synchron vor dem „fertig" des Import-Jobs läuft.

---

### B33 — Safe-to-Spend (8:00–9:00)

**Tust du:**
1. Über die Navigation zurück auf „Übersicht" (`/dashboard`).
2. Safe-to-Spend-Card zeigen — der Betrag hat sich gegenüber B30 verändert (September-Import
   jetzt eingerechnet; der zusätzliche August-Import aus B32b wirkt sich auf diese Zahl nicht
   aus, da nur der laufende Monat gerechnet wird).
3. Optional, falls Zeit bleibt: Drei-Monats-Tabelle darunter („Drei Monate bis …", Spalten
   Monat/Einnahmen/Ausgaben/Differenz) — zeigt dank B32b jetzt auch für August echte Zahlen statt
   „–".

**Sagst du:**

> „Und jetzt schliesst sich der Kreis: Einkommen und Fixkosten kennt die App aus dem Onboarding
> von eben, die tatsächlichen Ausgaben aus dem Import — daraus berechnet sie Laras
> Safe-to-Spend: [Betrag laut Bildschirm] Franken, das bleibt ihr diese Woche noch. Ohne dass sie
> eine einzige Zahl selbst eingetippt hat ausser ihrem Einkommen und ihren Fixkosten. Genau das
> ist unser Kernversprechen: ein Betrag, dem man vertraut, weil er aus echten Kontobewegungen
> kommt — nicht aus einer Excel-Tabelle, die man nie aktualisiert."

---

### B34 — Konto löschen (9:00–11:00)

**Tust du:**
1. Über das Account-Menü zu „Einstellungen" (`/einstellungen`), zur Card „Konto löschen"
   scrollen.
2. Button „Konto löschen" klicken → Bestätigungsdialog verlangt das Passwort → eingeben →
   „Konto löschen" bestätigen.
3. Landest auf `/login` mit dem Hinweis „Dein Konto wurde gelöscht. Alle deine Daten sind
   entfernt."
4. Optional (falls Zeit): einen Login-Versuch mit denselben Zugangsdaten zeigen — schlägt fehl.

**Sagst du:**

> „Zum Schluss ein Datenschutz-Feature, das genauso ernst genommen wird wie der
> Safe-to-Spend-Wert: Lara kann ihr Konto vollständig löschen — Profil, Transaktionen,
> Fixkosten, Benachrichtigungen, alles weg, mit Passwortbestätigung, nicht per Klick aus
> Versehen." *(löschen, Login-Fehlschlag zeigen)* „Ein späterer Login mit denselben
> Zugangsdaten schlägt danach fehl — genau das erwarten wir bei einem nDSG-konformen Löschen."

**Wichtig — B34 räumt sich selbst auf:** Läuft die Demo bis hierhin durch, ist `lara@demo.bb`
danach wieder frei für den nächsten Durchlauf (Zeitprobe, Aufzeichnung, o. Ä.) — kein manuelles
Zurücksetzen nötig. Nur ein abgebrochener Testlauf, der B34 nicht erreicht, braucht das manuelle
Zurücksetzen aus Abschnitt 2.

**Falls Zeit übrig bleibt (kein fester Puffer-Slot, siehe unten):** auf `/budget` (vor B34, das
Konto ist danach gelöscht) den „Kein Abo"-Button einer der in B32b gezeigten Zeilen zeigen — die
Zeile wandert in eine eigene Card „Kein Abo" darunter und lässt sich von dort per „Reaktivieren"
zurückholen. Zeigt, dass die Erkennung korrigierbar ist, nicht nur automatisch. (Die frühere
Idee, hier erstmals „Erkannte Abos" zu zeigen, entfällt — das übernimmt jetzt B32b als fester
Schritt, nicht als Puffer.)

---

## 5. Risiken & Fallback

| Risiko | Gegenmassnahme |
| --- | --- |
| Render-Spin-down (15 Min.) + Neon Scale-to-Zero (5 Min.) während Bereich 1+2 | Request bei Minute 8 von Bereich 2 (Abschnitt 1), zweite Kontrolle unmittelbar vor B30 (Abschnitt 3) |
| B31-Import hängt/dauert zu lange | Screenshot-Fallback zeigen, ehrlich sagen „normalerweise dauert das X Sekunden" |
| `ANTHROPIC_API_KEY` fällt aus / Claude-Call schlägt fehl | Fällt automatisch auf „Sonstiges" zurück — Import bricht nicht ab; B32 hat garantiert eine Sonstiges-Zeile |
| Registrierung schlägt mit `409` fehl (Lara existiert doch schon) | Vorbereitung in Abschnitt 2 hat das ausgeschlossen — falls doch: kurz auf zweitem Tab `lara2@demo.bb` registrieren und live erklären, dass es sich um eine frische Kopie handelt, statt die Demo abzubrechen |
| Onboarding-Eingabe dauert zu lange | Zeitdruck-Ventil in B30: auf 1–2 Fixkosten-Positionen kürzen, nicht B30 streichen |
| September-Auszug passt nicht mehr zum laufenden Monat (Termin rutscht in den Oktober) | Vorher `generate_demo_statements.py` neu laufen lassen (Abschnitt 2) |
| B32b: keine Abos erkannt (z. B. falsche Monats-Kombination erwischt) | August+September sind fest verdrahtete, identische Beträge im Generator (Miete/CSS/Salt/**Spotify**, Spotify eigens für B32b ergänzt, nicht in den Fixkosten) — bei korrektem Dateipaar praktisch ausgeschlossen; zur Sicherheit vor der Präsentation einmal in der Zeitprobe verifizieren, nicht erst live entdecken |
| Bereich 3 überschreitet die vereinbarten max. 12 Minuten (10 + 2) | Zeitdruck-Ventile in B30/B32 zuerst ziehen, B34s optionalen Login-Fehlschlag-Check (Schritt 4) weglassen, bevor B32b gekürzt wird — B32b ist der neue Inhalt, den das Team extra Zeit dafür eingeräumt hat |

---

## 6. Cheat-Sheet — exakte UI-Texte (Stand `feature/abschlusspraesentation`, gegen Code geprüft)

| Screen | Text |
| --- | --- |
| Registrierung (`/register`) | H1 „Registrieren", Felder „E-Mail" / „Passwort" / „Vorname (optional)" / „Nachname (optional)", Button „Konto erstellen" (während Submit „Konto wird angelegt…") |
| Login (`/login`) | H1 „Login", Felder „E-Mail" / „Passwort", Button „Einloggen" (während Submit „Wird eingeloggt…") |
| Nav | „Übersicht" (`/dashboard`), „Transaktionen" (`/categories`), „Import" (`/import`), „Budget" (`/budget`) — Einstellungen im Account-Menü, nicht in der Hauptnav |
| Onboarding (`/onboarding`) | H1 „Budget", Card „Einkommen" (Feld „Monatseinkommen (CHF)", Button „Einkommen speichern") **und** Abschnitt „Fixkosten" (Felder „Bezeichnung" / „Betrag (CHF)" / „Intervall", Button „Fixkosten speichern") auf derselben Seite, Abschluss-Button „Fertig — weiter zum Dashboard" bzw. „Später erfassen — weiter zum Dashboard" wenn nichts eingegeben wurde (beides überspringbar) |
| Import | H1 „Import", Dropzone „PDF-Datei hierhin ziehen oder" / Button „Datei wählen", Hinweis „Nur .pdf · maximal 10 MB" |
| Import — während Parsen | „Kontoauszug wird gelesen …" |
| Import — Fortschritt | „X von Y Transaktionen kategorisiert" |
| Import — Erfolg | „N Transaktionen erkannt." (Singular: „1 Transaktion erkannt.") |
| Import — Duplikat (409) | Modal „Kontoauszug bereits importiert", Buttons „Trotzdem importieren" / „Abbrechen" |
| Dashboard — Safe-to-Spend | Card-Titel „Safe-to-Spend", darunter „noch N Woche(n) im Monat", ggf. „Letzte Woche des Monats" |
| Dashboard — Budget überzogen | „Achtung: Dein Budget für diese Woche ist überzogen" |
| Dashboard — kein Einkommen | „Kein Einkommen erfasst" / „Bitte erfasse dein Monatseinkommen auf der Budget-Seite" (in B30 nicht sichtbar, da Einkommen schon im Onboarding gesetzt wird) |
| Dashboard — Drei-Monats-Tabelle | Titel „Drei Monate bis {Monat}", Spalten „Monat / Einnahmen / Ausgaben / Differenz" |
| Dashboard — Teaser-Card | Card „Monatliche fixe Ausgaben" (Total aus Fixkosten + erkannten Abos, darunter „X Fixkosten + Y erkannte Abos"), CTA „Zu Budget →", führt auf `/budget` — **keine** eigene Abo-Karte/-Seite mehr |
| Budget (`/budget`) | H1 „Budget", Card „Einkommen" (wie im Onboarding), Zwischenüberschrift „Ausgaben" mit „Erfasste Fixkosten" und „Erkannte Abos" darunter |
| Budget — Erkannte Abos | H3 „Erkannte Abos", neue Zeilen tragen ein „Neu"-Tag neben „seit …", solange die zugehörige Benachrichtigung ungelesen ist; Button je Zeile „Kein Abo" (verschiebt sie in Card „Kein Abo" mit „Reaktivieren"-Button) |
| Benachrichtigungsglocke | Ungelesen-Badge, Dropdown-Text „N neues Abo erkannt: …" bzw. „N neue Abos erkannt: …" (Empfängernamen, bei vielen „und N weitere") |
| Einstellungen (`/einstellungen`) | Card „Passwort", Theme-Auswahl, Card „Konto löschen" (Bestätigungsdialog mit Passwort) — **kein** Einkommensfeld mehr (seit FE-FC-09 auf `/budget`/`/onboarding`) |
| Nach Konto-Löschung | Login-Seite zeigt „Dein Konto wurde gelöscht. Alle deine Daten sind entfernt." |
| Kategorie „Sonstiges" | Icon 🗂️, Slug `sonstiges` — Fallback, wenn auch Claude unsicher war |

---

## Offene Punkte

- [ ] Exakter Termin der Abschlusspräsentation im Team bestätigen (dieses Dokument nennt keinen
      fixen, siehe Kopf).
- [ ] Wer schickt bei Minute 8 von Bereich 2 den Aufwärm-Request (Abschnitt 1) — vorher im Team
      klären, nicht erst live improvisieren.
- [ ] Vor der Präsentation kurz prüfen, ob `lara@demo.bb` auf der deployten Instanz seit der
      Testpräsentation tatsächlich wieder frei ist (Abschnitt 2).
- [ ] Zeitprobe für B30–B34 als Ganzes, insbesondere den Onboarding-Schritt (Einkommen + je eine
      Fixkosten-Position einzeln speichern ist potenziell langsamer als ein Formular mit allen
      Feldern auf einmal).
- [ ] **[abschlusspraesentation.md](../presentations/abschlusspraesentation.md) ist an mehreren
      Stellen veraltet gegenüber dem aktuellen Code** und sollte vor dem Vortrag aktualisiert
      werden, nicht nur dieses Playbook: B30 beschreibt noch den Fixkosten-Wizard mit
      anschliessendem Dashboard-Banner und separatem Einkommens-Schritt in den Einstellungen
      (seit FE-FC-09 liegt Einkommen direkt im Onboarding); Verweise auf eine „Ausgaben"-Seite
      (`/ausgaben`) meinen inzwischen `/budget`; ein „Abos"-Teaser mit eigener Abo-Karte existiert
      auf dem Dashboard nicht mehr, ersetzt durch die Card „Monatliche fixe Ausgaben". Ergänzend
      fehlt dort auch B32b (Aboerkennung) komplett, weil das Dokument die Zeitbudget-Erweiterung
      noch nicht kennt.
- [ ] **Zeitbudget-Erweiterung für Bereich 3 (max. +2 Minuten wegen B32b) mit Jason/Daniel bzw.
      der Kursleitung abgestimmt?** Im Team wurde das für dieses Playbook bereits freigegeben
      (siehe Kopf), aber die 30-Minuten-Gesamtzeit und die Aufteilung der drei Bereiche stehen in
      [abschlusspraesentation.md](../presentations/abschlusspraesentation.md) — dort sollte die
      Änderung ebenfalls landen, nicht nur hier.
