# [INFRA-09] Smoke-Test-Timeout begrenzt Versuche statt Zeit

- **Issue:** #71
- **Task-ID:** INFRA-09
- **Branch:** `fix/INFRA-09-smoke-test-deadline`
- **Typ:** Bug (Label `bug`) — Defekt im Fix aus #68 / PR #70
- **Vorgeschichte:** #68 (INFRA-08) verifiziert die deployte Version; dieser Fix korrigiert
  das dort eingebaute Timeout und poliert Logging.

## Problem

`cd.yml` sagt `max_attempts=80   # 80 × 15s = 1200s = 20 min` zu. Die Rechnung unterschlägt,
dass jeder Versuch zusätzlich bis zu `--max-time 10` pro curl kostet:

| Fall | pro Versuch | × 80 |
| --- | --- | --- |
| Annahme im Kommentar | 15s | 20 min |
| curl scheitert sofort (Connection refused) | ~15.2s (gemessen) | ~20 min ✓ |
| curl läuft in den Timeout (Service hängt) | bis 25s | **bis 33 min** ✗ |

Ab 25 min greift `timeout-minutes: 25` und killt den Job — die vorbereitete `::error::`-Meldung
mit Diagnose-Hinweis erscheint dann nie. Statt einer verständlichen Fehlermeldung sieht man
einen abgewürgten Job.

## Entscheide

- **Wall-Clock-Deadline statt Versuchszähler.** `deadline=$(( $(date +%s) + timeout_seconds ))`,
  Schleife `while [ "$(date +%s)" -lt "$deadline" ]`. Die zugesagte Obergrenze gilt damit
  unabhängig vom curl-Verhalten. Restüberschreitung: max. ein sleep + ein curl-Timeout
  (~35s) nach Ablauf, da die Bedingung vor jeder Iteration greift.
- **Timeout 20 → 15 min**, jetzt belegt statt geschätzt. Gemessen im ersten echten Lauf
  ([29409594339](https://github.com/dfme/budget-buddy/actions/runs/29409594339)): **5m53s**
  vom Hook bis `health 200` mit neuem Commit. 15 min = 2.5× Puffer.
  - Gegen 12 min: n=1, Streuung unbekannt (Build-Queue, Registry); der Build wächst über
    das Semester — bei +50% wären 12 min nur noch 1.4× Puffer.
  - Gegen 20 min: unnötig, jetzt wo die Zahl vorliegt.
- **Airbag 25 → 18 min.** Muss über der Deadline + Überschreitung (~15.6 min) liegen, aber
  eng genug, um bei echtem Hänger zu greifen.
- **Erfolgsmeldung loggt `$deployed`** (gemeldeter Commit) statt `$GITHUB_SHA` (erwarteter).
  Konkreter Anlass: Um das SHA-Format von `RENDER_GIT_COMMIT` zu klären, musste die Live-App
  abgefragt werden — im Log stand nur der erwartete Wert. (Ergebnis: voller 40-Zeichen-SHA.)
- **Log-Throttling:** Nur bei Zustandswechsel loggen (unerreichbar → alte Version → neue
  Version → health-Problem) plus jeden 4. Versuch als Lebenszeichen. Der letzte Lauf
  produzierte 23 identische Zeilen.
- **Präfix-Vergleich bleibt.** Render liefert zwar den vollen SHA (verifiziert), das Format
  ist aber weiterhin nicht dokumentiert. Die Toleranz kostet nichts und schützt vor einem
  stillen Formatwechsel.

## Betroffene / neue Files

- **Ändern:** `.github/workflows/cd.yml` — Deadline, Timeout, Airbag, Logging
- **Neu:** `docs/plans/INFRA-09-smoke-test-deadline.md` (dieser Plan)

Keine Backend-Änderung: `/actuator/info` funktioniert unverändert.

## Test-Strategie

1. **Deadline hält bei hängendem Server** — Fake-Server, der TCP annimmt und nicht antwortet
   (erzwingt curl `--max-time`). Erwartung: Abbruch nahe der Deadline, **nicht** erst nach
   `n × max-time`. Dieser Fall reproduziert den Bug.
2. **Gegenprobe alte Logik** — gleicher Server, Versuchszähler-Variante: muss die Obergrenze
   deutlich überschreiten.
3. **Happy Path** — Fake-Deploy (alt → down → neu): grün erst beim neuen SHA.
4. **Log-Throttling** — identische Zustände erzeugen keine Zeilenflut, Zustandswechsel wird
   sofort geloggt.
5. **Erfolgsmeldung** zeigt den gemeldeten Commit.
6. `bash -n` + YAML-Parse; `./mvnw verify` als Regressionsschutz (unverändert grün erwartet).
7. **Einschränkung:** wie bei INFRA-08 nur gegen lokalen Fake verifizierbar; der echte Beweis
   ist der CD-Lauf nach dem Merge.

## Acceptance Criteria (aus Issue #71)

- [ ] Die zugesagte Timeout-Obergrenze gilt unabhängig vom curl-Verhalten (Wall-Clock-Deadline)
- [ ] Bei Timeout erscheint die Fehlermeldung mit Diagnose-Hinweis, der Job wird nicht vom
      Airbag gekillt
- [ ] Das Log zeigt bei Erfolg den von der App **gemeldeten** Commit
- [ ] Timeout auf 15 min gesenkt, Begründung im Code nachvollziehbar
- [ ] Log ohne Dutzende identischer Zeilen
- [ ] `mvnw verify` bleibt grün
