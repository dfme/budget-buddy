# Demo-Daten für die Präsentation

Zwei vorzeigbare Accounts für die beiden Personas aus [README.md](../../README.md) — mit
importierter Transaktionshistorie über mehrere Monate, damit Safe-to-Spend, Kategorisierung und
Monatsverlauf live gezeigt werden können statt an einem leeren Dashboard (INFRA-38, #258).

| Persona | Bank-Format | E-Mail | Auszüge |
| ------- | ----------- | ------ | ------- |
| Lara Bieri (22, Studentin, Bern) | PostFinance | `lara@demo.bb` | 6 |
| Marc Steiner (25, Junior-Verkäufer, Zürich) | Raiffeisen | `marc@demo.bb` | 6 |

Die Passwörter stehen **nicht** hier und nirgends sonst im Repo — siehe
[Login-Daten](#login-daten).

## Loslegen

Die Auszüge liegen fertig unter [`statements/`](statements/) — es braucht nur den Import.
Voraussetzung: Backend und Datenbank laufen lokal (siehe
[README.md → Lokal starten](../../README.md#lokal-starten-dev)).

```bash
export DEMO_LARA_PASSWORD='<im Team vereinbart>'
export DEMO_MARC_PASSWORD='<im Team vereinbart>'
backend/tools/seed_demo_accounts.sh
```

Die beiden Variablen sind **Pflicht** — ohne sie bricht das Skript ab, bevor es etwas anlegt.
Warum, steht unter [Login-Daten](#login-daten).

Das Seed-Skript ist **idempotent**: eine bestehende E-Mail führt zum Login statt zum Abbruch,
vorhandene Fixkosten werden nicht doppelt angelegt, und ein bereits importiertes PDF wird über den
Duplikatschutz (409) übersprungen. Ein zweiter Lauf ändert also nichts.

`generate_demo_statements.py` wird dafür **nicht** gebraucht — die committeten PDFs sind bereits
seine Ausgabe. Wann es doch nötig ist, steht im nächsten Abschnitt.

## Vor der Präsentation neu generieren

`SafeToSpendService` rechnet ausschliesslich für den **laufenden** Monat; ein vergangener liefert
den `CLOSED`-Marker
([SafeToSpendService.java:56](../../backend/src/main/java/com/budgetbuddy/budget/SafeToSpendService.java#L56)).
Die Auszüge in [`statements/`](statements/) sind ein Snapshot über **April bis September 2026** —
der letzte Auszug jeder Persona reicht bis zum 11.09.2026, die fünf davor umfassen je einen vollen
Monat.

**Fällt die Präsentation in einen späteren Monat, zeigt das Dashboard für Safe-to-Spend nichts
mehr.** Nur dann neu generieren — und danach gegen eine frische Datenbank seeden, weil die alten
Auszüge sonst als Duplikate liegen bleiben (siehe [Zurücksetzen](#zurücksetzen)):

```bash
pip install reportlab
python3 backend/tools/generate_demo_statements.py     # Default: laufender Monat
backend/tools/seed_demo_accounts.sh
```

**Nicht zur Sicherheit mitlaufen lassen.** reportlab stempelt ein Erzeugungsdatum in jede Datei,
und der Lauf schreibt deshalb alle zwölf PDFs byteweise neu, auch wenn sich am Inhalt nichts ändert
(nachgemessen: identisch bis auf `/CreationDate` und `/ID`). Im selben Monat erzeugt das nur zwölf
unlesbare Binär-Diffs im Arbeitsbaum. Versehentlich passiert:

```bash
git checkout -- docs/demo/statements/
```

Der Generator nimmt `--end-month YYYY-MM`, `--as-of YYYY-MM-DD` und `--months N` (Default 6,
Minimum 3). Die Saldoketten laufen über alle Auszüge einer Persona durch, der Schlusssaldo eines
Monats ist der Anfangssaldo des nächsten.

## Kategorisierung: ohne API-Key nur die halbe Geschichte

Ohne `ANTHROPIC_API_KEY` fällt Stufe 2 des Hybrid-Ansatzes (ADR-6) aus, und alles, was die
Lookup-Tabelle nicht kennt, wird `Sonstiges` (BE-CAT-02). Gemessen am Snapshot:

| | mit Lookup kategorisiert | `Sonstiges` |
| --- | --- | --- |
| Lara (130 Buchungen) | 65 (50%) | 65 (50%) |
| Marc (324 Buchungen) | 154 (48%) | 170 (52%) |

Betroffen sind unter anderem Miete, Restaurants, Coiffeur und Bargeldbezüge — also genau die
Posten, an denen die Kategorienansicht interessant wird. **Für die Präsentation deshalb den Key
setzen**, bevor geseedet wird; das Skript warnt beim Start, wenn er fehlt.

Die Händler sind bewusst gemischt: ein Teil trifft die Seeds aus
[`V04__create_category_lookup_table.sql`](../../backend/src/main/resources/db/migration/V04__create_category_lookup_table.sql)
(Migros, Coop, SBB, CSS, Swisscom, Netflix, Zalando, digitec), ein Teil nicht. Ohne unbekannte
Händler hätte die Demo weder für die Claude-Stufe noch für die manuelle Korrektur (Stufe 3) etwas
zu zeigen.

## Login-Daten

Die Passwörter kommen ausschliesslich aus `DEMO_LARA_PASSWORD` und `DEMO_MARC_PASSWORD`. Fehlt
eines, bricht das Skript ab, bevor es den ersten Account anlegt.

**Das Team vereinbart ein Passwort je Persona und alle setzen dasselbe.** Würde das Skript eines
würfeln, hätte jeder Rechner ein anderes — der Demo-Account gehörte dann faktisch dem Laptop, auf
dem er entstanden ist, und wäre am Präsentationstag von keinem anderen Gerät aus erreichbar. Genau
deshalb gibt es hier keinen Zufallsgenerator.

Die Werte gehören in den Passwortmanager, **nicht ins Repo** — #258 verlangt die Login-Daten
ausdrücklich ausserhalb, und CLAUDE.md sagt «Keine Secrets im Git». Wer sie lokal nicht jedes Mal
tippen will, legt eine eigene `.env.demo` an (greift über `.env.*` in
[`.gitignore`](../../.gitignore)) und lädt sie selbst:

```bash
set -a; . ./.env.demo; set +a
backend/tools/seed_demo_accounts.sh
```

Das Skript schreibt diese Datei nicht und liest sie auch nicht von sich aus — sie ist reine
Bequemlichkeit auf deinem Rechner.

Geprüft wird zusätzlich gegen die Grenzen aus `RegisterRequest`: mindestens 8 Zeichen, höchstens
72 Bytes (die bcrypt-Grenze, BE-AUTH-10). Ein zu kurzes Passwort scheitert damit am Skript statt
an einem nackten 400 der API.

Passt das gesetzte Passwort nicht zu einem bereits angelegten Account, meldet das Skript das beim
Login und nennt die betroffene Variable. Dann entweder das ursprüngliche Passwort setzen oder die
Accounts [zurücksetzen](#zurücksetzen).

## Zurücksetzen

Es gibt **keinen Seed-Mechanismus in der App** — kein `data.sql`, keinen Demo-Endpoint, und das
soll so bleiben: ein Endpoint, der Accounts anlegt, wäre in Produktion ein offener Hebel. Die
Accounts entstehen ausschliesslich über die öffentliche API.

Entsprechend gibt es auch keinen Reset-Knopf. Um die beiden Demo-Accounts loszuwerden, ohne die
übrige lokale Datenbank zu verlieren:

```bash
docker exec -i budgetbuddy-postgres psql -U budgetbuddy -d budgetbuddy -v ON_ERROR_STOP=1 <<'SQL'
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

`docker exec` braucht hier das `-i`: ohne das wird das Heredoc nicht an `psql` durchgereicht, und
der Befehl läuft wirkungslos durch.

`docker compose down -v` wäre der grobe Weg — er verwirft die **ganze** lokale Datenbank, also
auch alle anderen Entwicklungs-Accounts.

## Was in den Auszügen steht

Alle Daten sind frei erfunden: Kontoinhaber, IBANs, Gegenparteien und Beträge. Es sind keine
anonymisierten Realdaten.

| | Lara — PostFinance | Marc — Raiffeisen |
| --- | --- | --- |
| Monatseinkommen (erfasst) | 1'900.— | 4'200.— |
| Fixkosten | WG-Zimmer 650, CSS 180, Salt 25 (monatlich), Semestergebühr 1'500 (jährlich) | Miete 1'450, Helsana 380, Swisscom 79, Fitnesspark 89 (monatlich), Hausrat 240 (jährlich) |
| Buchungen je voller Monat | 24 | 60 |
| Buchungen im Snapshot (6 Monate) | 130 | 324 |
| Story | Barjob-Lohn schwankt zwischen ~620 und ~980, Elternbeitrag fix — die Unregelmässigkeit, die Lara laut README den Überblick kostet | Lohn stabil, aber das «Kleinvieh» aus README.md frisst ihn fast auf: Take-away, Kiosk, Feierabendbier, Onlinekäufe |

Die Auszüge bilden das reale Satzbild der jeweiligen Bank nach — beim PostFinance-Layout trägt die
Buchungszeile die **Zahlungsart**, der Händler steht in den Detailzeilen darunter. Das ist kein
Detail: die sprechende Zeile muss erst durch `DETAIL_NOISE` und `MAX_DETAIL_LINES` kommen, damit
Lookup und Prompt sie überhaupt sehen. Deshalb importiert
[`generate_demo_statements.py`](../../backend/tools/generate_demo_statements.py) die
Layout-Bausteine aus dem Fixture-Generator, statt sie nachzubauen.

Die Parser-Testfixtures unter `backend/src/test/resources/pdf/` sind davon unberührt —
`generate_pdf_fixtures.py` wird nicht verändert, und der Demo-Generator bricht ab, falls sein
Ausgabeverzeichnis je auf das Fixture-Verzeichnis zeigen sollte.
