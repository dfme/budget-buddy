# [BE-STS-05] Einkommens-Heuristik über den Absender statt den Buchungstext gruppieren

- **Issue:** [#208](https://github.com/dfme/budget-buddy/issues/208)
- **Task-ID:** `BE-STS-05`
- **Branch:** `feature/BE-STS-05-absender-gruppierung`
- **Story:** US-06 — Safe-to-Spend (aus dem Issue-Body; das Issue trägt kein `us-*`-Label)
- **Sprint:** Sprint 6
- **Bestätigt am:** 2026-09-06

## Ausgangslage, belegt an der Fixture

`IncomeSuggestionService:142` gruppiert die Gutschriften über `groupingKey(tx.getBuchungstext())`
(`:213`). Bei PostFinance trägt `buchungstext` nur die Zahlungsart, jede Gutschrift heisst
`GUTSCHRIFT`. Ein Dump der geparsten Gutschriften aus `Post_Kontoauszug_2025_240_Buchungen.pdf`
zeigt, was das bedeutet:

```
[2025-01-31] txt=GUTSCHRIFT | 4250.00 | details=[MUSTER CONSULTING GMBH, LOHN JANUAR 2025]
[2025-04-20] txt=GUTSCHRIFT |  340.00 | details=[STEUERVERWALTUNG KT. BERN, RUECKERSTATTUNG APRIL 2025]
...
```

15 Gutschriften, ein einziger Schlüssel `gutschrift`: 12× Lohn 4250.00, 3× Rückerstattung 340.00.
Der Median der Gruppe ist 4250.00, das ±5 %-Band ±212.50 — die 340er liegen ausserhalb und kippen
nach der Regel aus `qualify()` die **ganze** Gruppe. Dieser reale Jahresauszug erzeugt heute also
gar keinen Vorschlag. Mit dem Absender als Schlüssel sind es zwei Gruppen, beide qualifizieren,
die Regel «höchster Median» wählt den Lohn: 4250.00.

Die Detailzeilen stehen seit BE-PDF-07 (#159) in `transactions.buchungsdetails` (V06), mit `\n`
verbunden und damit wieder splittbar.

## Entscheide

| Frage | Entscheid | Begründung |
| ----- | --------- | ---------- |
| Welcher Teil der Detailzeilen wird zum Schlüssel? | **Nur die erste Zeile** | Der Issue-Body nennt sie als Kandidat, die Fixture-Probe bestätigt sie: in allen vier PostFinance-Auszügen ist die erste Detailzeile durchgängig die Gegenpartei, die zweite der Verwendungszweck. Der Zweck variiert von Monat zu Monat — `Post_Kontoauszug_2026_Juli` bricht ihn sogar mitten im Wort auf zwei Zeilen um (`LOHN JULI 2026 SOWIE SPE` / `SENVERGUETUNG`). Alle Zeilen zu verketten zerlegte die Gruppe genau dort, wo sie zusammengehört. |
| Wann greift der Fallback? | `buchungsdetails` ist `null` **oder** die erste Zeile ist leer | AC2. Für alles vor V06 Importierte bleibt damit exakt das heutige Verhalten. `null` ist der Regelfall (kein Backfill möglich — die Zeilen stehen nur im Quell-PDF), die leere erste Zeile der defensive Rest. |
| Getrennte Schlüsselräume (Präfix `D:` / `T:`)? | **Nein** | Eine Kollision setzte einen Absender voraus, der normalisiert exakt wie eine Zahlungsart heisst. Träte sie ein, wäre das Ergebnis das heutige Verhalten — kein neuer Fehlermodus, der einen unleserlichen Schlüssel aufwöge. Der Schlüssel ist ohnehin intern: er verlässt den Service nie (`IncomeSuggestionPort` gibt nur den Betrag zurück) und dient nur als letzte Tiebreak-Stufe. |
| Normalisierung | unverändert | AC4. Kleinschreibung, Monatsnamen, ziffernhaltige Tokens, Whitespace und der Leerstring-Rückfall gelten unverändert, nur eben auf der neuen Quelle. |

## Scope-Erweiterung (bestätigt)

Die Breitensuche nach dem Begriff hinter AC6 (`grep -rni 'absender\|gruppier'`) findet vier
Stellen im **Produktivcode**, die nach dem Change einen überholten Ist-Zustand beschreiben. Die
ACs führen nur `docs/requirements/US-06-safe-to-spend.md:31` auf. Der User hat entschieden, sie im
selben PR mitzubeheben:

- `IncomeSuggestionService:27` — «über einen normalisierten Buchungstext gruppiert»
- `IncomeSuggestionService:40-48` — «Bekannte Einschränkung — Eigenübertragungen», mit dem Satz
  «Sauber löst es erst die Absender-Gruppierung»
- `IncomeSuggestionService:50-56` — «Bekannte Einschränkung — Gruppierung ohne Absender»
- `IncomeSuggestionPort:17` und `TransactionRepository:108` — «der Buchungstext, über den die
  Heuristik gruppiert»

**Die Einschränkung «Eigenübertragungen» wird korrigiert, nicht gestrichen.** Die
Absender-Gruppierung trennt die Umbuchung vom eigenen Sparkonto jetzt zwar in eine eigene Gruppe,
aber die Auswahlregel «höchster Median» kann sie weiterhin über den Lohn stellen. Der Satz «sauber
löst es erst die Absender-Gruppierung» war zu optimistisch und darf nicht durch die nächste
unqualifizierte Vereinfachung ersetzt werden.

`docs/plans/BE-STS-02-einkommens-heuristik.md` bleibt unangetastet: Pläne halten den Stand zum
Zeitpunkt ihrer Planung, nicht den aktuellen.

## Betroffene Dateien

**Geändert**

| Datei | Änderung |
| ----- | -------- |
| `backend/src/main/java/com/budgetbuddy/transaction/IncomeSuggestionService.java` | `groupingKey(Transaction)` statt `groupingKey(String)`, neue Helfer `senderLine()` / `normalise()`, Aufrufstelle `:142`, Klassen-Javadoc und beide Einschränkungs-Blöcke |
| `backend/src/main/java/com/budgetbuddy/transaction/IncomeSuggestionPort.java` | Javadoc `:17` |
| `backend/src/main/java/com/budgetbuddy/transaction/TransactionRepository.java` | Javadoc `:108` |
| `backend/src/test/java/com/budgetbuddy/transaction/IncomeSuggestionServiceTest.java` | `credit()`-Helfer um `buchungsdetails`, neue Tests |
| `backend/src/test/java/com/budgetbuddy/transaction/IncomeSuggestionServiceIntegrationTest.java` | `insertIncome`-Überladung mit `buchungsdetails`, neue Tests |

**Neu:** keine. **Nicht angefasst:** Migrationen (V06 liefert die Spalte bereits), Frontend,
`docs/requirements/US-06` (der Text war korrekt, nur unerfüllt).

## Implementierungsschritte

1. `senderLine(String buchungsdetails)`: `null`/blank → `null`; sonst die erste Zeile bis zum
   ersten `\n`, getrimmt; ist die leer → `null`.
2. `normalise(String)`: der heutige Rumpf von `groupingKey()`, unverändert.
3. `groupingKey(Transaction tx)`: `normalise(senderLine(tx.getBuchungsdetails()) != null ? … :
   tx.getBuchungstext())`.
4. Aufrufstelle `:142` auf `groupingKey(tx)` umstellen.
5. Javadoc nachziehen (siehe Scope-Erweiterung).
6. Tests.

## Test-Strategie

**Unit — `IncomeSuggestionServiceTest`.** Die 14 bestehenden Tests bleiben inhaltlich unverändert;
ihr `credit()`-Helfer setzt `buchungsdetails = null` und belegt damit als Ganzes, dass der
Fallback-Pfad das heutige Verhalten hält. Neu:

| Test | AC |
| ---- | -- |
| Zwei Absender unter identischem `GUTSCHRIFT` (Fixture-Werte 4250.00 / 340.00) → 4250.00 statt leer | AC3 |
| `Post_Kontoauszug_2025_240_Buchungen.pdf` real geparst, Gutschriften ins Repository-Mock → 4250.00 | AC3 |
| Gemischt `null` und gesetzt → Fallback greift pro Transaktion, nicht global | AC2 |
| Erste Detailzeile mit Monatsname und Jahreszahl → eine Gruppe | AC4 |
| Gleicher Absender, abweichende zweite/dritte Zeile → eine Gruppe | AC1 |
| Leere erste Detailzeile → Buchungstext | AC2 |

**Integration — `IncomeSuggestionServiceIntegrationTest`.** Die fünf bestehenden Tests schreiben
`buchungsdetails` nicht und belegen den `NULL`-Pfad im echten Schema. Neu: zwei Absender in einer
Spalte ergeben zwei Gruppen; ein gemischter Datenbestand aus `NULL` und gesetzt. Die
Mandantentrennungs-Gegenprobe (`aForeignUsersCreditsDoNotAffectTheSuggestion`) bleibt unverändert.

**Abschluss:** `mvn verify` im Backend, `ng build` im Frontend (DoD).

## Acceptance Criteria (aus #208)

- [ ] `groupingKey()` bildet den Schlüssel aus den Detailzeilen, wenn vorhanden, und fällt sonst auf den Buchungstext zurück
- [ ] Der Fallback greift für alle vor `V06` importierten Transaktionen — dort ist `buchungsdetails` `NULL`, und ein Backfill ist ausgeschlossen (die Zeilen stehen nur im Quell-PDF)
- [ ] Ein PostFinance-Auszug mit mehreren verschiedenen Gutschriften-Absendern erzeugt **mehrere** Gruppen, nicht eine
- [ ] Die Normalisierung (Kleinschreibung, Ziffern raus, Whitespace) gilt unverändert für den neuen Schlüssel
- [ ] Bestehende Erwartungen in `IncomeSuggestionServiceTest` und `IncomeSuggestionServiceIntegrationTest` sind auf das neue Verhalten gezogen, nicht abgeschaltet
- [ ] `docs/requirements/US-06-safe-to-spend.md:25` («desselben Absenders») ist damit tatsächlich erfüllt — bisher war es die schwächere Aussage
