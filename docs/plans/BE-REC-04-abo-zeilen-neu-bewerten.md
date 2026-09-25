# [BE-REC-04] Abo-Zeilen beim Import neu bewerten statt bekannte Empfänger zu überspringen

- **Issue:** [#350](https://github.com/dfme/budget-buddy/issues/350)
- **Task-ID:** `BE-REC-04`
- **Branch:** `feature/BE-REC-04-abo-zeilen-neu-bewerten`
- **Story:** US-08 — Wiederkehrende Ausgaben (Abos) erkennen
- **Sprint:** — (im Board nicht gesetzt; laufende Iteration zum Zeitpunkt der Planung: Sprint 7)
- **Bestätigt am:** 2026-09-22

## Ausgangslage

`RecurringExpenseService.detect` überspringt jeden Empfänger, für den bereits eine Zeile
existiert — in beiden Status. Die Folge sind zwei Fehler, die erst seit FE-FC-05 (#338) Geld
kosten, weil die Zeile seither ein Eingabewert des Safe-to-Spend ist:

1. `amount` bleibt die Momentaufnahme des ersten qualifizierenden Paars. Ein Preissprung über
   ±2 % wird nie nachgezogen.
2. Eine beendete Abo-Reihe bleibt `DETECTED`. Ein gekündigtes Netflix mindert den Safe-to-Spend
   dauerhaft weiter.

PR #345 hat beides mit zwei Grenzen abgefangen — Toleranzband beim Streichen, Aktivitätsfenster
in `RecurringExpenseAmountPort.detectedAmounts` — ohne die Wurzel zu lösen (ADR-13, Nachtrag
FE-FC-05, Abschnitt «Grenzen»). Dieser Task bewertet die Zeilen beim Import neu.

## Entscheide

### 1. «Beendet» wird ein dritter Status `ENDED`, kein Feld

V11 hält im Tabellenkommentar ausdrücklich fest, dass ein dritter Status «eine Migration — für
eine Zustandsmaschine die richtige Hürde» ist. Ein Feld neben dem Status ergäbe zwei Dimensionen
für einen Zustand, und `detectedAmounts` müsste beide lesen. Mit `ENDED` bleibt es beim einen
`findByUserIdAndStatus(DETECTED)`, und der Rückbau aus AC5 fällt mechanisch heraus.

`DISMISSED` bleibt unangetastet die Aussage «war nie ein Abo» und wird für «beendet» nicht
missbraucht — die ausdrückliche Vorgabe aus AC2.

Zustandsmaschine:

```
(neu)              → DETECTED | ENDED      Erkennung, mit Bündel-Notification
DETECTED           → DETECTED              Betrag/Erstmonat aktualisiert, keine Notification
DETECTED           ⇄ ENDED                 Aktivität entscheidet, keine Notification
DETECTED | ENDED   → DISMISSED             nur durch den Nutzer (dismiss)
DISMISSED          → DISMISSED             terminal, wird nie neu bewertet
```

### 2. Bezugsmonat für «aktiv» ist der jüngste Monat der Historie, nicht die Uhr

Der Entscheid mit der grössten Tragweite. «Beendet» heisst *«das Konto lief weiter, dieser
Empfänger nicht»*. Endet schlicht die Datenlage, gibt es keinen Beleg in eine der beiden
Richtungen — und die Vorannahme bei Abos ist Weiterlaufen, sie verlängern sich von selbst.

Der Uhr-Anker hätte zwei konkrete Folgen:

- Ein nachgereichter Jahresauszug 2025 erzeugte lauter sofort beendete Abos.
- Die E2E-Fixtures (Juni–August 2025, `kontoauszug-abo-erkennung.pdf`) kippten geschlossen auf
  `ENDED`; `e2e/tests/recurring-expenses.spec.ts` bräche, ohne dass fachlich etwas falsch wäre.

Zusätzlich macht der Historien-Anker `detect` uhrenfrei und damit ohne `Clock`-Stellschraube
testbar. Die Fensterbreite bleibt 3 Monate, nur die Begründung wechselt die Seite: nicht mehr
«Auszüge kommen rückdatiert», sondern «zwei ausgelassene Monate sind noch kein
Kündigungsindiz».

### 3. Bewusste Verhaltensänderung: kein Import heisst nicht mehr «keine Abzüge»

Heute verliert ein Nutzer, der drei Monate nichts importiert, seine Abo-Abzüge (ADR-13-Nachtrag,
«Nachlauf von bis zu zwei Monaten»). Künftig behält er sie: ohne neue Daten wird nichts neu
bewertet, und die Zeile bleibt `DETECTED`.

Das ist die richtige Richtung. ADR-13 nennt «Safe-to-Spend zu hoch» ausdrücklich die
unangenehmere Fehlerrichtung — sie sagt dem Nutzer, er habe mehr Spielraum als er hat — und
genau die erzeugte das Lese-Fenster. Die Änderung wird im ADR-Nachtrag und im PR-Body benannt.

### 4. `amount` **und** `firstDetectedMonth` werden aktualisiert

AC1 nennt nur `amount`. `qualify()` setzt den Erstmonat aber bei einer Lücke *und* bei einem
Preissprung neu (Review PR #298) — eine Zeile mit dem April-Betrag und «seit Januar» trüge zwei
Felder aus zwei verschiedenen Reihen. Beide kommen deshalb aus demselben `Detection`-Ergebnis.

Liefert `qualify()` für eine bekannte Zeile nichts (nur möglich, wenn die Historie schrumpft),
bleibt die Zeile inhaltlich unverändert; über `DETECTED`/`ENDED` entscheidet trotzdem die
Aktivität.

### 5. Notifications bleiben unberührt (AC4)

Nur *neue* Empfänger gehen ins Bündel. `bundleIsClosed` bleibt Zeile für Zeile wie es ist — die
Bündel-Logik aus FE-NOTIF-04 ist ausdrücklich nicht Gegenstand dieses Tasks.

### 6. Frontend bekommt einen Abschnitt «Beendet»

Ohne ihn verschwände eine `ENDED`-Zeile von `/ausgaben`: das Frontend filtert hart auf
`DETECTED` und `DISMISSED`. Der Abschnitt folgt dem Muster «Kein Abo» (FE-NOTIF-03) — ohne
Button, ohne «Neu». Damit hat auch der Klick auf eine noch ungelesene Bündel-Benachrichtigung
weiterhin ein Ziel.

## Betroffene Dateien

### Neu

| Datei | Zweck |
| --- | --- |
| `backend/src/main/resources/db/migration/V16__add_ended_status_to_recurring_expenses.sql` | CHECK-Constraint auf `('DETECTED','DISMISSED','ENDED')` erweitern |

### Backend geändert

| Datei | Änderung |
| --- | --- |
| `recurring/RecurringExpenseStatus.java` | `ENDED` + Javadoc |
| `recurring/RecurringExpense.java` | `updateFrom(amount, firstMonth)`, `markEnded()`, `markActive()` |
| `recurring/RecurringExpenseService.java` | `detect` bewertet bekannte Zeilen statt sie zu überspringen; `detectedAmounts` ohne Fenster und ohne `month` |
| `recurring/RecurringExpenseAmountPort.java` | `detectedAmounts(long)`; `ACTIVE_WINDOW_MONTHS` raus; «Momentaufnahme»-Absatz raus |
| `recurring/RecurringExpenseDetectionPort.java` | Javadoc: bekannte Zeilen werden bewertet |
| `recurring/RecurringExpenseRepository.java` | Javadoc `findByUserId` |
| `recurring/dto/RecurringExpenseResponse.java` | Javadoc `status` |
| `transaction/ExpenseHistoryPort.java`, `ExpenseHistoryService.java` | gefensterte Überladung entfällt (einziger Produktivaufrufer fällt weg) |
| `budget/SafeToSpendService.java` | Aufruf ohne `month`; Javadoc |
| `budget/FixedCostDebitMatcher.java` | Javadoc |

### Frontend geändert

| Datei | Änderung |
| --- | --- |
| `recurring/recurring-expense.model.ts` | `status: 'DETECTED' \| 'DISMISSED' \| 'ENDED'` |
| `recurring/recurring-expense.service.ts` | `ended` computed |
| `recurring/recurring-expense-list.ts/.html/.scss` | Abschnitt «Beendet» |

### Dokumentation

`docs/adr/ADR-13-fixkosten-transaktions-zuordnung.md` (Festlegung 7, beide Grenzen-Punkte,
Nachtrag BE-REC-04), `docs/requirements/US-08-wiederkehrende-ausgaben.md`.

Dazu die neun Javadoc-Stellen, die die breite Suche über
`Momentaufnahme|nie aktualisiert|bekannte Empfänger|übersprungen|ACTIVE_WINDOW|Nachlauf`
gefunden hat und die AC5 nicht aufführt — das AC ist zu eng formuliert, nicht die Umsetzung.

## Implementierungsschritte

1. V16 schreiben (Constraint droppen und mit drei Werten neu anlegen), `scripts/check-migrations.sh`.
2. `RecurringExpenseStatus.ENDED` + Entity-Methoden.
3. `detect(userId)` umbauen:
   - Historie einmal laden; `DISMISSED`-Schlüssel als Ausschlussmenge; `latestMonth` = Maximum
     über die Historie.
   - Je Empfänger (ausser `DISMISSED`) `qualify()`; aktiv = letzte Belastung ≥ `latestMonth − 2`.
   - Bestehende Zeile: `updateFrom(...)` falls `qualify` trifft, dann Status setzen — kein
     Insert, keine Notification.
   - Neuer Empfänger: nur wenn `qualify` trifft → ins Bündel, Zeile mit dem errechneten Status.
   - Log-Zeile auf neu/aktualisiert/beendet umstellen, weiterhin nur Zähler.
4. `detectedAmounts(userId)` auf `findByUserIdAndStatus(DETECTED)` reduzieren; Port-Signatur,
   `SafeToSpendService` und `FixedCostDebitMatcher`-Javadoc nachziehen.
5. Gefensterte `expenseHistory` aus Port, Service und `ExpenseHistoryServiceTest` entfernen.
6. Frontend: Modell, `ended`-Computed, dritter Abschnitt.
7. Doku: ADR-13, US-08, die neun Javadoc-Stellen.

## Test-Strategie

| Ebene | Fälle |
| --- | --- |
| `RecurringExpenseServiceTest` (Unit) | Preissprung >2 % aktualisiert `amount` **und** `firstDetectedMonth`; unveränderter Betrag schreibt keine Notification; Empfänger ohne Belastung im Fenster → `ENDED`; `ENDED` + neue Belastung → zurück auf `DETECTED`, weiterhin ohne Notification; `DISMISSED` wird weder bewertet noch reaktiviert (AC3); neu erkannter Alt-Empfänger entsteht direkt als `ENDED`; `detectedAmounts` liefert `DETECTED` und lädt keine Historie mehr |
| `RecurringExpenseDetectionIntegrationTest` | zwei Importe hintereinander: der zweite aktualisiert den Betrag, legt keine zweite Zeile und keine zweite Notification an |
| `RecurringExpensesMigrationTest` | `INSERT` mit `'ENDED'` geht durch, mit `'FOO'` fällt um |
| `SafeToSpendServiceIntegrationTest` | bisheriger Fenster-Test auf «Zeile steht auf `ENDED`» umgestellt; neuer Fall für die Verhaltensänderung aus Entscheid 3 |
| `SafeToSpendServiceTest` | Signatur ohne `month` |
| Frontend (Vitest/TestBed) | `ended` trennt sauber von `detected`/`dismissed`; Abschnitt «Beendet» erscheint nur mit Einträgen, ohne Button, ohne «Neu» |
| E2E | keine neue Spec; Vorgabe ist, dass `recurring-expenses.spec.ts` unverändert grün bleibt (Entscheid 2) |

## Acceptance Criteria (aus dem Issue)

- [ ] Ein bereits bekannter Empfänger (`DETECTED`) wird bei jedem Erkennungslauf gegen die
      Historie neu geprüft: qualifiziert ein jüngeres Paar mit abweichendem Betrag, wird
      `amount` auf den Betrag des jüngsten Paars aktualisiert (Preissprung über 2 % eingeschlossen)
- [ ] Eine `DETECTED`-Zeile ohne Abbuchung des Empfängers in den letzten N Monaten gilt als
      beendet und mindert den Safe-to-Spend nicht mehr
- [ ] `DISMISSED`-Empfänger werden weiterhin nie neu erkannt (US-08 AC3, unverändert)
- [ ] Eine Aktualisierung erzeugt keine neue «Neu»-Benachrichtigung; die Bündel-Logik aus
      FE-NOTIF-04 bleibt unverändert
- [ ] Das Aktivitätsfenster in `RecurringExpenseAmountPort.detectedAmounts` und der Punkt
      «Abbuchung ausserhalb des Bands zählt doppelt» im ADR-13-Nachtrag werden gegenstandslos und
      entsprechend zurückgebaut bzw. nachgezogen
