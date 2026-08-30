# [BE-CAT-06] Transaktionstext vor Claude-Call maskieren (Datenminimierung)

- **Issue:** [#134](https://github.com/dfme/budget-buddy/issues/134)
- **Task-ID:** `BE-CAT-06`
- **Branch:** `feature/BE-CAT-06-prompt-sanitizer`
- **Story:** US-05 — Transaktionen kategorisieren (Auto + manuell)
- **Sprint:** Sprint 5
- **Bestätigt am:** 2026-08-30

## Ziel

Ein Sanitizer unmittelbar vor dem Claude-Call, der Personen- und Zahlungsdaten aus dem
Transaktionstext entfernt, bevor er das System verlässt. Die Red Line der Risiko-Einstufung
(«Payment or card data is put into the model's context») soll nicht mehr allein durch den
Parser gehalten werden.

## Ausgangslage

Zwischen der Beschreibung des Tickets und heute hat sich der Code bewegt. Der Stand, gegen den
geplant wurde — jede Zeile nachgeprüft, nicht aus dem Ticket übernommen:

| Befund | Nachweis |
| ------ | -------- |
| Der Claude-Pfad hat genau **eine** Stelle | `ClaudeCategorizationService.java:316-325` (`buildUserPrompt`) ist der einzige `addUserMessage`. `AnthropicStartupHealthCheck.java:78` ruft nur `client.models().list()`, `report/` enthält nur `package-info.java` |
| IBAN, Kartennummer, Anschrift und Referenzzeilen erreichen den Prompt **nicht mehr** | `SwissBankStatementParser.java:215` (`DETAIL_NOISE`, eingeführt mit #196); verifiziert über einen Dump von `fullText()` für alle sechs PDF-Fixtures |
| Beträge waren **nie** im Prompt | `ParsedTransaction.fullText():47` verkettet ausschliesslich `buchungstext` und `details`; `betrag` ist ein eigenes Record-Feld |
| Die vier Log-Statements sind bereits sauber | `LogRedaction` (Review PR #174); breite Suche über *alle* Module nach `log.*` mit `text|betrag|amount|pattern|email` findet keinen Klartext |
| **Real offen:** Gegenpartei als natürliche Person | `LASTSCHRIFT MUSTER, LEA SACKGELD LEA` und `GUTSCHRIFT MUSTER, ANNA RUECKZAHLUNG FERIENKASSE` aus `Post_Kontoauszug_2026_Juli_20_Buchungen.pdf` |
| **Real offen:** Referenz-Token am Händlernamen | `Spotify P123456789, Stockholm SE …` aus der Viseca-Fixture |

Der Sanitizer ist damit **zweierlei**: für den Personennamen und das Referenz-Token eine echte
Lücke, die er schliesst — für IBAN, Kartennummer und Betrag eine zweite Verteidigungslinie an der
Stelle, an der die Daten tatsächlich das System verlassen. Die erste Linie liegt im Parser und
kann durch ein neues Bank-Layout jederzeit unvollständig werden.

## Entscheide

### 1. Der Sanitizer sitzt in `buildUserPrompt`, nicht im Parser und nicht im Hybrid-Service

`buildUserPrompt` ist die engste Stelle: der einzige Ort, an dem Text in einen Request
serialisiert wird. Weiter oben (etwa in `categorizeAll`) läge er zwar auch im Claude-Pfad, aber
mit mehr Code dazwischen, der ihn später umgehen könnte.

Wichtige Nebenwirkung: die Lookup-Stufe (`HybridCategorizationService:65`) arbeitet weiterhin auf
dem unmaskierten Text. Der Lookup ist lokal, sein Input verlässt das System nicht — ihn zu
maskieren würde nur die Trefferquote senken, ohne etwas zu schützen.

### 2. AC 4 bleibt im Wortlaut stehen und wird als nicht erfüllt ausgewiesen

Die AC lautet: «Die Lookup-Tabelle (Stufe 1) arbeitet unverändert auf dem **vollen** Text — die
Maskierung greift nur auf dem Pfad zur Claude API.» Der zweite Halbsatz wird durch Entscheid 1
erfüllt, der erste ist seit #196 nicht mehr erfüllbar: `DETAIL_NOISE` verwirft die Rauschzeilen
schon beim Parsen, der Lookup sieht also denselben bereinigten Text wie Claude.

Auf ausdrückliche Entscheidung des Teams wird die AC **nicht umformuliert**, sondern im PR-Body
als bewusst nicht erfüllt benannt, mit Verweis auf #196. Begründung: eine AC im Nachhinein
passend zu schreiben verwischt, dass sich die Architektur bewegt hat.

### 3. Regel 6 ist eine Heuristik mit bekanntem Rand

Personennamen druckt PostFinance als `NACHNAME, VORNAME` in Versalien. Die naheliegende Regel
«Wort, Wort» wäre fatal: die Viseca-Abrechnung besteht aus Zeilen der Form
`Händler, Ort LAND Kategorie` — `Coop-1122, Bern CH Lebensmittel`, `Zalando SE, Berlin DE
Bekleidung`. Eine zu breite Regel zerstörte dort genau den Händlertoken, den AC 3 schützt.

Der Trennschnitt ist die Schreibweise: **beide** Teile müssen mindestens zwei Grossbuchstaben am
Stück tragen. Nach dem Komma steht bei Viseca durchgängig ein gemischt geschriebener Ortsname,
der daran scheitert. Am Korpus aller sechs Fixtures gegengeprüft: von 14 Zeilen mit Komma treffen
genau die zwei Personennamen zu.

Der Rand bleibt: ein reiner Versalien-Händler mit Komma (`COOP, BERN`) fiele mit heraus. Im
Korpus kommt die Form nicht vor; die Grenze steht als solche im Javadoc, nach dem Vorbild von
`NOISE_ADDRESS` («Heuristik mit bekanntem Rand»).

### 4. Der Vorname in der Zweckzeile bleibt — dokumentiert, mit Folge-Issue

`LASTSCHRIFT MUSTER, LEA SACKGELD LEA` wird zu `LASTSCHRIFT <NAME> SACKGELD LEA`. Der Vorname in
der frei getippten Zweckzeile ist regelbasiert nicht von einem Händlernamen zu unterscheiden; ihn
über eine Namensliste zu jagen würde AC 3 gefährden.

Entscheid: als Grenze im Javadoc festhalten und ein Folge-Issue **BE-CAT-08** anlegen (BE-CAT-07
ist mit #162 belegt), Label `enhancement`, ohne Milestone und ohne Sprint.

### 5. Telefonnummern sind ein bewusster Nicht-Umfang

Die Fixture-Konstante von `ClaudeCategorizationServiceTest` ist
`DIGITEC GALAXUS AG 044 913 2323` — eine Telefonnummer geht heute mit hinaus. Keine AC nennt sie,
und es ist die Nummer des Händlers, nicht des Nutzers. Sie wird als Nebenpunkt in BE-CAT-08
aufgenommen statt still übergangen.

Zugleich ist diese Konstante eine **Regressionsbremse**: der Sanitizer darf sie nicht verändern,
sonst reisst `promptContainsNumberedTransactionTexts` (`ClaudeCategorizationServiceTest:246`).

## Betroffene Files

**Neu**

- `backend/src/main/java/com/budgetbuddy/categorization/PromptSanitizer.java` — package-private,
  `static String sanitize(String)`, aufgebaut wie `LogRedaction`
- `backend/src/test/java/com/budgetbuddy/categorization/PromptSanitizerTest.java`

**Geändert**

- `backend/src/main/java/com/budgetbuddy/categorization/ClaudeCategorizationService.java` —
  `buildUserPrompt` schreibt `PromptSanitizer.sanitize(…)`; Klassen-Javadoc um einen Absatz zur
  Datenminimierung
- `backend/src/test/java/com/budgetbuddy/categorization/ClaudeCategorizationServiceTest.java` —
  zwei Tests (AC 6, AC 7)
- `CLAUDE.md` — ein Absatz unter «Transaktions-Kategorisierung: Hybrid-Ansatz»

Kein ADR: die Begründung passt vollständig ins Javadoc, und die Entscheidung ändert weder den
Stack noch die Architektur.

## Sanitizer-Regeln

Reihenfolge = Anwendungsreihenfolge. Die Platzhalter (`<IBAN>` … `<EMAIL>`) sind so gewählt, dass
keine spätere Regel auf einer früheren Ersetzung greift.

| # | Trifft | Ersetzung | Gegenprobe am realen Korpus |
| - | ------ | --------- | --------------------------- |
| 1 | IBAN, kompakt und in 4er-Gruppen | `<IBAN>` | `MUSTER IMMOBILIEN AG` unberührt (die Regel verlangt `[A-Z]{2}\d{2}`) |
| 2 | Maskierte Kartennummer `XXXX4417`, `5500 20XX XXXX 5446` | `<KARTE>` | 4er-Gruppen nur ersetzt, wenn die Gruppe `XX` enthält |
| 3 | Ziffernlauf 12–19 | `<KARTE>` | `COOP-1234` und `044 913 2323` unberührt |
| 4 | Betrag `42.50`, `1'234.56` | `<BETRAG>` | Datum `03.07.2026` und `03.07.26` unberührt (Lookbehind `(?<!\d\.)`) |
| 5 | Referenz: ≥10 Zeichen `[0-9A-Z]`, **mit** Ziffer, case-sensitiv | `<REF>` | trifft `P123456789`; `CONSULTING`, `IMMOBILIEN`, `RUECKZAHLUNG` scheitern an der Ziffernbedingung |
| 6 | Person `NACHNAME, VORNAME` (siehe Entscheid 3) | `<NAME>` | trifft `MUSTER, LEA` und `MUSTER, ANNA`; alle zwölf Viseca-Kommazeilen bleiben |
| 7 | E-Mail-Adresse | `<EMAIL>` | Defense-in-depth, im Korpus ohne Treffer |

Regel 5 ist ausdrücklich case-**sensitiv**, aus demselben Grund wie `NOISE_OPAQUE_REFERENCE`
(`SwissBankStatementParser.java:195`): mit `(?i)` träfe `[0-9A-Z]` auch Kleinbuchstaben, und dann
verschwände jede einwortige Zweckzeile mit Ziffer ab zehn Zeichen aus dem Prompt.

## Implementierungsschritte

1. `PromptSanitizer` anlegen: die sieben Regeln als einzeln benannte, einzeln begründete
   Konstanten (Vorbild `SwissBankStatementParser`, wo jeder `NOISE_*`-Baustein sein eigenes
   Javadoc trägt), plus `sanitize(String)` mit `null`/blank-Behandlung
2. Regel 2 braucht eine bedingte Ersetzung (4er-Gruppen nur, wenn `XX` enthalten) — als kleiner
   `Matcher`-Loop, nicht als unlesbare Regex
3. `buildUserPrompt` auf `PromptSanitizer.sanitize(…)` umstellen; wird der Text dadurch leer,
   einen Platzhalter statt einer leeren Zeile schreiben, damit die Nummerierung lesbar bleibt
4. Klassen-Javadoc von `ClaudeCategorizationService` um einen Absatz «Datenminimierung» ergänzen
5. Tests schreiben (siehe unten)
6. `CLAUDE.md` ergänzen
7. Folge-Issue BE-CAT-08 anlegen

## Test-Strategie

| Test | AC |
| ---- | -- |
| `PromptSanitizerTest` — je Regel ein Treffer **und** ein Nicht-Treffer | 1, 2 |
| `PromptSanitizerTest` — Korpus aus realen `fullText()`-Zeilen der sechs PDF-Fixtures: die Händlertoken (`MIGROS`, `COOP-1234 BERN`, `SWISSCOM (SCHWEIZ) AG`, `DIGITEC GALAXUS AG`, `ZALANDO SE`, `SBB CFF FFS`) überleben unverändert | 3 |
| `ClaudeCategorizationServiceTest` — Text mit IBAN, Kartennummer und Betrag: `capturedUserPrompt()` enthält keines davon | 6 |
| `ClaudeCategorizationServiceTest` — maskierter Text liefert weiterhin die korrekte Kategorie | 7 |
| bestehend, muss grün bleiben: `promptContainsNumberedTransactionTexts` (`:246`) | Regressionsbremse, siehe Entscheid 5 |

AC 5 (Log-Statements) ist bereits erfüllt; Nachweis ist `CategorizationLogRedactionTest` plus die
breite Suche aus der Ausgangslage. Es wird dafür kein neuer Test geschrieben.

Verifikation: erst das `categorization`-Paket, danach die vollständige Backend-Suite.

## Acceptance Criteria (aus #134)

- [ ] Ein Sanitizer maskiert vor dem Claude-Call IBANs, Karten- und Kontonummern sowie
      Referenznummern im Transaktionstext
- [ ] Beträge werden vor dem Versand entfernt — sie fliessen nicht in den Prompt ein
- [ ] Die Kategorisierungs-Trefferquote bleibt erhalten: Der Händler-/Zwecktoken überlebt die
      Maskierung
- [ ] ~~Die Lookup-Tabelle (Stufe 1) arbeitet unverändert auf dem vollen Text~~ — **bewusst nicht
      erfüllt**, siehe Entscheid 2
- [ ] Die vier Log-Statements in `ClaudeCategorizationService` geben keinen unmaskierten
      Transaktionstext mehr aus — bereits erfüllt durch Review PR #174
- [ ] Unit-Test: Ein Text mit IBAN, Kartennummer und Betrag erzeugt einen Prompt, der keines
      dieser Elemente enthält
- [ ] Unit-Test: Der maskierte Text führt weiterhin zur korrekten Kategorie
