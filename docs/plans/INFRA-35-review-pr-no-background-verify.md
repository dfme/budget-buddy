# [INFRA-35] Automatisches PR-Review verstummt bei Hintergrund-Verifikation im nicht-interaktiven Lauf

- **Issue:** [#224](https://github.com/dfme/budget-buddy/issues/224)
- **Task-ID:** `INFRA-35`
- **Branch:** `fix/INFRA-35-review-pr-no-background-verify`
- **Story:** — (kein us-*-Label)
- **Sprint:** Sprint 5
- **Bestätigt am:** 2026-08-29

## Kontext

Der automatische Review-Agent (INFRA-31, #215) lief bei PR #223 (Lauf 33239595160) im
nicht-interaktiven GitHub-Actions-Kontext still ins Leere: der Agent startete den
Verifikationsbefehl (`./mvnw package`) im Hintergrund und beendete die Session danach, ohne auf
das Ergebnis zu warten. In der interaktiven CLI holt eine spätere Gesprächsrunde die
Hintergrund-Benachrichtigung ab — die GitHub Action führt aber einen einzelnen, einmaligen
SDK-Query aus. Ohne Folge-Runde geht das Ergebnis verloren; für den Commit `821ec46` existiert
kein Review-Objekt, obwohl der SDK-Query mit `is_error: false` endete. Der Fortschritts-Kommentar
der Action hängt trotzdem unbedingt ein "Claude finished …"-Banner an, wodurch der stille
Fehlschlag wie ein Erfolg aussieht.

## Entscheidungen

- Die Ausnahme wird als expliziter Text in `SKILL.md` Schritt 4 verankert (analog zum
  bestehenden Muster in Schritt 7), nicht nur als Kommentar im Workflow-Prompt — der Skill ist
  die Quelle, die auch der lokale interaktive Lauf liest, und dort muss die Regel unmissverständlich
  auf `GITHUB_ACTIONS` bedingt sein: lokal bleibt Hintergrundausführung sinnvoll (lange Läufe,
  Rückmeldung in einer späteren Runde), im Actions-Kontext ist sie ein Datenverlust.
- Der Workflow bekommt zusätzlich einen Guard (Gürtel-zu-Hosenträger-Prinzip, wie bereits bei der
  Bestätigungsgate-Ausnahme in INFRA-34 praktiziert): ein Prompt-Text allein garantiert kein
  Verhalten. Der Guard prüft nach dem Claude-Action-Schritt über die REST-API, ob für den
  auslösenden Head-Commit tatsächlich ein Review-Objekt existiert, und lässt den Job sonst
  sichtbar fehlschlagen statt still grün durchzulaufen.
- Kein neuer Retry- oder Wiederholungsmechanismus: ein fehlgeschlagener automatischer Lauf ist
  nach CLAUDE.md-Konvention (Review-Konvention Punkt 1–3) ohnehin nur eine Ergänzung zum
  menschlichen Review — ein sichtbar roter Job genügt, damit das Team es bemerkt und bei Bedarf
  manuell reviewt.

## Betroffene Files

- `.claude/skills/review-pr/SKILL.md` — geändert (Schritt 4, "Harte Grenzen")
- `.github/workflows/claude-pr-review.yml` — geändert (neuer Guard-Schritt)

## Implementierungsschritte

1. `SKILL.md` Schritt 4: neuer Unterabschnitt "Ausnahme: nicht-interaktive Läufe — synchron statt
   im Hintergrund" nach dem bestehenden Verifikations-Codeblock. Begründung mit dem
   PR-223-Vorfall, Tabelle `GITHUB_ACTIONS` gesetzt/nicht gesetzt → Hintergrund erlaubt/verboten.
2. `SKILL.md` "Harte Grenzen": die Aussage "Das ist die einzige Grenze mit einer solchen
   Ausnahme" korrigieren — es existieren jetzt zwei `GITHUB_ACTIONS`-bedingte Ausnahmen (Schritt 7
   Bestätigungsgate, Schritt 4 synchrone Ausführung).
3. `claude-pr-review.yml`: neuer Schritt nach "Run /review-pr", der über
   `gh api repos/{repo}/pulls/{number}/reviews` prüft, ob ein Review mit
   `commit_id == github.event.pull_request.head.sha` existiert; fehlt es, `exit 1` mit
   `::error::`-Annotation.
4. Manuelle Verifikation: Test-PR öffnen, automatischen Lauf beobachten, bestätigen dass entweder
   kein Backgrounding mehr auftritt oder der Guard bei einem fehlenden Review zuverlässig
   anschlägt.

## Test-Strategie

Kein automatisiertes Testharness für Skill-Prompt-Text/YAML (siehe DoD im Issue). Verifikation
ausschliesslich manuell über einen realen Test-PR in CI. Zusätzlich `mvn package`/`ng build` zur
Kontrolle, dass die App selbst unberührt bleibt (keine App-Code-Änderung in diesem PR).

## Acceptance Criteria (aus Issue #224)

- [ ] `SKILL.md` Schritt 4 verlangt im nicht-interaktiven Lauf synchrone Ausführung der
      Verifikationsbefehle statt Hintergrundausführung, mit Begründung. Lokaler, interaktiver Lauf
      bleibt unverändert.
- [ ] Die Aussage "Das ist die einzige Grenze mit einer solchen Ausnahme" ist korrigiert.
- [ ] `claude-pr-review.yml` prüft nach dem Claude-Action-Schritt, ob für den aktuellen Head-SHA
      ein Review-Objekt existiert; fehlt es, schlägt der Job sichtbar fehl.
- [ ] Manuell verifiziert: Testlauf mit künstlich verlangsamtem Verifikationsschritt zeigt kein
      Backgrounding mehr im nicht-interaktiven Modus, oder der neue Guard schlägt zuverlässig an.
