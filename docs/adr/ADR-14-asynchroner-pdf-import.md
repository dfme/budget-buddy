# ADR-14: Asynchroner PDF-Import mit Fortschritts-Job und Batch-Kategorisierung

**Status:** Accepted
**Date:** 2026-08-22
**Ergänzt:** [ADR-6](ADR-6-hybrid-categorization.md) (Hybrid-Kategorisierung)

## Context

Der PDF-Import lief seit BE-PDF-02 vollständig synchron im Upload-Request: Parse →
Kategorisierung → Persistierung, begrenzt durch ein kooperatives Zeitbudget von 30 Sekunden. In
Produktion lief er damit reproduzierbar ins Budget und verwarf **den gesamten Import**
([#192](https://github.com/dfme/budget-buddy/issues/192)):

```
16:57:30  WARN PdfImportService: PDF-Import für User 2 nach 75 von 108 Transaktionen abgebrochen (Timeout 30s).
17:56:16  WARN PdfImportService: PDF-Import für User 2 nach 65 von 108 Transaktionen abgebrochen (Timeout 30s).
```

Zweimal innerhalb einer Stunde, derselbe Auszug mit 108 Transaktionen. Der Nutzer wartete 30
Sekunden ohne jede Rückmeldung und bekam danach nichts — der einzige Schreibzugriff war der
abschliessende Commit. Das ist Risiko #1 aus README.md («Churn-Falle») im konkreten Fall.

### Die Ursache ist die Anzahl der Requests, nicht ihre Dauer

Die Analyse in den Issue-Kommentaren ist eindeutig. Aus dem einzigen erfolgreichen Import mit
Phasenmessung im Log-Zeitraum (BE-PDF-06): 2 Claude-Calls in 2278 ms, also **~1.14 s pro Call**.
Die Zeitachse des gescheiterten Imports zeigt ~1.95 s Parse und ~28 s Kategorisierung — also ~25
sequentielle HTTPS-Round-Trips.

Der CPU-gebundene Anteil ist knapp 2 s von 30. Eine grössere Instanz hilft daher nicht.
Hochgerechnet auf alle 108 Transaktionen: ~41 Calls ≈ 47 s + Parse ≈ **~49 s**.

Entscheidend für die Lösungswahl ist, **wo** diese Sekunde steckt: Der Prompt ist ~100 Tokens
gross und die Antwort auf 20 Tokens gedeckelt — es werden grob fünf Tokens generiert. Die Zeit
liegt fast vollständig im **Fixkostenanteil pro Request** (Netz-Round-Trip, Queueing,
Time-to-First-Token), nicht in der Generierung. Das schliesst zwei naheliegende Ideen aus:

- **Den Prompt kürzen bringt nichts** — er ist bereits vernachlässigbar klein.
- **Ein schnelleres Modell gibt es nicht** — `claude-haiku-4-5` ist bereits die schnellste Stufe.

## Decision

Zwei Änderungen, die zusammengehören.

### 1. Bündelung: mehrere Transaktionen pro Claude-Call

`CategorizationPort` bekommt `categorizeAll(List<String>)` mit positionsgleicher Rückgabe.
`ClaudeCategorizationService` fasst bis zu **20** Transaktionen in einen Request; die Antwort
kommt als Structured Output, dessen Schema das SDK aus einem Java-Record ableitet:

```java
public record CategorizedTransaction(int number, Category category) {}
public record BatchCategorization(List<CategorizedTransaction> categories) {}
```

Weil das Feld den `Category`-Enum trägt, landet dessen Konstantenliste als `enum`-Constraint im
JSON-Schema. Eine Kategorie ausserhalb der Liste ist damit **strukturell ausgeschlossen** statt
bloss erbeten — der frühere Zweig «unbekannte Kategorie → Sonstiges» entfällt ersatzlos. Die
Kategorienliste steht deshalb auch nicht mehr im Prompt: Sie wäre eine zweite Kopie derselben
Liste, die auseinanderläuft, sobald eine Kategorie dazukommt.

Erwartung: **~41 Calls → 3**, ~47 s → wenige Sekunden. Nebeneffekt auf die Kosten: Der Vorspann
aus System-Prompt geht statt 41× nur noch 3× hinaus.

Bündelgrösse 20 statt «alles auf einmal», weil ein fehlgeschlagener Call immer ein ganzes Bündel
kostet. Innerhalb eines Bündels ist der Schaden zusätzlich begrenzt: Erst fällt das Bündel auf
`Sonstiges`, dann überschreibt jeder gültige Eintrag seine Position — eine unvollständige Antwort
kostet nur die fehlenden Nummern.

### 2. Asynchroner Job mit Fortschrittsanzeige

Der Upload wird zweistufig:

| Phase | Wo | Dauer | Fehler |
| --- | --- | --- | --- |
| Hash, Duplikatcheck, Parse, Job anlegen | im Request | ~2 s | 400 mit `reason`, 409, 413, 408 |
| Kategorisierung, Persistierung | `@Async`-Worker | Sekunden | Job-Status `FAILED` |

`POST /api/import/pdf` antwortet mit `202 Accepted` und `{jobId, total}`;
`GET /api/import/{jobId}/status` liefert `{status, total, processed, degraded}`. Das Frontend
pollt im 700-ms-Takt und zeigt `processed`/`total` als Fortschrittsbalken.

**Der Schnitt liegt bewusst nach dem Parsen, nicht davor.** Das Parsen dauert ~2 s, die
Kategorisierung ~28 s — nur der lange Teil muss weg. Bleibt das Parsen im Request, behalten alle
Fehler, die es erzeugt, ihre gewöhnliche HTTP-Semantik, und das Frontend-Fehlermapping, der
Duplikat-Dialog (FE-PDF-03) und der E2E-Fehlerpfad bleiben unverändert. Zusätzlich kennt der
Fortschrittsbalken seinen Nenner schon in der Upload-Antwort, statt eine Phase lang «unbekannt»
anzeigen zu müssen.

### 3. Zwei Zeitbudgets mit verschiedenen Aufgaben

| Property | Wert | Gilt für | Bei Überschreitung |
| --- | --- | --- | --- |
| `budgetbuddy.import.timeout-seconds` | 30 | nur noch das Parsen | 408, kein Job angelegt |
| `budgetbuddy.import.categorization-timeout-seconds` | 300 | den Hintergrundlauf | Degradation, siehe unten |

Auf den Hintergrundlauf wartet kein Request mehr; sein Watchdog verhindert nur, dass ein
hängender Call einen Worker-Thread unbegrenzt bindet. Läuft er hinein, wird **nicht abgebrochen**:
Die restlichen Transaktionen fallen ohne Claude-Call auf `Sonstiges`, der Import wird vollständig
gespeichert und der Job endet auf `DONE` mit `degraded = true`. Das Frontend meldet das als
Erfolg mit Zusatz.

Die Abwägung dahinter: Eine fehlende automatische Kategorie kostet den Nutzer eine Korrektur —
die nach ADR-6 zugleich die Lookup-Tabelle füttert und damit den nächsten Import beschleunigt.
Ein verworfener Import kostet ihn alles.

## Consequences

**Positiv**

- Das Fehlerbild aus #192 ist nicht mehr erreichbar: Es gibt keinen wartenden Request, der ablaufen
  könnte, und selbst der Watchdog verwirft nichts mehr.
- Der Nutzer sieht während des Imports, was passiert — statt 30 Sekunden Stillstand.
- Halluzinierte Kategorien sind strukturell ausgeschlossen (`enum` im Schema).
- Weniger Requests heisst auch weniger Kosten und weniger Angriffsfläche für Rate-Limits.
- Die Instrumentierung aus BE-PDF-06 ist nicht mehr blind: Die Summary-Zeile läuft auch im
  degradierten Fall (#192, Nebenbefund).

**Negativ / Kosten**

- Mehr bewegliche Teile: Migration `V05__create_import_jobs_table.sql`, Entity, Repository,
  Thread-Pool, zweiter Endpoint, Polling im Client.
- Ein fehlgeschlagener Call kostet ein Bündel statt einer Transaktion. Abgefedert durch
  Bündelgrösse 20 und die positionsweise Auswertung.
- Der Job hält die geparsten Transaktionen im Speicher, bis er fertig ist. Bei einem Neustart
  mitten im Lauf ist der Import verloren — der Pool wartet beim Shutdown deshalb bis zu 60 s auf
  laufende Jobs.
- Der Fortschritt springt in Bündelschritten (20), nicht transaktionsweise.

## Alternatives Considered

**Timeout-Anhebung als Sofortmassnahme** — verlagert das Churn-Problem nur in eine längere
Wartezeit. 50 s Blindwarten sind für Lara nicht besser als 30 s Blindwarten mit anschliessendem
Datenverlust.

**Parallele Einzel-Calls (begrenzter Thread-Pool, 1 Transaktion pro Call)** — kleinerer Eingriff,
bei 8 gleichzeitigen Calls ~7 s. Zwei Haken: Anthropic-Rate-Limits, und der Circuit Breaker
(BE-CAT-02) zählt «Fehler **in Folge**» — die *Semantik* dieser Bedingung ist unter echter
Nebenläufigkeit nicht mehr sauber definiert. Mit der Bündelung kombinierbar, aber erst messen.

**Message-Batches-API** — 50 % günstiger, dafür bewusst asynchron mit Durchlaufzeit bis zu 24
Stunden und Polling bis `processing_status == "ended"`. Gebaut für Massenverarbeitung ohne
Zeitdruck, nicht zur Latenzsenkung. Auch nach diesem Umbau unbrauchbar: Niemand wartet einen Tag
auf den Import seines Kontoauszugs.

**Prompt Caching** — naheliegend, greift hier nicht: Die minimale cachefähige Prefix-Länge liegt
bei ~1024 Tokens, der Vorspann aus System-Prompt und Kategorienliste bei ~100. Der Cache würde
still nie anspringen.

**Transaktionen sofort mit `category = NULL` persistieren und nachtragen** — machte den
Fortschritt dauerhaft und den Job schlank. Verworfen, weil unkategorisierte Zeilen sofort in
Dashboard und Kategorie-Übersicht auftauchten und dort einen Zwischenstand zeigten, den niemand
angefordert hat.

## Offene Punkte

- Die Lookup-Trefferquote bleibt der billigste Hebel: Jeder Treffer kostet 0 ms statt ~1.1 s. Die
  Referenzmessung zeigte **0 von 2** Treffern, ADR-6 rechnet mit 70–80 %. Bündelung und
  Trefferquote wirken zusätzlich, nicht alternativ.
- Bei einem Neustart mitten im Lauf bleibt ein Job auf `RUNNING` stehen, wenn der Pool ihn nicht
  mehr fertigstellen kann. Ein Aufräum-Job beim Start ist bewusst nicht gebaut — bei einer
  Einzelinstanz mit `waitForTasksToCompleteOnShutdown` ist das Fenster klein.
