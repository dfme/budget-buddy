# Playbook Testpräsentation — Demo (T30–T33)

**Für:** Sergio, Vortragende:r des Demo-Teils an der Testpräsentation.
**Wann:** 23.09.2026, Demo-Anteil ca. 4–5 Minuten (Annahme aus
[abschlusspraesentation.md](../presentations/abschlusspraesentation.md), im Team noch nicht
bestätigt — siehe dort „Offene Punkte").
**Ziel:** Hauptnutzen zeigen — PDF-Import → automatische Kategorisierung + 1 manuelle Korrektur →
Safe-to-Spend. Kein Live-Registrieren, keine Wizard-Klicks, kein Konto löschen (anders als die
Abschlusspräsentation).

Dieses Playbook übernimmt den T30–T33-Ablauf aus
[abschlusspraesentation.md](../presentations/abschlusspraesentation.md#testpräsentation--verkürzter-demo-ablauf-t30–t33),
ist aber gegen den tatsächlichen aktuellen Stand von `origin/main` geschrieben (Texte, Routen,
Screens). **Zielumgebung:** die deployte Instanz `https://budgetbuddy-0myo.onrender.com`
(Render + Neon), kein lokales Setup. Das einzig verbleibende Risiko ist Neons Scale-to-Zero nach 5
Minuten Inaktivität — dagegen hilft das „Aufwärmen" in Abschnitt 2.

---

## 1. Vorbereitung — erledigt ✅

- [x] Passwort besorgt und getestet
- [x] `lara@demo.bb` auf der deployten Instanz erstellt (nicht lokal)
- [x] Einkommen (1'900.–) und alle Fixkosten manuell erfasst
- [x] April–August manuell importiert, September-Auszug bewusst zurückgehalten (lokal gesichert,
      bereit für T31)
- [x] `ANTHROPIC_API_KEY` korrekt gesetzt — Kategorien-Mischung in den bereits importierten
      Monaten bestätigt das
- [x] Timing für den September-Import unkritisch (anhand der bisherigen Importe eingeschätzt)

**Laras Zahlen** (für T30, nicht improvisieren müssen):

| Feld | Wert |
| --- | --- |
| Einkommen | 1'900.– |
| WG-Zimmer Länggasse | 650.– (monatlich) |
| Krankenkasse CSS | 180.– (monatlich) |
| Handy Salt | 25.– (monatlich) |
| Semestergebühr Uni Bern | 1'500.– (jährlich) |

---

## 2. Morgen, kurz vor der Demo

- **App aufwärmen:** 5–10 Minuten vor eurem Slot einmal `https://budgetbuddy-0myo.onrender.com`
  öffnen und einloggen — sonst weckt der erste Request live vor Publikum die Neon-Datenbank auf
  (Scale-to-Zero nach 5 Min. Inaktivität, ADR-12) und die App hängt kurz. Ein Funktionscheck
  30 Minuten vorher (geplant) ersetzt das nicht — Neon schläft nach 5 Minuten Inaktivität wieder
  ein, das Aufwärmen muss deshalb kurz vor dem Slot nochmal passieren.
- **Tab-Reihenfolge vorbereiten:** ein Tab mit `/login` (bzw. bereits eingeloggt auf
  `/dashboard`), Passwort im Passwortmanager griffbereit (nicht in der Adressleiste, nicht im
  Klartext auf dem Bildschirm sichtbar).
- **Screenshot-Fallback für T31** bereithalten (ein Screenshot der laufenden Fortschrittsanzeige
  und der fertigen Import-Liste) — falls die Live-Kategorisierung hängt, ist das der am längsten
  laufende Schritt.

---

## 3. Der Ablauf — Schritt für Schritt

| Nr | Min | Screen |
| --- | --- | --- |
| T30 | 0:00–1:00 | Login → Dashboard |
| T31 | 1:00–2:30 | `/import` |
| T32 | 2:30–4:00 | `/import` (gleiche Seite) |
| T33 | 4:00–5:00 | Dashboard |

### T30 — Login + Dashboard (0:00–1:00)

**Tust du:**
1. Browser zeigt `/login`. Feld „E-Mail" → `lara@demo.bb`, Feld „Passwort" → aus dem
   Passwortmanager einfügen (nicht tippen). Button „Einloggen" klicken.
2. Landest automatisch auf `/dashboard` (Titel „Dashboard", Nav-Label „Übersicht").
3. Zeigst kurz auf die Safe-to-Spend-Card (noch ohne den September-Import — der Betrag basiert
   auf den 5 bereits importierten Monaten plus Fixkosten/Einkommen).

**Sagst du (sinngemäss, kein Auswendiglernen nötig):**

> „Das ist Lara — 22, Studentin in Bern, eine unserer zwei Personas. Ihr Einkommen und ihre
> Fixkosten hat sie längst im Onboarding erfasst: WG-Zimmer, Krankenkasse, ihr Handy-Abo, anteilig
> die Semestergebühr — zusammen kommt da einiges zusammen bei 1'900 Franken Einkommen. Wir steigen
> nicht bei der Registrierung ein, sondern direkt bei dem, was BudgetBuddy eigentlich ausmacht:
> wie kommen die echten Ausgaben rein, und was macht die App daraus."

**Nicht tun:** nicht in `/ausgaben` reinklicken, um Fixkosten einzeln zu zeigen — dafür ist keine
Zeit, die Zahlen kennt ihr aus der Tabelle oben und könnt sie einfach nennen.

---

### T31 — PDF-Import (1:00–2:30)

**Tust du:**
1. Über die Navigation „Import" klicken (Route `/import`, Titel „Import", Untertitel „Kontoauszug
   als PDF hochladen — die Transaktionen werden automatisch eingelesen.").
2. Auf „Datei wählen" klicken (oder per Drag & Drop in die Dropzone ziehen) und den lokal
   gesicherten `PostFinance_Kontoauszug_Lara_2026-09.pdf` auswählen.
3. Während des Parsens steht kurz „Kontoauszug wird gelesen …", danach läuft ein Fortschrittsbalken
   mit dem Text „X von Y Transaktionen kategorisiert".
4. Nach Abschluss erscheint eine Erfolgsmeldung („N Transaktionen erkannt.") und direkt darunter
   die Liste der importierten Buchungen mit Datum, Buchungstext/Gegenpartei, Betrag und je einer
   vorausgewählten Kategorie.

**Sagst du:**

> „Lara bekommt von ihrer Bank — PostFinance — monatlich einen Kontoauszug als PDF. Genau den lädt
> sie hier hoch, mehr nicht." *(Datei wählen)* „Das läuft jetzt asynchron im Hintergrund — bewusst
> so gebaut: ein voller Kontoauszug mit über hundert Buchungen lief früher synchron ins
> 30-Sekunden-Timeout und der komplette Import ging verloren. Heute parst das Backend in rund zwei
> Sekunden, und die Kategorisierung läuft danach im Hintergrund weiter, mit Fortschrittsanzeige."
> *(auf den Balken zeigen, warten)* „Fertig — und jede Buchung hat schon eine Kategorie."

---

### T32 — Kategorisierung + 1 Korrektur (2:30–4:00)

**Tust du:**
1. Bleibst auf derselben Seite (`/import`), scrollst zur importierten Liste.
2. Zeigst auf ein, zwei Zeilen mit bereits sinnvoll gesetzter Kategorie (z. B. „Lebensmittel" bei
   Migros/Coop).
3. Suchst gezielt eine Zeile mit Kategorie „Sonstiges" (🗂️).
4. Öffnest das Dropdown dieser Zeile und wählst die passende Kategorie (z. B. „Restaurant" oder
   „Freizeit", je nach Händler).

**Sagst du:**

> „Die Kategorien kommen aus zwei Quellen: bekannte Händler wie Migros oder die SBB holt eine
> lokale Lookup-Tabelle sofort, ohne API-Call. Alles Unbekannte hat im Hintergrund Claude
> kategorisiert, gebündelt in einem einzigen Request für den ganzen Import." *(auf Sonstiges-Zeile
> zeigen)* „Diese hier ist bei 'Sonstiges' gelandet — der Fallback, wenn auch Claude sich nicht
> sicher war. Ich korrigiere das jetzt von Hand" *(Dropdown öffnen, Kategorie wählen)* „— und genau
> das merkt sich BudgetBuddy für Lara persönlich. Taucht derselbe Händler beim nächsten Import
> wieder auf, ist er sofort richtig kategorisiert, ganz ohne erneuten Claude-Call."

**Falls keine „Sonstiges"-Zeile dabei ist** (Kategorisierung diesmal vollständig): keine Zeit mit
Suchen verlieren — einfach eine beliebige Zeile korrigieren und sagen: „Das funktioniert
grundsätzlich für jede Zeile, nicht nur für 'Sonstiges' — falls Lara anderer Meinung ist als die
App, korrigiert sie einfach."

---

### T33 — Safe-to-Spend (4:00–5:00)

**Tust du:**
1. Über die Navigation zurück auf „Übersicht" (`/dashboard`).
2. Zeigst auf die Safe-to-Spend-Card — der Betrag hat sich gegenüber T30 verändert, weil der
   September-Import jetzt eingerechnet ist.
3. Optional, falls Zeit bleibt: kurzer Blick auf die Drei-Monats-Tabelle darunter
   („Drei Monate bis …").

**Sagst du:**

> „Und jetzt schliesst sich der Kreis: Einkommen und Fixkosten kennt die App aus dem Onboarding,
> die tatsächlichen Ausgaben kennt sie jetzt aus dem Import von eben — daraus berechnet sie Laras
> Safe-to-Spend: [Betrag laut. Bildschirm] Franken, das ist, was ihr diese Woche noch bleibt. Ohne
> dass Lara eine einzige Zahl selbst eingetippt hat, ausser ihrem Einkommen und ihren Fixkosten
> einmalig beim Onboarding. Genau das ist unser Kernversprechen: ein Safe-to-Spend-Betrag, dem man
> vertraut, weil er aus echten Kontobewegungen kommt — nicht aus einer Excel-Tabelle, die man nie
> aktualisiert."

Falls die Woche die letzte des Monats ist, zeigt die Card zusätzlich „Letzte Woche des Monats" —
kann man kommentarlos stehen lassen oder kurz erwähnen.

---

## 4. Risiken & Fallback

| Risiko | Gegenmassnahme |
| --- | --- |
| Neon Cold Start (Latenz beim ersten Request) | App 5–10 Min. vorher aufwärmen (Abschnitt 2) |
| September-Import hängt/dauert zu lange | Screenshot-Fallback zeigen, dabei ehrlich sagen „normalerweise dauert das X Sekunden" |
| `ANTHROPIC_API_KEY` fällt aus / Claude-Call schlägt fehl | Fällt automatisch auf „Sonstiges" zurück — Import bricht nicht ab; T32 einfach mit einer garantiert vorhandenen Sonstiges-Zeile weitermachen |
| Login schlägt fehl (falsches Passwort) | Sofort auf zweiten vorbereiteten Tab wechseln, der bereits eingeloggt ist (Session offenhalten, nicht ausloggen vor der Demo) |
| September-PDF wurde doch schon importiert (409) | Modal „Kontoauszug bereits importiert" erscheint — „Trotzdem importieren" klicken, funktioniert genauso, nur weniger überzeugend als Erstimport |

---

## 5. Nach der Testpräsentation: `lara@demo.bb` zurücksetzen

T30–T33 enthält **kein** „Konto löschen" (das kommt erst B34 in der Abschlusspräsentation). Ohne
Reset schlägt die **Live-Registrierung** in der Abschlusspräsentation nächste Woche mit `409` fehl,
weil `lara@demo.bb` dann schon existiert.

**Wichtig:** Die Reset-Anleitung in [README.md → Zurücksetzen](README.md#zurücksetzen) zielt mit
`docker exec ... budgetbuddy-postgres` auf die **lokale** Docker-Postgres — das funktioniert nicht
gegen die deployte Instanz. Gegen Neon (Render-Instanz) stattdessen dieselben Statements direkt per
`psql` mit dem Neon-Connection-String ausführen:

```bash
psql "$NEON_CONNECTION_STRING" -v ON_ERROR_STOP=1 <<'SQL'
BEGIN;
CREATE TEMP TABLE demo_users AS
  SELECT id FROM users
  WHERE email IN ('lara@demo.bb', 'marc@demo.bb');
DELETE FROM transactions       WHERE user_id IN (SELECT id FROM demo_users);
DELETE FROM fixed_costs        WHERE user_id IN (SELECT id FROM demo_users);
DELETE FROM import_jobs        WHERE user_id IN (SELECT id FROM demo_users);
DELETE FROM notifications      WHERE user_id IN (SELECT id FROM demo_users);
DELETE FROM recurring_expenses WHERE user_id IN (SELECT id FROM demo_users);
DELETE FROM users              WHERE id IN (SELECT id FROM demo_users);
COMMIT;
SQL
```

`$NEON_CONNECTION_STRING` steht **nicht im Repo** (CLAUDE.md: keine Secrets im Git) — holt ihr aus
dem Render-Dashboard, dort wo `SPRING_DATASOURCE_URL`/`_USERNAME`/`_PASSWORD` gesetzt sind, oder
direkt aus dem Neon-Dashboard. Alternative ohne Kommandozeile: dieselben `DELETE`-Statements im
Neon-Web-SQL-Editor ausführen.

Danach den lokal gesicherten September-Auszug wieder nach `docs/demo/statements/` zurücklegen,
damit der Ordner für den nächsten Durchlauf bzw. die Abschlusspräsentation wieder vollständig ist.

---

## 6. Cheat-Sheet — exakte UI-Texte (Stand `origin/main`)

| Screen | Text |
| --- | --- |
| Login | H1 „Login", Felder „E-Mail" / „Passwort", Button „Einloggen" (während Submit „Wird eingeloggt…") |
| Nav | „Übersicht" (`/dashboard`), „Transaktionen" (`/categories`), „Import" (`/import`), „Ausgaben" (`/ausgaben`) |
| Import | H1 „Import", Dropzone „PDF-Datei hierhin ziehen oder" / Button „Datei wählen", Hinweis „Nur .pdf · maximal 10 MB" |
| Import — während Parsen | „Kontoauszug wird gelesen …" |
| Import — Fortschritt | „X von Y Transaktionen kategorisiert" |
| Import — Erfolg | „N Transaktionen erkannt." |
| Import — Duplikat (409) | Modal „Kontoauszug bereits importiert", Buttons „Trotzdem importieren" / „Abbrechen" |
| Dashboard — Safe-to-Spend | Card-Titel „Safe-to-Spend", darunter „noch N Woche(n) im Monat", ggf. „Letzte Woche des Monats" |
| Dashboard — Budget überzogen | „Achtung: Dein Budget für diese Woche ist überzogen" |
| Dashboard — kein Einkommen (bei Lara nicht relevant, sie hat eins) | „Kein Einkommen erfasst" / „Bitte erfasse dein Monatseinkommen in den Einstellungen" |
| Dashboard — Drei-Monats-Tabelle | Titel „Drei Monate bis {Monat}", Spalten „Monat / Einnahmen / Ausgaben / Differenz" |
| Dashboard — Abo-Teaser | Card „Abos", Text „N Abo(s) erkannt" bzw. „Keine Abos erkannt", CTA „Zu Ausgaben →" |
| Ausgaben (`/ausgaben`) | H1 „Ausgaben", Card „Monatliche fixe Ausgaben" (Fixkosten + erkannte Abos summiert), Zwischenüberschrift „Erfasste Fixkosten", Abschnitt „Erkannte Abos" darunter |
| Kategorie „Sonstiges" | Icon 🗂️, Slug `sonstiges` — Fallback, wenn auch Claude unsicher war |

---

**Fragen/Unsicherheiten, die vor morgen noch geklärt gehören:**

- [ ] 4–5 Minuten Demo-Budget im Team bestätigt? (offen laut `abschlusspraesentation.md`)
