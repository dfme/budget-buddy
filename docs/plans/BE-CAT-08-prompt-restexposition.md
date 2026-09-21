# [BE-CAT-08] Restexposition im Claude-Prompt: Vorname in Zweckzeilen und Händler-Telefonnummer

- **Issue:** [#233](https://github.com/dfme/budget-buddy/issues/233)
- **Task-ID:** `BE-CAT-08`
- **Branch:** `feature/BE-CAT-08-prompt-restexposition`
- **Story:** US-05 — Transaktionen kategorisieren
- **Sprint:** Sprint 7
- **Bestätigt am:** 2026-09-21

## Ausgangslage

BE-CAT-06 (#134) hat mit `PromptSanitizer` die Maskierung vor dem Claude-Call eingeführt. Zwei
Restexpositionen blieben bewusst offen und standen seither als Grenzen im Klassen-Javadoc:

1. Ein Vorname in einer frei getippten Zweckzeile — `LASTSCHRIFT MUSTER, LEA SACKGELD LEA` wird
   zu `LASTSCHRIFT <NAME> SACKGELD LEA`, das nachgestellte `LEA` geht mit hinaus.
2. Die Telefonnummer eines Händlers — `DIGITEC GALAXUS AG 044 913 2323`. Die
   Ziffernlauf-Regel greift nicht, die Nummer ist durch Leerzeichen getrennt.

## Entscheide

### 1. Punkt 1 wird angegangen — per Echo-Maskierung, nicht per Vornamensliste

Das Issue verwirft eine Heuristik über eine Vornamensliste, und zu Recht: sie gefährdete die
Trefferquoten-AC von BE-CAT-06, gegen die `PromptSanitizerTest.RealerKorpus` absichert.

Der hier gewählte Weg braucht keine Liste. `PERSON_NAME` hat den Namen im selben Text bereits
**strukturell** erkannt — die Form `NACHNAME, VORNAME` in Versalien ist der Beweis, nicht eine
Vermutung. Die Tokens dieses Treffers werden anschliessend in ihren weiteren alleinstehenden
Vorkommen nachmaskiert.

Die Regel ist damit **selbst-bedingt**: ohne einen `PERSON_NAME`-Treffer im selben Text feuert
sie nie. Von den vierzehn `RealerKorpus`-Zeilen, die unverändert durchgehen müssen, hat keine
einen solchen Treffer — `Migros M Bern, Bern CH Lebensmittel` scheitert an `\p{Lu}{2,}`, weil
`Bern` gemischt geschrieben ist. Die Trefferquoten-AC ist damit strukturell unberührt, nicht
bloss empirisch.

Der geerbte Rand: `PERSON_NAME` hat eine dokumentierte Kante (`COOP, BERN` — ein reiner
Versalien-Händler mit Komma fiele mit heraus). Die Echo-Maskierung verbreitert diese Kante,
weil sie dann auch nachfolgende `COOP`/`BERN` nähme. Sie erfindet aber keine neue: ohne den
Fehltreffer der Grundregel gibt es keinen Echo-Fehltreffer. Im gesamten Fixture-Korpus kommt
die Form nicht vor.

### 2. `PHONE` läuft nach `OPAQUE_REFERENCE`, nicht davor

Eine kompakt gedruckte Schweizer Nummer (`0449132323`, zehn Ziffern) erfüllt auch
`OPAQUE_REFERENCE` und wird heute schon als `<REF>` maskiert. Liefe `PHONE` davor, würde jede
kompakte zehnstellige Kontonummer zu `<TEL>` umbenannt — maskiert wäre sie so oder so, nur
falsch benannt. Nachgestellt deckt `PHONE` genau die Lücke ab, die keine bestehende Regel
fängt: die durch Leerzeichen getrennte Nummer.

Dasselbe Argument, das im Javadoc der Klasse schon für `IBAN` vor `OPAQUE_REFERENCE` steht —
richtig maskiert, aber falsch benannt ist auch ein Mangel.

### 3. Lookahead `(?![0-9A-Z])`, nicht `(?![0-9])`

Die Lehre steht bereits im Javadoc von `LONG_DIGIT_RUN`: mit dem naheliegenden `(?!\d)`
zerschnitte die Regel `0441234567AB` in `<TEL>AB`. Mit `(?![0-9A-Z])` greift sie dort gar
nicht, und `OPAQUE_REFERENCE` nimmt den ganzen Token.

### 4. Kein Punkt als Trennzeichen

Erlaubt sind Leerzeichen und `/`. Mit dem Punkt könnte `01.02.2026 45` als zehnstellige Nummer
durchgehen. Ohne ihn sind Datumsangaben **strukturell** ausgeschlossen statt nur per Lookbehind
abgefangen — die Betragsregel zeigt, wie teuer ein Lookbehind zu lesen ist, der genau das
nachträglich reparieren muss.

### 5. Der Anker ist die Schweizer Nummernlänge

National `0` + neun Ziffern, international `+41`/`0041` + neun Ziffern. Das ist eng genug, dass
`RECHNUNG 2024 2025` und `COOP-1234` nicht in die Nähe kommen: die führende `0` ist die
eigentliche Bedingung, nicht die Länge allein.

## Betroffene Dateien

### Ändern

| Datei | Änderung |
| ----- | -------- |
| `backend/src/main/java/com/budgetbuddy/categorization/PromptSanitizer.java` | `PHONE`-Pattern, `maskPersonNames(...)` statt `replaceAll`, Grenzen-Absatz im Klassen-Javadoc, Korrektur am Javadoc von `LONG_DIGIT_RUN` |
| `backend/src/test/java/com/budgetbuddy/categorization/PromptSanitizerTest.java` | beide `_bekannteGrenze`-Tests umgestellt statt gelöscht, neue Positiv- und Gegenprobe-Fälle |
| `backend/src/test/java/com/budgetbuddy/categorization/ClaudeCategorizationServiceTest.java` | `promptContainsNumberedTransactionTexts` wird zum End-to-End-Nachweis für AC 3 |
| `backend/src/main/java/com/budgetbuddy/categorization/ClaudeCategorizationService.java` | Aufzählung der maskierten Elemente im Klassen-Javadoc |
| `CLAUDE.md` | Absatz «Maskierung vor dem Versand (BE-CAT-06)» — die zwei Restexpositionen |
| `docs/adr/ADR-15-mandantengebundener-lerneffekt.md` | Querverweis, der durch diesen Diff falsch wird |
| `docs/adr/ADR-6-hybrid-categorization.md` | **Scope-Erweiterung**, siehe unten |
| `docs/plans/README.md` | Indexzeile |

### Neu

| Datei | Zweck |
| ----- | ----- |
| `docs/plans/BE-CAT-08-prompt-restexposition.md` | dieses Dokument |

### Bewusste Scope-Erweiterung: ADR-6

`docs/adr/ADR-6-hybrid-categorization.md:80` behauptet, der rohe Transaktionstext gehe «ohne
Pseudonymisierung» an die Anthropic API — seit BE-CAT-06 (#134) falsch, also vorbestehend und
nicht von diesem Diff verursacht. Die Stelle benutzt ausgerechnet das Telefonnummer-Beispiel
dieses Tasks. Entscheid im Team: mitbeheben und hier deklarieren, statt ein Folge-Issue für
einen Absatz aufzumachen, den dieser Task ohnehin anfasst.

### Bewusst nicht nachgezogen

`docs/presentations/*`, `docs/prompts/08_01_*` und `docs/plans/BE-CAT-06-prompt-sanitizer.md`
nennen die zwei Restexpositionen ebenfalls. Es sind datierte Kurs- und Planartefakte; sie
halten wie jeder Plan die **historische** Wahrheit zum Zeitpunkt ihrer Entstehung. Ein
nachträglicher Eingriff machte sie als Beleg wertlos.

## Implementierungsschritte

1. `PHONE`-Pattern in `PromptSanitizer` aufnehmen:
   `(?<![0-9A-Z+])(?:\+41|0041|0)(?:[ /]?\d){9}(?![0-9A-Z])` → `<TEL>`, eingehängt nach
   `OPAQUE_REFERENCE`. Javadoc mit der Begründung zu Reihenfolge, Lookahead und Trennzeichen.
2. `maskPersonNames(String)` nach dem Muster des bestehenden `maskCardGroups`: über
   `PERSON_NAME` iterieren, jeden Treffer durch `<NAME>` ersetzen und dabei die Namenstokens
   sammeln (Nachname an `-` gesplittet, Vorname).
3. Zweiter Durchgang über die gesammelten Tokens: `(?<![\p{L}<])TOKEN(?![\p{L}>])` → `<NAME>`.
   Die `<`/`>` in den Grenzen halten die Ersetzung aus bereits gesetzten Platzhaltern heraus.
4. Aufrufreihenfolge in `sanitize` anpassen; Javadoc von `LONG_DIGIT_RUN` korrigieren —
   `044 913 2323` bleibt *für diese Regel* unberührt, wird jetzt aber von `PHONE` genommen.
5. Grenzen-Absatz im Klassen-Javadoc auf die tatsächlich verbleibende Kante ziehen.
6. Javadoc von `ClaudeCategorizationService` und den Absatz in `CLAUDE.md` nachziehen.
7. ADR-15-Querverweis präzisieren: die Restexposition in `user_category_lookup` bleibt, der
   Prompt-Pfad ist geschlossen.
8. ADR-6 «PII-Policy für MVP» auf den Ist-Zustand ziehen; die Aussage zu nDSG Art. 16 und zum
   fehlenden DPA bleibt inhaltlich stehen.

## Test-Strategie

Ausschliesslich Unit-Tests (JUnit 5 + AssertJ) — der Sanitizer ist eine reine Funktion, und der
Nachweis, dass er auf dem Weg zur API überhaupt angewendet wird, existiert bereits in
`ClaudeCategorizationServiceTest`.

| Test | Zweck |
| ---- | ----- |
| `Telefonnummer.telefonnummerWirdMaskiert` (parametrisiert, 7 Formate) | AC 3 — `044 913 2323`, `044 913 23 23`, `0800 123 456`, `079 123 45 67`, `+41 44 913 23 23`, `0041 44 913 2323`, `044/913 23 23` |
| `Telefonnummer.telefonnummerErreichtDenPromptNichtMehr` (ehemals `telefonnummerBleibtStehen_bekannteGrenze`) | AC 4 — derselbe Test, neue Erwartung |
| `Telefonnummer.zahlenOhneTelefonformBleibenStehen` | Gegenprobe: `RECHNUNG 2024 2025`, `KAUF VOM 03.07.2026`, `RECHNUNG 11-2025`, `COOP-1234` |
| `Personenname.vornameInDerZweckzeileWirdMitmaskiert` (ehemals `…BleibtStehen_bekannteGrenze`) | AC 2 — neue Erwartung `LASTSCHRIFT <NAME> SACKGELD <NAME>` |
| `Personenname.echoMaskierungGreiftNurBeiPersonentreffer` | Gegenprobe: ohne `NACHNAME, VORNAME` im Text bleibt alles stehen |
| `RealerKorpus.realeBuchungstexteUeberlebenUnveraendert` | **unverändert grün** — der eigentliche Nachweis für AC 2 |
| `ClaudeCategorizationServiceTest.promptContainsNumberedTransactionTexts` | AC 3 auf dem echten Weg zur API |

## Acceptance Criteria (aus #233)

- [ ] Entscheid dokumentiert, ob Punkt 1 technisch angegangen oder als Restrisiko akzeptiert
      wird — **angegangen**, Begründung in Entscheid 1
- [ ] Die Maskierung entfernt den Vornamen auch aus der Zweckzeile, ohne dass ein Händlertoken
      des Fixture-Korpus verlorengeht (`PromptSanitizerTest.RealerKorpus` bleibt grün)
- [ ] Telefonnummern im Buchungstext erreichen den Prompt nicht mehr
- [ ] `PromptSanitizerTest.telefonnummerBleibtStehen_bekannteGrenze` wird auf das neue Verhalten
      umgestellt statt gelöscht
- [ ] Die Grenzen-Absätze im Javadoc von `PromptSanitizer` und in `CLAUDE.md` sind auf dem
      neuen Stand
