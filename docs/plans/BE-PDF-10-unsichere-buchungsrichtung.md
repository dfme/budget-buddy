# [BE-PDF-10] Geratene Buchungsrichtung sichtbar machen und korrigierbar

- **Issue:** [#193](https://github.com/dfme/budget-buddy/issues/193)
- **Task-ID:** `BE-PDF-10`
- **Branch:** `fix/BE-PDF-10-unsichere-buchungsrichtung`
- **Story:** US-04 — PDF-Upload (Auswirkung auf US-06 — Safe-to-Spend)
- **Sprint:** Sprint 6
- **Bestätigt am:** 2026-09-04

## Ausgangslage

Kann der Parser die Richtung einer Buchung nicht aus dem Saldo ableiten, übernimmt er sie als
Belastung. Das ist als konservativer Fallback bewusst so entschieden. Das Problem ist, dass er
**gegenüber dem Nutzer stumm** ist: die Warnung geht ins Log, die Oberfläche zeigt die Buchung wie
jede andere. Ist eine Gutschrift darunter, ist der Betrag doppelt falsch und Safe-to-Spend fällt zu
tief aus.

## Scope-Delta gegenüber den Acceptance Criteria

Der enge Wortlaut des Issues (`Saldo-Delta …`) trifft zwei Code-Stellen, plus die dritte, die das
Issue selbst benennt. Die breite Suche über `als Belastung` und die stummen `isIncome`-Defaults
findet **sieben** Pfade, die eine Richtung raten:

| # | Stelle | Pfad | Im Issue genannt? |
| - | ------ | ---- | ----------------- |
| 1 | `SwissBankStatementParser.java:601` | PostFinance, Saldo-Delta mehrdeutig | ja |
| 2 | `SwissBankStatementParser.java:612` | PostFinance, Saldo-Delta nicht auflösbar | ja |
| 3 | `SwissBankStatementParser.java:583` | PostFinance, > 16 Buchungen im Saldo-Block | ja |
| 4 | `SwissBankStatementParser.java:569` | PostFinance, Buchungen vor der ersten `Kontostand`-Zeile — `assignDirections` kehrt still zurück, **ohne Log** | nein |
| 5 | `SwissBankStatementParser.java:549` | PostFinance, `pending` ohne abschliessenden Saldo am Auszugsende, **ohne Log** | nein |
| 6 | `SwissBankStatementParser.java:418` | Generisch/Raiffeisen ohne `Saldovortrag`-Zeile → erste Buchung ungeprüft | nein |
| 7 | `SwissBankStatementParser.java:668` | UBS ohne `Anfangssaldo`-Zeile → älteste Buchung ungeprüft | nein |

AC1 formuliert die Markierung **pro Buchung** und nennt keine Bank; die Zeile «Betrifft das nur
PostFinance oder auch andere Institute mit derselben Saldo-Anker-Logik?» unter *Zu untersuchen*
fragt genau danach. Zwei der vier zusätzlichen Pfade sind schlechter als der beschriebene Fall —
sie loggen nicht einmal. Viseca ist nicht betroffen: dort markiert ein nachgestelltes `-` die
Gutschrift explizit, es wird nichts geraten.

**Entscheid: alle sieben Pfade markieren.** Die Scope-Erweiterung wird im PR-Body deklariert.

## Entscheide

### 1. Teilauflösung pro Buchung, ohne Raten

`assignDirections` bricht heute bei der zweiten passenden Vorzeichenkombination ab und verwirft den
ganzen Block. Stattdessen werden **alle** Lösungen gesammelt und über `orMask`/`andMask`
zusammengefasst: Eine Buchung, deren Bit in allen Lösungen gleich ist, ist eindeutig bestimmt und
wird gesetzt; unsicher sind nur die, bei denen die Lösungen uneins sind.

Beispiel `100/50/50` bei Delta `−100`: die beiden Lösungen sind `−100+50−50` und `−100−50+50`. Die
`100` ist in beiden Belastung und wird als solche gesetzt; nur die beiden `50` bleiben offen. Der
bisherige Code hätte alle drei verworfen.

Das ist **kein Raten**, sondern nur weniger Über-Markierung — und es ist die Lesart, die AC1
ohnehin verlangt («Buchungen, deren Richtung nicht eindeutig bestimmt werden konnte», nicht
«Blöcke»). Speicherbedarf bleibt O(1), die Schleife ist dieselbe 2^k-Schleife wie heute.

Die unter *Zu untersuchen* angerissene **Buchungstext-Heuristik** («Gutschrift», «Lohn»,
«Vergütung») ist bewusst **nicht** Teil dieses Tasks: sie wäre ein Raten, und ein falsch geratenes
Vorzeichen ist doppelt falsch — genau das, was der bestehende Fallback vermeidet.

### 2. `BOOLEAN direction_uncertain`, kein Grund-Enum

Die drei bzw. sieben Pfade bleiben im Log unterscheidbar. Die Oberfläche braucht nur «unsicher
ja/nein», und ein Grund, den niemand liest, wäre eine zweite Wahrheit neben dem Log.

### 3. Nullbeträge bleiben unmarkiert

Buchungen über `0.00` nehmen an der Kombinatorik schon heute nicht teil
(`SwissBankStatementParser.java:557–563`). Ihre Richtung ist unbestimmbar, aber folgenlos: sie
verschieben weder Saldo noch Safe-to-Spend. Sie zu markieren erzeugte Rückfragen ohne jeden Wert —
genau die Geräuschentwicklung, gegen die das bestehende Javadoc argumentiert.

### 4. Korrektur räumt das Flag immer ab

Auch wenn der Nutzer die bestehende Richtung bestätigt: er hat entschieden, damit ist sie sicher.
Erzwungen durch eine einzige Entity-Methode `correctDirection(boolean income)` statt zweier
Setter, die man einzeln vergessen kann.

### 5. Endpoint monatsgebunden

`GET /api/transactions/uncertain?month=YYYY-MM`, wie `/transactions` und `/transactions/summary`.
Ein globaler Zähler im Dashboard behauptete Einfluss auf einen Safe-to-Spend, zu dem eine
März-Buchung nichts beiträgt. Folge und bekannte Einschränkung: unsichere Buchungen älterer Monate
findet man nur über den Monatswechsel der Kategorie-Übersicht.

### 6. Delegierende Convenience-Konstruktoren

`ParsedTransaction` (5-arg) und `Transaction` (8-arg) behalten ihre heutige Signatur und delegieren
mit `directionUncertain = false`. Ohne sie wären 13 Aufrufstellen in 10 Testdateien rein mechanisch
anzufassen; nur der Parser-Pfad setzt das Flag überhaupt.

### 7. Hinweis an zwei Orten

Der Schaden trifft Safe-to-Spend. Ein Nutzer, der die Kategorie-Übersicht nie öffnet, sähe einen
Hinweis, der nur dort steht, nie. Deshalb: Sammelkarte plus Zeilen-Marker auf der
Kategorie-Übersicht **und** ein Warn-Banner an der Safe-to-Spend-Zahl im Dashboard.

## Betroffene Dateien

### Neu (Backend)

| Datei | Zweck |
| ----- | ----- |
| `db/migration/V08__add_direction_uncertain_to_transactions.sql` | `ALTER TABLE transactions ADD COLUMN direction_uncertain BOOLEAN NOT NULL DEFAULT FALSE` |
| `transaction/TransactionDirectionController.java` | `GET /api/transactions/uncertain`, `PUT /api/transactions/{id}/direction` |
| `transaction/TransactionDirectionService.java` | Lesen + Korrektur, User-Einschränkung an der Query |
| `transaction/dto/UpdateDirectionRequest.java` | `record UpdateDirectionRequest(@NotNull Boolean income)` |

### Geändert (Backend)

| Datei | Änderung |
| ----- | -------- |
| `SwissBankStatementParser.java` | `MutableRow.directionUncertain`; `assignDirections` auf Teilauflösung; Markierung an allen sieben Pfaden; die zwei fehlenden Log-Zeilen ergänzen |
| `ParsedTransaction.java` | Komponente `directionUncertain` + 5-arg-Konstruktor |
| `Transaction.java` | Feld/Spalte, Getter, `correctDirection(boolean)`, 8-arg-Konstruktor |
| `ImportJobRunner.java` | Flag aus `ParsedTransaction` durchreichen |
| `TransactionRepository.java` | Query auf die unsicheren Buchungen eines Monats |
| `dto/TransactionResponse.java` | Feld `directionUncertain` |

### Geändert (Frontend)

`transactions/transaction.model.ts` · `transactions/transaction.service.ts` · `transactions/category-overview.{ts,html,scss}` · `dashboard/dashboard.{ts,html,scss}`

## Implementierungsschritte

1. `V08` anlegen; `Transaction`, `ParsedTransaction` und `TransactionResponse` um das Flag
   erweitern, `ImportJobRunner` durchreichen.
2. `assignDirections` umbauen: alle Lösungen sammeln, `orMask`/`andMask` bilden, pro Buchung setzen
   oder als unsicher markieren. Die Warnung nennt ab jetzt die Zahl der *unsicheren* Buchungen, nicht
   die des ganzen Blocks.
3. Die übrigen sechs Pfade markieren; die Pfade 4 und 5 bekommen erstmals eine Log-Zeile.
4. `TransactionDirectionService`, Controller und Request-DTO mit OpenAPI-Annotationen.
5. Repository-Query.
6. Frontend: Model und Service, Sammelkarte «Buchungsrichtung prüfen» plus Zeilen-Marker auf der
   Kategorie-Übersicht, Warn-Banner im Dashboard.
7. `mvn package` und `ng build`.

## Test-Strategie

| Ebene | Test | Deckt ab |
| ----- | ---- | -------- |
| Unit | `SwissBankStatementParserTest`: gemischter Block (Gutschrift + Belastung am selben Tag), mehrere Kombinationen passen zum Delta | **AC4** |
| Unit | Block `100/50/50` bei Delta `−100` → die `100` wird als Belastung gesetzt, nur die beiden `50` bleiben unsicher | Entscheid 1 |
| Unit | je ein Fall für `nicht auflösbar`, `> 16`, Buchungen vor der ersten `Kontostand`-Zeile, `pending` ohne Schlusssaldo, Raiffeisen ohne `Saldovortrag`, UBS ohne `Anfangssaldo` | Scope-Delta |
| Unit | `0.00`-Zeile in einem mehrdeutigen Block bleibt unmarkiert | Entscheid 3 |
| Integration | `TransactionDirectionControllerIntegrationTest`: User B korrigiert eine Buchung von User A → 404; Korrektur setzt `is_income` und räumt das Flag ab; Bestätigen der bestehenden Richtung räumt es ebenfalls ab | **AC1/AC2**, Mandantentrennung |
| Integration | `SafeToSpendServiceIntegrationTest`: unsichere Belastung → Korrektur auf Gutschrift → Safe-to-Spend steigt um genau diesen Betrag | **AC3** |
| Integration | `db/TransactionsMigrationTest`: Spalte `direction_uncertain`, Typ `boolean`, `NOT NULL`, Default `false` | AC1 |
| Integration | `ImportJobRunnerTest`: das Flag aus dem Parser landet in der persistierten Entity | AC1 |
| OpenAPI | `TransactionDirectionOpenApiTest` | DoD |
| Frontend | `category-overview.spec.ts`: Karte erscheint nur bei unsicheren Buchungen, Korrektur löst PUT und Nachladen aus, Marker in der Drilldown-Zeile | AC2 |
| Frontend | `dashboard.spec.ts`: Banner erscheint bei Anzahl > 0, sonst nicht; ein fehlgeschlagener Request verdrängt die Safe-to-Spend-Zahl nicht | AC2 |
| Frontend | `transaction.service.spec.ts`: beide neuen Aufrufe treffen URL und Body | — |

## Acceptance Criteria (aus dem Issue)

- [ ] Buchungen, deren Richtung nicht eindeutig bestimmt werden konnte, sind in der Persistenz als
      unsicher markiert
- [ ] Die Oberfläche weist den Nutzer auf diese Buchungen hin und lässt die Richtung korrigieren —
      analog zur manuellen Kategorie-Korrektur aus US-05
- [ ] Eine Richtungskorrektur schlägt auf Safe-to-Spend durch
- [ ] Ein Test deckt einen gemischten Block ab (Gutschrift + Belastung am selben Tag), bei dem
      mehrere Kombinationen zum Delta passen
