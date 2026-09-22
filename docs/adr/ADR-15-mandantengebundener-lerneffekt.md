# ADR-15: Mandantengebundener Lerneffekt der Hybrid-Kategorisierung

**Status:** Accepted
**Date:** 2026-09-18
**Ergänzt:** [ADR-6](ADR-6-hybrid-categorization.md) (Hybrid-Kategorisierung, Schritt 4)

## Context

Der Lerneffekt aus ADR-6 (Schritt 4) schrieb bis BE-CAT-12 in dieselbe Tabelle wie die Seeds:
`category_lookup` (Flyway V04), zwei Spalten, Primärschlüssel ist der rohe Buchungstext. Die
Tabelle kennt keinen User. Daraus folgten zwei Dinge, die zusammen das Issue
[#319](https://github.com/dfme/budget-buddy/issues/319) sind:

- **Der Text eines Nutzers wirkte auf alle.** Die Lookup-Stufe matcht global. Eine Korrektur von
  Lara kategorisierte Marcs Auszug — und ihre Korrektur von `MIGROS` überschrieb den Seed für
  jeden.
- **Die Zeile überlebte die Kontolöschung.** `UserService.deleteUser` räumt fünf Tabellen über
  Cleanup-Ports; `category_lookup` konnte sie nicht räumen, weil es nichts gab, wonach sie hätte
  filtern können. US-02 («alle personenbezogenen Daten») war damit nicht abhakbar, und der
  Swagger-Text von `DELETE /api/users/me` benannte die Lücke ausdrücklich.

Solange nur manuelle Korrekturen lernten (BE-CAT-04), war der Umfang überschaubar: eine bewusste
Handlung pro Zeile. Seit BE-CAT-11 ([#314](https://github.com/dfme/budget-buddy/issues/314))
schreibt zusätzlich der `HybridCategorizationService` jeden Händlertext, den die Claude-Stufe
erfolgreich eingestuft hat — ungefragt, roh und bei jedem importierten Auszug. Die Lücke wurde
dadurch breiter, nicht neu; dieser Zielkonflikt war bei BE-CAT-11 bekannt und bewusst hierher
verschoben. Das Threat-Model aus Modul 8 hatte die Fundstelle bereits als **F1** («trennen»)
markiert.

Die Frage war keine technische, sondern eine Produktfrage: **Soll der Lerneffekt global bleiben
oder pro Nutzer gelten?**

## Decision

**Zwischenform: Seeds global, Gelerntes pro Nutzer.**

1. **`category_lookup` (V04, ergänzt durch V15) bleibt die globale, kuratierte Basis.** Sie
   enthält die Seeds und wird vom Lerneffekt nicht mehr geschrieben. Änderungen daran sind
   Migrationen — also Code-Review, nicht Nutzereingabe.
2. **Alles Gelernte liegt in `user_category_lookup` (V12)** mit `user_id` (FK auf `users`, ohne
   `ON DELETE`, wie alle Nutzertabellen seit DB-07) und `UNIQUE (user_id, empfaenger_pattern)`.
   Beide Quellen des Lerneffekts — manuelle Korrektur (BE-CAT-04) und Claude-Treffer (BE-CAT-11)
   — schreiben ausschliesslich hierher, per `INSERT … ON CONFLICT DO UPDATE`. Die Upsert-Semantik
   («der jüngste Aufruf gewinnt») bleibt damit dieselbe wie bisher, nur pro Nutzer.
3. **Matching über beide Pools, eine Regel:** `LookupTableService` fragt die eigenen und die
   globalen Patterns ab; das längste gewinnt, bei gleicher Länge das eigene. Ein längeres
   gelerntes Pattern (`MIGROS BANK ZINS`) schlägt den kürzeren Seed (`MIGROS`), wie es innerhalb
   eines Pools schon immer galt; ein gleich langes eigenes (`MIGROS` → Sonstiges) gilt nur für
   diesen Nutzer. Fremde Lerneinträge sieht die Query nie — die Einschränkung auf `user_id` steht
   im Repository, nicht im Aufrufer.
4. **Die Ports tragen die User-ID.** `CategorizationPort.categorize(userId, text)` /
   `categorizeAll(userId, texts)` und `CategoryLearningPort.learn(userId, pattern, category)`.
   `ClaudeCategorizationService` bleibt hinter dem Port (CLAUDE.md-Konvention), ignoriert die ID
   und behält seine textbasierten Methoden als eigentliche Implementierung — für die Einstufung
   ist ohne Belang, wer importiert hat.
5. **Kontolöschung:** `CategoryLookupCleanupPort` hängt als fünfter Port in
   `UserService.deleteUser`, in derselben Bauart wie die übrigen. `UserDeletionIntegrationTest`
   zählt danach 0 Zeilen für den gelöschten Nutzer und 1 für einen zweiten — geräumt wird
   mandantenweise, nicht die Tabelle.
6. **Der gespeicherte Schlüssel bleibt roh — bewusst, nicht aus Versehen.** Er wird nicht durch
   `PromptSanitizer` (BE-CAT-06) geführt, obwohl er dieselbe Datenklasse enthält wie der
   versendete Prompt. Der Grund ist die Matching-Semantik: `findMatching` prüft per
   `LIKE '%pattern%'`, ob das gelernte Pattern als Substring im rohen Text des nächsten Imports
   vorkommt. Eine maskierte Fassung (`KAUF/DIENSTLEISTUNG <NAME> KARTE <KARTE>`) ist in keinem
   rohen Buchungstext enthalten — der Lerneffekt liefe damit für genau die Zeilen ins Leere, die
   er entschärfen sollte. Umgekehrt den Import-Text zu maskieren, bevor er gegen die Tabelle
   läuft, wäre möglich, kostete aber die Seeds und alle bisher gelernten Zeilen ihre Treffer und
   liesse die Frage «was heisst das für bereits gelernte Zeilen» mit «neu lernen» beantworten.
   Der Schutz der Zeilen kommt deshalb nicht aus der Maskierung, sondern aus Punkt 2 und 5:
   Mandantenbindung und Löschung mit dem Konto. Die verbleibende Exposition steht unten unter
   Negative.

**Altdaten.** Die vor V12 gelernten Zeilen in `category_lookup` lassen sich keinem Nutzer mehr
zuordnen. Sie bleiben auf Teamentscheid als globale Patterns stehen; V12 räumt `category_lookup`
bewusst nicht. Das ist eine bekannte Ausnahme von Punkt 1: Diese Zeilen sind rohe Buchungstexte
ohne Eigentümer und überleben jede Kontolöschung. Auf der Produktionsinstanz liegen zum
Zeitpunkt dieses Entscheids nur Demo- und Kursdaten (INFRA-38). Wer sie loswerden will, löscht
in einer späteren Migration alles, was nicht zu den kuratierten Seeds gehört — seit BE-CAT-17
sind das die 18 Zeilen aus V04 plus die sieben aus V15, nicht mehr V04 allein.

## Consequences

### Positive

- **US-02 ist abhakbar:** Alle sechs Nutzertabellen hängen an der Kontolöschung, jede mit einem
  Test, der danach zählt statt einen Cascade anzunehmen.
- **Mandantengrenze dort, wo die Query steht:** `UserCategoryLookupRepository` hat keine Methode
  ohne `user_id`. Ein Zugriff, der sie umginge, wäre eine sichtbare Änderung an diesem Interface.
- **Eigene Korrekturen überschreiben keine Seeds mehr.** Vor BE-CAT-12 änderte `learn("migros",
  Sonstiges)` die Seed-Zeile für alle Nutzer.
- **Die Datenbank wird laut, wenn ein Löschpfad die Tabelle vergisst** — der FK ohne Cascade
  ist die Regressionsbremse für genau die Lücke, die #319 beschreibt.

### Negative

- **Der Lerneffekt ist nicht mehr geteilt.** Ein Händler, den Lara korrigiert hat, kostet bei
  Marc trotzdem einen Claude-Call. Bei ~60–80 % Trefferquote aus den Seeds
  (`PdfLookupCoverageIntegrationTest`) ist das ein kleiner Preis; wer ihn nicht zahlen will,
  kuratiert häufige Händler in die Seeds — per Migration, mit Review.
- **Zwei Queries pro Lookup** statt einer. Beide sind Substring-Scans auf kleinen Tabellen; die
  Laufzeit des Imports hängt an den Claude-Calls (ADR-14), nicht hier.
- **Altdaten bleiben global** (siehe oben) — eine dokumentierte, nicht eine stille Ausnahme.
- **Rohe Buchungstexte liegen unmaskiert in `user_category_lookup`** (Punkt 6), darunter Namen
  natürlicher Personen aus Überweisungen (`MUSTER, ANNA`, `SACKGELD LEA` aus dem Korpus des
  `PromptSanitizerTest`). Auf dem **Prompt-Pfad** ist diese Exposition seit BE-CAT-08
  ([#233](https://github.com/dfme/budget-buddy/issues/233)) geschlossen — `PromptSanitizer`
  maskiert den Namen samt seinem Echo in der Zweckzeile. Für die **Ablage** gilt das
  ausdrücklich nicht: gelernt wird der unmaskierte Text, weil `findMatching` ihn als Substring
  des realen Buchungstexts braucht. Sie ist kleiner als vor BE-CAT-12 (kein anderer Nutzer sieht
  die Zeile, die Kontolöschung räumt sie), aber nicht null: Wer die Datenbank liest, liest diese
  Texte. Das gilt für `transactions` genauso — die Lookup-Tabelle vergrössert die Angriffsfläche
  nicht um eine neue Datenklasse, sondern um eine zweite Kopie derselben.

## Alternatives

### Global belassen, nur die maskierte Fassung lernen

**Rejected.** `PromptSanitizer.sanitize(buchungstext)` vor `learn(...)` hätte die Daten in der
Tabelle entschärft, aber nicht das Mandantenproblem gelöst: Die Zeile bliebe global, würde von
allen gematcht und überstünde die Löschung. US-02 wäre weiter nicht erfüllt.

### Pro Nutzer, auch Seeds

**Rejected.** `user_id` direkt in `category_lookup`, Seeds bei der Registrierung pro Nutzer
kopiert. Grösserer Umbau (Primärschlüssel-Änderung, Seed-Kopie im Auth-Modul, 18 Zeilen × Nutzer)
ohne fachlichen Gewinn: Ein Seed ist kein Nutzerdatum, er muss nicht mit dem Konto gelöscht
werden.

### `user_id` nullable in derselben Tabelle

**Rejected.** `NULL` = global, PK auf `(user_id, empfaenger_pattern)` — der Vorschlag aus dem
Threat-Model. Postgres erlaubt keine `NULL`-Spalte im Primärschlüssel; es bräuchte einen
Surrogat-Key plus zwei partielle Unique-Indizes. Und die E2E-Suite bewahrt `category_lookup` über
`TRUNCATE … CASCADE` hinweg als Schema-Bestandteil — ein FK auf `users` in dieser Tabelle würde
sie mit den Seeds mit-truncaten. Zwei Tabellen für zwei Lebenszyklen (Schema vs. Nutzerdaten)
sind die einfachere Antwort.

### `ON DELETE CASCADE` statt Cleanup-Port

**Rejected**, aus demselben Grund wie in DB-07 für alle anderen Tabellen: Die Löschung soll eine
sichtbare, einzeln testbare Operation im Code sein, keine stille DB-Nebenwirkung.

## Related Decisions

- **ADR-6:** Hybrid-Kategorisierung — Schritt 4 (Feedback Loop) schreibt seit diesem ADR pro Nutzer
- **ADR-12:** PostgreSQL bei Neon — `ON CONFLICT` ist Postgres-Syntax, seit ADR-12 die einzige
- **ADR-14:** Batch-Kategorisierung — `categorizeAll` trägt jetzt die User-ID durch die Kette
